-- ERD-TBL-031 reassignment_assignees: Shoot Stage Reassignment now also reassigns Model(s)/Talent
-- alongside Cameraperson(s) within the same reassignment call. The existing CHECK only allowed
-- CAMERAPERSON/EDITOR for assignee_role - MODEL could never be persisted here. The existing
-- UNIQUE(reassignment_id, user_id, set_side) also omitted assignee_role, so a person who is both a
-- Cameraperson and a Model on the same Content Plan (an already-valid, existing state) would
-- collide across roles within one reassignment. Both are widened; no other column, table, or
-- existing row is touched.
ALTER TABLE reassignment_assignees DROP CONSTRAINT ck_reassignment_assignees_role;
ALTER TABLE reassignment_assignees ADD CONSTRAINT ck_reassignment_assignees_role
    CHECK (assignee_role IN ('CAMERAPERSON', 'EDITOR', 'MODEL'));

ALTER TABLE reassignment_assignees DROP CONSTRAINT uq_reassignment_assignees;
ALTER TABLE reassignment_assignees ADD CONSTRAINT uq_reassignment_assignees
    UNIQUE (reassignment_id, user_id, assignee_role, set_side);
