package com.cognivault.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cognivault.api.EmployeeDtos;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditLedgerService;
import com.cognivault.domain.AccountStatus;
import com.cognivault.domain.Employee;
import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.security.AuthenticatedPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RollbackServiceTest {

    @Mock
    private AuditEventRepository audits;

    @Mock
    private EmployeeRepository employees;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private AuditLedgerService auditLedgerService;

    private RollbackService service;

    @BeforeEach
    void setUp() {
        service = new RollbackService(audits, employees, employeeService, new ObjectMapper(), auditLedgerService);
    }

    @Test
    void executeRollbackUsesAuthenticatedContextAndRestoresState() {
        UUID employeeId = UUID.randomUUID();
        UserAccount user = new UserAccount(
                "current-user",
                "current-user@example.test",
                "Current User",
                "encoded-password",
                Role.EMPLOYEE,
                AccountStatus.ACTIVE,
                false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        Employee employee = new Employee(
                "EMP-100",
                "Current",
                "User",
                "current-user@example.test",
                "555-0000",
                "Old address",
                "Engineering",
                "Engineer",
                "manager-user",
                LocalDate.of(2024, 1, 15),
                "FULL_TIME",
                "Remote",
                user);
        ReflectionTestUtils.setField(employee, "id", employeeId);

        when(employees.findById(employeeId)).thenReturn(Optional.of(employee));

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedPrincipal(user.getId(), "current-user"),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        Map<String, Object> targetState = Map.of(
                "firstName", "Historical",
                "lastName", "Record",
                "email", "historical@example.test",
                "phone", "555-0101",
                "department", "Finance",
                "designation", "Analyst",
                "manager", "manager-user",
                "employmentType", "FULL_TIME",
                "location", "Hybrid",
                "role", Role.EMPLOYEE);

        when(employeeService.update(eq(employeeId), any(EmployeeDtos.UpdateEmployeeRequest.class), any())).thenReturn(
                new EmployeeDtos.EmployeeResponse(
                        employeeId,
                        "EMP-100",
                        "Historical",
                        "Record",
                        "historical@example.test",
                        "555-0101",
                        "Old address",
                        "Finance",
                        "Analyst",
                        "manager-user",
                        LocalDate.of(2024, 1, 15),
                        "FULL_TIME",
                        "Hybrid",
                        "current-user",
                        Role.EMPLOYEE,
                        AccountStatus.ACTIVE,
                        "VALID",
                        null,
                        null));

        service.executeRollback(employeeId, targetState, "Restored to historical record");

        verify(employeeService).update(eq(employeeId), any(EmployeeDtos.UpdateEmployeeRequest.class), any());
        SecurityContextHolder.clearContext();
    }
}
