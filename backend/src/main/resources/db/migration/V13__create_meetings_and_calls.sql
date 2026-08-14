-- Meetings and Calls: first-class CRM activities linked to exactly one record.
-- FKs do not cascade-delete; application returns HTTP 409 when parents still have activities.

CREATE TABLE meetings (
    id              UUID PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    description     VARCHAR(2000),
    start_at        TIMESTAMP    NOT NULL,
    end_at          TIMESTAMP    NOT NULL,
    location        VARCHAR(255),
    meeting_url     VARCHAR(500),
    status          VARCHAR(32)  NOT NULL,
    assigned_to_id  UUID         NOT NULL,
    lead_id         UUID,
    account_id      UUID,
    contact_id      UUID,
    deal_id         UUID,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_meetings_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES users (id),
    CONSTRAINT fk_meetings_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_meetings_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_meetings_contact FOREIGN KEY (contact_id) REFERENCES contacts (id),
    CONSTRAINT fk_meetings_deal FOREIGN KEY (deal_id) REFERENCES deals (id),
    CONSTRAINT ck_meetings_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_meetings_end_after_start CHECK (end_at > start_at),
    CONSTRAINT ck_meetings_exactly_one_relation CHECK (
        (CASE WHEN lead_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN account_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN contact_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN deal_id IS NOT NULL THEN 1 ELSE 0 END)
        = 1
    )
);

CREATE INDEX idx_meetings_assigned_to ON meetings (assigned_to_id);
CREATE INDEX idx_meetings_status ON meetings (status);
CREATE INDEX idx_meetings_start_at ON meetings (start_at);
CREATE INDEX idx_meetings_lead ON meetings (lead_id);
CREATE INDEX idx_meetings_account ON meetings (account_id);
CREATE INDEX idx_meetings_contact ON meetings (contact_id);
CREATE INDEX idx_meetings_deal ON meetings (deal_id);

CREATE TABLE calls (
    id                UUID PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    description       VARCHAR(2000),
    scheduled_at      TIMESTAMP    NOT NULL,
    duration_minutes  INTEGER,
    direction         VARCHAR(32)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    phone_number      VARCHAR(50),
    outcome           VARCHAR(2000),
    assigned_to_id    UUID         NOT NULL,
    lead_id           UUID,
    account_id        UUID,
    contact_id        UUID,
    deal_id           UUID,
    created_at        TIMESTAMP    NOT NULL,
    updated_at        TIMESTAMP    NOT NULL,
    CONSTRAINT fk_calls_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES users (id),
    CONSTRAINT fk_calls_lead FOREIGN KEY (lead_id) REFERENCES leads (id),
    CONSTRAINT fk_calls_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_calls_contact FOREIGN KEY (contact_id) REFERENCES contacts (id),
    CONSTRAINT fk_calls_deal FOREIGN KEY (deal_id) REFERENCES deals (id),
    CONSTRAINT ck_calls_direction CHECK (direction IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT ck_calls_status CHECK (status IN ('PLANNED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_calls_duration CHECK (duration_minutes IS NULL OR duration_minutes >= 0),
    CONSTRAINT ck_calls_exactly_one_relation CHECK (
        (CASE WHEN lead_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN account_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN contact_id IS NOT NULL THEN 1 ELSE 0 END)
        + (CASE WHEN deal_id IS NOT NULL THEN 1 ELSE 0 END)
        = 1
    )
);

CREATE INDEX idx_calls_assigned_to ON calls (assigned_to_id);
CREATE INDEX idx_calls_status ON calls (status);
CREATE INDEX idx_calls_scheduled_at ON calls (scheduled_at);
CREATE INDEX idx_calls_lead ON calls (lead_id);
CREATE INDEX idx_calls_account ON calls (account_id);
CREATE INDEX idx_calls_contact ON calls (contact_id);
CREATE INDEX idx_calls_deal ON calls (deal_id);
