package com.cognivault.api;

import com.cognivault.service.ApprovalService;
import com.cognivault.service.RollbackService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/rollback")
public class RollbackController {

    private final RollbackService rollbackService;
    private final ApprovalService approvalService;

    public RollbackController(RollbackService rollbackService, ApprovalService approvalService) {
        this.rollbackService = rollbackService;
        this.approvalService = approvalService;
    }

    @GetMapping("/preview")
    @PreAuthorize("hasRole('ADMIN')")
    public RollbackService.RollbackPreview preview(
            @RequestParam UUID employeeId,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant targetTimestamp) {
        return rollbackService.previewRollback(employeeId, targetTimestamp);
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('ADMIN')")
    public ApprovalDtos.ApprovalResponse requestRollback(@RequestBody @Valid RollbackRequest request) {
        ApprovalDtos.CreateApprovalRequest approvalRequest = new ApprovalDtos.CreateApprovalRequest(
                com.cognivault.domain.ApprovalRequestType.ROLLBACK,
                request.employeeId().toString(),
                request.justification(),
                request.previousState(),
                request.targetState());

        return approvalService.create(approvalRequest);
    }

    public record RollbackRequest(
            UUID employeeId,
            Instant targetTimestamp,
            String justification,
            Map<String, Object> previousState,
            Map<String, Object> targetState) {}
}
