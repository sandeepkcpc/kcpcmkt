-- V31: Planned Output types redesigned (ERD-CON-007) - PHOTOGRAPHY/VIDEO retired, replaced by
-- STORY/POST/LONG_VIDEO (REEL unchanged: Story/Post are short-form image-or-carousel content,
-- Reel stays short-form vertical video, Long Video is long-form/horizontal video). Existing
-- test/dev rows are remapped to the closest equivalent new type (PHOTOGRAPHY -> POST, the closest
-- "static/image" replacement; VIDEO -> LONG_VIDEO, the closest "long-form video" replacement)
-- rather than left violating the new CHECK constraint - this is a pre-launch fresh deployment (see
-- V1/V7's own "no historical Planning data to preserve" precedent), not a real-data migration.
ALTER TABLE planned_outputs DROP CONSTRAINT ck_planned_outputs_type;

UPDATE planned_outputs SET output_type = 'POST' WHERE output_type = 'PHOTOGRAPHY';
UPDATE planned_outputs SET output_type = 'LONG_VIDEO' WHERE output_type = 'VIDEO';

ALTER TABLE planned_outputs
    ADD CONSTRAINT ck_planned_outputs_type CHECK (output_type IN ('STORY', 'POST', 'REEL', 'LONG_VIDEO'));
