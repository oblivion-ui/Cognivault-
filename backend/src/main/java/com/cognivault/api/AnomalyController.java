package com.cognivault.api;

import com.cognivault.audit.AuditEventRepository;
import com.cognivault.anomaly.AnomalyProcessor;
import com.cognivault.domain.AnomalyFinding;
import com.cognivault.domain.RiskLevel;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.service.EmployeeScopeService;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyFindingRepository findings;
    private final EmployeeRepository employees;
    private final AuditEventRepository audits;
    private final AnomalyProcessor anomalyProcessor;
    private final EmployeeScopeService employeeScope;

    public AnomalyController(
            AnomalyFindingRepository findings,
            EmployeeRepository employees,
            AuditEventRepository audits,
            AnomalyProcessor anomalyProcessor,
            EmployeeScopeService employeeScope) {
        this.findings = findings;
        this.employees = employees;
        this.audits = audits;
        this.anomalyProcessor = anomalyProcessor;
        this.employeeScope = employeeScope;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER')")
    public PageResponse<AnomalyDtos.AnomalyResponse> list(Pageable pageable, Authentication authentication) {
        org.springframework.data.domain.Page<AnomalyFinding> page;
        if (employeeScope.hasRole(authentication, com.cognivault.domain.Role.MANAGER)) {
            List<UUID> employeeIds = managedEmployeeUuids(authentication);
            page = employeeIds.isEmpty()
                    ? Page.empty(pageable)
                    : findings.findByEmployeeIdInOrderByFlaggedAtDesc(employeeIds, pageable);
        } else {
            page = findings.findAllByOrderByFlaggedAtDesc(pageable);
        }

        var content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER')")
    public AnomalyDtos.AnomalySummary summary(Authentication authentication) {
        long total;
        long unknownRisk;
        if (employeeScope.hasRole(authentication, com.cognivault.domain.Role.MANAGER)) {
            List<UUID> employeeIds = managedEmployeeUuids(authentication);
            total = employeeIds.isEmpty() ? 0 : findings.countByEmployeeIdIn(employeeIds);
            unknownRisk = employeeIds.isEmpty()
                    ? 0
                    : findings.countByEmployeeIdInAndRiskLevel(employeeIds, RiskLevel.UNKNOWN);
        } else {
            total = findings.count();
            unknownRisk = findings.countByRiskLevel(RiskLevel.UNKNOWN);
        }

        return new AnomalyDtos.AnomalySummary(total, unknownRisk);
    }

    private List<UUID> managedEmployeeUuids(Authentication authentication) {
        Set<String> ids = employeeScope.managedEmployeeIds(authentication);
        return ids.stream().map(UUID::fromString).sorted().toList();
    }

    @PostMapping("/reclassify")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AnomalyDtos.ReclassificationResponse reclassify() {
        int reclassified = anomalyProcessor.reclassifyUnknownFindings();

        return new AnomalyDtos.ReclassificationResponse(reclassified);
    }

    @PostMapping("/backfill")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public AnomalyDtos.BackfillResponse backfill() {
        var events = audits.ordered();

        for (var event : events) {
            anomalyProcessor.process(event);
        }

        return new AnomalyDtos.BackfillResponse(
                events.size(),
                events.size());
    }

    private AnomalyDtos.AnomalyResponse toResponse(AnomalyFinding finding) {
        String employeeName = resolveEmployeeName(finding.getEmployeeId());

        return new AnomalyDtos.AnomalyResponse(
                finding.getId(),
                finding.getEventId(),
                employeeName,
                finding.getScore(),
                finding.getRiskLevel(),
                finding.getFlaggedAt(),
                finding.getModel(),
                finding.getIndicators() == null
                        ? List.of()
                        : finding.getIndicators(),
                finding.getExplanations() == null
                        ? List.of()
                        : finding.getExplanations());
    }

    private String resolveEmployeeName(UUID employeeId) {
        if (employeeId == null) {
            return null;
        }

        return employees.findById(employeeId)
                .map(employee -> employee.getFirstName() + " " + employee.getLastName())
                .orElse(null);
    }
}
