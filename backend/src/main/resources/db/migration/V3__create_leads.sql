-- Leads table for lead management.

CREATE TABLE leads (
    id              UUID PRIMARY KEY,
    full_name       VARCHAR(255) NOT NULL,
    email           VARCHAR(255),
    phone           VARCHAR(50),
    company         VARCHAR(255),
    source          VARCHAR(32)  NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    assigned_to_id  UUID         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT fk_leads_assigned_to FOREIGN KEY (assigned_to_id) REFERENCES users (id),
    CONSTRAINT ck_leads_source CHECK (source IN ('WEB', 'REFERRAL', 'COLD_CALL', 'EVENT', 'OTHER')),
    CONSTRAINT ck_leads_status CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'LOST', 'CONVERTED'))
);

CREATE INDEX idx_leads_assigned_to ON leads (assigned_to_id);
CREATE INDEX idx_leads_status ON leads (status);
CREATE INDEX idx_leads_email ON leads (email);
