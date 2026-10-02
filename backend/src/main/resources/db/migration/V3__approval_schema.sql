CREATE TABLE approval_requests (
    id BINARY(16) NOT NULL,
    requester_id BINARY(16) NOT NULL,
    approver_id BINARY(16) NULL,
    request_type VARCHAR(50) NOT NULL,
    target VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    required_approver VARCHAR(80) NULL,
    risk_level VARCHAR(20) NULL,
    justification VARCHAR(2000) NULL,
    previous_state JSON NULL,
    requested_state JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    decided_at TIMESTAMP(6) NULL,
    decision_comment VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_approval_requester
        FOREIGN KEY (requester_id) REFERENCES users(id),
    CONSTRAINT fk_approval_approver
        FOREIGN KEY (approver_id) REFERENCES users(id)
);

CREATE INDEX idx_approval_status
    ON approval_requests(status);

CREATE INDEX idx_approval_requester
    ON approval_requests(requester_id);

CREATE INDEX idx_approval_created_at
    ON approval_requests(created_at);

CREATE INDEX idx_approval_request_type
    ON approval_requests(request_type);
