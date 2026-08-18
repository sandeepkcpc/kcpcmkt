-- ENG-046: per-stage Description (one shared value per Content Plan per stage, not per assignee)
-- and a Jira-style discussion thread per stage. Not present in the frozen ERD - see
-- docs/IMPLEMENTATION_DECISIONS.md ENG-046 for the explicit user request and rationale.

ALTER TABLE content_plans ADD COLUMN shoot_description TEXT;
ALTER TABLE content_plans ADD COLUMN edit_description TEXT;
ALTER TABLE content_plans ADD COLUMN publishing_description TEXT;

-- One thread per (content_plan, stage) - deliberately separate from the descriptions above (a
-- single mutable value) since this is an ever-growing append-only list. stage stores the same
-- values LifecycleStage already uses for SHOOTING/EDITING/PUBLISHING, reusing that existing enum
-- rather than inventing a parallel one.
CREATE TABLE stage_comments (
    comment_id         UUID PRIMARY KEY,
    content_plan_id     UUID NOT NULL REFERENCES content_plans (content_plan_id),
    stage                VARCHAR(20) NOT NULL CHECK (stage IN ('SHOOTING', 'EDITING', 'PUBLISHING')),
    commenter_user_id    UUID NOT NULL REFERENCES users (user_id),
    comment_text          TEXT NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_stage_comments_plan_stage ON stage_comments (content_plan_id, stage, created_at);

-- Append-only (ERD-CON-058 pattern, ENG-018): no employee - not even the author - can edit or
-- delete a comment; the discussion trail is permanent history, mirroring every other audit-adjacent
-- table this trigger already guards.
CREATE TRIGGER trg_stage_comments_reject_update BEFORE UPDATE ON stage_comments
    FOR EACH ROW EXECUTE FUNCTION trg_reject_update_delete();
CREATE TRIGGER trg_stage_comments_reject_delete BEFORE DELETE ON stage_comments
    FOR EACH ROW EXECUTE FUNCTION trg_reject_update_delete();

-- V13's privilege split (Part B) only granted kcpc_app privileges on tables that existed at that
-- time; this table is new as of V17, so it needs the same append-only grant level (SELECT, INSERT
-- only - matching every other append-only table, not the SELECT/INSERT/UPDATE/DELETE level
-- shooting_assignments/editing_assignments/publishing_assignments get), guarded the same way V13/V15
-- guard themselves (ENG-021: safely no-op where kcpc_app is the same role as the migrator, e.g.
-- local dev/test).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN
        EXECUTE 'GRANT SELECT, INSERT ON stage_comments TO kcpc_app';
    END IF;
END $$;
