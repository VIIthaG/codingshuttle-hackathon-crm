-- Delay publishing until available_at; allow superseding stale PENDING schedules.
-- Do not alter V5/V6; existing rows backfill available_at = created_at (portable across Postgres/H2).

ALTER TABLE outbox_events ADD COLUMN available_at TIMESTAMP;

UPDATE outbox_events
SET available_at = created_at
WHERE available_at IS NULL;

ALTER TABLE outbox_events ALTER COLUMN available_at SET NOT NULL;

ALTER TABLE outbox_events DROP CONSTRAINT ck_outbox_events_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'SUPERSEDED'));

CREATE INDEX idx_outbox_events_status_available_at
    ON outbox_events (status, available_at);
