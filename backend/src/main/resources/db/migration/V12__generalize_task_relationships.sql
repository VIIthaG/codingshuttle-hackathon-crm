-- Generalized task relationships: a task belongs to exactly one CRM record.
-- Existing lead-linked rows remain valid (lead_id stays populated).
-- FKs use RESTRICT (default) so deleting a Lead/Account/Contact/Deal that still
-- has tasks is rejected rather than cascade-deleting follow-ups. Application
-- code returns HTTP 409 in that case.

ALTER TABLE tasks ALTER COLUMN lead_id DROP NOT NULL;

ALTER TABLE tasks ADD COLUMN account_id UUID;
ALTER TABLE tasks ADD COLUMN contact_id UUID;
ALTER TABLE tasks ADD COLUMN deal_id UUID;

ALTER TABLE tasks ADD CONSTRAINT fk_tasks_account
    FOREIGN KEY (account_id) REFERENCES accounts (id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_contact
    FOREIGN KEY (contact_id) REFERENCES contacts (id);
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_deal
    FOREIGN KEY (deal_id) REFERENCES deals (id);

ALTER TABLE tasks ADD CONSTRAINT ck_tasks_exactly_one_relation CHECK (
    (CASE WHEN lead_id IS NOT NULL THEN 1 ELSE 0 END)
    + (CASE WHEN account_id IS NOT NULL THEN 1 ELSE 0 END)
    + (CASE WHEN contact_id IS NOT NULL THEN 1 ELSE 0 END)
    + (CASE WHEN deal_id IS NOT NULL THEN 1 ELSE 0 END)
    = 1
);

CREATE INDEX idx_tasks_account ON tasks (account_id);
CREATE INDEX idx_tasks_contact ON tasks (contact_id);
CREATE INDEX idx_tasks_deal ON tasks (deal_id);
