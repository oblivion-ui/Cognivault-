package com.cognivault.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cognivault.api.EmployeeDtos.CredentialResetRequest;
import com.cognivault.api.EmployeeDtos.CredentialResetResponse;
import com.cognivault.domain.Employee;
import com.cognivault.domain.UserAccount;
import com.cognivault.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditAspectTest {
    @Test
    void credentialResetAuditReflectsRequestedForcedChangeWithoutCredential() throws Throwable {
        var ledger = mock(AuditLedgerService.class);
        var mapper = new ObjectMapper();
        var aspect = new AuditAspect(ledger, mapper);
        var service = mock(EmployeeService.class);
        var employee = mock(Employee.class);
        var user = mock(UserAccount.class);
        var joinPoint = mock(ProceedingJoinPoint.class);
        var employeeId = UUID.randomUUID();
        var request = new CredentialResetRequest(true, false);
        var response = new CredentialResetResponse("temporary-secret-value", null);
        var action = new AuditAction() {
            @Override public String action() { return "CREDENTIAL_RESET"; }
            @Override public String entityType() { return "EMPLOYEE"; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return AuditAction.class; }
        };
        when(joinPoint.getArgs()).thenReturn(new Object[]{employeeId, request});
        when(joinPoint.getTarget()).thenReturn(service);
        when(joinPoint.proceed()).thenReturn(response);
        when(service.get(employeeId)).thenReturn(employee);
        when(employee.getUser()).thenReturn(user);
        when(user.isMustChangePassword()).thenReturn(true);
        when(ledger.append(eq("CREDENTIAL_RESET"), eq("EMPLOYEE"), eq(employeeId.toString()), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AuditEventDocument("evt-reset", "actor", null, "CREDENTIAL_RESET", "EMPLOYEE", employeeId.toString(), java.time.Instant.now(), null, null, "GENESIS", "hash", 1));

        var result = (CredentialResetResponse) aspect.audit(joinPoint, action);

        assertEquals("temporary-secret-value", result.temporaryPassword());
        assertEquals("evt-reset", result.auditEventId());
        var before = ArgumentCaptor.forClass(Object.class);
        var after = ArgumentCaptor.forClass(Object.class);
        verify(ledger).append(eq("CREDENTIAL_RESET"), eq("EMPLOYEE"), eq(employeeId.toString()), before.capture(), after.capture());
        assertEquals(Map.of("credentialStatus", "RESET_REQUIRED", "mustChangePassword", true), before.getValue());
        assertEquals(Map.of("credentialStatus", "VALID", "mustChangePassword", false), after.getValue());
        assertFalse(mapper.writeValueAsString(before.getValue()).contains(response.temporaryPassword()));
        assertFalse(mapper.writeValueAsString(after.getValue()).contains(response.temporaryPassword()));
    }
}