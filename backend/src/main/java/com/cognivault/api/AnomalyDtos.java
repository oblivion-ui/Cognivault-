package com.cognivault.api;

import com.cognivault.domain.AnomalyExplanation;
import com.cognivault.domain.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AnomalyDtos {

    private AnomalyDtos() {
    }

    public record AnomalyResponse(
            UUID id,
            String eventId,
            String employee,
            BigDecimal score,
            RiskLevel riskLevel,
            Instant flaggedAt,
            String model,
            List<String> indicators,
            List<AnomalyExplanation> explanations) {
    }

    public record AnomalySummary(
            long total,
            long unknownRisk) {
    }

    public record BackfillResponse(
            int eventsDiscovered,
            int eventsProcessed) {
    }

    public record ReclassificationResponse(
            int findingsReclassified) {
    }
}
