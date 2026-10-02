package com.cognivault.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cognivault.api.EmployeeDtos.UpdateEmployeeRequest;
import com.cognivault.domain.Employee;
import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.repository.UserAccountRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {
    @Mock private EmployeeRepository employees;
    @Mock private UserAccountRepository users;
    @Mock private PasswordEncoder encoder;
    @Mock private Employee employee;
    @Mock private UserAccount user;

    private EmployeeService service;
    private UUID employeeId;

    @BeforeEach
    void setUp() {
        service = new EmployeeService(employees, users, encoder);
        employeeId = UUID.randomUUID();
        when(employees.findByIdWithUser(employeeId)).thenReturn(Optional.of(employee));
        when(employee.getUser()).thenReturn(user);
        when(user.getRole()).thenReturn(Role.EMPLOYEE);
    }

    @Test
    void managerCannotPromoteEmployeeToAdministrator() {
        var request = request(Role.ADMIN);

        assertThrows(AccessDeniedException.class, () -> service.update(employeeId, request, authentication("ROLE_MANAGER")));

        verify(user, never()).setRole(Role.ADMIN);
        verify(employees, never()).save(employee);
    }

    @Test
    void managerCanUpdateEmployeeProfileWithoutChangingRole() {
        var request = request(null);

        assertDoesNotThrow(() -> service.update(employeeId, request, authentication("ROLE_MANAGER")));

        verify(employee).update("Ari", "Ng", "ari.ng@example.test", "555-0100", "Finance", "Analyst", null, "FULL_TIME", "Remote");
        verify(user, never()).setRole(Role.ADMIN);
        verify(employees).save(employee);
    }

    private UpdateEmployeeRequest request(Role role) {
        return new UpdateEmployeeRequest("Ari", "Ng", "ari.ng@example.test", "555-0100", "Finance", "Analyst", null, "FULL_TIME", "Remote", role);
    }

    private UsernamePasswordAuthenticationToken authentication(String authority) {
        return UsernamePasswordAuthenticationToken.authenticated("manager", null, List.of(new SimpleGrantedAuthority(authority)));
    }
}