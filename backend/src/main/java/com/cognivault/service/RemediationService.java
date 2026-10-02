package com.cognivault.service;

import com.cognivault.api.ApprovalDtos;
import com.cognivault.api.EmployeeDtos;
import com.cognivault.api.RemediationDtos;
import com.cognivault.domain.*;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.ApprovalRequestRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RemediationService {

    private final AnomalyFindingRepository findings;
    private final ApprovalRequestRepository approvals;
    private final ApprovalService approvalService;
    private final EmployeeService employees;
    private final RollbackService rollbackService;

    public RemediationService(
            AnomalyFindingRepository findings,
            ApprovalRequestRepository approvals,
            ApprovalService approvalService,
            EmployeeService employees,
            RollbackService rollbackService) {
        this.findings = findings;
        this.approvals = approvals;
        this.approvalService = approvalService;
        this.employees = employees;
        this.rollbackService = rollbackService;
    }

    @Transactional
    public RemediationDtos.RemediationResponse create(
            RemediationDtos.CreateRequest request,
            Authentication authentication) {
        requireRole(authentication, "ADMIN", "HR", "MANAGER");
        if (request == null || request.employeeId() == null || request.action() == null
                || request.justification() == null || request.justification().isBlank()
                || request.sourceFindingEventId() == null || request.sourceFindingEventId().isBlank()) {
            throw new IllegalArgumentException("A target, persisted finding, action, and justification are required");
        }

        var finding = findings.findByEventId(request.sourceFindingEventId())
                .orElseThrow(() -> new IllegalArgumentException("Stored anomaly finding was not found"));
        if (!request.employeeId().equals(finding.getEmployeeId())) {
            throw new AccessDeniedException("The finding does not belong to the requested employee");
        }

        RemediationDtos.Action action = request.action();
        if ((action == RemediationDtos.Action.CREDENTIAL_RESET || action == RemediationDtos.Action.ROLLBACK)
                && !hasRole(authentication, "ADMIN")) {
            throw new AccessDeniedException("Only administrators may request this remediation action");
        }
        if (hasRole(authentication, "MANAGER") || hasRole(authentication, "HR")) {
            employees.assertVisible(request.employeeId(), authentication);
        }

        Map<String, Object> requestedState = switch (action) {
            case ACCOUNT_STATUS_CHANGE -> {
                String accountAction = request.accountAction() == null ? "" : request.accountAction().toUpperCase();
                if (!List.of("ACTIVATE", "REACTIVATE", "SUSPEND", "DEACTIVATE").contains(accountAction)) {
                    throw new IllegalArgumentException("A supported account action is required");
                }
                yield Map.of("action", accountAction, "reason", request.justification());
            }
            case CREDENTIAL_RESET -> Map.of("generateTemporary", false, "forceChange", request.forceChange());
            case ROLLBACK -> {
                if (request.rollbackTimestamp() == null) {
                    throw new IllegalArgumentException("Rollback timestamp is required");
                }
                yield rollbackService.previewRollback(request.employeeId(), request.rollbackTimestamp()).historicalState();
            }
            case ESCALATION_REVIEW -> Map.of();
        };

        ApprovalDtos.ApprovalResponse approval = approvalService.createRemediation(
                request.employeeId(),
                action,
                request.sourceFindingEventId(),
                request.justification(),
                requestedState);
        return new RemediationDtos.RemediationResponse(
                approval.id(),
                approval.sourceFindingEventId(),
                request.employeeId(),
                action,
                approval.justification(),
                approval.status(),
                approval.requester(),
                approval.approver(),
                approval.createdAt(),
                approval.decidedAt(),
                approval.executionStatus(),
                approval.executionResult(),
                approval.verificationStatus(),
                approval.verificationResult(),
                approval.verifiedAt());
    }

    @Transactional(readOnly = true)
    public Page<RemediationDtos.RemediationResponse> list(Pageable pageable, Authentication authentication) {
        requireRole(authentication, "ADMIN", "AUDITOR", "COMPLIANCE_OFFICER", "HR", "MANAGER");
        Page<ApprovalRequest> page = approvals.findByRequestTypeOrderByCreatedAtDesc(
                ApprovalRequestType.REMEDIATION,
                pageable);
        List<RemediationDtos.RemediationResponse> visible = page.getContent().stream()
                .filter(request -> canView(request, authentication))
                .map(this::toResponse)
                .toList();
        return new PageImpl<>(visible, pageable, visible.size());
    }

    @Transactional(readOnly = true)
    public RemediationDtos.RemediationResponse get(UUID id, Authentication authentication) {
        requireRole(authentication, "ADMIN", "AUDITOR", "COMPLIANCE_OFFICER", "HR", "MANAGER");
        ApprovalRequest request = approvals.findById(id)
                .filter(item -> item.getRequestType() == ApprovalRequestType.REMEDIATION)
                .orElseThrow(() -> new IllegalArgumentException("Remediation request not found"));
        if (!canView(request, authentication)) {
            throw new AccessDeniedException("Remediation request is outside your employee scope");
        }
        return toResponse(request);
    }

    private boolean canView(ApprovalRequest request, Authentication authentication) {
        if (hasRole(authentication, "ADMIN") || hasRole(authentication, "AUDITOR")
                || hasRole(authentication, "COMPLIANCE_OFFICER") || hasRole(authentication, "HR")) {
            return true;
        }
        try {
            employees.assertVisible(UUID.fromString(request.getTarget()), authentication);
            return true;
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return false;
        }
    }

    private RemediationDtos.RemediationResponse toResponse(ApprovalRequest request) {
        return new RemediationDtos.RemediationResponse(
                request.getId(),
                request.getSourceFindingEventId(),
                UUID.fromString(request.getTarget()),
                RemediationDtos.Action.valueOf(request.getRemediationAction()),
                request.getJustification(),
                request.getStatus(),
                request.getRequester().getFullName(),
                request.getApprover() == null ? null : request.getApprover().getFullName(),
                request.getCreatedAt(),
                request.getDecidedAt(),
                request.getExecutionStatus(),
                request.getExecutionResult(),
                request.getVerificationStatus(),
                request.getVerificationResult(),
                request.getVerifiedAt());
    }

    private void requireRole(Authentication authentication, String... roles) {
        for (String role : roles) {
            if (hasRole(authentication, role)) {
                return;
            }
        }
        throw new AccessDeniedException("This role cannot access remediation requests");
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role)
                        || authority.getAuthority().equals(role));
    }
}