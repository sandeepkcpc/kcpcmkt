-- V36: Mark Catalogue (ENG-092) - replaces the previously-hardcoded [0, 0.5, 1.0, 2.0, 3.0]
-- Cameraperson/Editor/Model Mark value list with an admin-manageable catalogue table. Seeded with
-- the new allowed set [0, 0.1, 0.5, 1.0] for all three roles.
--
-- The old CHECK constraints on predefined_role_marks/predefined_mark_corrections hardcoded that
-- exact old list - they must be dropped, otherwise 0.1 (and any future admin-added value) would be
-- rejected at the DB layer regardless of what the catalogue allows. mark_catalogue_entries plus
-- application-layer validation (MarkCatalogueService#requireActiveValue) becomes the single source
-- of truth going forward - the same shape Platforms/Channels already use (no CHECK-constrained
-- "allowed name" list, just admin-editable rows + a service-layer duplicate check).

CREATE TABLE mark_catalogue_entries (
    mark_catalogue_entry_id UUID PRIMARY KEY,
    role_type               VARCHAR(20) NOT NULL CHECK (role_type IN ('CAMERAPERSON', 'EDITOR', 'MODEL')),
    mark_value              NUMERIC(3, 1) NOT NULL,
    is_active                BOOLEAN NOT NULL DEFAULT TRUE,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mark_catalogue_entries_role_value UNIQUE (role_type, mark_value)
);

INSERT INTO mark_catalogue_entries (mark_catalogue_entry_id, role_type, mark_value, is_active) VALUES
    ('01926e3e-0092-7000-8000-000000000001', 'CAMERAPERSON', 0.0, TRUE),
    ('01926e3e-0092-7000-8000-000000000002', 'CAMERAPERSON', 0.1, TRUE),
    ('01926e3e-0092-7000-8000-000000000003', 'CAMERAPERSON', 0.5, TRUE),
    ('01926e3e-0092-7000-8000-000000000004', 'CAMERAPERSON', 1.0, TRUE),
    ('01926e3e-0092-7000-8000-000000000005', 'EDITOR', 0.0, TRUE),
    ('01926e3e-0092-7000-8000-000000000006', 'EDITOR', 0.1, TRUE),
    ('01926e3e-0092-7000-8000-000000000007', 'EDITOR', 0.5, TRUE),
    ('01926e3e-0092-7000-8000-000000000008', 'EDITOR', 1.0, TRUE),
    ('01926e3e-0092-7000-8000-000000000009', 'MODEL', 0.0, TRUE),
    ('01926e3e-0092-7000-8000-00000000000a', 'MODEL', 0.1, TRUE),
    ('01926e3e-0092-7000-8000-00000000000b', 'MODEL', 0.5, TRUE),
    ('01926e3e-0092-7000-8000-00000000000c', 'MODEL', 1.0, TRUE);

ALTER TABLE predefined_role_marks DROP CONSTRAINT ck_predefined_role_marks_cameraperson;
ALTER TABLE predefined_role_marks DROP CONSTRAINT ck_predefined_role_marks_editor;
ALTER TABLE predefined_role_marks DROP CONSTRAINT ck_predefined_role_marks_model;
ALTER TABLE predefined_mark_corrections DROP CONSTRAINT ck_predefined_mark_corrections_new_camera;
ALTER TABLE predefined_mark_corrections DROP CONSTRAINT ck_predefined_mark_corrections_new_editor;
ALTER TABLE predefined_mark_corrections DROP CONSTRAINT ck_predefined_mark_corrections_new_model;
