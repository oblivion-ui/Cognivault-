package com.cognivault.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.cognivault.api.ApprovalDtos;
import com.cognivault.domain.AccountStatus;
import com.cognivault.domain.Employee;
import com.cognivault.domain.ApprovalRequest;
import com.cognivault.domain.ApprovalRequestType;
import com.cognivault.domain.ApprovalStatus;
import com.cognivault.domain.Role;
import com.cognivault.domain.UserAccount;
import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditLedgerService;
import com.cognivault.repository.ApprovalRequestRepository;
import com.cognivault.repository.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock
    private ApprovalRequestRepository approvals;

    @Mock
    private UserAccountRepository users;

        @Mock
        private EmployeeScopeService employeeScope;
    @Mock
    private EmployeeService employees;

    @Mock
    private RollbackService rollbackService;

        @Mock
        private AuditEventRepository auditEvents;

        @Mock
        private AuditLedgerService auditLedger;

    private ApprovalService service;
    private UserAccount requester;
    private UserAccount approver;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(
                approvals,
                users,
                employees,
                rollbackService,
                new ObjectMapper(),
                auditEvents,
                auditLedger);

        requester = user("requester", Role.ADMIN);
        approver = user("approver", Role.ADMIN);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        ApprovalRequest approval = pending(ApprovalRequestType.CREDENTIAL_RESET);

        when(users.findByUsernameIgnoreCase("requester")).thenReturn(Optional.of(requester));

        when(approvals.findById(approval.getId()))
                .thenReturn(Optional.of(approval));

        authenticate("requester", requester.getId());

        assertThrows(
                AccessDeniedException.class,
                () -> service.decide(
                        approval.getId(),
                        new ApprovalDtos.DecisionRequest(
                                ApprovalDtos.Decision.APPROVE,
                                "Self approval")));

        verify(employees, never()).resetCredential(any(), any());
    }

    @Test
    void rejectionDoesNotExecuteMutation() {
        ApprovalRequest approval = pending(ApprovalRequestType.CREDENTIAL_RESET);

        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));

        when(approvals.findById(approval.getId()))
                .thenReturn(Optional.of(approval));
        when(approvals.save(approval))
                .thenReturn(approval);

        authenticate("approver", approver.getId());

        var response = service.decide(
                approval.getId(),
                new ApprovalDtos.DecisionRequest(
                        ApprovalDtos.Decision.REJECT,
                        "Not approved"));

        assertEquals(ApprovalStatus.REJECTED, response.status());
        verify(employees, never()).resetCredential(any(), any());
    }

    @Test
    void alreadyDecidedRequestCannotBeDecidedAgain() {
        ApprovalRequest approval = pending(ApprovalRequestType.CREDENTIAL_RESET);
        approval.decide(approver, ApprovalStatus.REJECTED, "Already rejected");

        when(approvals.findById(approval.getId()))
                .thenReturn(Optional.of(approval));

        authenticate("approver", approver.getId());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.decide(
                        approval.getId(),
                        new ApprovalDtos.DecisionRequest(
                                ApprovalDtos.Decision.APPROVE,
                                "Too late")));

        verify(employees, never()).resetCredential(any(), any());
    }

    @Test
    void unauthorizedRoleCannotApproveAdminOnlyRequest() {
        UserAccount manager = user("manager", Role.MANAGER);
        when(users.findByUsernameIgnoreCase("manager"))
                .thenReturn(Optional.of(manager));

        ApprovalRequest approval = pending(ApprovalRequestType.CREDENTIAL_RESET);

        when(approvals.findById(approval.getId()))
                .thenReturn(Optional.of(approval));

        authenticate("manager", manager.getId());

        assertThrows(
                AccessDeniedException.class,
                () -> service.decide(
                        approval.getId(),
                        new ApprovalDtos.DecisionRequest(
                                ApprovalDtos.Decision.APPROVE,
                                "Manager approval")));

        verify(employees, never()).resetCredential(any(), any());
    }

    @Test
    void approvedCredentialResetExecutesEmployeeService() {
        UUID employeeId = UUID.randomUUID();

        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));

        ApprovalRequest approval = new ApprovalRequest(
                requester,
                ApprovalRequestType.CREDENTIAL_RESET,
                employeeId.toString(),
                "ADMIN",
                null,
                "Reset requested",
                null,
                Map.of(
                        "generateTemporary", true,
                        "forceChange", true));

        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());

        when(approvals.findById(approval.getId()))
                .thenReturn(Optional.of(approval));
        when(approvals.save(approval))
                .thenReturn(approval);

        authenticate("approver", approver.getId());

        when(employees.resetCredential(
                any(UUID.class),
                any(com.cognivault.api.EmployeeDtos.CredentialResetRequest.class)))
                .thenReturn(new com.cognivault.api.EmployeeDtos.CredentialResetResponse(
                        "TEST-TEMP-PASSWORD",
                        null));

        var response = service.decide(
                approval.getId(),
                new ApprovalDtos.DecisionRequest(
                        ApprovalDtos.Decision.APPROVE,
                        "Approved"));

        verify(employees).resetCredential(
                any(UUID.class),
                any(com.cognivault.api.EmployeeDtos.CredentialResetRequest.class));
        assertEquals(ApprovalStatus.APPROVED, approval.getStatus());
        assertEquals("TEST-TEMP-PASSWORD", response.temporaryPassword());
    }

    @Test
    void rejectedRemediationDoesNotMutateAndRecordsRejectedExecution() {
        ApprovalRequest approval = remediation(ApprovalStatus.PENDING);
        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        when(approvals.save(approval)).thenReturn(approval);
        authenticate("approver", approver.getId());

        var response = service.decide(approval.getId(), new ApprovalDtos.DecisionRequest(
                ApprovalDtos.Decision.REJECT, "Rejected after review"));

        assertEquals(ApprovalStatus.REJECTED, response.status());
        assertEquals("REJECTED", response.executionStatus());
        verify(employees, never()).transition(any(), any());
        verify(rollbackService, never()).executeRollback(any(), any(), any(), any());
    }

        @Test
        void remediationRequesterCannotApproveOwnRequest() {
                ApprovalRequest approval = remediation(ApprovalStatus.PENDING);
                when(users.findByUsernameIgnoreCase("requester")).thenReturn(Optional.of(requester));
                when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
                authenticate("requester", requester.getId());

                assertThrows(AccessDeniedException.class, () -> service.decide(
                                approval.getId(), new ApprovalDtos.DecisionRequest(
                                                ApprovalDtos.Decision.APPROVE, "Self approval")));

                verify(employees, never()).transition(any(), any());
        }

    @Test
    void approvedRemediationExecutesMutationAndVerifiesAuditChain() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest approval = remediation(ApprovalStatus.PENDING);
        ReflectionTestUtils.setField(approval, "target", employeeId.toString());
        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        when(approvals.save(approval)).thenReturn(approval);
        var user = org.mockito.Mockito.mock(UserAccount.class);
        when(user.getStatus()).thenReturn(AccountStatus.SUSPENDED);
        var employee = org.mockito.Mockito.mock(Employee.class);
        when(employee.getUser()).thenReturn(user);
        when(employees.get(employeeId)).thenReturn(employee);
        when(auditEvents.findFirstByEntityTypeAndEntityIdOrderByTimestampDesc("EMPLOYEE", employeeId.toString()))
                .thenReturn(Optional.of(new AuditEventDocument(
                        "evt-new", "approver", approver.getId().toString(), "ACCOUNT_STATUS_CHANGED",
                        "EMPLOYEE", employeeId.toString(), java.time.Instant.now(), Map.of(), Map.of(),
                        "prev", "hash", 1)));
        when(auditLedger.verify()).thenReturn(new AuditLedgerService.IntegrityResult(
                "VERIFIED", java.time.Instant.now(), 1, null, "GENESIS", "hash", 1, "Verified"));
        authenticate("approver", approver.getId());

        var response = service.decide(approval.getId(), new ApprovalDtos.DecisionRequest(
                ApprovalDtos.Decision.APPROVE, "Approved"));

        verify(employees).transition(employeeId, new com.cognivault.api.EmployeeDtos.AccountActionRequest(
                "SUSPEND", "Remediation test"));
        assertEquals("SUCCEEDED", response.executionStatus());
        assertEquals("VERIFIED", response.verificationStatus());
        assertEquals(ApprovalStatus.APPROVED, response.status());
        verify(auditLedger).verify();
    }

    @Test
    void approvedCredentialRemediationDoesNotReturnOrPersistTemporaryPassword() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest approval = new ApprovalRequest(
                requester, ApprovalRequestType.REMEDIATION, employeeId.toString(), "ADMIN", null,
                "Reset credentials", null, Map.of("generateTemporary", false, "forceChange", true));
        approval.configureRemediation("CREDENTIAL_RESET", "evt-finding");
        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        when(approvals.save(approval)).thenReturn(approval);
        var user = org.mockito.Mockito.mock(UserAccount.class);
        when(user.isMustChangePassword()).thenReturn(true);
        var employee = org.mockito.Mockito.mock(Employee.class);
        when(employee.getUser()).thenReturn(user);
        when(employees.get(employeeId)).thenReturn(employee);
        when(employees.resetCredential(eq(employeeId), any()))
                .thenReturn(new com.cognivault.api.EmployeeDtos.CredentialResetResponse(null, "evt-reset"));
        when(auditEvents.findByEventId("evt-reset")).thenReturn(Optional.of(new AuditEventDocument(
                "evt-reset", "approver", approver.getId().toString(), "CREDENTIAL_RESET", "EMPLOYEE",
                employeeId.toString(), java.time.Instant.now(), Map.of(), Map.of(), "prev", "hash", 1)));
        when(auditLedger.verify()).thenReturn(new AuditLedgerService.IntegrityResult(
                "VERIFIED", java.time.Instant.now(), 1, null, "GENESIS", "hash", 1, "Verified"));
        authenticate("approver", approver.getId());

        var response = service.decide(approval.getId(), new ApprovalDtos.DecisionRequest(
                ApprovalDtos.Decision.APPROVE, "Approved"));

        verify(employees).resetCredential(eq(employeeId), any());
        assertEquals("SUCCEEDED", response.executionStatus());
        assertEquals("VERIFIED", response.verificationStatus());
        org.junit.jupiter.api.Assertions.assertNull(response.temporaryPassword());
        org.junit.jupiter.api.Assertions.assertFalse(approval.getRequestedState().containsKey("password"));
    }

    @Test
    void approvedRollbackRemediationUsesRollbackServiceAndVerifiesNewAuditEvent() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest approval = new ApprovalRequest(
                requester, ApprovalRequestType.REMEDIATION, employeeId.toString(), "ADMIN", null,
                "Restore historical state", null, Map.of("firstName", "Historic"));
        approval.configureRemediation("ROLLBACK", "evt-finding");
        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        when(approvals.save(approval)).thenReturn(approval);
        when(auditEvents.findFirstByEntityTypeAndEntityIdOrderByTimestampDesc("EMPLOYEE", employeeId.toString()))
                .thenReturn(Optional.of(new AuditEventDocument(
                                "evt-before", "admin", "admin-id", "EMPLOYEE_UPDATED", "EMPLOYEE",
                                employeeId.toString(), java.time.Instant.now(), Map.of(), Map.of(), "prev", "hash-1", 1)),
                        Optional.of(new AuditEventDocument(
                                "evt-rollback", "approver", approver.getId().toString(), "EMPLOYEE_ROLLED_BACK",
                                "EMPLOYEE", employeeId.toString(), java.time.Instant.now(), Map.of(), Map.of(),
                                "hash-1", "hash-2", 2)));
        var restoredEmployee = org.mockito.Mockito.mock(Employee.class);
        when(restoredEmployee.getFirstName()).thenReturn("Historic");
        when(employees.get(employeeId)).thenReturn(restoredEmployee);
        when(auditLedger.verify()).thenReturn(new AuditLedgerService.IntegrityResult(
                "VERIFIED", java.time.Instant.now(), 2, null, "hash-1", "hash-2", 2, "Verified"));
        authenticate("approver", approver.getId());

        var response = service.decide(approval.getId(), new ApprovalDtos.DecisionRequest(
                ApprovalDtos.Decision.APPROVE, "Approved"));

        verify(rollbackService).executeRollback(eq(employeeId), eq(approval.getRequestedState()),
                eq("Restore historical state"), any());
        assertEquals("SUCCEEDED", response.executionStatus());
        assertEquals("VERIFIED", response.verificationStatus());
    }

    @Test
    void failedApprovedRemediationIsNotReportedAsSuccessful() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest approval = remediation(ApprovalStatus.PENDING);
        ReflectionTestUtils.setField(approval, "target", employeeId.toString());
        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        when(approvals.save(approval)).thenReturn(approval);
        doThrow(new IllegalStateException("mutation unavailable"))
                .when(employees).transition(any(), any());
        authenticate("approver", approver.getId());

        var response = service.decide(approval.getId(), new ApprovalDtos.DecisionRequest(
                ApprovalDtos.Decision.APPROVE, "Approved"));

        assertEquals(ApprovalStatus.APPROVED, response.status());
        assertEquals("FAILED", response.executionStatus());
        assertEquals("FAILED", response.verificationStatus());
        verify(employees, never()).get(any());
        verify(auditLedger, never()).verify();
    }

    @Test
    void managerCannotCreateEmployeeApprovalOutsideAssignedTeam() {
        UUID employeeId = UUID.randomUUID();
        UserAccount manager = user("manager", Role.MANAGER);
        when(users.findByUsernameIgnoreCase("manager")).thenReturn(Optional.of(manager));
        doThrow(new AccessDeniedException("outside assigned scope"))
                .when(employeeScope).assertManagedBy(employeeId, manager);
        service = new ApprovalService(
                approvals, users, employees, rollbackService, new ObjectMapper(),
                auditEvents, auditLedger, employeeScope);
        authenticate("manager", manager.getId());

        assertThrows(AccessDeniedException.class, () -> service.create(
                new ApprovalDtos.CreateApprovalRequest(
                        ApprovalRequestType.ACCOUNT_STATUS_CHANGE,
                        employeeId.toString(),
                        "Suspend request",
                        null,
                        Map.of("action", "SUSPEND", "reason", "Review"))));

        verify(employeeScope).assertManagedBy(employeeId, manager);
        verify(approvals, never()).save(any());
    }

    @Test
    void managerCannotApproveOutOfTeamEmployeeRequest() {
        UUID employeeId = UUID.randomUUID();
        UserAccount manager = user("manager", Role.MANAGER);
        ApprovalRequest approval = new ApprovalRequest(
                requester,
                ApprovalRequestType.EMPLOYEE_UPDATE,
                employeeId.toString(),
                "ADMIN/MANAGER",
                null,
                "Update request",
                null,
                Map.of("firstName", "Updated"));
        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
        when(users.findByUsernameIgnoreCase("manager")).thenReturn(Optional.of(manager));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        doThrow(new AccessDeniedException("outside assigned scope"))
                .when(employeeScope).assertManagedBy(employeeId, manager);
        service = new ApprovalService(
                approvals, users, employees, rollbackService, new ObjectMapper(),
                auditEvents, auditLedger, employeeScope);
        authenticate("manager", manager.getId());

        assertThrows(AccessDeniedException.class, () -> service.decide(
                approval.getId(), new ApprovalDtos.DecisionRequest(
                        ApprovalDtos.Decision.APPROVE, "Approve")));

        verify(employeeScope).assertManagedBy(employeeId, manager);
        verify(employees, never()).update(any(), any(), any());
    }

    @Test
    void adminCannotExecuteManagerRequestAfterTargetLeavesRequestersTeam() {
        UUID employeeId = UUID.randomUUID();
        UserAccount managerRequester = user("team-manager", Role.MANAGER);
        ApprovalRequest approval = new ApprovalRequest(
                managerRequester,
                ApprovalRequestType.ACCOUNT_STATUS_CHANGE,
                employeeId.toString(),
                "ADMIN/MANAGER",
                null,
                "Suspend request",
                null,
                Map.of("action", "SUSPEND", "reason", "Review"));
        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
        when(users.findByUsernameIgnoreCase("approver")).thenReturn(Optional.of(approver));
        when(approvals.findById(approval.getId())).thenReturn(Optional.of(approval));
        doThrow(new AccessDeniedException("requester no longer manages target"))
                .when(employeeScope).assertManagedBy(employeeId, managerRequester);
        service = new ApprovalService(
                approvals, users, employees, rollbackService, new ObjectMapper(),
                auditEvents, auditLedger, employeeScope);
        authenticate("approver", approver.getId());

        assertThrows(AccessDeniedException.class, () -> service.decide(
                approval.getId(), new ApprovalDtos.DecisionRequest(
                        ApprovalDtos.Decision.APPROVE, "Approve")));

        verify(employeeScope).assertManagedBy(employeeId, managerRequester);
        verify(employees, never()).transition(any(), any());
    }

    @Test
    void managerApprovalListContainsOnlyOwnTeamRequests() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest ownRequest = approvalFor(
                ApprovalRequestType.EMPLOYEE_UPDATE, employeeId.toString(), requester);
        var pageable = PageRequest.of(0, 10);
        setUpManagerReadScope(Set.of(employeeId.toString()));
        when(approvals.findByRequestTypeInAndTargetInOrderByCreatedAtDesc(
                List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE),
                List.of(employeeId.toString()), pageable))
                .thenReturn(new PageImpl<>(List.of(ownRequest), pageable, 1));

        var result = service.list(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(ownRequest.getId(), result.getContent().get(0).id());
        verify(approvals).findByRequestTypeInAndTargetInOrderByCreatedAtDesc(
                List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE),
                List.of(employeeId.toString()), pageable);
        verify(approvals, never()).findByRequestTypeNotOrderByCreatedAtDesc(
                any(ApprovalRequestType.class), any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void managerApprovalListExcludesOtherTeamRequests() {
        UUID ownEmployeeId = UUID.randomUUID();
        UUID otherEmployeeId = UUID.randomUUID();
        ApprovalRequest ownRequest = approvalFor(
                ApprovalRequestType.EMPLOYEE_UPDATE, ownEmployeeId.toString(), requester);
        ApprovalRequest otherRequest = approvalFor(
                ApprovalRequestType.ACCOUNT_STATUS_CHANGE, otherEmployeeId.toString(), requester);
        var pageable = PageRequest.of(0, 10);
        setUpManagerReadScope(Set.of(ownEmployeeId.toString()));
        when(approvals.findByRequestTypeInAndTargetInOrderByCreatedAtDesc(
                List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE),
                List.of(ownEmployeeId.toString()), pageable))
                .thenAnswer(invocation -> {
                    List<String> queryTargets = invocation.getArgument(1);
                    List<ApprovalRequest> matching = List.of(ownRequest, otherRequest).stream()
                            .filter(request -> queryTargets.contains(request.getTarget()))
                            .toList();
                    return new PageImpl<>(matching, pageable, matching.size());
                });

        var result = service.list(pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(List.of(ownRequest.getId()), result.getContent().stream()
                .map(ApprovalDtos.ApprovalResponse::id).toList());
        verify(approvals).findByRequestTypeInAndTargetInOrderByCreatedAtDesc(
                List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE),
                List.of(ownEmployeeId.toString()), pageable);
        verify(approvals, never()).findById(any());
    }

    @Test
    void managerApprovalStatusListIsTeamScoped() {
        UUID employeeId = UUID.randomUUID();
        ApprovalRequest ownRequest = approvalFor(
                ApprovalRequestType.ACCOUNT_STATUS_CHANGE, employeeId.toString(), requester);
        var pageable = PageRequest.of(0, 10);
        setUpManagerReadScope(Set.of(employeeId.toString()));
        when(approvals.findByRequestTypeInAndStatusAndTargetInOrderByCreatedAtDesc(
                List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE),
                ApprovalStatus.PENDING, List.of(employeeId.toString()), pageable))
                .thenReturn(new PageImpl<>(List.of(ownRequest), pageable, 1));

        var result = service.list(ApprovalStatus.PENDING, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(ownRequest.getId(), result.getContent().get(0).id());
        verify(approvals).findByRequestTypeInAndStatusAndTargetInOrderByCreatedAtDesc(
                List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE),
                ApprovalStatus.PENDING, List.of(employeeId.toString()), pageable);
        verify(approvals, never()).findByRequestTypeNotAndStatusOrderByCreatedAtDesc(any(), any(), any());
    }

    @Test
    void managerCannotGetOtherTeamApprovalById() {
        UUID otherEmployeeId = UUID.randomUUID();
        ApprovalRequest otherRequest = approvalFor(
                ApprovalRequestType.EMPLOYEE_UPDATE, otherEmployeeId.toString(), requester);
        UserAccount manager = user("manager", Role.MANAGER);
        authenticate("manager", manager.getId());
        service = new ApprovalService(
                approvals, users, employees, rollbackService, new ObjectMapper(),
                auditEvents, auditLedger, employeeScope);
        when(approvals.findById(otherRequest.getId())).thenReturn(Optional.of(otherRequest));
        doThrow(new AccessDeniedException("outside assigned scope"))
                .when(employeeScope).assertManagedBy(
                        eq(otherEmployeeId), any(org.springframework.security.core.Authentication.class));

        assertThrows(AccessDeniedException.class, () -> service.get(otherRequest.getId()));
    }

    @Test
    void managerWithNoEmployeesSeesNoApprovals() {
        var pageable = PageRequest.of(0, 10);
        setUpManagerReadScope(Set.of());

        var result = service.list(pageable);

        assertEquals(0, result.getTotalElements());
        assertEquals(List.of(), result.getContent());
        verifyNoInteractions(approvals);
    }

    @Test
    void auditorApprovalListRetainsOrganizationWideQuery() {
        var pageable = PageRequest.of(0, 10);
        authenticate("auditor", UUID.randomUUID());
        when(approvals.findByRequestTypeNotOrderByCreatedAtDesc(
                ApprovalRequestType.REMEDIATION, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        var result = service.list(pageable);

        assertEquals(0, result.getTotalElements());
        verify(approvals).findByRequestTypeNotOrderByCreatedAtDesc(ApprovalRequestType.REMEDIATION, pageable);
    }

    private void setUpManagerReadScope(Set<String> employeeIds) {
        UserAccount manager = user("manager", Role.MANAGER);
        authenticate("manager", manager.getId());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        when(employeeScope.managedEmployeeIds(authentication)).thenReturn(employeeIds);
        service = new ApprovalService(
                approvals, users, employees, rollbackService, new ObjectMapper(),
                auditEvents, auditLedger, employeeScope);
    }

    private ApprovalRequest approvalFor(
            ApprovalRequestType type,
            String target,
            UserAccount requestUser) {
        ApprovalRequest approval = new ApprovalRequest(
                requestUser, type, target, "ADMIN/MANAGER", null, "Test approval", null, Map.of());
        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
        return approval;
    }
    private ApprovalRequest pending(ApprovalRequestType type) {
        ApprovalRequest approval = new ApprovalRequest(
                requester,
                type,
                UUID.randomUUID().toString(),
                requiredApprover(type),
                null,
                "Test request",
                null,
                Map.of());

        ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
        return approval;
    }

    private String requiredApprover(ApprovalRequestType type) {
        return switch (type) {
            case EMPLOYEE_CREATE, CREDENTIAL_RESET, ROLLBACK -> "ADMIN";
            case EMPLOYEE_UPDATE, ACCOUNT_STATUS_CHANGE -> "ADMIN/MANAGER";
                        case REMEDIATION -> "ADMIN/MANAGER/HR";
        };
    }

        private ApprovalRequest remediation(ApprovalStatus initialStatus) {
                ApprovalRequest request = new ApprovalRequest(
                                requester,
                                ApprovalRequestType.REMEDIATION,
                                UUID.randomUUID().toString(),
                                "ADMIN/MANAGER",
                                null,
                                "Remediation test",
                                null,
                                Map.of("action", "SUSPEND", "reason", "Remediation test"));
                request.configureRemediation("ACCOUNT_STATUS_CHANGE", "evt-finding");
                if (initialStatus != ApprovalStatus.PENDING) {
                        request.decide(approver, initialStatus, "test");
                }
                ReflectionTestUtils.setField(request, "id", UUID.randomUUID());
                return request;
        }

    private UserAccount user(String username, Role role) {
        UserAccount user = new UserAccount(
                username,
                username + "@example.test",
                username,
                "$2a$10$not-used",
                role,
                AccountStatus.ACTIVE,
                false);

        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private void authenticate(String username, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        new com.cognivault.security.AuthenticatedPrincipal(userId, username),
                        null,
                        List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                "ROLE_" + (username.equals("manager") ? "MANAGER" : "ADMIN")))));
    }
}
