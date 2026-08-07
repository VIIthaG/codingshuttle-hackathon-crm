-- Transactional outbox foundation for async reminder/event publishing.

CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    aggregate_type  VARCHAR(64)   NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(64)   NOT NULL,
    payload         TEXT          NOT NULL,
    status          VARCHAR(32)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    published_at    TIMESTAMP,
    CONSTRAINT ck_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status ON outbox_events (status);
CREATE INDEX idx_outbox_events_created_at ON outbox_events (created_at);
CREATE INDEX idx_outbox_events_aggregate_id ON outbox_events (aggregate_id);
CREATE INDEX idx_outbox_events_status_created_at ON outbox_events (status, created_at);
