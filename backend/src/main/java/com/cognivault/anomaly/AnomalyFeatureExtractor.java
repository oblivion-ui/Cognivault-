package com.cognivault.anomaly;

import com.cognivault.audit.AuditEventDocument;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AnomalyFeatureExtractor {

    public static final int MINIMUM_BASELINE = 8;

    public ExtractionResult extract(
            AuditEventDocument current,
            List<AuditEventDocument> previousEvents) {

        if (previousEvents == null || previousEvents.size() < MINIMUM_BASELINE) {
            return ExtractionResult.insufficientBaseline(previousEvents == null ? 0 : previousEvents.size());
        }

        long actionFrequency = previousEvents.stream()
                .filter(event -> current.getAction().equals(event.getAction()))
                .count();

        long actorFrequency = previousEvents.stream()
                .filter(event -> current.getActor().equals(event.getActor()))
                .count();

        long actorActionFrequency = previousEvents.stream()
                .filter(event -> current.getActor().equals(event.getActor()))
                .filter(event -> current.getAction().equals(event.getAction()))
                .count();

        long secondsSincePreviousActorEvent = previousEvents.stream()
                .filter(event -> current.getActor().equals(event.getActor()))
                .findFirst()
                .map(event -> secondsBetween(event.getTimestamp(), current.getTimestamp()))
                .orElse(0L);

        long secondsSincePreviousGlobalEvent = previousEvents.stream()
                .findFirst()
                .map(event -> secondsBetween(event.getTimestamp(), current.getTimestamp()))
                .orElse(0L);

        Set<String> actorEntities = new HashSet<>();
        Set<String> actionEntities = new HashSet<>();

        previousEvents.forEach(event -> {
            if (current.getActor().equals(event.getActor()) && event.getEntityId() != null) {
                actorEntities.add(event.getEntityId());
            }

            if (current.getAction().equals(event.getAction()) && event.getEntityId() != null) {
                actionEntities.add(event.getEntityId());
            }
        });

        return ExtractionResult.ready(
        previousEvents.size(),
        actionFrequency,
        actorFrequency,
        actorActionFrequency,
        secondsSincePreviousActorEvent,
        secondsSincePreviousGlobalEvent,
        actorEntities.size(),
        actionEntities.size());
    }

    private long secondsBetween(java.time.Instant previous, java.time.Instant current) {
        if (previous == null || current == null) {
            return 0L;
        }

        return Math.max(0L, Duration.between(previous, current).getSeconds());
    }

    public record ExtractionResult(
            boolean ready,
            int baselineSize,
            long eventActionFrequency,
            long actorEventFrequency,
            long actorActionFrequency,
            long secondsSincePreviousActorEvent,
            long secondsSincePreviousGlobalEvent,
            long actorUniqueEntitiesRecently,
            long actionUniqueEntitiesRecently) {

        public static ExtractionResult insufficientBaseline(int baselineSize) {
            return new ExtractionResult(
                    false,
                    baselineSize,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0);
        }

        public static ExtractionResult ready(
                int baselineSize,
                long eventActionFrequency,
                long actorEventFrequency,
                long actorActionFrequency,
                long secondsSincePreviousActorEvent,
                long secondsSincePreviousGlobalEvent,
                long actorUniqueEntitiesRecently,
                long actionUniqueEntitiesRecently) {

            return new ExtractionResult(
                    true,
                    baselineSize,
                    eventActionFrequency,
                    actorEventFrequency,
                    actorActionFrequency,
                    secondsSincePreviousActorEvent,
                    secondsSincePreviousGlobalEvent,
                    actorUniqueEntitiesRecently,
                    actionUniqueEntitiesRecently);
        }
    }
}