-- V29: Model Mark - a third governed predefined mark alongside Cameraperson/Editor, set at Idea
-- Review approval time (same controlled list [0, 0.5, 1.0, 2.0, 3.0], ERD-CON-010) and attributed
-- immediately to every selected Model/Talent (there is no future "Model Review" gate the way
-- Shoot/Edit Review exist - Models are already fully resolved at approval time, so unlike
-- Cameraperson/Editor marks there is nothing to defer attribution until).

ALTER TABLE predefined_role_marks
    ADD COLUMN predefined_model_mark NUMERIC(3, 1) NOT NULL DEFAULT 0.0;
ALTER TABLE predefined_role_marks ALTER COLUMN predefined_model_mark DROP DEFAULT;
ALTER TABLE predefined_role_marks
    ADD CONSTRAINT ck_predefined_role_marks_model
        CHECK (predefined_model_mark IN (0.0, 0.5, 1.0, 2.0, 3.0));

ALTER TABLE personal_mark_attributions DROP CONSTRAINT ck_personal_mark_attributions_role;
ALTER TABLE personal_mark_attributions
    ADD CONSTRAINT ck_personal_mark_attributions_role CHECK (role_type IN ('CAMERAPERSON', 'EDITOR', 'MODEL'));

-- predefined_mark_corrections (ERD-TBL-026): the correction ledger mirrors the same
-- prior/new pair per role, so Model Mark corrections are auditable exactly like Cameraperson/Editor.
ALTER TABLE predefined_mark_corrections
    ADD COLUMN prior_model_mark NUMERIC(3, 1) NOT NULL DEFAULT 0.0;
ALTER TABLE predefined_mark_corrections ALTER COLUMN prior_model_mark DROP DEFAULT;
ALTER TABLE predefined_mark_corrections
    ADD COLUMN new_model_mark NUMERIC(3, 1) NOT NULL DEFAULT 0.0;
ALTER TABLE predefined_mark_corrections ALTER COLUMN new_model_mark DROP DEFAULT;
ALTER TABLE predefined_mark_corrections
    ADD CONSTRAINT ck_predefined_mark_corrections_new_model
        CHECK (new_model_mark IN (0.0, 0.5, 1.0, 2.0, 3.0));
