package com.cognivault.api;

import com.cognivault.domain.ApprovalStatus;
import com.cognivault.service.ApprovalService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalService service;

    public ApprovalController(ApprovalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
    public Page<ApprovalDtos.ApprovalResponse> list(
            @RequestParam(required = false) ApprovalStatus status,
            Pageable pageable) {

        return status == null
                ? service.list(pageable)
                : service.list(status, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','AUDITOR')")
    public ApprovalDtos.ApprovalResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ApprovalDtos.ApprovalResponse create(
            @RequestBody @Valid ApprovalDtos.CreateApprovalRequest request) {

        return service.create(request);
    }

    @PostMapping("/{id}/decisions")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','HR')")
    public ApprovalDtos.ApprovalResponse decide(
            @PathVariable UUID id,
            @RequestBody @Valid ApprovalDtos.DecisionRequest request) {

        return service.decide(id, request);
    }
}
