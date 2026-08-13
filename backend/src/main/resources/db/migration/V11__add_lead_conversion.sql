-- Lead conversion metadata. Converted account/contact/deal FKs are RESTRICT
-- (no ON DELETE CASCADE/SET NULL) so historical conversion links are not
-- silently dropped. Application code rejects deletes of referenced records.

ALTER TABLE leads ADD COLUMN converted_at TIMESTAMP;
ALTER TABLE leads ADD COLUMN converted_account_id UUID;
ALTER TABLE leads ADD COLUMN converted_contact_id UUID;
ALTER TABLE leads ADD COLUMN converted_deal_id UUID;

ALTER TABLE leads ADD CONSTRAINT fk_leads_converted_account
    FOREIGN KEY (converted_account_id) REFERENCES accounts (id);
ALTER TABLE leads ADD CONSTRAINT fk_leads_converted_contact
    FOREIGN KEY (converted_contact_id) REFERENCES contacts (id);
ALTER TABLE leads ADD CONSTRAINT fk_leads_converted_deal
    FOREIGN KEY (converted_deal_id) REFERENCES deals (id);

CREATE INDEX idx_leads_converted_account ON leads (converted_account_id);
CREATE INDEX idx_leads_converted_contact ON leads (converted_contact_id);
CREATE INDEX idx_leads_converted_deal ON leads (converted_deal_id);
