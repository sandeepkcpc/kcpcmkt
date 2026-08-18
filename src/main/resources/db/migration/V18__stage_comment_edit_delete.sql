-- ENG-050: comment authors can now edit/soft-delete their OWN comments (previously fully
-- append-only per ENG-046/ERD-CON-058's pattern) - explicit later user request, deliberately
-- narrower than a full reversal: hard delete stays forbidden (trg_stage_comments_reject_delete is
-- untouched) and every edit/delete is still permanently recorded via the existing generic audit log
-- (AuditService), the same "old value -> new value" pattern ENG-048 used for Shoot Instructions
-- edits - so no history is actually lost even though the row itself becomes mutable.
DROP TRIGGER trg_stage_comments_reject_update ON stage_comments;

ALTER TABLE stage_comments ADD COLUMN edited_at TIMESTAMPTZ;
ALTER TABLE stage_comments ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE stage_comments ADD COLUMN deleted_at TIMESTAMPTZ;

-- kcpc_app needs UPDATE now (edit text + the two new columns); DELETE is still never granted -
-- matches keeping the DELETE-reject trigger, same no-op-safe guard V13/V15/V17 already use.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN
        EXECUTE 'GRANT UPDATE ON stage_comments TO kcpc_app';
    END IF;
END $$;
