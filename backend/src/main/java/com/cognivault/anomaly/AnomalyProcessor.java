package com.cognivault.anomaly;

import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.config.CogniVaultProperties;
import com.cognivault.domain.AnomalyExplanation;
import com.cognivault.domain.AnomalyFinding;
import com.cognivault.domain.RiskLevel;
import com.cognivault.repository.AnomalyFindingRepository;
import com.cognivault.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AnomalyProcessor {

    private static final int BASELINE_SIZE = 32;

    private final AuditEventRepository audits;
    private final AnomalyFindingRepository findings;
    private final EmployeeRepository employees;
    private final CogniVaultProperties properties;
    private final ObjectMapper mapper;
    private final AnomalyFeatureExtractor extractor = new AnomalyFeatureExtractor();

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public AnomalyProcessor(
            AuditEventRepository audits,
            AnomalyFindingRepository findings,
            EmployeeRepository employees,
            CogniVaultProperties properties,
            ObjectMapper mapper) {
        this.audits = audits;
        this.findings = findings;
        this.employees = employees;
        this.properties = properties;
        this.mapper = mapper;
    }

    public int reclassifyUnknownFindings() {
        var unknownFindings = findings.findAll().stream()
                .filter(finding -> finding.getRiskLevel() == RiskLevel.UNKNOWN)
                .toList();

        for (var finding : unknownFindings) {
            finding.updateRiskLevel(
                    classifyRisk(finding.getScore().doubleValue()));
        }

        findings.saveAll(unknownFindings);
        return unknownFindings.size();
    }

    public void process(AuditEventDocument event) {
        if (event == null || event.getEventId() == null) {
            return;
        }

        try {
            if (findings.findByEventId(event.getEventId()).isPresent()) {
                return;
            }

            List<AuditEventDocument> previousEvents =
                    audits.findByChainPositionLessThanOrderByChainPositionDesc(
                            event.getChainPosition(),
                            org.springframework.data.domain.PageRequest.of(
                                    0,
                                    BASELINE_SIZE));

            var extraction = extractor.extract(event, previousEvents);

            if (!extraction.ready()) {
                return;
            }

            List<Map<String, Double>> baseline = new ArrayList<>();

            for (AuditEventDocument historicalEvent : previousEvents) {
                List<AuditEventDocument> historicalPrevious =
                        audits.findByChainPositionLessThanOrderByChainPositionDesc(
                                historicalEvent.getChainPosition(),
                                org.springframework.data.domain.PageRequest.of(
                                        0,
                                        BASELINE_SIZE));

                var historicalExtraction =
                        extractor.extract(historicalEvent, historicalPrevious);

                if (historicalExtraction.ready()) {
                    baseline.add(
                            toFeatureVector(
                                    historicalEvent,
                                    historicalExtraction));
                }
            }

            if (baseline.size() < AnomalyFeatureExtractor.MINIMUM_BASELINE) {
                return;
            }

            var instance = toFeatureVector(event, extraction);

            JsonNode scoreResponse = post(
                    "/v1/anomaly/score",
                    Map.of(
                            "baseline", baseline.stream().map(values -> Map.of("values", values)).toList(),
                            "instance", Map.of("values", instance),
                            "contamination", 0.1));

            boolean flagged = scoreResponse.path("flagged").asBoolean(false);

            if (!flagged) {
                return;
            }

            double score = scoreResponse.path("score").asDouble();

            JsonNode explanationResponse = post(
                    "/v1/anomaly/explain",
                    Map.of(
                            "baseline", baseline.stream().map(values -> Map.of("values", values)).toList(),
                            "instance", Map.of("values", instance),
                            "contamination", 0.1));

            List<AnomalyExplanation> explanations =
                    parseExplanations(explanationResponse.path("explanations"));

            List<String> indicators = explanations.stream()
                    .sorted(java.util.Comparator.comparingDouble(
                            (AnomalyExplanation explanation) -> Math.abs(explanation.contribution()))
                            .reversed())
                    .limit(5)
                    .map(AnomalyExplanation::feature)
                    .toList();

            UUID employeeId = resolveEmployeeId(event);

            String model = scoreResponse.path("model").asText("Isolation Forest");

            RiskLevel riskLevel = classifyRisk(score);

            AnomalyFinding finding = new AnomalyFinding(
                    event.getEventId(),
                    employeeId,
                    BigDecimal.valueOf(score),
                    riskLevel,
                    event.getTimestamp(),
                    model,
                    indicators,
                    explanations);

            findings.save(finding);

        } catch (Exception exception) {
            System.err.println(
                    "Anomaly processing failed for event "
                            + event.getEventId()
                            + ": "
                            + exception.getClass().getName()
                            + ": "
                            + exception.getMessage());
        }
    }

    private RiskLevel classifyRisk(double score) {
        var risk = properties.anomalyRisk();

        if (score < risk.mediumThreshold()) {
            return RiskLevel.LOW;
        }
        if (score < risk.highThreshold()) {
            return RiskLevel.MEDIUM;
        }
        if (score < risk.criticalThreshold()) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.CRITICAL;
    }

    private Map<String, Double> toFeatureVector(AuditEventDocument event) {
        Map<String, Double> values = new HashMap<>();

        values.put("event_action_frequency", 0.0);
        values.put("actor_event_frequency", 0.0);
        values.put("actor_action_frequency", 0.0);
        values.put("seconds_since_previous_actor_event", 0.0);
        values.put("seconds_since_previous_global_event", 0.0);
        values.put("actor_unique_entities_recently", 0.0);
        values.put("action_unique_entities_recently", 0.0);

        return values;
    }

    private Map<String, Double> toFeatureVector(
            AuditEventDocument event,
            AnomalyFeatureExtractor.ExtractionResult extraction) {

        Map<String, Double> values = new HashMap<>();

        values.put(
                "event_action_frequency",
                (double) extraction.eventActionFrequency());

        values.put(
                "actor_event_frequency",
                (double) extraction.actorEventFrequency());

        values.put(
                "actor_action_frequency",
                (double) extraction.actorActionFrequency());

        values.put(
                "seconds_since_previous_actor_event",
                (double) extraction.secondsSincePreviousActorEvent());

        values.put(
                "seconds_since_previous_global_event",
                (double) extraction.secondsSincePreviousGlobalEvent());

        values.put(
                "actor_unique_entities_recently",
                (double) extraction.actorUniqueEntitiesRecently());

        values.put(
                "action_unique_entities_recently",
                (double) extraction.actionUniqueEntitiesRecently());

        return values;
    }

    private List<AnomalyExplanation> parseExplanations(JsonNode node) {
        List<AnomalyExplanation> explanations = new ArrayList<>();

        if (!node.isArray()) {
            return explanations;
        }

        for (JsonNode item : node) {
            explanations.add(new AnomalyExplanation(
                    item.path("feature").asText(),
                    item.path("contribution").asDouble(),
                    item.path("direction").asText("NEUTRAL"),
                    item.path("explanation").asText()));
        }

        return explanations;
    }

    private UUID resolveEmployeeId(AuditEventDocument event) {
        if (!"EMPLOYEE".equals(event.getEntityType())
                || event.getEntityId() == null
                || event.getEntityId().isBlank()) {
            return null;
        }

        try {
            UUID employeeId = UUID.fromString(event.getEntityId());

            return employees.findById(employeeId)
                    .map(employee -> employee.getId())
                    .orElse(null);

        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private JsonNode post(String path, Object body) throws Exception {
        var ml = properties.ml();

        if (ml.baseUrl() == null
                || ml.baseUrl().isBlank()
                || ml.apiKey() == null
                || ml.apiKey().isBlank()) {
            throw new IllegalStateException("ML service is not configured.");
        }

        var request = HttpRequest.newBuilder(
                        URI.create(ml.baseUrl() + path))
                .timeout(Duration.ofSeconds(240))
                .header("Content-Type", "application/json")
                .header("X-CogniVault-Service-Key", ml.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body)))
                .build();

        var response = http.send(
                request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "ML service returned HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body());
        }

        return mapper.readTree(response.body());
    }
}







\n