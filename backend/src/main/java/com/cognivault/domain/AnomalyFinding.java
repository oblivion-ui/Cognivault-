package com.cognivault.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "anomaly_findings")
public class AnomalyFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 120, unique = true)
    private String eventId;

    @Column(name = "employee_id")
    private UUID employeeId;

    @Column(nullable = false, precision = 6, scale = 5)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "flagged_at", nullable = false)
    private Instant flaggedAt;

    @Column(nullable = false, length = 80)
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<String> indicators;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<AnomalyExplanation> explanations;

    protected AnomalyFinding() {
    }

    public AnomalyFinding(
            String eventId,
            UUID employeeId,
            BigDecimal score,
            RiskLevel riskLevel,
            Instant flaggedAt,
            String model,
            List<String> indicators,
            List<AnomalyExplanation> explanations) {
        this.eventId = eventId;
        this.employeeId = employeeId;
        this.score = score;
        this.riskLevel = riskLevel;
        this.flaggedAt = flaggedAt;
        this.model = model;
        this.indicators = indicators;
        this.explanations = explanations;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void updateRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public Instant getFlaggedAt() {
        return flaggedAt;
    }

    public String getModel() {
        return model;
    }

    public List<String> getIndicators() {
        return indicators;
    }

    public List<AnomalyExplanation> getExplanations() {
        return explanations;
    }
}
