package com.cognivault.api;

import com.cognivault.domain.ApprovalRequestType;
import com.cognivault.domain.ApprovalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class ApprovalDtos {

    private ApprovalDtos() {
    }

    public record CreateApprovalRequest(
            @NotNull ApprovalRequestType requestType,
            @NotBlank String target,
            String justification,
            Map<String, Object> previousState,
            @NotNull Map<String, Object> requestedState) {
    }

    public record DecisionRequest(
            @NotNull Decision decision,
            String comment) {
    }

    public enum Decision {
        APPROVE,
        REJECT
    }

    public record ApprovalResponse(
            UUID id,
            String requester,
            ApprovalRequestType requestType,
            String target,
            Instant createdAt,
            ApprovalStatus status,
            String requiredApprover,
            String riskLevel,
            String justification,
            Map<String, Object> previousState,
            Map<String, Object> requestedState,
            String approver,
            Instant decidedAt,
            String decisionComment,
                        String temporaryPassword,
                        String remediationAction,
                        String sourceFindingEventId,
                        String executionStatus,
                        String executionResult,
                        String verificationStatus,
                        String verificationResult,
                        Instant verifiedAt) {
    }
}


\n