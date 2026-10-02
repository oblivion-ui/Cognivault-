package com.cognivault.audit;

import com.cognivault.anomaly.AnomalyProcessor;
import com.cognivault.lineage.DataLineageService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AuditEventCreatedListener {

    private final AnomalyProcessor anomalyProcessor;
    private final DataLineageService lineageService;

    public AuditEventCreatedListener(AnomalyProcessor anomalyProcessor, DataLineageService lineageService) {
        this.anomalyProcessor = anomalyProcessor;
        this.lineageService = lineageService;
    }

    @Async
    @EventListener
    public void handle(AuditEventCreated event) {
        anomalyProcessor.process(event.event());
        lineageService.recordEvent(event.event());
    }
}
