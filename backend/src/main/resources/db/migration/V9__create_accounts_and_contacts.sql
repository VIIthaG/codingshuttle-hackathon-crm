-- Accounts (companies) and Contacts. Contacts may exist without an account.

CREATE TABLE accounts (
    id           UUID PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    website      VARCHAR(255),
    phone        VARCHAR(50),
    industry     VARCHAR(255),
    description  VARCHAR(2000),
    owner_id     UUID         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_accounts_owner ON accounts (owner_id);
CREATE INDEX idx_accounts_name ON accounts (name);
CREATE INDEX idx_accounts_industry ON accounts (industry);

CREATE TABLE contacts (
    id           UUID PRIMARY KEY,
    first_name   VARCHAR(255) NOT NULL,
    last_name    VARCHAR(255) NOT NULL,
    email        VARCHAR(255),
    phone        VARCHAR(50),
    job_title    VARCHAR(255),
    notes        VARCHAR(2000),
    account_id   UUID,
    owner_id     UUID         NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    CONSTRAINT fk_contacts_account FOREIGN KEY (account_id) REFERENCES accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_contacts_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_contacts_owner ON contacts (owner_id);
CREATE INDEX idx_contacts_account ON contacts (account_id);
CREATE INDEX idx_contacts_email ON contacts (email);
CREATE INDEX idx_contacts_last_name ON contacts (last_name);
