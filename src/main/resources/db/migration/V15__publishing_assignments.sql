-- publishing_assignments: multi-Publisher assignment tracking for the Publishing stage, mirroring
-- ERD-TBL-013 shooting_assignments / ERD-TBL-014 editing_assignments. Not present in the frozen ERD -
-- added per docs/IMPLEMENTATION_DECISIONS.md ENG-033 to give Publishing Assignment the same Model(s)-style
-- chip-picker UX as Shoot/Edit Assignment. Purely additive tracking: does not gate Start Publishing, which
-- remains governed solely by PERM_08_PUBLISHING_EXECUTION.

CREATE TABLE publishing_assignments (
    assignment_id       UUID PRIMARY KEY,
    content_plan_id      UUID NOT NULL REFERENCES content_plans (content_plan_id),
    publisher_user_id     UUID NOT NULL REFERENCES users (user_id),
    assigned_by_user_id    UUID NOT NULL REFERENCES users (user_id),
    assigned_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    ended_at                  TIMESTAMPTZ
);

CREATE INDEX ix_publishing_assignments_plan ON publishing_assignments (content_plan_id);
CREATE INDEX ix_publishing_assignments_active ON publishing_assignments (content_plan_id) WHERE is_active = TRUE;

-- V13's privilege split (Part B) only granted the restricted kcpc_app role privileges on tables that
-- existed at that time; this table is new as of V15, so it needs the same non-append-only grant
-- (SELECT/INSERT/UPDATE/DELETE, matching shooting_assignments/editing_assignments) applied here,
-- guarded the same way V13 guards itself (ENG-021: safely no-op where kcpc_app is the same role as the
-- migrator, e.g. local dev/test).
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kcpc_app')
       AND to_regrole('kcpc_app') IS DISTINCT FROM to_regrole(current_user) THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON publishing_assignments TO kcpc_app';
    END IF;
END $$;
