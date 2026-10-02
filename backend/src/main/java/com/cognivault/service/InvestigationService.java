package com.cognivault.service;

import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.config.CogniVaultProperties;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.security.CurrentActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class InvestigationService {
    private static final int INDEX_PAGE_SIZE = 200;

    private final AuditEventRepository audits;
    private final EmployeeRepository employees;
    private final CogniVaultProperties properties;
    private final ObjectMapper mapper;
    private final EmployeeScopeService employeeScope;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();

    public InvestigationService(
            AuditEventRepository audits,
            EmployeeRepository employees,
            CogniVaultProperties properties,
            ObjectMapper mapper,
            EmployeeScopeService employeeScope) {
        this.audits = audits;
        this.employees = employees;
        this.properties = properties;
        this.mapper = mapper;
        this.employeeScope = employeeScope;
    }

    public InvestigationResponse investigate(String question, Authentication authentication) {
        var ml = properties.ml();
        if (ml.baseUrl() == null || ml.baseUrl().isBlank() || ml.apiKey() == null || ml.apiKey().isBlank()) {
            return unavailable("NOT_CONFIGURED", "RAG service authentication or URL is not configured.");
        }

        try {
            List<AuditEventDocument> allowedEvents = loadAuthorizedEvents(authentication);
            if (allowedEvents.isEmpty()) {
                return new InvestigationResponse(null, List.of(), "EMPTY", List.of("No audit evidence is authorized for this account."));
            }

            var documents = allowedEvents.stream().map(this::toDocument).toList();
            var ids = allowedEvents.stream().map(AuditEventDocument::getEventId).toList();
            JsonNode indexed = post(ml.baseUrl(), ml.apiKey(), "/v1/rag/index", Map.of("documents", documents));
            if (!indexed.path("indexed").isNumber()) {
                return unavailable("UNAVAILABLE", "Authorized evidence could not be indexed for retrieval.");
            }

            JsonNode result = post(ml.baseUrl(), ml.apiKey(), "/v1/rag/query", Map.of("query", question, "allowed_document_ids", ids, "top_k", 5));
            Map<String, AuditEventDocument> authorizedById = allowedEvents.stream()
                    .collect(java.util.stream.Collectors.toMap(AuditEventDocument::getEventId, event -> event));
            List<Evidence> evidence = new ArrayList<>();
            JsonNode evidenceNode = result.path("evidence");
            if (evidenceNode.isArray()) {
                for (JsonNode item : evidenceNode) {
                    String eventId = item.path("id").asText();
                    AuditEventDocument source = authorizedById.get(eventId);
                    if (source != null) {
                        evidence.add(new Evidence(eventId, source.getAction() + " · " + source.getEntityType(), item.path("text").asText(), eventId, source.getTimestamp().toString()));
                    }
                }
            }
            List<String> limitations = new ArrayList<>();
            JsonNode limitationsNode = result.path("limitations");
            if (limitationsNode.isArray()) {
                limitationsNode.forEach(item -> limitations.add(item.asText()));
            }
            return new InvestigationResponse(result.path("answer").isNull() ? null : result.path("answer").asText(), evidence,
                    result.path("status").asText("ERROR"), limitations);
        } catch (Exception exception) {
            System.err.println("RAG investigation failed: " + exception.getClass().getName() + ": " + exception.getMessage());
            return unavailable("UNAVAILABLE", "Authorized retrieval is currently unavailable.");
        }
    }

    private List<AuditEventDocument> loadAuthorizedEvents(Authentication authentication) {
        boolean privileged = employeeScope.hasRole(authentication, com.cognivault.domain.Role.ADMIN)
                || employeeScope.hasRole(authentication, com.cognivault.domain.Role.AUDITOR)
                || employeeScope.hasRole(authentication, com.cognivault.domain.Role.COMPLIANCE_OFFICER);
        boolean manager = employeeScope.hasRole(authentication, com.cognivault.domain.Role.MANAGER);
        UUID employeeId = null;
        List<String> managerEmployeeIds = List.of();
        if (manager) {
            managerEmployeeIds = employeeScope.managedEmployeeIds(authentication).stream().sorted().toList();
            if (managerEmployeeIds.isEmpty()) {
                return List.of();
            }
        } else if (!privileged) {
            if (!employeeScope.hasRole(authentication, com.cognivault.domain.Role.EMPLOYEE)) {
                throw new AccessDeniedException("No investigation evidence scope is available for this role.");
            }
            Object principal = authentication.getPrincipal();
            if (!(principal instanceof CurrentActor.AuthenticatedPrincipal authenticatedPrincipal)) {
                throw new AccessDeniedException("Employee identity is unavailable.");
            }
            employeeId = employees.findEmployeeIdByUserId(authenticatedPrincipal.userId())
                    .orElseThrow(() -> new AccessDeniedException("No employee identity is associated with this account."));
        }

        List<AuditEventDocument> results = new ArrayList<>();
        for (int pageNumber = 0; ; pageNumber++) {
            Pageable pageable = PageRequest.of(pageNumber, INDEX_PAGE_SIZE, Sort.by(Sort.Direction.ASC, "chainPosition"));
            Page<AuditEventDocument> page;
            if (privileged) {
                page = audits.findAll(pageable);
            } else if (manager) {
                page = audits.findByEntityTypeAndEntityIdInOrderByChainPositionAsc(
                        "EMPLOYEE", managerEmployeeIds, pageable);
            } else {
                page = audits.findByEntityTypeAndEntityId("EMPLOYEE", employeeId.toString(), pageable);
            }
            results.addAll(page.getContent());
            if (!page.hasNext()) {
                return results;
            }
        }
    }

    private Map<String, Object> toDocument(AuditEventDocument event) {
        String text = "eventId=" + event.getEventId()
                + " action=" + event.getAction()
                + " actor=" + event.getActor()
                + " entityType=" + event.getEntityType()
                + " entityId=" + event.getEntityId()
                + " timestamp=" + event.getTimestamp()
                + " before=" + json(event.getBeforeSnapshot())
                + " after=" + json(event.getAfterSnapshot());
        return Map.of("id", event.getEventId(), "text", text, "metadata", Map.of("entityType", event.getEntityType(), "entityId", safe(event.getEntityId())));
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private JsonNode post(String baseUrl, String apiKey, String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(240))
                .header("Content-Type", "application/json")
                .header("X-CogniVault-Service-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("ML service returned HTTP " + response.statusCode() + ": " + response.body());
        }
        return mapper.readTree(response.body());
    }

    private InvestigationResponse unavailable(String status, String reason) {
        return new InvestigationResponse(null, List.of(), status, List.of(reason));
    }

    public record Evidence(String id, String title, String excerpt, String eventId, String timestamp) {}
    public record InvestigationResponse(String answer, List<Evidence> evidence, String status, List<String> limitations) {}
}
