package com.cognivault.api;

import com.cognivault.service.ComplianceReportService;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceController {

    private final ComplianceReportService complianceReportService;

    public ComplianceController(ComplianceReportService complianceReportService) {
        this.complianceReportService = complianceReportService;
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','COMPLIANCE_OFFICER','MANAGER','HR','EMPLOYEE')")
    public ComplianceDtos.ComplianceReport getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            Authentication authentication) {
        return complianceReportService.generateReport(from, to, entityType, entityId, authentication);
    }
}
