package com.cognivault.api;

import com.cognivault.api.EmployeeDtos.*;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.domain.ApprovalRequestType;
import com.cognivault.service.ApprovalService;
import com.cognivault.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;
    private final AuditEventRepository audit;
    private final ApprovalService approvals;
    private final ObjectMapper mapper;

    public EmployeeController(
            EmployeeService service,
            AuditEventRepository audit,
            ApprovalService approvals,
            ObjectMapper mapper) {
        this.service = service;
        this.audit = audit;
        this.approvals = approvals;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR','HR')")
    public Page<EmployeeResponse> list(Pageable pageable, Authentication authentication) {
        return service.list(pageable, authentication).map(EmployeeResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR','HR','EMPLOYEE')")
    public EmployeeResponse get(
            @PathVariable UUID id,
            Authentication authentication) {
        return service.getVisibleResponse(id, authentication);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ApprovalDtos.ApprovalResponse create(
            @RequestBody @Valid CreateEmployeeRequest request) {

        return approvals.create(new ApprovalDtos.CreateApprovalRequest(
                ApprovalRequestType.EMPLOYEE_CREATE,
                request.personal().employeeId(),
                "Employee creation requested",
                null,
                mapper.convertValue(request, Map.class)));
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR')")
    public ApprovalDtos.ApprovalResponse update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateEmployeeRequest request,
            Authentication authentication) {

        EmployeeResponse current = service.getVisibleResponse(id, authentication);

        return approvals.create(new ApprovalDtos.CreateApprovalRequest(
                ApprovalRequestType.EMPLOYEE_UPDATE,
                id.toString(),
                "Employee update requested",
                mapper.convertValue(current, Map.class),
                mapper.convertValue(request, Map.class)));
    }

    @PostMapping("/{id}/account-actions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR')")
    public ApprovalDtos.ApprovalResponse transition(
            @PathVariable UUID id,
            @RequestBody @Valid AccountActionRequest request,
            Authentication authentication) {

        EmployeeResponse current = service.getVisibleResponse(id, authentication);

        return approvals.create(new ApprovalDtos.CreateApprovalRequest(
                ApprovalRequestType.ACCOUNT_STATUS_CHANGE,
                id.toString(),
                request.reason(),
                mapper.convertValue(current, Map.class),
                mapper.convertValue(request, Map.class)));
    }

    @GetMapping("/{id}/audit-events")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER','HR','EMPLOYEE')")
    public Page<AuditDtos.AuditResponse> audit(
            @PathVariable UUID id,
            Authentication authentication,
            Pageable pageable) {

        service.assertVisible(id, authentication);

        return audit.findByEntityTypeAndEntityId(
                        "EMPLOYEE",
                        id.toString(),
                        pageable)
                .map(AuditDtos.AuditResponse::from);
    }

    @PostMapping("/{id}/credentials/reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApprovalDtos.ApprovalResponse reset(
            @PathVariable UUID id,
            @RequestBody @Valid CredentialResetRequest request,
            Authentication authentication) {

        EmployeeResponse current = service.getVisibleResponse(id, authentication);

        return approvals.create(new ApprovalDtos.CreateApprovalRequest(
                ApprovalRequestType.CREDENTIAL_RESET,
                id.toString(),
                "Credential reset requested",
                mapper.convertValue(current, Map.class),
                mapper.convertValue(request, Map.class)));
    }
}
