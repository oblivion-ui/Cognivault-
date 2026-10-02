package com.cognivault.api;

import com.cognivault.lineage.DataLineageDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ComplianceDtos {

    private ComplianceDtos() {
    }

    public record ComplianceReport(
            Instant generatedAt,
            Instant from,
            Instant to,
            String scope,
            String scopeEntityType,
            String scopeEntityId,
            AuditSummary auditActivity,
            IntegritySummary integrity,
            RiskSummary anomalySignals,
            ApprovalSummary approvals,
            LineageSummary lineage) {
    }

    public record AuditSummary(
            long totalRelevantEvents,
            Map<String, Long> actions,
            Map<String, Long> entityTypes,
            String status) {
    }

    public record IntegritySummary(
            String status,
            String message,
            String affectedEventId,
            long totalEventsChecked,
            boolean available) {
    }

    public record RiskSummary(
            long totalFindings,
            Map<String, Long> byRiskLevel,
            String status,
            String note) {
    }

    public record ApprovalSummary(
            long pendingCount,
            long approvedCount,
            long rejectedCount,
            Map<String, Long> byRequestType,
            String status) {
    }

    public record LineageSummary(
            long totalEntries,
            List<DataLineageDocument> entries,
            String status) {
    }
}
