package com.cognivault.api;

import com.cognivault.service.InvestigationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investigations")
public class InvestigationController {
    private final InvestigationService service;

    public InvestigationController(InvestigationService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER','COMPLIANCE_OFFICER','EMPLOYEE')")
    public InvestigationService.InvestigationResponse investigate(@RequestBody @Valid InvestigationRequest request, Authentication authentication) {
        return service.investigate(request.query(), authentication);
    }

    public record InvestigationRequest(@NotBlank String query) {}
}
