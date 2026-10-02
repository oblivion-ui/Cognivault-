package com.cognivault.lineage;

import com.cognivault.audit.AuditEventDocument;
import com.cognivault.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataLineageServiceTest {

    @Mock
    private DataLineageRepository repository;

    @Mock
    private EmployeeService employeeService;

    @Test
    void recordsAuditEventAsLineageEntry() {
        DataLineageService service = new DataLineageService(repository, employeeService);

        AuditEventDocument event = new AuditEventDocument(
                "evt-123",
                "admin-user",
                "user-1",
                "EMPLOYEE_UPDATED",
                "EMPLOYEE",
                "d1f9d3cb-0c3d-4d1d-8d30-d29d7d8f6d56",
                Instant.parse("2026-01-02T03:04:05Z"),
                Map.of("department", "Engineering"),
                Map.of("department", "Finance"),
                "GENESIS",
                "hash-1",
                1L);

        service.recordEvent(event);

        ArgumentCaptor<DataLineageDocument> captor = ArgumentCaptor.forClass(DataLineageDocument.class);
        verify(repository).save(captor.capture());

        DataLineageDocument saved = captor.getValue();
        assertEquals("EMPLOYEE", saved.getSourceEntityType());
        assertEquals("EMPLOYEE_UPDATED", saved.getOperation());
        assertEquals("Finance", saved.getTargetSnapshot().get("department"));
        assertEquals("evt-123", saved.getEventId());
    }

    @Test
    void employeeQueryChecksVisibility() {
        DataLineageService service = new DataLineageService(repository, employeeService);

        UUID employeeId = UUID.randomUUID();
        doNothing().when(employeeService).assertVisible(any(UUID.class), any());

        DataLineageDocument document = new DataLineageDocument(
                "EMPLOYEE",
                employeeId.toString(),
                "EMPLOYEE_UPDATED",
                "EMPLOYEE",
                employeeId.toString(),
                "evt-456",
                "employee-user",
                "user-9",
                Instant.parse("2026-01-03T00:00:00Z"),
                Map.of("status", "ACTIVE"),
                Map.of("status", "SUSPENDED"));

        when(repository.findBySourceEntityTypeAndSourceEntityIdOrderByTimestampDesc("EMPLOYEE", employeeId.toString()))
                .thenReturn(List.of(document));

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                "employee-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        var result = service.forEntity("EMPLOYEE", employeeId.toString(), auth);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("evt-456", result.get(0).getEventId());
        verify(employeeService).assertVisible(employeeId, auth);
    }
}
