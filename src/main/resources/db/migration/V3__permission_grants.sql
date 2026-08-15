-- V3: ERD-TBL-005 permission_grants, ERD-TBL-034 permission_grant_stage_scopes,
-- ERD-TBL-035 permission_grant_item_scopes.
-- Workflow-instance scoped grants (ITEM_SPECIFIC) reference workflow_instances, created in V4;
-- this migration is ordered after V4 dependency is satisfied by creating that table first here
-- is not possible without workflow_instances, so permission_grant_item_scopes' FK is added in V4.

CREATE TABLE permission_grants (
    grant_id          UUID PRIMARY KEY,
    grantee_user_id   UUID NOT NULL REFERENCES users (user_id),
    grantor_user_id   UUID NOT NULL REFERENCES users (user_id),
    permission_number INTEGER NOT NULL REFERENCES operational_permissions (permission_number),
    scope_type        VARCHAR(20) NOT NULL, -- ERD-CON-021
    effective_from     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_until    TIMESTAMPTZ,
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    revoked_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_permission_grants_scope_type
        CHECK (scope_type IN ('GLOBAL', 'STAGE_RESTRICTED', 'ITEM_SPECIFIC')), -- ERD-CON-021
    CONSTRAINT ck_permission_grants_effective_window
        CHECK (effective_until IS NULL OR effective_until > effective_from) -- ERD-CON-025
);

-- SAD-DES-004 real-time evaluation: partial index over currently-active grants.
CREATE INDEX ix_permission_grants_active_lookup
    ON permission_grants (grantee_user_id, permission_number) WHERE is_active = TRUE;

CREATE TABLE permission_grant_stage_scopes (
    scope_id     UUID PRIMARY KEY,
    grant_id     UUID NOT NULL REFERENCES permission_grants (grant_id),
    stage_number INTEGER NOT NULL REFERENCES permission_grant_stages (stage_number), -- ERD-CON-019
    CONSTRAINT uq_permission_grant_stage_scopes UNIQUE (grant_id, stage_number) -- ERD-CON-043
);
