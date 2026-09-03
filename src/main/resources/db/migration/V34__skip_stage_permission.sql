-- V34: adds PERM_20_SKIP_STAGE to the operational_permissions catalogue (ERD-TBL-004). Grants a
-- CEO/MM the ability to move a Content Plan directly to the next stage (Shoot -> Edit Assigned,
-- Edit -> Ready for Publishing) without the normal execution/review cycle, while still collecting
-- the required next-stage team assignment. Granted per LifecycleStage exactly like every other
-- operational permission - SHOOTING scope authorizes Skip Shoot, EDITING scope authorizes Skip
-- Edit. Deliberately distinct from PERM_05/PERM_07 (Review authority does not imply Skip authority).
--
-- Schema/catalogue only - this migration deliberately does NOT insert any permission_grants rows.
-- Granting it to specific users happens through the normal audited Permission Administration flow
-- after deployment, not as an automatic data migration.

ALTER TABLE operational_permissions DROP CONSTRAINT ck_operational_permissions_number;
ALTER TABLE operational_permissions ADD CONSTRAINT ck_operational_permissions_number
    CHECK (permission_number BETWEEN 1 AND 20); -- ERD-CON-002, extended for PERM_20

INSERT INTO operational_permissions (permission_number, permission_code, permission_name, description) VALUES
    (20, 'PERM_20_SKIP_STAGE', 'Skip Stage', 'Eligible to skip the current Shoot or Edit stage and move the Content Plan directly to the next stage, collecting the required next-stage team assignment in the same action.');
