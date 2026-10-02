CREATE TABLE anomaly_findings (
    id BINARY(16) NOT NULL,
    event_id VARCHAR(120) NOT NULL,
    employee_id BINARY(16) NULL,
    score DECIMAL(6,5) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    flagged_at TIMESTAMP(6) NOT NULL,
    model VARCHAR(80) NOT NULL,
    indicators JSON NULL,
    explanations JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_anomaly_findings_event (event_id),
    CONSTRAINT fk_anomaly_findings_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
);

CREATE INDEX idx_anomaly_findings_employee
    ON anomaly_findings(employee_id);

CREATE INDEX idx_anomaly_findings_risk
    ON anomaly_findings(risk_level);

CREATE INDEX idx_anomaly_findings_flagged_at
    ON anomaly_findings(flagged_at);
