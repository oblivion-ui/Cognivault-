package com.cognivault.service;

import com.cognivault.api.ApprovalDtos;
import com.cognivault.api.EmployeeDtos;
import com.cognivault.api.RemediationDtos;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditLedgerService;
import com.cognivault.domain.*;
import com.cognivault.repository.ApprovalRequestRepository;
import com.cognivault.repository.UserAccountRepository;
import com.cognivault.security.CurrentActor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ApprovalService {

    private final ApprovalRequestRepository approvals;
    private final UserAccountRepository users;
    private final EmployeeService employees;
    private final RollbackService rollbackService;
    private final ObjectMapper mapper;
    private final AuditEventRepository auditEvents;
    private final AuditLedgerService auditLedger;
    private final TransactionTemplate remediationTransactions;
    private final EmployeeScopeService employeeScope;

    public ApprovalService(
            ApprovalRequestRepository approvals,
            UserAccountRepository users,
            EmployeeService employees,
            RollbackService rollbackService,
            ObjectMapper mapper) {
        this.approvals = approvals;
        this.users = users;
        this.employees = employees;
        this.rollbackService = rollbackService;
        this.mapper = mapper;
        this.auditEvents = null;
        this.auditLedger = null;
        this.remediationTransactions = null;
        this.employeeScope = null;
    }

    public ApprovalService(
            ApprovalRequestRepository approvals,
            UserAccountRepository users,
            EmployeeService employees,
            RollbackService rollbackService,
            ObjectMapper mapper,
            AuditEventRepository auditEvents,
            AuditLedgerService auditLedger) {
        this.approvals = approvals;
        this.users = users;
        this.employees = employees;
        this.rollbackService = rollbackService;
        this.mapper = mapper;
        this.auditEvents = auditEvents;
        this.auditLedger = auditLedger;
        this.remediationTransactions = null;
        this.employeeScope = null;
    }

    public ApprovalService(
            ApprovalRequestRepository approvals,
            UserAccountRepository users,
            EmployeeService employees,
            RollbackService rollbackService,
            ObjectMapper mapper,
            AuditEventRepository auditEvents,
            AuditLedgerService auditLedger,
            EmployeeScopeService employeeScope) {
        this.approvals = approvals;
        this.users = users;
        this.employees = employees;
        this.rollbackService = rollbackService;
        this.mapper = mapper;
        this.auditEvents = auditEvents;
        this.auditLedger = auditLedger;
        this.employeeScope = employeeScope;
        this.remediationTransactions = null;
    }

    @Autowired
    public ApprovalService(
            ApprovalRequestRepository approvals,
            UserAccountRepository users,
            EmployeeService employees,
            RollbackService rollbackService,
            ObjectMapper mapper,
            AuditEventRepository auditEvents,
            AuditLedgerService auditLedger,
            EmployeeScopeService employeeScope,
            PlatformTransactionManager transactionManager) {
        this.approvals = approvals;
        this.users = users;
        this.employees = employees;
        this.rollbackService = rollbackService;
        this.mapper = mapper;
        this.auditEvents = auditEvents;
        this.auditLedger = auditLedger;
        this.employeeScope = employeeScope;
        this.remediationTransactions = new TransactionTemplate(transactionManager);
        this.remediationTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public ApprovalDtos.ApprovalResponse create(ApprovalDtos.CreateApprovalRequest request) {
        if (request.requestType() == ApprovalRequestType.REMEDIATION) {
            throw new AccessDeniedException("Use the remediation request endpoint to create controlled remediation requests");
        }
        UserAccount requester = currentUser();
        assertCanRequest(request.requestType(), requester);
        assertManagerTargetScope(request.requestType(), request.target(), requester);

        Map<String, Object> requestedState =
                request.requestedState() == null
                        ? Map.of()
                        : new HashMap<>(request.requestedState());

        Map<String, Object> previousState =
                request.previousState() == null
                        ? null
                        : new HashMap<>(request.previousState());

        if (request.requestType() == ApprovalRequestType.EMPLOYEE_CREATE) {
            requestedState.remove("password");

            Object credentialObject = requestedState.get("credential");
            if (credentialObject instanceof Map<?, ?> credential) {
                Map<String, Object> safeCredential = new HashMap<>();
                safeCredential.put("method", "GENERATE");
                safeCredential.put(
                        "forceChange",
                        credential.get("forceChange") instanceof Boolean value
                                ? value
                                : Boolean.TRUE);
                requestedState.put("credential", safeCredential);
            }
        }

        String requiredApprover = requiredApprover(request.requestType());

        ApprovalRequest approval = new ApprovalRequest(
                requester,
                request.requestType(),
                request.target(),
                requiredApprover,
                null,
                request.justification(),
                previousState,
                requestedState);

        return toResponse(approvals.save(approval));
    }

    @Transactional
    public ApprovalDtos.ApprovalResponse createRemediation(
            UUID employeeId,
            RemediationDtos.Action action,
            String sourceFindingEventId,
            String justification,
            Map<String, Object> requestedState) {
        UserAccount requester = currentUser();
        assertCanRequest(ApprovalRequestType.REMEDIATION, requester);
        if (requester.getRole() == Role.MANAGER || requester.getRole() == Role.HR) {
            employees.assertVisible(employeeId, currentAuthentication());
        }
        if ((action == RemediationDtos.Action.CREDENTIAL_RESET || action == RemediationDtos.Action.ROLLBACK)
                && requester.getRole() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators may request this remediation action");
        }

        ApprovalRequest approval = new ApprovalRequest(
                requester,
                ApprovalRequestType.REMEDIATION,
                employeeId.toString(),
                action == RemediationDtos.Action.CREDENTIAL_RESET || action == RemediationDtos.Action.ROLLBACK
                        ? "ADMIN" : "ADMIN/MANAGER",
                null,
                justification,
                null,
                requestedState == null ? Map.of() : new HashMap<>(requestedState));
        approval.configureRemediation(action.name(), sourceFindingEventId);
        return toResponse(approvals.save(approval));
    }

    @Transactional(readOnly = true)
    public Page<ApprovalDtos.ApprovalResponse> list(Pageable pageable) {
        if (isManager(currentAuthentication())) {
            List<String> employeeIds = managerEmployeeIds();
            if (employeeIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return approvals.findByRequestTypeInAndTargetInOrderByCreatedAtDesc(
                            managerVisibleRequestTypes(), employeeIds, pageable)
                    .map(this::toResponse);
        }
        return approvals.findByRequestTypeNotOrderByCreatedAtDesc(ApprovalRequestType.REMEDIATION, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalDtos.ApprovalResponse> list(
            ApprovalStatus status,
            Pageable pageable) {
        if (isManager(currentAuthentication())) {
            List<String> employeeIds = managerEmployeeIds();
            if (employeeIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return approvals.findByRequestTypeInAndStatusAndTargetInOrderByCreatedAtDesc(
                            managerVisibleRequestTypes(), status, employeeIds, pageable)
                    .map(this::toResponse);
        }
        return approvals.findByRequestTypeNotAndStatusOrderByCreatedAtDesc(
                ApprovalRequestType.REMEDIATION, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ApprovalDtos.ApprovalResponse get(UUID id) {
        ApprovalRequest approval = approvals.findById(id)
            .filter(request -> request.getRequestType() != ApprovalRequestType.REMEDIATION)
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));
        if (isManager(currentAuthentication())) {
            if (!managerVisibleRequestTypes().contains(approval.getRequestType())) {
                throw new AccessDeniedException("Approval request is outside the manager's supported scope");
            }
            if (employeeScope == null) {
                throw new AccessDeniedException("Manager employee scope is unavailable");
            }
            try {
                employeeScope.assertManagedBy(UUID.fromString(approval.getTarget()), currentAuthentication());
            } catch (IllegalArgumentException exception) {
                throw new AccessDeniedException("Approval request is outside the manager's assigned scope");
            }
        }
        return toResponse(approval);
    }

    private boolean isManager(org.springframework.security.core.Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGER"));
    }

    private List<String> managerEmployeeIds() {
        if (employeeScope == null) {
            throw new AccessDeniedException("Manager employee scope is unavailable");
        }
        return employeeScope.managedEmployeeIds(currentAuthentication()).stream().sorted().toList();
    }

    private List<ApprovalRequestType> managerVisibleRequestTypes() {
        return List.of(ApprovalRequestType.EMPLOYEE_UPDATE, ApprovalRequestType.ACCOUNT_STATUS_CHANGE);
    }

    @Transactional
    public ApprovalDtos.ApprovalResponse decide(
            UUID id,
            ApprovalDtos.DecisionRequest request) {

        ApprovalRequest approval = approvals.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Only pending approval requests can be decided");
        }

        UserAccount approver = currentUser();

        if (approval.getRequester().getId().equals(approver.getId())) {
            throw new AccessDeniedException("The requester cannot approve or reject their own request");
        }

        assertCanApprove(approval, approver);
        assertManagerTargetScope(approval, approver);
        if (approval.getRequestType() == ApprovalRequestType.REMEDIATION
                && (approver.getRole() == Role.MANAGER || approver.getRole() == Role.HR)) {
            employees.assertVisible(UUID.fromString(approval.getTarget()), currentAuthentication());
        }

        ApprovalStatus decision = request.decision() == ApprovalDtos.Decision.APPROVE
                ? ApprovalStatus.APPROVED
                : ApprovalStatus.REJECTED;

        String temporaryPassword = null;

        if (decision == ApprovalStatus.APPROVED) {
            temporaryPassword = executeApprovedRequest(approval);
        } else if (approval.getRequestType() == ApprovalRequestType.REMEDIATION) {
            approval.recordExecution("REJECTED", "Request rejected; no mutation was executed.");
            approval.recordVerification("NOT_RUN", "Rejected requests are not executed or verified.", Instant.now());
        }

        approval.decide(approver, decision, request.comment());

        ApprovalRequest saved = approvals.save(approval);

        return toResponse(saved, temporaryPassword);
    }

    private void assertManagerTargetScope(
            ApprovalRequestType type,
            String target,
            UserAccount requester) {
        if (requester.getRole() == Role.MANAGER
                && (type == ApprovalRequestType.EMPLOYEE_UPDATE
                        || type == ApprovalRequestType.ACCOUNT_STATUS_CHANGE)) {
            if (employeeScope == null) {
                throw new AccessDeniedException("Manager employee scope is unavailable");
            }
            employeeScope.assertManagedBy(UUID.fromString(target), requester);
        }
    }

    private void assertManagerTargetScope(ApprovalRequest approval, UserAccount approver) {
        if (approval.getRequestType() != ApprovalRequestType.EMPLOYEE_UPDATE
                && approval.getRequestType() != ApprovalRequestType.ACCOUNT_STATUS_CHANGE) {
            return;
        }
        UUID employeeId = UUID.fromString(approval.getTarget());
        if (approval.getRequester().getRole() == Role.MANAGER) {
            if (employeeScope == null) {
                throw new AccessDeniedException("Manager employee scope is unavailable");
            }
            employeeScope.assertManagedBy(employeeId, approval.getRequester());
        }
        if (approver.getRole() == Role.MANAGER) {
            if (employeeScope == null) {
                throw new AccessDeniedException("Manager employee scope is unavailable");
            }
            employeeScope.assertManagedBy(employeeId, approver);
        }
    }

    private String executeApprovedRequest(ApprovalRequest approval) {
        Map<String, Object> state = approval.getRequestedState();

        return switch (approval.getRequestType()) {
            case EMPLOYEE_CREATE -> {
                EmployeeDtos.CreateEmployeeRequest request =
                        mapper.convertValue(state, EmployeeDtos.CreateEmployeeRequest.class);

                EmployeeDtos.Credential credential = request.credential();

                EmployeeDtos.Credential safeCredential =
                        new EmployeeDtos.Credential(
                                EmployeeDtos.CredentialMethod.GENERATE,
                                null,
                                credential.forceChange());

                EmployeeDtos.CreateEmployeeRequest executable =
                        new EmployeeDtos.CreateEmployeeRequest(
                                request.personal(),
                                request.employment(),
                                request.account(),
                                safeCredential);

                EmployeeDtos.CreateEmployeeResponse response = employees.create(executable);
                yield response.temporaryPassword();
            }

            case EMPLOYEE_UPDATE -> {
                UUID employeeId = UUID.fromString(approval.getTarget());

                EmployeeDtos.UpdateEmployeeRequest request =
                        mapper.convertValue(state, EmployeeDtos.UpdateEmployeeRequest.class);

                employees.update(employeeId, request, currentAuthentication());
                yield null;
            }

            case ACCOUNT_STATUS_CHANGE -> {
                UUID employeeId = UUID.fromString(approval.getTarget());

                EmployeeDtos.AccountActionRequest request =
                        mapper.convertValue(state, EmployeeDtos.AccountActionRequest.class);

                employees.transition(employeeId, request);
                yield null;
            }

            case CREDENTIAL_RESET -> {
                UUID employeeId = UUID.fromString(approval.getTarget());

                EmployeeDtos.CredentialResetRequest request =
                        mapper.convertValue(state, EmployeeDtos.CredentialResetRequest.class);

                EmployeeDtos.CredentialResetResponse response =
                        employees.resetCredential(employeeId, request);

                yield response.temporaryPassword();
            }

            case ROLLBACK -> {
                UUID employeeId = UUID.fromString(approval.getTarget());
                String justification = approval.getJustification();

                rollbackService.executeRollback(employeeId, state, justification, currentAuthentication());
                yield null;
            }
            case REMEDIATION -> {
                executeApprovedRemediation(approval);
                yield null;
            }
        };
    }

    private void executeApprovedRemediation(ApprovalRequest approval) {
        UUID employeeId = UUID.fromString(approval.getTarget());
        RemediationDtos.Action action = RemediationDtos.Action.valueOf(approval.getRemediationAction());
        org.springframework.security.core.Authentication authentication = currentAuthentication();
        if (action == RemediationDtos.Action.ESCALATION_REVIEW) {
            approval.recordExecution("NOT_REQUIRED", "Review escalation recorded; no employee mutation was requested.");
            approval.recordVerification("NOT_REQUIRED", "No mutation required verification.", Instant.now());
            return;
        }
        try {
            if (remediationTransactions == null) {
                executeRemediationMutation(approval, employeeId, action, authentication);
            } else {
                remediationTransactions.execute(status -> {
                    executeRemediationMutation(approval, employeeId, action, authentication);
                    return null;
                });
            }
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            if (reason.length() > 1500) {
                reason = reason.substring(0, 1500);
            }
            approval.recordExecution("FAILED", "Execution failed: " + reason);
            approval.recordVerification("FAILED", "Post-execution verification did not complete successfully.", Instant.now());
        }
    }

    private void executeRemediationMutation(
            ApprovalRequest approval,
            UUID employeeId,
            RemediationDtos.Action action,
            org.springframework.security.core.Authentication authentication) {
        String auditAction;
        switch (action) {
            case ACCOUNT_STATUS_CHANGE -> {
                EmployeeDtos.AccountActionRequest request = mapper.convertValue(
                        approval.getRequestedState(), EmployeeDtos.AccountActionRequest.class);
                employees.transition(employeeId, request);
                AccountStatus expected = switch (request.action().toUpperCase()) {
                    case "ACTIVATE", "REACTIVATE" -> AccountStatus.ACTIVE;
                    case "SUSPEND" -> AccountStatus.SUSPENDED;
                    case "DEACTIVATE" -> AccountStatus.DEACTIVATED;
                    default -> throw new IllegalArgumentException("Unsupported account status action");
                };
                Employee actual = employees.get(employeeId);
                if (actual.getUser().getStatus() != expected) {
                    throw new IllegalStateException("Account status did not match the approved target state");
                }
                auditAction = "ACCOUNT_STATUS_CHANGED";
            }
            case CREDENTIAL_RESET -> {
                EmployeeDtos.CredentialResetRequest request = mapper.convertValue(
                        approval.getRequestedState(), EmployeeDtos.CredentialResetRequest.class);
                EmployeeDtos.CredentialResetResponse result = employees.resetCredential(employeeId, request);
                Employee actual = employees.get(employeeId);
                if (actual.getUser().isMustChangePassword() != request.forceChange()) {
                    throw new IllegalStateException("Credential reset state did not match the approved request");
                }
                if (auditEvents == null || result.auditEventId() == null
                        || auditEvents.findByEventId(result.auditEventId()).isEmpty()) {
                    throw new IllegalStateException("Credential reset audit event was not found");
                }
                auditAction = "CREDENTIAL_RESET";
            }
            case ROLLBACK -> {
                var prior = auditEvents == null ? Optional.<com.cognivault.audit.AuditEventDocument>empty()
                        : auditEvents.findFirstByEntityTypeAndEntityIdOrderByTimestampDesc("EMPLOYEE", employeeId.toString());
                rollbackService.executeRollback(employeeId, approval.getRequestedState(), approval.getJustification(), authentication);
                var latest = auditEvents == null ? Optional.<com.cognivault.audit.AuditEventDocument>empty()
                        : auditEvents.findFirstByEntityTypeAndEntityIdOrderByTimestampDesc("EMPLOYEE", employeeId.toString());
                if (latest.isEmpty() || prior.map(event -> event.getEventId().equals(latest.get().getEventId())).orElse(false)
                        || !"EMPLOYEE_ROLLED_BACK".equals(latest.get().getAction())) {
                    throw new IllegalStateException("Rollback audit event was not recorded");
                }
                if (!matchesRollbackState(approval.getRequestedState(), employees.get(employeeId))) {
                    throw new IllegalStateException("Restored employee state did not match the approved historical state");
                }
                auditAction = "EMPLOYEE_ROLLED_BACK";
            }
            default -> throw new IllegalArgumentException("Unsupported remediation action");
        }

        if (action != RemediationDtos.Action.CREDENTIAL_RESET && action != RemediationDtos.Action.ROLLBACK) {
            var latest = auditEvents == null ? Optional.<com.cognivault.audit.AuditEventDocument>empty()
                    : auditEvents.findFirstByEntityTypeAndEntityIdOrderByTimestampDesc("EMPLOYEE", employeeId.toString());
            if (latest.isEmpty() || !auditAction.equals(latest.get().getAction())) {
                throw new IllegalStateException("Mutation audit event was not found");
            }
        }
        if (auditLedger == null || !"VERIFIED".equals(auditLedger.verify().status())) {
            throw new IllegalStateException("Audit hash-chain verification did not pass after execution");
        }
        approval.recordExecution("SUCCEEDED", "Approved operation executed through the existing service.");
        approval.recordVerification("VERIFIED", "Target state and audit evidence were verified.", Instant.now());
    }

    private boolean matchesRollbackState(Map<String, Object> expected, Employee actual) {
        if (actual == null) {
            return false;
        }
        return matches(expected, "firstName", actual.getFirstName())
                && matches(expected, "lastName", actual.getLastName())
                && matches(expected, "email", actual.getEmail())
                && matches(expected, "phone", actual.getPhone())
                && matches(expected, "department", actual.getDepartment())
                && matches(expected, "designation", actual.getDesignation())
                && matches(expected, "manager", actual.getManager())
                && matches(expected, "employmentType", actual.getEmploymentType())
                && matches(expected, "location", actual.getLocation());
    }

    private boolean matches(Map<String, Object> expected, String key, String actual) {
        return !expected.containsKey(key) || Objects.equals(
                expected.get(key) == null ? null : String.valueOf(expected.get(key)), actual);
    }

    private void assertCanRequest(
            ApprovalRequestType type,
            UserAccount user){

        switch (type) {
            case EMPLOYEE_CREATE -> {
                if (user.getRole() != Role.ADMIN && user.getRole() != Role.HR) {
                    throw new AccessDeniedException(
                            "Only administrators or HR may request employee creation");
                }
            }
            case CREDENTIAL_RESET -> {
                if (user.getRole() != Role.ADMIN) {
                    throw new AccessDeniedException(
                            "Only administrators may request credential reset");
                }
            }
            case EMPLOYEE_UPDATE, ACCOUNT_STATUS_CHANGE -> {
                if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER && user.getRole() != Role.HR) {
                    throw new AccessDeniedException(
                            "Only administrators, managers, or HR may request this operation");
                }
            }
            case ROLLBACK -> {
                if (user.getRole() != Role.ADMIN) {
                    throw new AccessDeniedException(
                            "Only administrators may request rollback operations");
                }
            }
            case REMEDIATION -> {
                if (user.getRole() != Role.ADMIN && user.getRole() != Role.HR && user.getRole() != Role.MANAGER) {
                    throw new AccessDeniedException("Only administrators, HR, or managers may request remediation");
                }
            }
        }
    }
    private void assertCanApprove(
            ApprovalRequest approval,
            UserAccount user){

        switch (approval.getRequestType()) {
            case EMPLOYEE_CREATE, CREDENTIAL_RESET, ROLLBACK -> {
                if (user.getRole() != Role.ADMIN) {
                    throw new AccessDeniedException(
                            "Only administrators may approve this operation");
                }
            }
            case EMPLOYEE_UPDATE, ACCOUNT_STATUS_CHANGE -> {
                if (user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER) {
                    throw new AccessDeniedException(
                            "Only administrators or managers may approve this operation");
                }
            }
            case REMEDIATION -> {
                RemediationDtos.Action action = RemediationDtos.Action.valueOf(
                        approval.getRemediationAction());
                if ((action == RemediationDtos.Action.CREDENTIAL_RESET || action == RemediationDtos.Action.ROLLBACK)
                        ? user.getRole() != Role.ADMIN
                        : user.getRole() != Role.ADMIN && user.getRole() != Role.MANAGER && user.getRole() != Role.HR) {
                    throw new AccessDeniedException("This role cannot approve the requested remediation");
                }
            }
        }
    }
    private String requiredApprover(ApprovalRequestType type) {
        return switch (type) {
            case EMPLOYEE_CREATE, CREDENTIAL_RESET, ROLLBACK -> "ADMIN";
            case EMPLOYEE_UPDATE, ACCOUNT_STATUS_CHANGE -> "ADMIN/MANAGER";
            case REMEDIATION -> "ADMIN/MANAGER/HR";
        };
    }

    private UserAccount currentUser() {
        CurrentActor.Actor actor = CurrentActor.get();

        if (actor.userId() == null || actor.username() == null) {
            throw new AccessDeniedException("Authenticated user identity is unavailable");
        }

        return users.findByUsernameIgnoreCase(actor.username())
                .orElseThrow(() ->
                        new AccessDeniedException("Authenticated user account was not found"));
    }

    private org.springframework.security.core.Authentication currentAuthentication() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authenticated user identity is unavailable");
        }

        return authentication;
    }

    private ApprovalDtos.ApprovalResponse toResponse(ApprovalRequest approval) {
        return toResponse(approval, null);
    }

    private ApprovalDtos.ApprovalResponse toResponse(
            ApprovalRequest approval,
            String temporaryPassword) {

        return new ApprovalDtos.ApprovalResponse(
                approval.getId(),
                approval.getRequester().getFullName(),
                approval.getRequestType(),
                approval.getTarget(),
                approval.getCreatedAt(),
                approval.getStatus(),
                approval.getRequiredApprover(),
                approval.getRiskLevel(),
                approval.getJustification(),
                approval.getPreviousState(),
                approval.getRequestedState(),
                approval.getApprover() == null
                        ? null
                        : approval.getApprover().getFullName(),
                approval.getDecidedAt(),
                approval.getDecisionComment(),
                temporaryPassword,
                approval.getRemediationAction(),
                approval.getSourceFindingEventId(),
                approval.getExecutionStatus(),
                approval.getExecutionResult(),
                approval.getVerificationStatus(),
                approval.getVerificationResult(),
                approval.getVerifiedAt());
    }
}
