package com.cognivault.service;

import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.config.CogniVaultProperties;
import com.cognivault.repository.EmployeeRepository;
import com.cognivault.security.AuthenticatedPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestigationServiceScopeTest {

    @Mock private AuditEventRepository audits;
    @Mock private EmployeeRepository employees;
    @Mock private EmployeeScopeService employeeScope;

    @Test
    void managerRagDocumentsAndAllowedIdsContainOnlyManagedEmployeeEvidence() throws Exception {
        UUID managedEmployeeId = UUID.randomUUID();
        UUID managerUserId = UUID.randomUUID();
        String eventId = "evt-team-only";
        Authentication manager = authentication(managerUserId, "manager", "MANAGER");
        when(employeeScope.hasRole(manager, com.cognivault.domain.Role.ADMIN)).thenReturn(false);
        when(employeeScope.hasRole(manager, com.cognivault.domain.Role.AUDITOR)).thenReturn(false);
        when(employeeScope.hasRole(manager, com.cognivault.domain.Role.COMPLIANCE_OFFICER)).thenReturn(false);
        when(employeeScope.hasRole(manager, com.cognivault.domain.Role.MANAGER)).thenReturn(true);
        when(employeeScope.managedEmployeeIds(manager)).thenReturn(java.util.Set.of(managedEmployeeId.toString()));

        var event = new AuditEventDocument(
                eventId, "employee-manager", managerUserId.toString(), "EMPLOYEE_UPDATED", "EMPLOYEE",
                managedEmployeeId.toString(), Instant.parse("2026-10-01T10:00:00Z"), Map.of(), Map.of(),
                "previous", "hash", 1L);
        when(audits.findByEntityTypeAndEntityIdInOrderByChainPositionAsc(
                eq("EMPLOYEE"), eq(List.of(managedEmployeeId.toString())), eq(PageRequest.of(0, 200,
                        Sort.by(Sort.Direction.ASC, "chainPosition")))))
                .thenReturn(new PageImpl<>(List.of(event)));

        ObjectMapper mapper = new ObjectMapper();
        var requests = new CopyOnWriteArrayList<JsonNode>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rag/index", exchange -> respond(exchange, requests, mapper,
                "{\"indexed\":1}"));
        server.createContext("/v1/rag/query", exchange -> respond(exchange, requests, mapper,
                "{\"answer\":\"Scoped answer\",\"status\":\"READY\",\"evidence\":[{\"id\":\"" + eventId + "\",\"text\":\"Scoped evidence\"}]}"));
        server.start();
        try {
            var properties = new CogniVaultProperties(null, null,
                    new CogniVaultProperties.Ml("http://127.0.0.1:" + server.getAddress().getPort(), "service-key"),
                    null);
            var service = new InvestigationService(audits, employees, properties, mapper, employeeScope);

            var response = service.investigate("team evidence", manager);

            assertEquals("READY", response.status());
            assertEquals(List.of(eventId), response.evidence().stream().map(InvestigationService.Evidence::eventId).toList());
            assertEquals(2, requests.size());
            assertEquals(List.of(eventId), mapper.convertValue(
                    requests.get(1).path("allowed_document_ids"), mapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class)));
            assertEquals(List.of(eventId), mapper.convertValue(
                    requests.get(0).path("documents").findValuesAsText("id"), mapper.getTypeFactory()
                            .constructCollectionType(List.class, String.class)));
            verify(audits).findByEntityTypeAndEntityIdInOrderByChainPositionAsc(
                    eq("EMPLOYEE"), eq(List.of(managedEmployeeId.toString())), eq(PageRequest.of(0, 200,
                            Sort.by(Sort.Direction.ASC, "chainPosition"))));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void employeeInvestigationContinuesToUseOwnEmployeeAuditQuery() throws Exception {
        UUID employeeId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Authentication employee = authentication(userId, "employee", "EMPLOYEE");
        when(employeeScope.hasRole(employee, com.cognivault.domain.Role.ADMIN)).thenReturn(false);
        when(employeeScope.hasRole(employee, com.cognivault.domain.Role.AUDITOR)).thenReturn(false);
        when(employeeScope.hasRole(employee, com.cognivault.domain.Role.COMPLIANCE_OFFICER)).thenReturn(false);
        when(employeeScope.hasRole(employee, com.cognivault.domain.Role.MANAGER)).thenReturn(false);
        when(employeeScope.hasRole(employee, com.cognivault.domain.Role.EMPLOYEE)).thenReturn(true);
        when(employees.findEmployeeIdByUserId(userId)).thenReturn(java.util.Optional.of(employeeId));

        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/rag/index", exchange -> respond(exchange, new CopyOnWriteArrayList<>(), mapper,
                "{\"indexed\":0}"));
        try {
            var properties = new CogniVaultProperties(null, null,
                    new CogniVaultProperties.Ml("http://127.0.0.1:" + server.getAddress().getPort(), "service-key"),
                    null);
            var service = new InvestigationService(audits, employees, properties, mapper, employeeScope);
            when(audits.findByEntityTypeAndEntityId("EMPLOYEE", employeeId.toString(),
                    PageRequest.of(0, 200, Sort.by(Sort.Direction.ASC, "chainPosition"))))
                    .thenReturn(new PageImpl<>(List.of()));

            var response = service.investigate("my activity", employee);

            assertEquals("EMPTY", response.status());
            verify(audits).findByEntityTypeAndEntityId("EMPLOYEE", employeeId.toString(),
                    PageRequest.of(0, 200, Sort.by(Sort.Direction.ASC, "chainPosition")));
        } finally {
            server.stop(0);
        }
    }

    private void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            CopyOnWriteArrayList<JsonNode> requests,
            ObjectMapper mapper,
            String response) throws java.io.IOException {
        requests.add(mapper.readTree(exchange.getRequestBody()));
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var body = exchange.getResponseBody()) {
            body.write(bytes);
        }
    }

    private Authentication authentication(UUID userId, String username, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedPrincipal(userId, username), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}