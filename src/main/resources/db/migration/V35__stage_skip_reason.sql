-- V35: adds shoot_stage_skip_reason/edit_stage_skip_reason to content_plans (ENG-091, Stages at
-- Idea Approval). Non-null only when the Idea Review "Stages" selection excluded that stage from
-- the pipeline (Direct Edit / Direct Publishing) - the stage's WorkflowStatus was never entered at
-- all, so this is deliberately a plain nullable note, not a fake workflow_transition_history row
-- (that table is append-only/audit-grade and records only real transitions that occurred).

ALTER TABLE content_plans ADD COLUMN shoot_stage_skip_reason TEXT;
ALTER TABLE content_plans ADD COLUMN edit_stage_skip_reason TEXT;
