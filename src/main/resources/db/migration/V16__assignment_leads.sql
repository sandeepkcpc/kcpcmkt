-- Team Lead for Shoot/Edit Assignment (not Publishing - see docs/IMPLEMENTATION_DECISIONS.md
-- ENG-036). One additional boolean per assignment row rather than a separate lead_user_id column:
-- the Lead is simply the currently-active assignment row flagged is_lead = TRUE, so removing that
-- assignee (which sets is_active = FALSE) automatically stops them being the Lead with no extra
-- cascade code, and re-adding someone starts a fresh, non-lead row. The partial unique indexes
-- enforce "at most one Lead" at the DB level, matching this schema's existing defensive-constraint
-- style (e.g. the active-assignment partial indexes already on both tables).

ALTER TABLE shooting_assignments ADD COLUMN is_lead BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE editing_assignments ADD COLUMN is_lead BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX ux_shooting_assignments_one_lead ON shooting_assignments (content_plan_id)
    WHERE is_lead = TRUE AND is_active = TRUE;
CREATE UNIQUE INDEX ux_editing_assignments_one_lead ON editing_assignments (content_plan_id)
    WHERE is_lead = TRUE AND is_active = TRUE;
