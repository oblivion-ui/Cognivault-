package com.cognivault.service;

import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.repository.UserAccountRepository;
import com.cognivault.security.CurrentActor;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class EmployeeScopeService {

    private final EmployeeRepository employees;
    private final UserAccountRepository users;

    public EmployeeScopeService(EmployeeRepository employees, UserAccountRepository users) {
        this.employees = employees;
        this.users = users;
    }

    public Set<String> managedEmployeeIds(Authentication authentication) {
        UserAccount manager = authenticatedUser(authentication);
        if (manager.getRole() != Role.MANAGER || !hasRole(authentication, Role.MANAGER)) {
            throw new AccessDeniedException("Manager identity is required to resolve team scope");
        }
        return employees.findEmployeeIdsByManagerUserId(manager.getId()).stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public Set<String> workforceEmployeeIds() {
        return employees.findAllEmployeeIds().stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public String currentEmployeeId(Authentication authentication) {
        UserAccount user = authenticatedUser(authentication);
        if (user.getRole() != Role.EMPLOYEE || !hasRole(authentication, Role.EMPLOYEE)) {
            throw new AccessDeniedException("Employee identity is required to resolve self scope");
        }
        return employees.findEmployeeIdByUserId(user.getId())
                .map(UUID::toString)
                .orElseThrow(() -> new AccessDeniedException("No employee record is associated with this account"));
    }

    public void assertManagedBy(UUID employeeId, UserAccount manager) {
        if (manager == null || manager.getRole() != Role.MANAGER
                || !employees.existsManagedBy(employeeId, manager.getId())) {
            throw new AccessDeniedException("Employee is outside the manager's assigned scope");
        }
    }

    public void assertManagedBy(UUID employeeId, Authentication authentication) {
        UserAccount manager = authenticatedUser(authentication);
        if (!hasRole(authentication, Role.MANAGER)) {
            throw new AccessDeniedException("Manager identity is required to access this employee");
        }
        assertManagedBy(employeeId, manager);
    }

    public boolean hasRole(Authentication authentication, Role role) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role.name()));
    }

    private UserAccount authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new AccessDeniedException("Authenticated user identity is unavailable");
        }
        UserAccount user = users.findByUsernameIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user account was not found"));
        if (authentication.getPrincipal() instanceof CurrentActor.AuthenticatedPrincipal principal
                && !user.getId().equals(principal.userId())) {
            throw new AccessDeniedException("Authenticated principal does not match the stored user account");
        }
        return user;
    }
}