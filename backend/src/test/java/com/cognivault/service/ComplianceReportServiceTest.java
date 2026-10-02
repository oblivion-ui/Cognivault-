package com.cognivault.service;

import com.cognivault.api.ComplianceDtos;
import com.cognivault.audit.AuditEventDocument;
import com.cognivault.audit.AuditEventRepository;
import com.cognivault.audit.AuditLedgerService;
import com.cognivault.domain.*;
import com.cognivault.lineage.DataLineageDocument;
import com.cognivault.lineage.DataLineageRepository;
import com.cognivault.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComplianceReportServiceTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @Mock
    private AnomalyFindingRepository anomalyFindingRepository;

    @Mock
    private ApprovalRequestRepository approvalRequestRepository;

    @Mock
    private DataLineageRepository dataLineageRepository;

    @Mock
    private AuditLedgerService auditLedgerService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmployeeService employeeService;

    @Test
    void generatesReportFromStoredDataForAuthorizedRequest() {
        UUID employeeId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var service = new ComplianceReportService(
                auditEventRepository,
                anomalyFindingRepository,
                approvalRequestRepository,
                dataLineageRepository,
                auditLedgerService,
                employeeRepository,
                userAccountRepository,
                employeeService);

        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-01-31T23:59:59Z");

        when(auditEventRepository.findAll()).thenReturn(List.of(
                new AuditEventDocument(
                        "evt-1",
                        "admin-user",
                        "user-1",
                        "EMPLOYEE_UPDATED",
                        "EMPLOYEE",
                        employeeId.toString(),
                        Instant.parse("2026-01-02T10:00:00Z"),
                        Map.of("status", "ACTIVE"),
                        Map.of("status", "SUSPENDED"),
                        "GENESIS",
                        "hash-1",
                        1L),
                new AuditEventDocument(
                        "evt-2",
                        "other-user",
                        "user-2",
                        "ROLE_CHANGED",
                        "EMPLOYEE",
                        employeeId.toString(),
                        Instant.parse("2025-12-15T10:00:00Z"),
                        Map.of("role", "EMPLOYEE"),
                        Map.of("role", "MANAGER"),
                        "hash-a",
                        "hash-b",
                        2L)
        ));

        when(anomalyFindingRepository.findAll()).thenReturn(List.of(
                new AnomalyFinding(
                        "evt-1",
                        employeeId,
                        new BigDecimal("0.91"),
                        RiskLevel.HIGH,
                        Instant.parse("2026-01-02T10:05:00Z"),
                        "model-x",
                        List.of("late_login"),
                        List.of())
        ));

        var approved = new ApprovalRequest(
                new UserAccount("requester-a", "r@demo.com", "Requester A", "hash", Role.ADMIN, AccountStatus.ACTIVE, false),
                ApprovalRequestType.EMPLOYEE_UPDATE,
                employeeId.toString(),
                "manager-1",
                "MEDIUM",
                "Needs review",
                Map.of("status", "ACTIVE"),
                Map.of("status", "SUSPENDED"));
        ReflectionTestUtils.setField(approved, "createdAt", Instant.parse("2026-01-05T10:00:00Z"));
        approved.decide(new UserAccount("approver", "a@demo.com", "Approver", "hash", Role.ADMIN, AccountStatus.ACTIVE, false), ApprovalStatus.APPROVED, "accepted");

        var rejected = new ApprovalRequest(
                new UserAccount("requester-b", "b@demo.com", "Requester B", "hash", Role.MANAGER, AccountStatus.ACTIVE, false),
                ApprovalRequestType.ACCOUNT_STATUS_CHANGE,
                employeeId.toString(),
                "admin-1",
                "LOW",
                "Approved",
                Map.of("status", "ACTIVE"),
                Map.of("status", "DEACTIVATED"));
        ReflectionTestUtils.setField(rejected, "createdAt", Instant.parse("2026-01-12T10:00:00Z"));
        rejected.decide(new UserAccount("approver2", "b@demo.com", "Approver2", "hash", Role.ADMIN, AccountStatus.ACTIVE, false), ApprovalStatus.REJECTED, "rejected");
        when(approvalRequestRepository.findAll()).thenReturn(List.of(approved, rejected));

        when(dataLineageRepository.findAll()).thenReturn(List.of(
                new DataLineageDocument(
                        "EMPLOYEE",
                        employeeId.toString(),
                        "EMPLOYEE_UPDATED",
                        "EMPLOYEE",
                        employeeId.toString(),
                        "evt-1",
                        "admin-user",
                        "user-1",
                        Instant.parse("2026-01-02T10:00:00Z"),
                        Map.of("status", "ACTIVE"),
                        Map.of("status", "SUSPENDED"))
        ));
        when(auditLedgerService.verify()).thenReturn(new AuditLedgerService.IntegrityResult(
                "VERIFIED",
                Instant.parse("2026-01-03T00:00:00Z"),
                1,
                null,
                "GENESIS",
                "hash-1",
                1,
                "All look good"));

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                "admin-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        ComplianceDtos.ComplianceReport report = service.generateReport(from, to, null, null, auth);

        assertEquals("GLOBAL", report.scope());
        assertEquals(1, report.auditActivity().totalRelevantEvents());
        assertEquals(1L, report.auditActivity().actions().get("EMPLOYEE_UPDATED"));
        assertEquals("VERIFIED", report.integrity().status());
        assertEquals(1, report.anomalySignals().totalFindings());
                assertEquals(0L, report.approvals().pendingCount());
                assertEquals(1L, report.approvals().approvedCount());
                assertEquals(1L, report.approvals().rejectedCount());
        assertEquals(1, report.lineage().totalEntries());
    }

    @Test
    void employeeScopeCanBeEnforcedForScopedUsers() {
        UUID employeeId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var service = new ComplianceReportService(
                auditEventRepository,
                anomalyFindingRepository,
                approvalRequestRepository,
                dataLineageRepository,
                auditLedgerService,
                employeeRepository,
                userAccountRepository,
                employeeService);

        var user = new UserAccount("worker-a", "worker@demo.com", "Worker A", "hash", Role.EMPLOYEE, AccountStatus.ACTIVE, false);
        lenient().when(userAccountRepository.findByUsernameIgnoreCase("worker-a")).thenReturn(Optional.of(user));
        lenient().when(employeeRepository.findEmployeeIdByUserId(user.getId())).thenReturn(Optional.of(employeeId));
        doNothing().when(employeeService).assertVisible(eq(employeeId), any());
        lenient().when(auditEventRepository.findAll()).thenReturn(List.of());
        lenient().when(anomalyFindingRepository.findAll()).thenReturn(List.of());
        lenient().when(approvalRequestRepository.findAll()).thenReturn(List.of());
        lenient().when(dataLineageRepository.findAll()).thenReturn(List.of());
        when(auditLedgerService.verify()).thenReturn(new AuditLedgerService.IntegrityResult("VERIFIED", Instant.now(), 0, null, "GENESIS", "GENESIS", 0, "empty"));

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                "worker-a",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        ComplianceDtos.ComplianceReport report = service.generateReport(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"),
                "EMPLOYEE",
                employeeId.toString(),
                auth);

        assertEquals("EMPLOYEE", report.scope());
        verify(employeeService).assertVisible(employeeId, auth);
    }

    @Test
    void emptyDataIsHandledWithoutInventingValues() {
        UUID employeeId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        var service = new ComplianceReportService(
                auditEventRepository,
                anomalyFindingRepository,
                approvalRequestRepository,
                dataLineageRepository,
                auditLedgerService,
                employeeRepository,
                userAccountRepository,
                employeeService);

        when(auditEventRepository.findAll()).thenReturn(List.of());
        when(anomalyFindingRepository.findAll()).thenReturn(List.of());
        when(approvalRequestRepository.findAll()).thenReturn(List.of());
        when(dataLineageRepository.findAll()).thenReturn(List.of());
        when(auditLedgerService.verify()).thenReturn(new AuditLedgerService.IntegrityResult("VERIFIED", Instant.now(), 0, null, "GENESIS", "GENESIS", 0, "No records"));

        var auth = UsernamePasswordAuthenticationToken.authenticated(
                "admin-user",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        ComplianceDtos.ComplianceReport report = service.generateReport(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"),
                null,
                null,
                auth);

        assertEquals(0, report.auditActivity().totalRelevantEvents());
        assertTrue(report.auditActivity().actions().isEmpty());
        assertEquals(0, report.anomalySignals().totalFindings());
        assertEquals(0, report.approvals().pendingCount());
        assertEquals("VERIFIED", report.integrity().status());
        assertEquals(0, report.lineage().totalEntries());
    }

}
