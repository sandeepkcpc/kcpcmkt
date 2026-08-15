-- V10: ERD-TBL-021 actual_publication_events, ERD-TBL-022 publication_target_na_records,
-- ERD-TBL-023 performance_obligations, ERD-TBL-024 creative_performance_scorecards.
-- Correction-ledger tables (ERD-TBL-026/027/028) are deferred - see docs/IMPLEMENTATION_DECISIONS.md.

CREATE TABLE actual_publication_events (
    event_id                          UUID PRIMARY KEY,
    content_plan_id                    UUID NOT NULL REFERENCES content_plans (content_plan_id),
    planned_output_id                    UUID NOT NULL REFERENCES planned_outputs (planned_output_id),
    publication_target_id                  UUID NOT NULL REFERENCES publication_targets (publication_target_id),
    event_type                                VARCHAR(20) NOT NULL,
    actual_publication_timestamp                TIMESTAMPTZ NOT NULL,
    evidence_url                                  TEXT NOT NULL,
    published_by_user_id                            UUID NOT NULL REFERENCES users (user_id),
    recorded_at                                        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_actual_publication_events_type CHECK (event_type IN ('ORIGINAL', 'REPOST')) -- ERD-CON-014
);

CREATE INDEX ix_actual_publication_events_plan ON actual_publication_events (content_plan_id);
CREATE INDEX ix_actual_publication_events_output_target ON actual_publication_events (planned_output_id, publication_target_id);

CREATE TABLE publication_target_na_records (
    na_record_id                UUID PRIMARY KEY,
    planned_output_id             UUID NOT NULL REFERENCES planned_outputs (planned_output_id),
    publication_target_id           UUID NOT NULL REFERENCES publication_targets (publication_target_id),
    action_type                       VARCHAR(20) NOT NULL,
    supersedes_na_record_id             UUID REFERENCES publication_target_na_records (na_record_id),
    mandatory_reason                      TEXT,
    actor_user_id                           UUID NOT NULL REFERENCES users (user_id),
    recorded_at                               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_publication_target_na_records_action CHECK (action_type IN ('DESIGNATED', 'REVERSED')), -- ERD-CON-...
    CONSTRAINT ck_publication_target_na_records_reason -- ERD-CON-040
        CHECK (action_type <> 'DESIGNATED' OR (mandatory_reason IS NOT NULL AND length(trim(mandatory_reason)) > 0))
);

CREATE INDEX ix_publication_target_na_records_output_target
    ON publication_target_na_records (planned_output_id, publication_target_id);

CREATE TABLE performance_obligations (
    obligation_id           UUID PRIMARY KEY,
    event_id                  UUID NOT NULL UNIQUE REFERENCES actual_publication_events (event_id),
    performance_due_date        DATE NOT NULL,
    is_completed                  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_performance_obligations_due_date ON performance_obligations (performance_due_date) WHERE is_completed = FALSE;

CREATE TABLE creative_performance_scorecards (
    scorecard_id                 UUID PRIMARY KEY,
    obligation_id                  UUID NOT NULL UNIQUE REFERENCES performance_obligations (obligation_id),
    views_3sec                       INTEGER,
    plays                              INTEGER,
    average_watch_time_seconds           NUMERIC(8, 2),
    video_length_seconds                   NUMERIC(8, 2),
    link_clicks                              INTEGER,
    impressions                                INTEGER,
    views_3sec_is_na                             BOOLEAN NOT NULL DEFAULT FALSE,
    watch_time_is_na                               BOOLEAN NOT NULL DEFAULT FALSE,
    video_length_is_na                               BOOLEAN NOT NULL DEFAULT FALSE,
    clicks_is_na                                       BOOLEAN NOT NULL DEFAULT FALSE,
    hook_rate_percent                                    NUMERIC(5, 2),
    hold_rate_percent                                      NUMERIC(5, 2),
    ctr_percent                                              NUMERIC(5, 2),
    submitted_at                                               TIMESTAMPTZ,
    recorded_by_user_id                                          UUID NOT NULL REFERENCES users (user_id),
    recorded_at                                                    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ERD-CON-060: sealed immutable once submitted.
CREATE OR REPLACE FUNCTION trg_scorecards_seal_on_submit() RETURNS trigger AS $$
BEGIN
    IF OLD.submitted_at IS NOT NULL THEN
        RAISE EXCEPTION 'creative_performance_scorecards row is sealed once submitted (ERD-CON-060)';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_scorecards_seal_on_submit
    BEFORE UPDATE ON creative_performance_scorecards
    FOR EACH ROW EXECUTE FUNCTION trg_scorecards_seal_on_submit();
