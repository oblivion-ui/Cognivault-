package com.cognivault.api;

import com.cognivault.audit.AuditEventRepository;
import com.cognivault.api.AuditDtos;
import com.cognivault.audit.AuditEventDocument;
import com.cognivault.domain.AccountStatus;
import com.cognivault.domain.RiskLevel;
import com.cognivault.domain.Role;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.service.EmployeeScopeService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final EmployeeRepository employees;
    private final AuditEventRepository audit;
        private final AnomalyFindingRepository anomalies;
        private final EmployeeScopeService employeeScope;

    public DashboardController(
            EmployeeRepository employees,
                        AuditEventRepository audit,
                        AnomalyFindingRepository anomalies,
                        EmployeeScopeService employeeScope) {
        this.employees = employees;
        this.audit = audit;
                this.anomalies = anomalies;
                this.employeeScope = employeeScope;
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public DashboardDtos.DashboardResponse summary(Authentication authentication) {
        boolean admin = employeeScope.hasRole(authentication, Role.ADMIN);
        boolean auditor = employeeScope.hasRole(authentication, Role.AUDITOR);
        boolean complianceOfficer = employeeScope.hasRole(authentication, Role.COMPLIANCE_OFFICER);
        boolean hr = employeeScope.hasRole(authentication, Role.HR);
        boolean manager = employeeScope.hasRole(authentication, Role.MANAGER);
        boolean employee = employeeScope.hasRole(authentication, Role.EMPLOYEE);
        if (!(admin || auditor || complianceOfficer || hr || manager || employee)) {
            throw new AccessDeniedException("A supported CogniVault role is required");
        }

        List<String> scopedEmployeeIds = null;
        if (!admin && !auditor && !complianceOfficer) {
            scopedEmployeeIds = (hr
                    ? employeeScope.workforceEmployeeIds()
                    : manager
                            ? employeeScope.managedEmployeeIds(authentication)
                            : List.of(employeeScope.currentEmployeeId(authentication)))
                    .stream()
                    .sorted()
                    .toList();
        }
        List<UUID> scopedEmployeeUuids = scopedEmployeeIds == null
                ? null
                : scopedEmployeeIds.stream().map(UUID::fromString).toList();

        var today = LocalDate.now(ZoneOffset.UTC);
        var todayStart = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        var tomorrowStart = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        var trendStart = today.minusDays(6).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<AuditEventDocument> scopedWindowEvents = scopedEmployeeIds == null || scopedEmployeeIds.isEmpty()
                ? null
                : audit.findByEntityTypeAndEntityIdInAndTimestampRange(
                        "EMPLOYEE", scopedEmployeeIds, trendStart, tomorrowStart);

        long todayCount = scopedEmployeeIds == null
                ? audit.countByTimestampRange(todayStart, tomorrowStart)
                : scopedWindowEvents == null ? 0
                        : scopedWindowEvents.stream()
                                .filter(event -> !event.getTimestamp().isBefore(todayStart)
                                        && event.getTimestamp().isBefore(tomorrowStart))
                                .count();

        Object activeEmployeeCount = admin || hr
                ? employees.countByUser_Status(AccountStatus.ACTIVE)
                : auditor || complianceOfficer
                        ? "Unavailable"
                        : scopedEmployeeUuids.isEmpty()
                                ? 0L
                                : employees.countByIdInAndUser_Status(scopedEmployeeUuids, AccountStatus.ACTIVE);

        var metrics = List.of(
                new DashboardDtos.Metric(
                        "employees",
                        "Active employees",
                        activeEmployeeCount,
                        null,
                        null),

                new DashboardDtos.Metric(
                        "events",
                        "Audit events today",
                        todayCount,
                        null,
                        null),

                new DashboardDtos.Metric(
                        "anomalies",
                        "Open anomalies",
                        "Unavailable",
                        null,
                        "UNKNOWN"),

                new DashboardDtos.Metric(
                        "approvals",
                        "Pending approvals",
                        "Unavailable",
                        null,
                        "UNKNOWN")
        );

        var trend = new ArrayList<DashboardDtos.TrendPoint>();

        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);

            Instant start = date.atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            long count = scopedEmployeeIds == null
                    ? audit.countByTimestampRange(start, end)
                    : scopedWindowEvents == null ? 0
                            : scopedWindowEvents.stream()
                                    .filter(event -> !event.getTimestamp().isBefore(start)
                                            && event.getTimestamp().isBefore(end))
                                    .count();

            String label = date.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            trend.add(new DashboardDtos.TrendPoint(label, count));
        }

        var recentEvents = scopedEmployeeIds == null
                ? audit.findTop10ByOrderByChainPositionDesc()
                : scopedEmployeeIds.isEmpty()
                        ? List.<AuditEventDocument>of()
                        : audit.findTop10ByEntityTypeAndEntityIdInOrderByChainPositionDesc(
                                "EMPLOYEE", scopedEmployeeIds);
        var recent = recentEvents
                .stream()
                .map(AuditDtos.AuditResponse::from)
                .toList();

        var riskDistribution = java.util.Arrays.stream(RiskLevel.values())
                .map(level -> new DashboardDtos.RiskPoint(
                        level.name(),
                        scopedEmployeeUuids == null
                                ? anomalies.countByRiskLevel(level)
                                : scopedEmployeeUuids.isEmpty()
                                        ? 0L
                                        : anomalies.countByEmployeeIdInAndRiskLevel(scopedEmployeeUuids, level)))
                .toList();

        return new DashboardDtos.DashboardResponse(
                metrics,
                trend,
                riskDistribution,
                recent);
    }
}
