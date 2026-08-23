-- BR-063 Hold/Resume: an optional, purely informational Expected Resume Date on a hold record.
-- Never read by any workflow/delay rule - a manager who wants to actually move a planned date
-- still uses the existing Reschedule governed action; this is display-only context for "when do
-- we expect this to come back."
ALTER TABLE work_hold_records ADD COLUMN expected_resume_date DATE;
