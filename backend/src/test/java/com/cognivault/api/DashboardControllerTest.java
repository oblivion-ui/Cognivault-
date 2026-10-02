package com.cognivault.api;

import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditEventDocument;
import com.cognivault.domain.AccountStatus;
import com.cognivault.domain.Role;
import com.cognivault.domain.RiskLevel;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.service.EmployeeScopeService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private EmployeeRepository employees;

    @Mock
    private AuditEventRepository audit;

    @Mock
    private AnomalyFindingRepository anomalies;

        @Mock
        private EmployeeScopeService employeeScope;

    @Test
        void adminDashboardRetainsOrganizationWideData() {
                DashboardController controller = controller();
                Authentication admin = authentication("admin", Role.ADMIN);
                stubRoles(admin);
        when(employees.countByUser_Status(AccountStatus.ACTIVE)).thenReturn(4L);
        when(audit.countByTimestampRange(any(Instant.class), any(Instant.class)))
                .thenReturn(99L, 1L, 2L, 3L, 4L, 5L, 6L, 7L);
        when(audit.findTop10ByOrderByChainPositionDesc()).thenReturn(List.of());
        when(anomalies.countByRiskLevel(RiskLevel.LOW)).thenReturn(0L);
        when(anomalies.countByRiskLevel(RiskLevel.MEDIUM)).thenReturn(6L);
        when(anomalies.countByRiskLevel(RiskLevel.HIGH)).thenReturn(3L);
        when(anomalies.countByRiskLevel(RiskLevel.CRITICAL)).thenReturn(0L);
        when(anomalies.countByRiskLevel(RiskLevel.UNKNOWN)).thenReturn(0L);

        DashboardDtos.DashboardResponse response = controller.summary(admin);

        assertEquals(5, response.riskDistribution().size());
        assertEquals(List.of("LOW", "MEDIUM", "HIGH", "CRITICAL", "UNKNOWN"),
                response.riskDistribution().stream().map(DashboardDtos.RiskPoint::label).toList());
        assertEquals(List.of(0L, 6L, 3L, 0L, 0L),
                response.riskDistribution().stream().map(DashboardDtos.RiskPoint::value).toList());
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                response.auditTrend().stream().map(DashboardDtos.TrendPoint::value).toList());
        assertEquals(7, response.auditTrend().size());
        assertEquals(4, response.metrics().size());
        assertEquals(List.of(), response.recentEvents());

        verify(anomalies).countByRiskLevel(RiskLevel.LOW);
        verify(anomalies).countByRiskLevel(RiskLevel.MEDIUM);
        verify(anomalies).countByRiskLevel(RiskLevel.HIGH);
        verify(anomalies).countByRiskLevel(RiskLevel.CRITICAL);
        verify(anomalies).countByRiskLevel(RiskLevel.UNKNOWN);
        verify(audit).findTop10ByOrderByChainPositionDesc();
        verify(employees).countByUser_Status(AccountStatus.ACTIVE);
        verify(employeeScope, never()).managedEmployeeIds(any());
        verify(employeeScope, never()).currentEmployeeId(any());
    }

    @Test
    void employeeDashboardContainsOnlyOwnAuditAndRiskData() {
        DashboardController controller = controller();
        UUID employeeId = UUID.randomUUID();
        String employeeIdText = employeeId.toString();
        Authentication employee = authentication("employee", Role.EMPLOYEE);
        stubRoles(employee);
        when(employeeScope.currentEmployeeId(employee)).thenReturn(employeeIdText);
        when(employees.countByIdInAndUser_Status(List.of(employeeId), AccountStatus.ACTIVE)).thenReturn(1L);
        AuditEventDocument ownEvent = event("evt-own", employeeIdText, Instant.now());
        when(audit.findByEntityTypeAndEntityIdInAndTimestampRange(
                eq("EMPLOYEE"), eq(List.of(employeeIdText)), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(ownEvent));
        when(audit.findTop10ByEntityTypeAndEntityIdInOrderByChainPositionDesc(
                "EMPLOYEE", List.of(employeeIdText))).thenReturn(List.of(ownEvent));
        lenient().when(anomalies.countByEmployeeIdInAndRiskLevel(anyList(), any(RiskLevel.class)))
                .thenAnswer(invocation -> invocation.getArgument(1) == RiskLevel.HIGH ? 2L : 0L);

        DashboardDtos.DashboardResponse response = controller.summary(employee);

        assertEquals(1L, response.metrics().get(0).value());
        assertEquals(List.of("evt-own"), response.recentEvents().stream()
                .map(AuditDtos.AuditResponse::eventId).toList());
        assertEquals(2L, response.riskDistribution().stream()
                .filter(point -> point.label().equals("HIGH"))
                .findFirst().orElseThrow().value());
        assertEquals(1L, response.auditTrend().stream().mapToLong(DashboardDtos.TrendPoint::value).sum());
        verify(employeeScope).currentEmployeeId(employee);
        verify(employees).countByIdInAndUser_Status(List.of(employeeId), AccountStatus.ACTIVE);
        verify(audit, never()).countByTimestampRange(any(Instant.class), any(Instant.class));
        verify(audit, never()).findTop10ByOrderByChainPositionDesc();
        verify(anomalies, never()).countByRiskLevel(any(RiskLevel.class));
    }

    @Test
    void managerDashboardUsesOnlyManagedEmployeeIds() {
        DashboardController controller = controller();
        UUID firstEmployeeId = UUID.randomUUID();
        UUID secondEmployeeId = UUID.randomUUID();
        List<String> managedIds = List.of(firstEmployeeId.toString(), secondEmployeeId.toString()).stream()
                .sorted().toList();
        List<UUID> managedUuids = managedIds.stream().map(UUID::fromString).toList();
        Authentication manager = authentication("manager", Role.MANAGER);
        stubRoles(manager);
        when(employeeScope.managedEmployeeIds(manager)).thenReturn(Set.copyOf(managedIds));
        when(employees.countByIdInAndUser_Status(managedUuids, AccountStatus.ACTIVE)).thenReturn(2L);
        AuditEventDocument teamEvent = event("evt-team", managedIds.get(0), Instant.now());
        when(audit.findByEntityTypeAndEntityIdInAndTimestampRange(
                eq("EMPLOYEE"), anyList(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(teamEvent));
        when(audit.findTop10ByEntityTypeAndEntityIdInOrderByChainPositionDesc("EMPLOYEE", managedIds))
                .thenReturn(List.of(teamEvent));
        lenient().when(anomalies.countByEmployeeIdInAndRiskLevel(anyList(), any(RiskLevel.class)))
                .thenAnswer(invocation -> invocation.getArgument(1) == RiskLevel.MEDIUM ? 1L : 0L);

        DashboardDtos.DashboardResponse response = controller.summary(manager);

        assertEquals(2L, response.metrics().get(0).value());
        assertEquals(List.of("evt-team"), response.recentEvents().stream()
                .map(AuditDtos.AuditResponse::eventId).toList());
        assertEquals(1L, response.riskDistribution().stream()
                .filter(point -> point.label().equals("MEDIUM"))
                .findFirst().orElseThrow().value());
        verify(employeeScope).managedEmployeeIds(manager);
        verify(audit, never()).countByTimestampRange(any(Instant.class), any(Instant.class));
        verify(anomalies, never()).countByRiskLevel(any(RiskLevel.class));
    }

    private DashboardController controller() {
        return new DashboardController(employees, audit, anomalies, employeeScope);
    }

    private Authentication authentication(String username, Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                username,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }

    private void stubRoles(Authentication authentication) {
        when(employeeScope.hasRole(eq(authentication), any(Role.class))).thenAnswer(invocation -> {
            Role role = invocation.getArgument(1);
            return authentication.getAuthorities().stream()
                    .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
        });
    }

    private AuditEventDocument event(String eventId, String employeeId, Instant timestamp) {
        return new AuditEventDocument(eventId, "actor", "actor-id", "EMPLOYEE_UPDATED", "EMPLOYEE",
                employeeId, timestamp, Map.of(), Map.of(), "prev", "hash", 1L);
    }
}
