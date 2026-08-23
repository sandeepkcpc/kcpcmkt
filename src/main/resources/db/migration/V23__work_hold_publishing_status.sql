-- BR-063 Hold/Resume extended to Publishing (PUBG) - the DB-level guard (ERD-CON-061,
-- V9__shooting_editing_marks_hold.sql) must allow the same three in-progress execution stages the
-- application layer (WorkHoldRecord/HoldService) now permits: Shoot In Progress, Editing, or
-- Publishing. Recreated rather than altered in place since Postgres has no ALTER CONSTRAINT for
-- CHECK clauses.
ALTER TABLE work_hold_records DROP CONSTRAINT ck_work_hold_records_status;
ALTER TABLE work_hold_records ADD CONSTRAINT ck_work_hold_records_status CHECK (held_status_code IN ('SIP', 'ED', 'PUBG'));
