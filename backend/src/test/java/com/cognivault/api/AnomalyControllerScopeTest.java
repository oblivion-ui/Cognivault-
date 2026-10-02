package com.cognivault.api;

import com.cognivault.anomaly.AnomalyProcessor;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.domain.AnomalyFinding;
import com.cognivault.domain.RiskLevel;
import com.cognivault.domain.Role;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.security.AuthenticatedPrincipal;
import com.cognivault.service.EmployeeScopeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyControllerScopeTest {

    @Mock private AnomalyFindingRepository findings;
    @Mock private EmployeeRepository employees;
    @Mock private AuditEventRepository audits;
    @Mock private AnomalyProcessor processor;
    @Mock private EmployeeScopeService employeeScope;

    @Test
    void managerAnomalyListQueriesOnlyAssignedEmployees() {
        UUID employeeId = UUID.randomUUID();
        List<UUID> ids = List.of(employeeId);
        Authentication manager = authentication("manager", Role.MANAGER);
        when(employeeScope.hasRole(manager, Role.MANAGER)).thenReturn(true);
        when(employeeScope.managedEmployeeIds(manager)).thenReturn(Set.of(employeeId.toString()));
        when(findings.findByEmployeeIdInOrderByFlaggedAtDesc(ids, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(finding("evt-team", employeeId, RiskLevel.HIGH))));
        when(employees.findById(employeeId)).thenReturn(Optional.empty());

        var response = controller().list(PageRequest.of(0, 10), manager);

        assertEquals(1, response.totalElements());
        assertEquals("evt-team", response.content().get(0).eventId());
        verify(findings).findByEmployeeIdInOrderByFlaggedAtDesc(ids, PageRequest.of(0, 10));
        verify(findings, never()).findAllByOrderByFlaggedAtDesc(any());
    }

    @Test
    void managerAnomalySummaryCountsOnlyAssignedEmployees() {
        UUID employeeId = UUID.randomUUID();
        Authentication manager = authentication("manager", Role.MANAGER);
        when(employeeScope.hasRole(manager, Role.MANAGER)).thenReturn(true);
        when(employeeScope.managedEmployeeIds(manager)).thenReturn(Set.of(employeeId.toString()));
        when(findings.countByEmployeeIdIn(List.of(employeeId))).thenReturn(3L);
        when(findings.countByEmployeeIdInAndRiskLevel(List.of(employeeId), RiskLevel.UNKNOWN)).thenReturn(1L);

        var summary = controller().summary(manager);

        assertEquals(3L, summary.total());
        assertEquals(1L, summary.unknownRisk());
        verify(findings, never()).count();
        verify(findings, never()).countByRiskLevel(any());
    }

    @Test
    void managerWithNoAssignedEmployeesGetsAnEmptyAnomalyPage() {
        Authentication manager = authentication("manager", Role.MANAGER);
        when(employeeScope.hasRole(manager, Role.MANAGER)).thenReturn(true);
        when(employeeScope.managedEmployeeIds(manager)).thenReturn(Set.of());

        var response = controller().list(PageRequest.of(0, 10), manager);

        assertEquals(0, response.totalElements());
        assertEquals(List.of(), response.content());
        verify(findings, never()).findByEmployeeIdInOrderByFlaggedAtDesc(anyList(), any());
        verify(findings, never()).findAllByOrderByFlaggedAtDesc(any());
    }

    @Test
    void auditorKeepsGlobalAnomalySummary() {
        Authentication auditor = authentication("auditor", Role.AUDITOR);
        when(employeeScope.hasRole(auditor, Role.MANAGER)).thenReturn(false);
        when(findings.count()).thenReturn(9L);
        when(findings.countByRiskLevel(RiskLevel.UNKNOWN)).thenReturn(0L);

        var summary = controller().summary(auditor);

        assertEquals(9L, summary.total());
        assertEquals(0L, summary.unknownRisk());
        verify(employeeScope, never()).managedEmployeeIds(any());
    }

    private AnomalyController controller() {
        return new AnomalyController(findings, employees, audits, processor, employeeScope);
    }

    private AnomalyFinding finding(String eventId, UUID employeeId, RiskLevel riskLevel) {
        return new AnomalyFinding(eventId, employeeId, new BigDecimal("0.75"), riskLevel,
                Instant.now(), "test-model", List.of(), List.of());
    }

    private Authentication authentication(String username, Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(UUID.randomUUID(), username), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}