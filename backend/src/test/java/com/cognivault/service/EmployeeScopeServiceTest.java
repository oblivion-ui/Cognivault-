package com.cognivault.service;

import com.cognivault.domain.AccountStatus;
import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.repository.UserAccountRepository;
import com.cognivault.security.AuthenticatedPrincipal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeScopeServiceTest {

    @Mock private EmployeeRepository employees;
    @Mock private UserAccountRepository users;

    @Test
    void resolvesOnlyManagerAssignedEmployeeIdsFromStoredRelationship() {
        UUID managerId = UUID.randomUUID();
        UUID firstEmployeeId = UUID.randomUUID();
        UUID secondEmployeeId = UUID.randomUUID();
        UserAccount manager = user(managerId, Role.MANAGER);
        var authentication = authentication(managerId, "manager", Role.MANAGER);
        when(users.findByUsernameIgnoreCase("manager")).thenReturn(Optional.of(manager));
        when(employees.findEmployeeIdsByManagerUserId(managerId))
                .thenReturn(List.of(firstEmployeeId, secondEmployeeId));

        var scope = new EmployeeScopeService(employees, users).managedEmployeeIds(authentication);

        assertEquals(java.util.Set.of(firstEmployeeId.toString(), secondEmployeeId.toString()), scope);
        verify(employees).findEmployeeIdsByManagerUserId(managerId);
    }

    @Test
    void rejectsUnknownAuthenticatedUserInsteadOfReturningGlobalScope() {
        UUID managerId = UUID.randomUUID();
        when(users.findByUsernameIgnoreCase("manager")).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> new EmployeeScopeService(employees, users)
                        .managedEmployeeIds(authentication(managerId, "manager", Role.MANAGER)));
    }

    @Test
    void rejectsManagerScopeWhenStoredRoleDoesNotMatchAuthenticationRole() {
        UUID userId = UUID.randomUUID();
        when(users.findByUsernameIgnoreCase("not-manager"))
                .thenReturn(Optional.of(user(userId, Role.EMPLOYEE)));

        assertThrows(AccessDeniedException.class,
                () -> new EmployeeScopeService(employees, users)
                        .managedEmployeeIds(authentication(userId, "not-manager", Role.MANAGER)));
    }

    private UserAccount user(UUID id, Role role) {
        UserAccount user = new UserAccount("user", "user@example.test", "User", "hash",
                role, AccountStatus.ACTIVE, false);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UsernamePasswordAuthenticationToken authentication(UUID id, String username, Role role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(id, username),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}