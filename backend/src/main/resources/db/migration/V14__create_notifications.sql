-- In-app assignment notifications. Informational only: no FKs to CRM records.
-- CASCADE when the recipient user is deleted (test cleanup / account removal).

CREATE TABLE notifications (
    id                   UUID PRIMARY KEY,
    user_id              UUID         NOT NULL,
    type                 VARCHAR(32)  NOT NULL,
    title                VARCHAR(255) NOT NULL,
    message              VARCHAR(2000),
    related_entity_type  VARCHAR(32),
    related_entity_id    UUID,
    read_at              TIMESTAMP,
    created_at           TIMESTAMP    NOT NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type CHECK (type IN ('ASSIGNMENT'))
);

CREATE INDEX idx_notifications_user ON notifications (user_id);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, read_at);
CREATE INDEX idx_notifications_created_at ON notifications (created_at);
