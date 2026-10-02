package com.cognivault.audit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cognivault.anomaly.AnomalyProcessor;
import com.cognivault.lineage.DataLineageService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditEventCreatedListenerTest {

    @Test
    void receivesCreatedAuditEvent() {
        var anomalyProcessor = mock(AnomalyProcessor.class);
        var lineageService = mock(DataLineageService.class);
        var listener = new AuditEventCreatedListener(anomalyProcessor, lineageService);

        var event = new AuditEventDocument(
                "evt-test",
                "tester",
                "user-1",
                "TEST_CREATED",
                "TEST",
                "1",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                Map.of("value", "one"),
                "GENESIS",
                "test-hash",
                1
        );

        assertDoesNotThrow(() ->
                listener.handle(new AuditEventCreated(event))
        );

        verify(anomalyProcessor).process(event);
        verify(lineageService).recordEvent(event);
    }
}
