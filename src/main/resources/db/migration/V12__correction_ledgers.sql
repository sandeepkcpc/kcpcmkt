-- V12: CORR-001 closure - the three governed correction/supersession ledgers deferred out of V9/V10:
-- ERD-TBL-026 predefined_mark_corrections, ERD-TBL-027 publication_evidence_corrections,
-- ERD-TBL-028 performance_metric_corrections. See docs/IMPLEMENTATION_DECISIONS.md CORR-001.

CREATE TABLE predefined_mark_corrections (
    correction_id              UUID PRIMARY KEY,
    predefined_mark_id         UUID NOT NULL REFERENCES predefined_role_marks (mark_id),
    supersedes_correction_id   UUID REFERENCES predefined_mark_corrections (correction_id),
    prior_cameraperson_mark    NUMERIC(3, 1) NOT NULL,
    prior_editor_mark          NUMERIC(3, 1) NOT NULL,
    new_cameraperson_mark      NUMERIC(3, 1) NOT NULL,
    new_editor_mark            NUMERIC(3, 1) NOT NULL,
    correction_reason          TEXT NOT NULL,
    corrected_by_user_id       UUID NOT NULL REFERENCES users (user_id),
    corrected_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id   UUID REFERENCES permission_grants (grant_id),
    -- ERD-CON-029: corrected marks must be from the controlled list.
    CONSTRAINT ck_predefined_mark_corrections_new_camera
        CHECK (new_cameraperson_mark IN (0.0, 0.5, 1.0, 2.0, 3.0)),
    CONSTRAINT ck_predefined_mark_corrections_new_editor
        CHECK (new_editor_mark IN (0.0, 0.5, 1.0, 2.0, 3.0)),
    CONSTRAINT ck_predefined_mark_corrections_reason CHECK (length(trim(correction_reason)) > 0)
);

CREATE INDEX ix_predefined_mark_corrections_mark ON predefined_mark_corrections (predefined_mark_id);

CREATE TABLE publication_evidence_corrections (
    correction_id              UUID PRIMARY KEY,
    event_id                   UUID NOT NULL REFERENCES actual_publication_events (event_id),
    supersedes_correction_id   UUID REFERENCES publication_evidence_corrections (correction_id),
    prior_evidence_url         TEXT NOT NULL,
    corrected_evidence_url     TEXT NOT NULL,
    mandatory_reason           TEXT NOT NULL,
    corrected_by_user_id       UUID NOT NULL REFERENCES users (user_id),
    corrected_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id   UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_publication_evidence_corrections_reason CHECK (length(trim(mandatory_reason)) > 0)
);

CREATE INDEX ix_publication_evidence_corrections_event ON publication_evidence_corrections (event_id);

CREATE TABLE performance_metric_corrections (
    correction_id              UUID PRIMARY KEY,
    scorecard_id                UUID NOT NULL REFERENCES creative_performance_scorecards (scorecard_id),
    supersedes_correction_id   UUID REFERENCES performance_metric_corrections (correction_id),
    prior_views_3sec            INTEGER,
    new_views_3sec               INTEGER,
    prior_plays                    INTEGER,
    new_plays                        INTEGER,
    prior_watch_time                   NUMERIC(8, 2),
    new_watch_time                       NUMERIC(8, 2),
    prior_video_length                     NUMERIC(8, 2),
    new_video_length                         NUMERIC(8, 2),
    prior_clicks                               INTEGER,
    new_clicks                                   INTEGER,
    prior_impressions                              INTEGER,
    new_impressions                                  INTEGER,
    prior_views_3sec_is_na                             BOOLEAN,
    new_views_3sec_is_na                                 BOOLEAN,
    prior_watch_time_is_na                                 BOOLEAN,
    new_watch_time_is_na                                     BOOLEAN,
    prior_video_length_is_na                                   BOOLEAN,
    new_video_length_is_na                                       BOOLEAN,
    prior_clicks_is_na                                             BOOLEAN,
    new_clicks_is_na                                                 BOOLEAN,
    mandatory_reason                                                   TEXT NOT NULL,
    corrected_by_user_id                                                 UUID NOT NULL REFERENCES users (user_id),
    corrected_at                                                           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id                                               UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_performance_metric_corrections_reason CHECK (length(trim(mandatory_reason)) > 0)
);

CREATE INDEX ix_performance_metric_corrections_scorecard ON performance_metric_corrections (scorecard_id);

-- ERD-CON-050/051/049: a correction's supersedes_correction_id must chain to a correction of the
-- SAME parent (predefined_mark_id / event_id / scorecard_id respectively).
CREATE OR REPLACE FUNCTION trg_predefined_mark_corrections_same_parent() RETURNS trigger AS $$
BEGIN
    IF NEW.supersedes_correction_id IS NOT NULL THEN
        PERFORM 1 FROM predefined_mark_corrections
            WHERE correction_id = NEW.supersedes_correction_id AND predefined_mark_id = NEW.predefined_mark_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'supersedes_correction_id must belong to the same predefined_mark_id (ERD-CON-050)';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_predefined_mark_corrections_same_parent
    BEFORE INSERT ON predefined_mark_corrections
    FOR EACH ROW EXECUTE FUNCTION trg_predefined_mark_corrections_same_parent();

CREATE OR REPLACE FUNCTION trg_publication_evidence_corrections_same_parent() RETURNS trigger AS $$
BEGIN
    IF NEW.supersedes_correction_id IS NOT NULL THEN
        PERFORM 1 FROM publication_evidence_corrections
            WHERE correction_id = NEW.supersedes_correction_id AND event_id = NEW.event_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'supersedes_correction_id must belong to the same event_id (ERD-CON-051)';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_publication_evidence_corrections_same_parent
    BEFORE INSERT ON publication_evidence_corrections
    FOR EACH ROW EXECUTE FUNCTION trg_publication_evidence_corrections_same_parent();

CREATE OR REPLACE FUNCTION trg_performance_metric_corrections_same_parent() RETURNS trigger AS $$
BEGIN
    IF NEW.supersedes_correction_id IS NOT NULL THEN
        PERFORM 1 FROM performance_metric_corrections
            WHERE correction_id = NEW.supersedes_correction_id AND scorecard_id = NEW.scorecard_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'supersedes_correction_id must belong to the same scorecard_id (ERD-CON-049)';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_performance_metric_corrections_same_parent
    BEFORE INSERT ON performance_metric_corrections
    FOR EACH ROW EXECUTE FUNCTION trg_performance_metric_corrections_same_parent();

-- ERD-CON-058: append-only history tables reject UPDATE/DELETE/TRUNCATE after insertion.
-- Reusable trigger function; also attached to the pre-existing append-only tables here since none
-- of them had DB-level enforcement before this migration (DB-001, closed alongside CORR-001).
CREATE OR REPLACE FUNCTION trg_reject_update_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only; % is not permitted (ERD-CON-058)', TG_TABLE_NAME, TG_OP;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'predefined_mark_corrections', 'publication_evidence_corrections', 'performance_metric_corrections',
        'workflow_transition_history', 'personal_mark_attributions', 'actual_publication_events',
        'publication_target_na_records', 'reschedule_records', 'reassignment_records',
        'reassignment_assignees', 'cancellation_records', 'reopen_records', 'planning_preparers',
        'shooting_execution_participants', 'editing_execution_participants', 'system_audit_log'
    ]
    LOOP
        EXECUTE format(
            'CREATE TRIGGER trg_%1$s_reject_update BEFORE UPDATE ON %1$s FOR EACH ROW EXECUTE FUNCTION trg_reject_update_delete();',
            t);
        EXECUTE format(
            'CREATE TRIGGER trg_%1$s_reject_delete BEFORE DELETE ON %1$s FOR EACH ROW EXECUTE FUNCTION trg_reject_update_delete();',
            t);
    END LOOP;
END $$;
