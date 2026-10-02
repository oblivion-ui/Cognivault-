package com.cognivault.service;

import com.cognivault.api.EmployeeDtos;
import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditLedgerService;
import com.cognivault.domain.Employee;
import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RollbackService {

    private final AuditEventRepository audits;
    private final EmployeeRepository employees;
    private final EmployeeService employeeService;
    private final ObjectMapper mapper;
    private final AuditLedgerService auditLedgerService;

    public RollbackService(
            AuditEventRepository audits,
            EmployeeRepository employees,
            EmployeeService employeeService,
            ObjectMapper mapper) {
        this(audits, employees, employeeService, mapper, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public RollbackService(
            AuditEventRepository audits,
            EmployeeRepository employees,
            EmployeeService employeeService,
            ObjectMapper mapper,
            AuditLedgerService auditLedgerService) {
        this.audits = audits;
        this.employees = employees;
        this.employeeService = employeeService;
        this.mapper = mapper;
        this.auditLedgerService = auditLedgerService;
    }

    @Transactional(readOnly = true)
    public RollbackPreview previewRollback(UUID employeeId, Instant targetTimestamp) {
        Employee currentEmployee = employees.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        AuditEventDocument latestEvent = audits.findFirstByEntityTypeAndEntityIdOrderByTimestampDesc(
                "EMPLOYEE", employeeId.toString())
                .orElseThrow(() -> new IllegalArgumentException("No audit history found for this employee"));

        AuditEventDocument historicalEvent = audits.findFirstByEntityTypeAndEntityIdAndTimestampLessThanEqualOrderByTimestampDesc(
                "EMPLOYEE", employeeId.toString(), targetTimestamp)
                .orElseThrow(() -> new IllegalArgumentException("No audit state found at or before the specified timestamp"));

        if (historicalEvent.getAfterSnapshot() == null) {
            throw new IllegalArgumentException("Historical snapshot is not available");
        }

        Map<String, Object> currentState = snapshotFor(currentEmployee);
        Map<String, Object> historicalState = historicalEvent.getAfterSnapshot();

        return new RollbackPreview(
                employeeId,
                currentEmployee.getEmployeeId(),
                currentEmployee.getFirstName() + " " + currentEmployee.getLastName(),
                targetTimestamp,
                currentState,
                historicalState,
                historicalEvent.getEventId(),
                historicalEvent.getTimestamp());
    }

    @Transactional
    public void executeRollback(UUID employeeId, Map<String, Object> targetState, String justification) {
        executeRollback(employeeId, targetState, justification, authenticationFromContext());
    }

    @Transactional
    public void executeRollback(UUID employeeId, Map<String, Object> targetState, String justification, Authentication authentication) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        Map<String, Object> normalizedState = normalizeEmployeeRestoreState(targetState, employee);
        EmployeeDtos.UpdateEmployeeRequest updateRequest = mapper.convertValue(normalizedState, EmployeeDtos.UpdateEmployeeRequest.class);

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated user identity is unavailable");
        }

        employeeService.update(employeeId, updateRequest, authentication);
        Employee updatedEmployee = employees.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found after rollback"));

        if (auditLedgerService != null) {
            Map<String, Object> before = snapshotFor(employee);
            Map<String, Object> after = snapshotFor(updatedEmployee);
            after.put("rollbackJustification", justification == null ? "" : justification);
            after.put("restoreSource", "historical-audit-snapshot");
            auditLedgerService.append("EMPLOYEE_ROLLED_BACK", "EMPLOYEE", employeeId.toString(), before, after);
        }
    }

    private Map<String, Object> normalizeEmployeeRestoreState(Map<String, Object> snapshot, Employee currentEmployee) {
        Map<String, Object> source = extractEmployeeState(snapshot);
        if (source == null || source.isEmpty()) {
            throw new IllegalArgumentException("Rollback target state is required");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("firstName", valueOf(source.getOrDefault("firstName", currentEmployee.getFirstName())));
        normalized.put("lastName", valueOf(source.getOrDefault("lastName", currentEmployee.getLastName())));
        normalized.put("email", valueOf(source.getOrDefault("email", currentEmployee.getEmail())));
        normalized.put("phone", source.getOrDefault("phone", currentEmployee.getPhone()));
        normalized.put("department", source.getOrDefault("department", currentEmployee.getDepartment()));
        normalized.put("designation", source.getOrDefault("designation", currentEmployee.getDesignation()));
        normalized.put("manager", source.getOrDefault("manager", currentEmployee.getManager()));
        normalized.put("employmentType", source.getOrDefault("employmentType", currentEmployee.getEmploymentType()));
        normalized.put("location", source.getOrDefault("location", currentEmployee.getLocation()));

        Object roleValue = source.get("role");
        if (roleValue == null && source.get("user") instanceof Map<?, ?> userState) {
            roleValue = userState.get("role");
        }
        if (roleValue != null) {
            normalized.put("role", Role.valueOf(String.valueOf(roleValue).trim().toUpperCase()));
        } else {
            normalized.put("role", currentEmployee.getUser().getRole());
        }

        return normalized;
    }

    private Map<String, Object> snapshotFor(Employee employee) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", employee.getId());
        snapshot.put("employeeId", employee.getEmployeeId());
        snapshot.put("firstName", employee.getFirstName());
        snapshot.put("lastName", employee.getLastName());
        snapshot.put("email", employee.getEmail());
        snapshot.put("phone", employee.getPhone());
        snapshot.put("address", employee.getAddress());
        snapshot.put("department", employee.getDepartment());
        snapshot.put("designation", employee.getDesignation());
        snapshot.put("manager", employee.getManager());
        snapshot.put("joiningDate", employee.getJoiningDate());
        snapshot.put("employmentType", employee.getEmploymentType());
        snapshot.put("location", employee.getLocation());
        if (employee.getUser() != null) {
            UserAccount user = employee.getUser();
            snapshot.put("username", user.getUsername());
            snapshot.put("role", user.getRole());
            snapshot.put("accountStatus", user.getStatus());
            snapshot.put("credentialStatus", user.isMustChangePassword() ? "RESET_REQUIRED" : "VALID");
        }
        return snapshot;
    }

    private Map<String, Object> extractEmployeeState(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return Map.of();
        }
        Object nested = snapshot.get("employee");
        if (nested instanceof Map<?, ?> employeeState) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : employeeState.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String valueOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Authentication authenticationFromContext() {
        Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated user identity is unavailable");
        }
        return authentication;
    }

    public record RollbackPreview(
            UUID employeeId,
            String employeeCode,
            String employeeName,
            Instant targetTimestamp,
            Map<String, Object> currentState,
            Map<String, Object> historicalState,
            String sourceEventId,
            Instant sourceEventTimestamp) {}
}
