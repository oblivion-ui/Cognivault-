package com.cognivault.api;

import com.cognivault.service.RemediationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remediations")
public class RemediationController {

    private final RemediationService remediationService;

    public RemediationController(RemediationService remediationService) {
        this.remediationService = remediationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','HR','MANAGER')")
    public RemediationDtos.RemediationResponse create(
            @RequestBody @Valid RemediationDtos.CreateRequest request,
            Authentication authentication) {
        return remediationService.create(request, authentication);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','COMPLIANCE_OFFICER','HR','MANAGER')")
    public Page<RemediationDtos.RemediationResponse> list(Pageable pageable, Authentication authentication) {
        return remediationService.list(pageable, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','COMPLIANCE_OFFICER','HR','MANAGER')")
    public RemediationDtos.RemediationResponse get(@PathVariable UUID id, Authentication authentication) {
        return remediationService.get(id, authentication);
    }
}