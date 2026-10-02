package com.cognivault.lineage;

import com.cognivault.audit.AuditEventDocument;
import com.cognivault.service.EmployeeService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DataLineageService {

    private final DataLineageRepository lineageRepository;
    private final EmployeeService employeeService;

    public DataLineageService(DataLineageRepository lineageRepository, EmployeeService employeeService) {
        this.lineageRepository = lineageRepository;
        this.employeeService = employeeService;
    }

    public void recordEvent(AuditEventDocument event) {
        if (event == null) {
            return;
        }

        String sourceEntityType = normalize(event.getEntityType());
        String sourceEntityId = event.getEntityId();
        String operation = event.getAction();

        if (sourceEntityType == null || sourceEntityId == null) {
            return;
        }

        Map<String, Object> sourceState = event.getBeforeSnapshot();
        Map<String, Object> targetState = event.getAfterSnapshot();
        if (sourceState == null && targetState == null) {
            return;
        }

        DataLineageDocument lineage = new DataLineageDocument(
                sourceEntityType,
                sourceEntityId,
                operation,
                sourceEntityType,
                sourceEntityId,
                event.getEventId(),
                event.getActor(),
                event.getActorUserId(),
                event.getTimestamp(),
                sourceState,
                targetState);

        lineageRepository.save(lineage);
    }

    public List<DataLineageDocument> forEntity(String entityType, String entityId, Authentication authentication) {
        String normalizedType = normalize(entityType);
        if (normalizedType == null || entityId == null || entityId.isBlank()) {
            return List.of();
        }

        if ("EMPLOYEE".equals(normalizedType)) {
            employeeService.assertVisible(UUID.fromString(entityId), authentication);
        }

        List<DataLineageDocument> events = new ArrayList<>(
                lineageRepository.findBySourceEntityTypeAndSourceEntityIdOrderByTimestampDesc(
                        normalizedType,
                        entityId));

        events.addAll(lineageRepository.findByTargetEntityTypeAndTargetEntityIdOrderByTimestampDesc(
                normalizedType,
                entityId));

        return events.stream()
                .distinct()
                .sorted((left, right) -> right.getTimestamp().compareTo(left.getTimestamp()))
                .toList();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
