-- Durable HTTP idempotency for selected create APIs (multi-instance safe).

CREATE TABLE idempotency_records (
    id               UUID PRIMARY KEY,
    user_id          UUID          NOT NULL,
    operation        VARCHAR(64)   NOT NULL,
    idempotency_key  VARCHAR(255)  NOT NULL,
    request_hash     VARCHAR(64)   NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    response_status  INTEGER,
    response_body    TEXT,
    created_at       TIMESTAMP     NOT NULL,
    completed_at     TIMESTAMP,
    CONSTRAINT uq_idempotency_user_operation_key UNIQUE (user_id, operation, idempotency_key),
    CONSTRAINT ck_idempotency_status CHECK (status IN ('STARTED', 'COMPLETED')),
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_idempotency_records_status ON idempotency_records (status);
CREATE INDEX idx_idempotency_records_created_at ON idempotency_records (created_at);
