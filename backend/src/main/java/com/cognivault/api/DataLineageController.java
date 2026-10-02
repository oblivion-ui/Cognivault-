package com.cognivault.api;

import com.cognivault.lineage.DataLineageDocument;
import com.cognivault.lineage.DataLineageService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lineage")
public class DataLineageController {

    private final DataLineageService lineageService;

    public DataLineageController(DataLineageService lineageService) {
        this.lineageService = lineageService;
    }

    @GetMapping("/entities/{entityType}/{entityId}")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR','MANAGER','HR','EMPLOYEE')")
    public List<DataLineageDocument> getForEntity(
            @PathVariable String entityType,
            @PathVariable String entityId,
            Authentication authentication) {
        return lineageService.forEntity(entityType, entityId, authentication);
    }
}
