-- V7: ERD-TBL-009 ideas, ERD-TBL-042 content_id_sequences, ERD-TBL-010 content_plans (full
-- physical shape - Planning-stage fields are nullable here and validated required by the
-- application before Planning Review submission, ERD-CON-026), ERD-TBL-012
-- predefined_role_marks, ERD-TBL-015 review_cycles.

CREATE TABLE ideas (
    idea_id              UUID PRIMARY KEY,
    workflow_instance_id UUID NOT NULL UNIQUE REFERENCES workflow_instances (workflow_instance_id),
    business_idea_code   VARCHAR(30) NOT NULL UNIQUE,
    title                VARCHAR(200) NOT NULL,
    reference_link       TEXT,
    notes_remarks        TEXT,
    submitted_by_user_id UUID NOT NULL REFERENCES users (user_id),
    submitted_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_ideas_submitted_by ON ideas (submitted_by_user_id);

CREATE TABLE content_id_sequences (
    business_month_mmyy  VARCHAR(4) PRIMARY KEY,
    last_sequence_number INTEGER NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_plans (
    content_plan_id      UUID PRIMARY KEY,
    idea_id              UUID NOT NULL UNIQUE REFERENCES ideas (idea_id),
    workflow_instance_id UUID NOT NULL UNIQUE REFERENCES workflow_instances (workflow_instance_id),
    content_id           VARCHAR(20) NOT NULL UNIQUE,
    category_text        TEXT,
    content_priority     VARCHAR(10),
    sku_reference        VARCHAR(100),
    sku_not_applicable   BOOLEAN NOT NULL DEFAULT FALSE,
    planned_live_date    DATE,
    planning_mode        VARCHAR(10) NOT NULL DEFAULT 'STANDARD',
    urgency_reason       TEXT,
    planned_shoot_date   DATE,
    planned_edit_date    DATE,
    folder_link          TEXT,
    prepared_by_user_id  UUID REFERENCES users (user_id),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_content_plans_sku_na_exclusion
        CHECK (NOT (sku_not_applicable AND sku_reference IS NOT NULL)), -- ERD-CON-009
    CONSTRAINT ck_content_plans_priority CHECK (content_priority IN ('LOW', 'MEDIUM', 'HIGH')), -- ERD-CON-055
    CONSTRAINT ck_content_plans_planning_mode CHECK (planning_mode IN ('STANDARD', 'URGENT')), -- ERD-CON-064
    CONSTRAINT ck_content_plans_urgency_reason -- ERD-CON-065
        CHECK ((planning_mode = 'URGENT' AND urgency_reason IS NOT NULL AND length(trim(urgency_reason)) > 0)
            OR (planning_mode = 'STANDARD' AND urgency_reason IS NULL)),
    CONSTRAINT ck_content_plans_date_chronology -- ERD-CON-066 (equal dates permitted under URGENT)
        CHECK (
            (planned_shoot_date IS NULL OR planned_edit_date IS NULL OR planned_shoot_date <= planned_edit_date)
            AND (planned_edit_date IS NULL OR planned_live_date IS NULL OR planned_edit_date <= planned_live_date)
        )
);

CREATE INDEX ix_content_plans_content_id ON content_plans (content_id);

-- ERD-CON-036: content_id is immutable once allocated.
CREATE OR REPLACE FUNCTION trg_content_plans_content_id_lock() RETURNS trigger AS $$
BEGIN
    IF NEW.content_id IS DISTINCT FROM OLD.content_id THEN
        RAISE EXCEPTION 'content_plans.content_id is immutable once allocated (ERD-CON-036)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_content_plans_content_id_lock
    BEFORE UPDATE ON content_plans
    FOR EACH ROW EXECUTE FUNCTION trg_content_plans_content_id_lock();

CREATE TABLE predefined_role_marks (
    mark_id                      UUID PRIMARY KEY,
    content_plan_id              UUID NOT NULL UNIQUE REFERENCES content_plans (content_plan_id),
    predefined_cameraperson_mark NUMERIC(3, 1) NOT NULL,
    predefined_editor_mark       NUMERIC(3, 1) NOT NULL,
    set_by_user_id               UUID NOT NULL REFERENCES users (user_id),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_predefined_role_marks_cameraperson -- ERD-CON-010
        CHECK (predefined_cameraperson_mark IN (0.0, 0.5, 1.0, 2.0, 3.0)),
    CONSTRAINT ck_predefined_role_marks_editor -- ERD-CON-010
        CHECK (predefined_editor_mark IN (0.0, 0.5, 1.0, 2.0, 3.0))
);

CREATE TABLE review_cycles (
    review_cycle_id             UUID PRIMARY KEY,
    workflow_instance_id        UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    gate_type                   VARCHAR(20) NOT NULL,
    cycle_number                INTEGER NOT NULL,
    submitted_by_user_id        UUID NOT NULL REFERENCES users (user_id),
    submitted_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewer_user_id            UUID REFERENCES users (user_id),
    decision                    VARCHAR(20),
    decision_reason             TEXT,
    decided_at                  TIMESTAMPTZ,
    acting_permission_grant_id  UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_review_cycles_gate_type
        CHECK (gate_type IN ('IDEA_REVIEW', 'PLANNING_REVIEW', 'SHOOT_REVIEW', 'EDIT_REVIEW')),
    CONSTRAINT uq_review_cycles_cycle UNIQUE (workflow_instance_id, gate_type, cycle_number) -- ERD-CON-030
);

CREATE INDEX ix_review_cycles_workflow_instance ON review_cycles (workflow_instance_id);

-- ERD-CON-039: decision fields immutable once decided_at is set.
CREATE OR REPLACE FUNCTION trg_review_cycles_decision_lock() RETURNS trigger AS $$
BEGIN
    IF OLD.decided_at IS NOT NULL THEN
        RAISE EXCEPTION 'review_cycles decision fields are immutable once decided_at is set (ERD-CON-039)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_review_cycles_decision_lock
    BEFORE UPDATE ON review_cycles
    FOR EACH ROW EXECUTE FUNCTION trg_review_cycles_decision_lock();
