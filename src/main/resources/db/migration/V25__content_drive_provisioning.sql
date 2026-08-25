-- Google Drive folder provisioning: one row per content_plans row, tracking the real Drive
-- folder IDs (never just a display URL - urls are derived from these IDs, never stored as the
-- canonical identifier) for the governed 3-subfolder structure created under each Content ID's
-- own root folder:
--   CONTENT_ID/
--     01 - Raw Shoot
--     02 - Edit
--     03 - Final Content
--
-- content_plans.folder_link (V7) stays as the existing free-text override field - Folder Link
-- Management (PERM_13) can still paste/replace it manually for repair/relinking. Once automatic
-- provisioning succeeds, the application also syncs folder_link to the provisioned root folder's
-- URL so every existing "Drive Link" display keeps working unchanged; this table is the sole
-- source of truth for the actual folder IDs and provisioning status.
--
-- status transitions: NOT_STARTED -> IN_PROGRESS -> SUCCEEDED | FAILED. Retry is only permitted
-- from NOT_STARTED/FAILED, and moves to IN_PROGRESS (committed) before any Drive API call, so a
-- concurrent second retry attempt sees IN_PROGRESS and refuses rather than double-provisioning.
-- Each folder id is persisted as soon as that individual folder is created (root, then each
-- child) - never batched - so a mid-provisioning failure leaves this row accurately reflecting
-- exactly which folders already exist, and retry never re-creates them (idempotent).

CREATE TABLE content_drive_provisioning (
    provisioning_id           UUID PRIMARY KEY,
    content_plan_id           UUID NOT NULL UNIQUE REFERENCES content_plans (content_plan_id),
    status                    VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED',
    root_folder_id             VARCHAR(128),
    raw_shoot_folder_id        VARCHAR(128),
    edit_folder_id             VARCHAR(128),
    final_content_folder_id    VARCHAR(128),
    last_error                 TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_content_drive_provisioning_status
        CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_content_drive_provisioning_status ON content_drive_provisioning (status);

-- V13's privilege split (Part B) only granted the restricted kcpc_app role privileges on tables
-- that existed at that time; this table is new, so it needs the same non-append-only grant
-- (SELECT/INSERT/UPDATE/DELETE), guarded the same way V13/V15 guard themselves (safely no-op
-- where kcpc_app is the same role as the migrator, e.g. local dev/test).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON content_drive_provisioning TO kcpc_app';
    END IF;
END $$;
