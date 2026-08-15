-- V9: ERD-TBL-014 editing_assignments, ERD-TBL-016 personal_mark_attributions,
-- ERD-TBL-038 shooting_execution_participants, ERD-TBL-039 editing_execution_participants,
-- ERD-TBL-043 work_hold_records.

CREATE TABLE editing_assignments (
    assignment_id      UUID PRIMARY KEY,
    content_plan_id     UUID NOT NULL REFERENCES content_plans (content_plan_id),
    editor_user_id       UUID NOT NULL REFERENCES users (user_id),
    assigned_by_user_id   UUID NOT NULL REFERENCES users (user_id),
    assigned_at            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    ended_at                 TIMESTAMPTZ
);

CREATE INDEX ix_editing_assignments_plan ON editing_assignments (content_plan_id);
CREATE INDEX ix_editing_assignments_active ON editing_assignments (content_plan_id) WHERE is_active = TRUE;

CREATE TABLE personal_mark_attributions (
    attribution_id       UUID PRIMARY KEY,
    recipient_user_id      UUID NOT NULL REFERENCES users (user_id),
    role_type                VARCHAR(20) NOT NULL,
    content_plan_id          UUID NOT NULL REFERENCES content_plans (content_plan_id),
    review_cycle_id           UUID NOT NULL REFERENCES review_cycles (review_cycle_id),
    predefined_mark_id        UUID NOT NULL REFERENCES predefined_role_marks (mark_id),
    attributed_mark_value      NUMERIC(3, 1) NOT NULL,
    attributed_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_personal_mark_attributions_role CHECK (role_type IN ('CAMERAPERSON', 'EDITOR')),
    -- ERD-CON-020: duplicate attribution to same recipient for same review trigger blocked.
    CONSTRAINT uq_personal_mark_attributions_recipient_cycle UNIQUE (recipient_user_id, review_cycle_id)
);

CREATE INDEX ix_personal_mark_attributions_recipient ON personal_mark_attributions (recipient_user_id);

CREATE TABLE shooting_execution_participants (
    participant_id           UUID PRIMARY KEY,
    shooting_assignment_id    UUID REFERENCES shooting_assignments (assignment_id),
    content_plan_id            UUID NOT NULL REFERENCES content_plans (content_plan_id),
    cameraperson_user_id        UUID NOT NULL REFERENCES users (user_id),
    recorded_at                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_shooting_execution_participants_plan ON shooting_execution_participants (content_plan_id);

CREATE TABLE editing_execution_participants (
    participant_id          UUID PRIMARY KEY,
    editing_assignment_id     UUID REFERENCES editing_assignments (assignment_id),
    content_plan_id            UUID NOT NULL REFERENCES content_plans (content_plan_id),
    editor_user_id               UUID NOT NULL REFERENCES users (user_id),
    recorded_at                    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_editing_execution_participants_plan ON editing_execution_participants (content_plan_id);

CREATE TABLE work_hold_records (
    hold_record_id       UUID PRIMARY KEY,
    workflow_instance_id   UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    held_status_code         VARCHAR(10) NOT NULL REFERENCES workflow_concepts (status_code),
    held_by_user_id            UUID NOT NULL REFERENCES users (user_id),
    held_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hold_reason                   TEXT NOT NULL,
    resumed_by_user_id              UUID REFERENCES users (user_id),
    resumed_at                        TIMESTAMPTZ,
    created_at                          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_work_hold_records_status CHECK (held_status_code IN ('SIP', 'ED')), -- ERD-CON-061
    CONSTRAINT ck_work_hold_records_resume_pair -- part of ERD-CON-062 rule 3
        CHECK ((resumed_by_user_id IS NULL AND resumed_at IS NULL) OR (resumed_by_user_id IS NOT NULL AND resumed_at IS NOT NULL)),
    CONSTRAINT ck_work_hold_records_resume_after_hold CHECK (resumed_at IS NULL OR resumed_at >= held_at) -- ERD-CON-062 rule 5
);

-- ERD-CON-062 rule 1: at most one OPEN hold (resumed_at IS NULL) per workflow instance.
CREATE UNIQUE INDEX uq_work_hold_records_one_open_per_instance
    ON work_hold_records (workflow_instance_id) WHERE resumed_at IS NULL;

-- ERD-CON-062 rules 6-8: hold-origin fields immutable; resume fields transition exactly once
-- from NULL to populated together; row fully immutable thereafter.
CREATE OR REPLACE FUNCTION trg_work_hold_records_lifecycle() RETURNS trigger AS $$
BEGIN
    IF OLD.resumed_at IS NOT NULL THEN
        RAISE EXCEPTION 'work_hold_records row is fully immutable once resumed (ERD-CON-062 rule 8)';
    END IF;
    IF NEW.workflow_instance_id IS DISTINCT FROM OLD.workflow_instance_id
        OR NEW.held_status_code IS DISTINCT FROM OLD.held_status_code
        OR NEW.held_by_user_id IS DISTINCT FROM OLD.held_by_user_id
        OR NEW.held_at IS DISTINCT FROM OLD.held_at
        OR NEW.hold_reason IS DISTINCT FROM OLD.hold_reason THEN
        RAISE EXCEPTION 'work_hold_records hold-origin fields are immutable after insertion (ERD-CON-062 rule 6)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_work_hold_records_lifecycle
    BEFORE UPDATE ON work_hold_records
    FOR EACH ROW EXECUTE FUNCTION trg_work_hold_records_lifecycle();
