-- V37: Category Catalogue (ENG-094) - replaces the previously-unconstrained free-text Planning
-- Category field with an admin-manageable catalogue table, matching Mark Catalogue's shape
-- (V36): categories.name is a plain string, never a FK target from content_plans.category_text -
-- so renaming/deactivating/deleting a catalogue entry never touches an already-recorded Content
-- Plan's category.
--
-- "N/A" is seeded as the one permanent default category (is_default = TRUE) - CategoryService
-- refuses to rename, deactivate, or delete it, so Planning always has a "no specific category"
-- option available.

CREATE TABLE categories (
    category_id UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

INSERT INTO categories (category_id, name, is_active, is_default) VALUES
    ('01926e3e-0094-7000-8000-000000000001', 'N/A', TRUE, TRUE);
