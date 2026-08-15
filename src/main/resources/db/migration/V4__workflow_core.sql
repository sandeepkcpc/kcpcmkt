-- V4: ERD-TBL-007 workflow_instances, ERD-TBL-008 workflow_transition_history.
-- Also completes V3: ERD-TBL-035 permission_grant_item_scopes (depends on workflow_instances).

CREATE TABLE workflow_instances (
    workflow_instance_id UUID PRIMARY KEY,
    current_status_code  VARCHAR(10) NOT NULL REFERENCES workflow_concepts (status_code),
    first_completed_at   TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workflow_instances_status_not_delayed CHECK (current_status_code <> 'DLY') -- ERD-CON-004
);

-- SAD-DES-031: active-workflow query support.
CREATE INDEX ix_workflow_instances_active
    ON workflow_instances (current_status_code) WHERE first_completed_at IS NULL;

-- ERD-CON-005: first_completed_at is a one-way flag enforced at the application/service layer
-- (Hibernate never issues an UPDATE that clears a non-null first_completed_at); reinforced by trigger below.
CREATE OR REPLACE FUNCTION trg_workflow_instances_completion_lock() RETURNS trigger AS $$
BEGIN
    IF OLD.first_completed_at IS NOT NULL AND NEW.first_completed_at IS DISTINCT FROM OLD.first_completed_at THEN
        RAISE EXCEPTION 'workflow_instances.first_completed_at is immutable once set (ERD-CON-005)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_workflow_instances_completion_lock
    BEFORE UPDATE ON workflow_instances
    FOR EACH ROW EXECUTE FUNCTION trg_workflow_instances_completion_lock();

CREATE TABLE workflow_transition_history (
    transition_id              UUID PRIMARY KEY,
    workflow_instance_id       UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    from_status_code           VARCHAR(10) NOT NULL REFERENCES workflow_concepts (status_code),
    to_status_code              VARCHAR(10) NOT NULL REFERENCES workflow_concepts (status_code),
    triggered_by_user_id        UUID NOT NULL REFERENCES users (user_id),
    acting_permission_grant_id  UUID REFERENCES permission_grants (grant_id),
    trigger_command              VARCHAR(100) NOT NULL,
    transition_reason            TEXT,
    transition_timestamp         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_workflow_transition_history_instance ON workflow_transition_history (workflow_instance_id);

-- ERD-CON-035 / ERD-CON-058: append-only (DB Privilege enforcement layer).
-- NOTE (see docs/IMPLEMENTATION_DECISIONS.md "DB-001"): full two-role privilege segregation
-- (migrator-owner vs. restricted runtime role, so REVOKE actually binds a non-owner grantee) is
-- deferred to Phase 14 Hardening. Interim enforcement is at the JPA/service layer: no
-- update/delete repository methods are exposed for append-only entities.

CREATE TABLE permission_grant_item_scopes (
    scope_id              UUID PRIMARY KEY,
    grant_id              UUID NOT NULL REFERENCES permission_grants (grant_id),
    workflow_instance_id  UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    CONSTRAINT uq_permission_grant_item_scopes UNIQUE (grant_id, workflow_instance_id) -- ERD-CON-044
);
