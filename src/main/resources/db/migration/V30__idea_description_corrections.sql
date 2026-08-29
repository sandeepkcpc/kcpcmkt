-- V30: append-only correction ledger for Idea Description (ideas.notes_remarks) edits, same shape
-- as predefined_mark_corrections/publication_evidence_corrections (V12). CEO/Marketing Manager
-- (native authority only - see AuthorizationService#requireNativeAuthority) may edit the
-- Description/Details on an already-submitted idea; the originally submitted text is never lost -
-- every prior/new pair is preserved here with a mandatory reason recording who changed it and why.

CREATE TABLE idea_description_corrections (
    correction_id               UUID PRIMARY KEY,
    idea_id                      UUID NOT NULL REFERENCES ideas (idea_id),
    supersedes_correction_id      UUID REFERENCES idea_description_corrections (correction_id),
    prior_description               TEXT,
    new_description                   TEXT,
    correction_reason                   TEXT NOT NULL,
    corrected_by_user_id                  UUID NOT NULL REFERENCES users (user_id),
    corrected_at                            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acting_permission_grant_id                UUID REFERENCES permission_grants (grant_id),
    CONSTRAINT ck_idea_description_corrections_reason CHECK (length(trim(correction_reason)) > 0)
);

CREATE INDEX ix_idea_description_corrections_idea ON idea_description_corrections (idea_id);

-- Same-parent supersession chain, mirroring trg_predefined_mark_corrections_same_parent (V12).
CREATE OR REPLACE FUNCTION trg_idea_description_corrections_same_parent() RETURNS trigger AS $$
BEGIN
    IF NEW.supersedes_correction_id IS NOT NULL THEN
        PERFORM 1 FROM idea_description_corrections
            WHERE correction_id = NEW.supersedes_correction_id AND idea_id = NEW.idea_id;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'supersedes_correction_id must belong to the same idea_id';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_idea_description_corrections_same_parent
    BEFORE INSERT ON idea_description_corrections
    FOR EACH ROW EXECUTE FUNCTION trg_idea_description_corrections_same_parent();

-- Append-only enforcement: reuse the shared trg_reject_update_delete/trg_reject_truncate
-- functions already defined by V12/V13 for every other correction ledger.
CREATE TRIGGER trg_idea_description_corrections_reject_update
    BEFORE UPDATE ON idea_description_corrections
    FOR EACH ROW EXECUTE FUNCTION trg_reject_update_delete();

CREATE TRIGGER trg_idea_description_corrections_reject_delete
    BEFORE DELETE ON idea_description_corrections
    FOR EACH ROW EXECUTE FUNCTION trg_reject_update_delete();

CREATE TRIGGER trg_idea_description_corrections_reject_truncate
    BEFORE TRUNCATE ON idea_description_corrections
    FOR EACH STATEMENT EXECUTE FUNCTION trg_reject_truncate();

-- kcpc_app privilege split (V13 Part B pattern): SELECT/INSERT only, no UPDATE/DELETE - a no-op
-- in today's single-role local dev/test setup, same guard V13 itself uses.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN
        EXECUTE 'GRANT SELECT, INSERT ON idea_description_corrections TO kcpc_app';
    END IF;
END $$;
