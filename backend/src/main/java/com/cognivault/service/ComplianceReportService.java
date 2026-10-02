package com.cognivault.service;

import com.cognivault.api.ComplianceDtos;
import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditLedgerService;
import com.cognivault.domain.*;
import com.cognivault.lineage.DataLineageDocument;
import com.cognivault.lineage.DataLineageRepository;
import com.cognivault.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class ComplianceReportService {

    private final AuditEventRepository auditEventRepository;
    private final AnomalyFindingRepository anomalyFindingRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final DataLineageRepository dataLineageRepository;
    private final AuditLedgerService auditLedgerService;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmployeeService employeeService;

    public ComplianceReportService(
            AuditEventRepository auditEventRepository,
            AnomalyFindingRepository anomalyFindingRepository,
            ApprovalRequestRepository approvalRequestRepository,
            DataLineageRepository dataLineageRepository,
            AuditLedgerService auditLedgerService,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            EmployeeService employeeService) {
        this.auditEventRepository = auditEventRepository;
        this.anomalyFindingRepository = anomalyFindingRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.dataLineageRepository = dataLineageRepository;
        this.auditLedgerService = auditLedgerService;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.employeeService = employeeService;
    }

    public ComplianceDtos.ComplianceReport generateReport(
            Instant from,
            Instant to,
            String entityType,
            String entityId,
            Authentication authentication) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("From and to timestamps are required.");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("The report end time must be after the start time.");
        }

        Scope scope = resolveScope(authentication, entityType, entityId);

        List<AuditEventDocument> auditEvents = auditEventRepository.findAll().stream()
                .filter(event -> !event.getTimestamp().isBefore(from))
                .filter(event -> !event.getTimestamp().isAfter(to))
                .filter(event -> matchesScope(event.getEntityType(), event.getEntityId(), scope))
                .sorted(Comparator.comparing(AuditEventDocument::getTimestamp).reversed())
                .toList();

        List<AnomalyFinding> findings = anomalyFindingRepository.findAll().stream()
                .filter(finding -> finding.getFlaggedAt() != null)
                .filter(finding -> !finding.getFlaggedAt().isBefore(from))
                .filter(finding -> !finding.getFlaggedAt().isAfter(to))
                .filter(finding -> matchesEmployeeScope(finding.getEmployeeId(), scope))
                .sorted(Comparator.comparing(AnomalyFinding::getFlaggedAt).reversed())
                .toList();

        List<ApprovalRequest> approvals = approvalRequestRepository.findAll().stream()
                .filter(request -> request.getCreatedAt() != null)
                .filter(request -> !request.getCreatedAt().isBefore(from))
                .filter(request -> !request.getCreatedAt().isAfter(to))
                .filter(request -> matchesApprovalScope(request, scope))
                .sorted(Comparator.comparing(ApprovalRequest::getCreatedAt).reversed())
                .toList();

        List<DataLineageDocument> lineageEntries = dataLineageRepository.findAll().stream()
                .filter(entry -> entry.getTimestamp() != null)
                .filter(entry -> !entry.getTimestamp().isBefore(from))
                .filter(entry -> !entry.getTimestamp().isAfter(to))
                .filter(entry -> matchesLineageScope(entry, scope))
                .sorted(Comparator.comparing(DataLineageDocument::getTimestamp).reversed())
                .toList();

        AuditLedgerService.IntegrityResult integrity = auditLedgerService.verify();

        ComplianceDtos.AuditSummary auditSummary = summarizeAudit(auditEvents);
        ComplianceDtos.IntegritySummary integritySummary = summarizeIntegrity(integrity, authentication, scope, entityType);
        ComplianceDtos.RiskSummary riskSummary = summarizeFindings(findings);
        ComplianceDtos.ApprovalSummary approvalSummary = summarizeApprovals(approvals);
        ComplianceDtos.LineageSummary lineageSummary = summarizeLineage(lineageEntries);

        return new ComplianceDtos.ComplianceReport(
                Instant.now(),
                from,
                to,
                scope.scopeName(),
                scope.entityType(),
                scope.entityId(),
                auditSummary,
                integritySummary,
                riskSummary,
                approvalSummary,
                lineageSummary);
    }

    private ComplianceDtos.AuditSummary summarizeAudit(List<AuditEventDocument> events) {
        Map<String, Long> actions = new TreeMap<>();
        Map<String, Long> entityTypes = new TreeMap<>();
        for (var event : events) {
            actions.merge(event.getAction(), 1L, Long::sum);
            entityTypes.merge(event.getEntityType(), 1L, Long::sum);
        }
        return new ComplianceDtos.AuditSummary(
                events.size(),
                actions,
                entityTypes,
                events.isEmpty() ? "EMPTY" : "AVAILABLE");
    }

    private ComplianceDtos.IntegritySummary summarizeIntegrity(
            AuditLedgerService.IntegrityResult result,
            Authentication authentication,
            Scope scope,
            String entityType) {
        String status = result == null ? "UNAVAILABLE" : result.status();
        String message = result == null ? "Integrity information is unavailable." : result.message();
        String eventId = null;
        if (result != null && (isGlobalRole(authentication) || isScopedEmployeeAccessAllowed(authentication, entityType, scope))) {
            eventId = result.affectedEventId();
        }

        return new ComplianceDtos.IntegritySummary(
                status,
                message,
                eventId,
                result == null ? 0 : result.totalEventsChecked(),
                result != null);
    }

    private ComplianceDtos.RiskSummary summarizeFindings(List<AnomalyFinding> findings) {
        Map<String, Long> byRiskLevel = new TreeMap<>();
        for (var finding : findings) {
            if (finding.getRiskLevel() != null) {
                byRiskLevel.merge(finding.getRiskLevel().name(), 1L, Long::sum);
            }
        }
        return new ComplianceDtos.RiskSummary(
                findings.size(),
                byRiskLevel,
                findings.isEmpty() ? "EMPTY" : "AVAILABLE",
                "Investigative risk indicators only; not proof of fraud.");
    }

    private ComplianceDtos.ApprovalSummary summarizeApprovals(List<ApprovalRequest> approvals) {
        long pending = 0L;
        long approved = 0L;
        long rejected = 0L;
        Map<String, Long> byRequestType = new TreeMap<>();

        for (var request : approvals) {
            if (request.getStatus() == ApprovalStatus.PENDING) {
                pending++;
            } else if (request.getStatus() == ApprovalStatus.APPROVED) {
                approved++;
            } else if (request.getStatus() == ApprovalStatus.REJECTED) {
                rejected++;
            }
            if (request.getRequestType() != null) {
                byRequestType.merge(request.getRequestType().name(), 1L, Long::sum);
            }
        }

        return new ComplianceDtos.ApprovalSummary(
                pending,
                approved,
                rejected,
                byRequestType,
                approvals.isEmpty() ? "EMPTY" : "AVAILABLE");
    }

    private ComplianceDtos.LineageSummary summarizeLineage(List<DataLineageDocument> entries) {
        return new ComplianceDtos.LineageSummary(
                entries.size(),
                entries,
                entries.isEmpty() ? "EMPTY" : "AVAILABLE");
    }

    private Scope resolveScope(Authentication authentication, String entityType, String entityId) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication required for compliance reporting.");
        }

        boolean globalAccess = isGlobalRole(authentication);
        if (globalAccess) {
            return new Scope("GLOBAL", null, null, Set.of());
        }

        String normalizedType = normalize(entityType);
        if (normalizedType == null || normalizedType.isBlank()) {
            if (hasRole(authentication, "EMPLOYEE")) {
                UUID employeeId = resolveCurrentEmployeeId(authentication);
                return new Scope("EMPLOYEE", "EMPLOYEE", employeeId.toString(), Set.of(employeeId.toString()));
            }
            if (hasRole(authentication, "HR")) {
                Set<String> workforceEmployeeIds = employeeRepository.findAllEmployeeIds()
                        .stream()
                        .map(UUID::toString)
                        .collect(Collectors.toSet());
                return new Scope("WORKFORCE", "EMPLOYEE", null, workforceEmployeeIds);
            }

            if (hasRole(authentication, "MANAGER")) {
                Set<String> teamEmployeeIds = resolveManagedEmployeeIds(authentication);
                if (teamEmployeeIds.isEmpty()) {
                    throw new AccessDeniedException("No employee scope is available for this user.");
                }
                return new Scope("TEAM", "EMPLOYEE", null, teamEmployeeIds);
            }
            throw new AccessDeniedException("This role cannot access compliance reporting.");
        }

        if (!"EMPLOYEE".equals(normalizedType)) {
            throw new AccessDeniedException("Only employee-scoped compliance data is available for this role.");
        }

        if (entityId == null || entityId.isBlank()) {
            throw new AccessDeniedException("An employee ID is required for scoped compliance access.");
        }

        UUID employeeId = UUID.fromString(entityId);
        employeeService.assertVisible(employeeId, authentication);
        return new Scope("EMPLOYEE", "EMPLOYEE", employeeId.toString(), Set.of(employeeId.toString()));
    }

    private UUID resolveCurrentEmployeeId(Authentication authentication) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found."));
        return employeeRepository.findEmployeeIdByUserId(user.getId())
                .orElseThrow(() -> new AccessDeniedException("No employee profile is linked to this account."));
    }

    private Set<String> resolveManagedEmployeeIds(Authentication authentication) {
        UserAccount user = userAccountRepository.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found."));
        return employeeRepository.findAllByManagerUserId(user.getId(), Pageable.unpaged())
                .stream()
                .map(Employee::getId)
                .map(UUID::toString)
                .collect(Collectors.toSet());
    }

    private boolean matchesScope(String entityType, String entityId, Scope scope) {
        if (scope.employeeIds().isEmpty()) {
            return true;
        }
        if (entityType == null || entityType.isBlank() || entityId == null || entityId.isBlank()) {
            return false;
        }
        return "EMPLOYEE".equalsIgnoreCase(entityType) && scope.employeeIds().contains(entityId);
    }

    private boolean matchesApprovalScope(ApprovalRequest request, Scope scope) {
        if (scope.employeeIds().isEmpty()) {
            return true;
        }
        String target = request.getTarget();
        if (target != null && scope.employeeIds().contains(target)) {
            return true;
        }
        if (request.getRequester() != null && request.getRequester().getId() != null) {
            return false;
        }
        return false;
    }

    private boolean matchesEmployeeScope(UUID employeeId, Scope scope) {
        if (scope.employeeIds().isEmpty()) {
            return true;
        }
        return employeeId != null && scope.employeeIds().contains(employeeId.toString());
    }

    private boolean matchesLineageScope(DataLineageDocument entry, Scope scope) {
        if (scope.employeeIds().isEmpty()) {
            return true;
        }
        Set<String> ids = scope.employeeIds();
        return ids.contains(entry.getSourceEntityId()) || ids.contains(entry.getTargetEntityId());
    }

    private boolean isGlobalRole(Authentication authentication) {
        return hasRole(authentication, "ADMIN")
                || hasRole(authentication, "AUDITOR")
                || hasRole(authentication, "COMPLIANCE_OFFICER");
    }

    private boolean isScopedEmployeeAccessAllowed(Authentication authentication, String entityType, Scope scope) {
        if (authentication == null) {
            return false;
        }
        if (isGlobalRole(authentication)) {
            return true;
        }
        if (scope != null && scope.entityType() != null && "EMPLOYEE".equals(scope.entityType())) {
            return true;
        }
        return entityType != null && "EMPLOYEE".equalsIgnoreCase(entityType);
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role)
                        || authority.getAuthority().equals(role));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private record Scope(String scopeName, String entityType, String entityId, Set<String> employeeIds) {
    }
}
