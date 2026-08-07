-- Consumer idempotency store for RabbitMQ reminder deliveries.
-- message_id corresponds to the outbox event id published to the broker.

CREATE TABLE processed_messages (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_processed_messages_processed_at ON processed_messages (processed_at);
