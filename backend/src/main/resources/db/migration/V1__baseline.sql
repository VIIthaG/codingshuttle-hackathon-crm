-- FlowCRM baseline schema marker.
-- Domain tables (users, leads, etc.) will be added in later phases.

CREATE TABLE crm_schema_meta (
    version     INTEGER PRIMARY KEY,
    description VARCHAR(255) NOT NULL,
    applied_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO crm_schema_meta (version, description)
VALUES (1, 'baseline');
