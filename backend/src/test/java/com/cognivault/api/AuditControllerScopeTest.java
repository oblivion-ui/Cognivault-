package com.cognivault.api;

import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.domain.Role;
import com.cognivault.security.AuthenticatedPrincipal;
import com.cognivault.service.EmployeeScopeService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditControllerScopeTest {

    @Mock private AuditEventRepository audits;
    @Mock private EmployeeScopeService employeeScope;

    @Test
    void managerListQueriesOnlyAssignedEmployeeEvents() {
        UUID employeeId = UUID.randomUUID();
        List<String> ids = List.of(employeeId.toString());
        Authentication manager = authentication("manager", Role.MANAGER);
        when(employeeScope.hasRole(manager, Role.MANAGER)).thenReturn(true);
        when(employeeScope.managedEmployeeIds(manager)).thenReturn(java.util.Set.copyOf(ids));
        when(audits.findByEntityTypeAndEntityIdInOrderByChainPositionAsc("EMPLOYEE", ids, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(event("evt-team", "EMPLOYEE", employeeId.toString()))));

        var page = new AuditController(audits, employeeScope).list(PageRequest.of(0, 20), manager);

        assertEquals(List.of("evt-team"), page.getContent().stream().map(AuditDtos.AuditResponse::eventId).toList());
        verify(audits).findByEntityTypeAndEntityIdInOrderByChainPositionAsc("EMPLOYEE", ids, PageRequest.of(0, 20));
        verify(audits, never()).findAll(any(PageRequest.class));
    }

    @Test
    void managerCannotReadOtherManagersEmployeeAuditEvent() {
        UUID otherEmployeeId = UUID.randomUUID();
        Authentication manager = authentication("manager", Role.MANAGER);
        when(audits.findByEventId("evt-other"))
                .thenReturn(Optional.of(event("evt-other", "EMPLOYEE", otherEmployeeId.toString())));
        when(employeeScope.hasRole(manager, Role.MANAGER)).thenReturn(true);
        doThrow(new AccessDeniedException("outside assigned scope"))
                .when(employeeScope).assertManagedBy(otherEmployeeId, manager);

        assertThrows(AccessDeniedException.class,
                () -> new AuditController(audits, employeeScope).get("evt-other", manager));
    }

    @Test
    void managerCannotReadNonEmployeeAuditEvent() {
        Authentication manager = authentication("manager", Role.MANAGER);
        when(audits.findByEventId("evt-global"))
                .thenReturn(Optional.of(event("evt-global", "SYSTEM", "config-1")));
        when(employeeScope.hasRole(manager, Role.MANAGER)).thenReturn(true);

        assertThrows(AccessDeniedException.class,
                () -> new AuditController(audits, employeeScope).get("evt-global", manager));
        verify(employeeScope, never()).assertManagedBy(any(), any(Authentication.class));
    }

    @Test
    void auditorKeepsOrganizationWideAuditAccess() {
        Authentication auditor = authentication("auditor", Role.AUDITOR);
        when(employeeScope.hasRole(auditor, Role.MANAGER)).thenReturn(false);
        when(audits.findByEventId("evt-global"))
                .thenReturn(Optional.of(event("evt-global", "SYSTEM", "config-1")));

        var response = new AuditController(audits, employeeScope).get("evt-global", auditor);

        assertEquals("evt-global", response.eventId());
        verify(employeeScope, never()).managedEmployeeIds(any());
    }

    private AuditEventDocument event(String eventId, String entityType, String entityId) {
        return new AuditEventDocument(eventId, "actor", "user-id", "UPDATE", entityType, entityId,
                Instant.now(), Map.of(), Map.of(), "prev", "hash", 1);
    }

    private Authentication authentication(String username, Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(UUID.randomUUID(), username),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}