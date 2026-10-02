package com.cognivault.api;

import com.cognivault.domain.ApprovalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class RemediationDtos {

    private RemediationDtos() {
    }

    public enum Action {
        ACCOUNT_STATUS_CHANGE,
        CREDENTIAL_RESET,
        ROLLBACK,
        ESCALATION_REVIEW
    }

    public record CreateRequest(
            @NotNull UUID employeeId,
            @NotBlank String sourceFindingEventId,
            @NotNull Action action,
            @NotBlank String justification,
            String accountAction,
            Instant rollbackTimestamp,
            boolean forceChange) {
    }

    public record RemediationResponse(
            UUID remediationId,
            String sourceFindingEventId,
            UUID employeeId,
            Action recommendedAction,
            String justification,
            ApprovalStatus approvalStatus,
            String requester,
            String approver,
            Instant requestedAt,
            Instant decidedAt,
            String executionStatus,
            String executionResult,
            String verificationStatus,
            String verificationResult,
            Instant verifiedAt) {
    }
}