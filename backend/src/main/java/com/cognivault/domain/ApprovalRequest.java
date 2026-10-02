package com.cognivault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private UserAccount requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private UserAccount approver;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 50)
    private ApprovalRequestType requestType;

    @Column(nullable = false, length = 255)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(name = "required_approver", length = 80)
    private String requiredApprover;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(length = 2000)
    private String justification;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "previous_state", columnDefinition = "json")
    private Map<String, Object> previousState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_state", nullable = false, columnDefinition = "json")
    private Map<String, Object> requestedState;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_comment", length = 2000)
    private String decisionComment;

    @Column(name = "remediation_action", length = 40)
    private String remediationAction;

    @Column(name = "source_finding_event_id", length = 120)
    private String sourceFindingEventId;

    @Column(name = "execution_status", length = 30)
    private String executionStatus;

    @Column(name = "execution_result", length = 2000)
    private String executionResult;

    @Column(name = "verification_status", length = 30)
    private String verificationStatus;

    @Column(name = "verification_result", length = 2000)
    private String verificationResult;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(
            UserAccount requester,
            ApprovalRequestType requestType,
            String target,
            String requiredApprover,
            String riskLevel,
            String justification,
            Map<String, Object> previousState,
            Map<String, Object> requestedState) {
        this.requester = requester;
        this.requestType = requestType;
        this.target = target;
        this.requiredApprover = requiredApprover;
        this.riskLevel = riskLevel;
        this.justification = justification;
        this.previousState = previousState;
        this.requestedState = requestedState;
        this.status = ApprovalStatus.PENDING;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ApprovalStatus.PENDING;
        }
    }

    public UUID getId() {
        return id;
    }

    public UserAccount getRequester() {
        return requester;
    }

    public UserAccount getApprover() {
        return approver;
    }

    public ApprovalRequestType getRequestType() {
        return requestType;
    }

    public String getTarget() {
        return target;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public String getRequiredApprover() {
        return requiredApprover;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public String getJustification() {
        return justification;
    }

    public Map<String, Object> getPreviousState() {
        return previousState;
    }

    public Map<String, Object> getRequestedState() {
        return requestedState;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionComment() {
        return decisionComment;
    }

    public String getRemediationAction() { return remediationAction; }
    public String getSourceFindingEventId() { return sourceFindingEventId; }
    public String getExecutionStatus() { return executionStatus; }
    public String getExecutionResult() { return executionResult; }
    public String getVerificationStatus() { return verificationStatus; }
    public String getVerificationResult() { return verificationResult; }
    public Instant getVerifiedAt() { return verifiedAt; }

    public void configureRemediation(String action, String sourceFindingEventId) {
        this.remediationAction = action;
        this.sourceFindingEventId = sourceFindingEventId;
        this.executionStatus = "PENDING_APPROVAL";
        this.verificationStatus = "PENDING";
    }

    public void recordExecution(String status, String result) {
        this.executionStatus = status;
        this.executionResult = result;
    }

    public void recordVerification(String status, String result, Instant verifiedAt) {
        this.verificationStatus = status;
        this.verificationResult = result;
        this.verifiedAt = verifiedAt;
    }

    public void decide(UserAccount approver, ApprovalStatus status, String comment) {
        this.approver = approver;
        this.status = status;
        this.decisionComment = comment;
        this.decidedAt = Instant.now();
    }
}
