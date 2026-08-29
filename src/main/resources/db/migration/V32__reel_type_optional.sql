-- Reel Type is no longer mandatory for a REEL output (ERD-CON-008's "mandatory when REEL" half is
-- removed by business decision; a REEL output with no Reel Type is now valid). The reverse half -
-- Reel Type must stay NULL for every non-REEL output type - is unchanged.
ALTER TABLE planned_outputs DROP CONSTRAINT ck_planned_outputs_reel_type;

ALTER TABLE planned_outputs
    ADD CONSTRAINT ck_planned_outputs_reel_type
        CHECK (output_type = 'REEL' OR reel_type IS NULL);
