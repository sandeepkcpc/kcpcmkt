-- V11: ERD-TBL-029 reschedule_records, ERD-TBL-030 reassignment_records,
-- ERD-TBL-031 reassignment_assignees, ERD-TBL-032 cancellation_records, ERD-TBL-033 reopen_records.

CREATE TABLE reschedule_records (
    reschedule_id             UUID PRIMARY KEY,
    workflow_instance_id        UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    stage_context                 VARCHAR(30) NOT NULL,
    prior_planned_shoot_date        DATE,
    prior_planned_edit_date           DATE,
    prior_planned_live_date             DATE,
    new_planned_shoot_date                DATE,
    new_planned_edit_date                   DATE,
    new_planned_live_date                     DATE,
    mandatory_reason                            TEXT NOT NULL,
    rescheduled_by_user_id                        UUID NOT NULL REFERENCES users (user_id),
    rescheduled_at                                  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id                        UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_reschedule_records_stage CHECK (stage_context IN ('SHOOTING', 'EDITING', 'PUBLISHING'))
);

CREATE INDEX ix_reschedule_records_workflow ON reschedule_records (workflow_instance_id);

CREATE TABLE reassignment_records (
    reassignment_id            UUID PRIMARY KEY,
    workflow_instance_id         UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    task_stage                     VARCHAR(30) NOT NULL,
    mandatory_reason                  TEXT NOT NULL,
    reassigned_by_user_id                UUID NOT NULL REFERENCES users (user_id),
    reassigned_at                          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id               UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_reassignment_records_stage CHECK (task_stage IN ('SHOOTING', 'EDITING'))
);

CREATE INDEX ix_reassignment_records_workflow ON reassignment_records (workflow_instance_id);

CREATE TABLE reassignment_assignees (
    reassignment_assignee_id     UUID PRIMARY KEY,
    reassignment_id                 UUID NOT NULL REFERENCES reassignment_records (reassignment_id),
    user_id                            UUID NOT NULL REFERENCES users (user_id),
    assignee_role                        VARCHAR(20) NOT NULL,
    set_side                               VARCHAR(20) NOT NULL,
    recorded_at                              TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_reassignment_assignees_role CHECK (assignee_role IN ('CAMERAPERSON', 'EDITOR')),
    CONSTRAINT ck_reassignment_assignees_side CHECK (set_side IN ('PREVIOUS', 'NEW')),
    CONSTRAINT uq_reassignment_assignees UNIQUE (reassignment_id, user_id, set_side) -- ERD-CON-045
);

CREATE TABLE cancellation_records (
    cancellation_id            UUID PRIMARY KEY,
    workflow_instance_id         UUID NOT NULL UNIQUE REFERENCES workflow_instances (workflow_instance_id),
    mandatory_reason                TEXT NOT NULL,
    cancelled_by_user_id               UUID NOT NULL REFERENCES users (user_id),
    cancelled_at                         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id             UUID REFERENCES permission_grants (grant_id)
);

CREATE TABLE reopen_records (
    reopen_id                  UUID PRIMARY KEY,
    workflow_instance_id         UUID NOT NULL REFERENCES workflow_instances (workflow_instance_id),
    from_status_code                VARCHAR(10) NOT NULL REFERENCES workflow_concepts (status_code),
    to_status_code                     VARCHAR(10) NOT NULL REFERENCES workflow_concepts (status_code),
    reopen_purpose                       VARCHAR(50) NOT NULL,
    mandatory_reason                       TEXT,
    reopened_by_user_id                      UUID NOT NULL REFERENCES users (user_id),
    reopened_at                                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id                   UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_reopen_records_purpose
        CHECK (reopen_purpose IN ('RETAINED_REOPEN', 'PUBLISHING_REOPEN', 'METRIC_CORRECTION_REOPEN'))
);

CREATE INDEX ix_reopen_records_workflow ON reopen_records (workflow_instance_id);
