-- ERD-TBL-011 planned_outputs: add reel_group_id so multiple Reel Types (VERY_SHORT/SHORT/LONG)
-- selected together for one REEL "+ Add Output" submission stay linked as one logical group.
-- Each Reel Type remains its own independent Planned Output row (lifecycle/status/KPI tracking
-- stays per-row), but every row in a group shares one common Publication Target set - see
-- PlanningService.mapPublicationScope/unmapPublicationTarget, which propagate to every Planned
-- Output sharing a reel_group_id rather than mapping targets per individual output.
--
-- Every existing/non-grouped Planned Output backfills to its own id (a "group of one"), so the
-- column is NOT NULL immediately with no nullable transition period.
ALTER TABLE planned_outputs ADD COLUMN reel_group_id UUID;
UPDATE planned_outputs SET reel_group_id = planned_output_id WHERE reel_group_id IS NULL;
ALTER TABLE planned_outputs ALTER COLUMN reel_group_id SET NOT NULL;

CREATE INDEX ix_planned_outputs_reel_group ON planned_outputs (reel_group_id);
