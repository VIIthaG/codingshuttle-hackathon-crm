-- Tasks / follow-up reminders domain.

CREATE TABLE tasks (
    id              UUID PRIMARY KEY,
    lead_id         UUID         NOT NULL,
    assigned_to_id  UUID         NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     VARCHAR(2000),
    due_at          TIMESTAMP    NOT NULL,
    reminder_at     TIMESTAMP,
    status          VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_tasks_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_tasks_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES users (id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_tasks_reminder_before_due CHECK (reminder_at IS NULL OR reminder_at <= due_at)
);

CREATE INDEX idx_tasks_lead ON tasks (lead_id);
CREATE INDEX idx_tasks_assigned_to ON tasks (assigned_to_id);
CREATE INDEX idx_tasks_status ON tasks (status);
CREATE INDEX idx_tasks_due_at ON tasks (due_at);
