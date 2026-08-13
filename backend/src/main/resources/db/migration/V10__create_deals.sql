-- Deals / opportunity pipeline. Account is required; primary contact is optional.

CREATE TABLE deals (
    id                   UUID PRIMARY KEY,
    name                 VARCHAR(255)  NOT NULL,
    account_id           UUID          NOT NULL,
    primary_contact_id   UUID,
    owner_id             UUID          NOT NULL,
    stage                VARCHAR(32)   NOT NULL,
    amount               NUMERIC(19, 2),
    currency             VARCHAR(3)    NOT NULL DEFAULT 'USD',
    probability          INTEGER       NOT NULL,
    expected_close_date  DATE,
    description          VARCHAR(2000),
    lost_reason          VARCHAR(2000),
    created_at           TIMESTAMP     NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    CONSTRAINT fk_deals_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_deals_primary_contact FOREIGN KEY (primary_contact_id) REFERENCES contacts (id) ON DELETE SET NULL,
    CONSTRAINT fk_deals_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT chk_deals_amount_non_negative CHECK (amount IS NULL OR amount >= 0),
    CONSTRAINT chk_deals_probability CHECK (probability >= 0 AND probability <= 100),
    CONSTRAINT chk_deals_currency CHECK (char_length(currency) = 3)
);

CREATE INDEX idx_deals_owner ON deals (owner_id);
CREATE INDEX idx_deals_account ON deals (account_id);
CREATE INDEX idx_deals_stage ON deals (stage);
CREATE INDEX idx_deals_expected_close_date ON deals (expected_close_date);
CREATE INDEX idx_deals_primary_contact ON deals (primary_contact_id);
