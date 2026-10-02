package com.cognivault.anomaly;

import com.cognivault.audit.AuditEventDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyFeatureExtractorTest {

    private final AnomalyFeatureExtractor extractor = new AnomalyFeatureExtractor();

    @Test
    void extractsExpectedBehavioralFeatures() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        AuditEventDocument current = event(
                "evt-current",
                "admin.local",
                "EMPLOYEE_UPDATED",
                "EMPLOYEE",
                "employee-9",
                base.plusSeconds(100));

        List<AuditEventDocument> previous = List.of(
               event("evt-8", "admin.local", "CREDENTIAL_RESET", "EMPLOYEE", "employee-7", base.plusSeconds(70)),
               event("evt-7", "manager", "EMPLOYEE_UPDATED", "EMPLOYEE", "employee-6", base.plusSeconds(60)),
               event("evt-6", "admin.local", "EMPLOYEE_UPDATED", "EMPLOYEE", "employee-1", base.plusSeconds(50)),
               event("evt-5", "admin.local", "ACCOUNT_STATUS_CHANGED", "EMPLOYEE", "employee-5",     base.plusSeconds(40)),
               event("evt-4", "manager", "EMPLOYEE_UPDATED", "EMPLOYEE", "employee-4", base.plusSeconds(30)),
               event("evt-3", "admin.local", "EMPLOYEE_UPDATED", "EMPLOYEE", "employee-3", base.plusSeconds(20)),
               event("evt-2", "admin.local", "EMPLOYEE_CREATED", "EMPLOYEE", "employee-2", base.plusSeconds(10)),
               event("evt-1", "admin.local", "EMPLOYEE_UPDATED", "EMPLOYEE", "employee-1", base)
        );

        var result = extractor.extract(current, previous);

        assertTrue(result.ready());
        assertEquals(8, result.baselineSize());
        assertEquals(5, result.eventActionFrequency());
        assertEquals(6, result.actorEventFrequency());
        assertEquals(3, result.actorActionFrequency());
        assertEquals(30, result.secondsSincePreviousActorEvent());
        assertEquals(30, result.secondsSincePreviousGlobalEvent());
        assertEquals(5, result.actorUniqueEntitiesRecently());
        assertEquals(4, result.actionUniqueEntitiesRecently());
    }

    @Test
    void refusesToExtractWhenBaselineIsTooSmall() {
        AuditEventDocument current = event(
                "evt-current",
                "admin.local",
                "EMPLOYEE_UPDATED",
                "EMPLOYEE",
                "employee-9",
                Instant.parse("2026-01-01T00:01:00Z"));

        var result = extractor.extract(current, List.of(
                event(
                        "evt-1",
                        "admin.local",
                        "EMPLOYEE_UPDATED",
                        "EMPLOYEE",
                        "employee-1",
                        Instant.parse("2026-01-01T00:00:00Z"))
        ));

        assertFalse(result.ready());
        assertEquals(1, result.baselineSize());
    }

    private AuditEventDocument event(
            String eventId,
            String actor,
            String action,
            String entityType,
            String entityId,
            Instant timestamp) {

        return new AuditEventDocument(
                eventId,
                actor,
                "user-1",
                action,
                entityType,
                entityId,
                timestamp,
                null,
                Map.of(),
                "GENESIS",
                "hash-" + eventId,
                1);
    }
}