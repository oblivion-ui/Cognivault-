package com.cognivault.service;

import com.cognivault.api.ApprovalDtos;
import com.cognivault.api.RemediationDtos;
import com.cognivault.domain.AnomalyFinding;
import com.cognivault.domain.ApprovalStatus;
import com.cognivault.domain.RiskLevel;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.ApprovalRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemediationServiceTest {

    @Mock private AnomalyFindingRepository findings;
    @Mock private ApprovalRequestRepository approvals;
    @Mock private ApprovalService approvalService;
    @Mock private EmployeeService employees;
    @Mock private RollbackService rollbackService;

    private RemediationService service;
    private UUID employeeId;
    private AuthenticationFixture auth;

    @BeforeEach
    void setUp() {
        service = new RemediationService(findings, approvals, approvalService, employees, rollbackService);
        employeeId = UUID.randomUUID();
        auth = authenticate("admin", "ADMIN");
    }

    @Test
    void createsApprovalFromPersistedFindingWithoutPersistingSecrets() {
        when(findings.findByEventId("evt-risk")).thenReturn(Optional.of(finding(employeeId)));
        when(approvalService.createRemediation(
                eq(employeeId),
                eq(RemediationDtos.Action.CREDENTIAL_RESET),
                eq("evt-risk"),
                eq("Investigate suspicious access"),
                anyMap())).thenReturn(new ApprovalDtos.ApprovalResponse(
                        UUID.randomUUID(), "admin", null, employeeId.toString(), Instant.now(),
                        ApprovalStatus.PENDING, "ADMIN", null, "Investigate suspicious access",
                        null, Map.of(), null, null, null, null, "CREDENTIAL_RESET", "evt-risk",
                        "PENDING_APPROVAL", null, "PENDING", null, null));

        var response = service.create(new RemediationDtos.CreateRequest(
                employeeId, "evt-risk", RemediationDtos.Action.CREDENTIAL_RESET,
                "Investigate suspicious access", null, null, true), auth.authentication());

        assertEquals(RemediationDtos.Action.CREDENTIAL_RESET, response.recommendedAction());
        assertEquals("PENDING_APPROVAL", response.executionStatus());
        verify(approvalService).createRemediation(
                eq(employeeId), eq(RemediationDtos.Action.CREDENTIAL_RESET), eq("evt-risk"),
                anyString(), argThat(state -> Boolean.FALSE.equals(state.get("generateTemporary"))
                        && Boolean.TRUE.equals(state.get("forceChange"))
                        && !state.containsKey("password")));
    }

    @Test
    void rejectsFindingThatDoesNotBelongToTargetEmployee() {
        when(findings.findByEventId("evt-risk")).thenReturn(Optional.of(finding(UUID.randomUUID())));

        assertThrows(AccessDeniedException.class, () -> service.create(
                new RemediationDtos.CreateRequest(
                        employeeId, "evt-risk", RemediationDtos.Action.ACCOUNT_STATUS_CHANGE,
                        "Review status", "SUSPEND", null, false), auth.authentication()));

        verifyNoInteractions(approvalService);
    }

    @Test
    void nonAdminCannotRequestCredentialReset() {
        auth = authenticate("hr-user", "HR");
        when(findings.findByEventId("evt-risk")).thenReturn(Optional.of(finding(employeeId)));

        assertThrows(AccessDeniedException.class, () -> service.create(
                new RemediationDtos.CreateRequest(
                        employeeId, "evt-risk", RemediationDtos.Action.CREDENTIAL_RESET,
                        "Review access", null, null, true), auth.authentication()));

        verifyNoInteractions(approvalService);
    }

    @Test
    void missingStoredFindingDoesNotCreateRequest() {
        when(findings.findByEventId("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.create(
                new RemediationDtos.CreateRequest(
                        employeeId, "missing", RemediationDtos.Action.ESCALATION_REVIEW,
                        "Escalate for review", null, null, false), auth.authentication()));

        verifyNoInteractions(approvalService);
    }

    private AnomalyFinding finding(UUID target) {
        return new AnomalyFinding("evt-risk", target, new BigDecimal("0.9"), RiskLevel.HIGH,
                Instant.parse("2026-01-01T00:00:00Z"), "stored-model", List.of("indicator"), List.of());
    }

    private AuthenticationFixture authenticate(String name, String role) {
        return new AuthenticationFixture(UsernamePasswordAuthenticationToken.authenticated(
                name, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private record AuthenticationFixture(
            org.springframework.security.core.Authentication authentication) {
    }
}