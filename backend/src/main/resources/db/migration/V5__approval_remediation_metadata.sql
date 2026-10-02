ALTER TABLE approval_requests
    ADD COLUMN remediation_action VARCHAR(40) NULL,
    ADD COLUMN source_finding_event_id VARCHAR(120) NULL,
    ADD COLUMN execution_status VARCHAR(30) NULL,
    ADD COLUMN execution_result VARCHAR(2000) NULL,
    ADD COLUMN verification_status VARCHAR(30) NULL,
    ADD COLUMN verification_result VARCHAR(2000) NULL,
    ADD COLUMN verified_at TIMESTAMP(6) NULL;

CREATE INDEX idx_approval_source_finding
    ON approval_requests(source_finding_event_id);