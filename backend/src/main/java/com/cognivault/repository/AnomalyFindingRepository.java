package com.cognivault.repository;

import com.cognivault.domain.AnomalyFinding;
import com.cognivault.domain.RiskLevel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyFindingRepository extends JpaRepository<AnomalyFinding, UUID> {

    Optional<AnomalyFinding> findByEventId(String eventId);

    Page<AnomalyFinding> findAllByOrderByFlaggedAtDesc(Pageable pageable);

        Page<AnomalyFinding> findByEmployeeIdInOrderByFlaggedAtDesc(List<UUID> employeeIds, Pageable pageable);

    long countByRiskLevel(RiskLevel riskLevel);

        long countByEmployeeIdIn(List<UUID> employeeIds);

        long countByEmployeeIdInAndRiskLevel(List<UUID> employeeIds, RiskLevel riskLevel);
}
