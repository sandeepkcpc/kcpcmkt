-- V24: adds PERM_18_SHOOT_EXECUTION and PERM_19_EDIT_EXECUTION to the operational_permissions
-- catalogue (ERD-TBL-004). PERM_04/06 authorize the manager who ASSIGNS a Cameraperson/Editor;
-- they never meant "eligible to execute Shoot/Edit work." PERM_18/19 are the explicit execution
-- permissions, mirroring what PERM_08 already means for Publishing.
--
-- Schema/catalogue only - this migration deliberately does NOT insert any permission_grants rows.
-- Backfilling existing intended Camera Person/Video Editor users happens through the normal
-- audited Permission Administration flow after deployment, not as an automatic data migration.

ALTER TABLE operational_permissions DROP CONSTRAINT ck_operational_permissions_number;
ALTER TABLE operational_permissions ADD CONSTRAINT ck_operational_permissions_number
    CHECK (permission_number BETWEEN 1 AND 19); -- ERD-CON-002, extended for PERM_18/19

INSERT INTO operational_permissions (permission_number, permission_code, permission_name, description) VALUES
    (18, 'PERM_18_SHOOT_EXECUTION', 'Shoot Execution', 'Eligible to be assigned as a Shoot executor and to execute an actively assigned Shoot task.'),
    (19, 'PERM_19_EDIT_EXECUTION', 'Edit Execution', 'Eligible to be assigned as an Edit executor and to execute an actively assigned Edit task.');
