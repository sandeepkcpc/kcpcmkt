-- ENG-067: "My Shoots" (Model employee screen) needs a reliable way to find which shoots a given
-- Model User is part of. content_plan_talent_entries.talent_name was free text only (ERD-TBL-041),
-- with no link back to the Model User the Planning UI's picker actually selected - added here as a
-- nullable FK (nullable because the frozen REST API contract, API-OP-017/018, still accepts plain
-- talent name strings with no user behind them; the MVC Model(s) picker always supplies a real
-- User and populates this column going forward).
ALTER TABLE content_plan_talent_entries ADD COLUMN talent_user_id UUID REFERENCES users (user_id);

CREATE INDEX ix_content_plan_talent_entries_talent_user ON content_plan_talent_entries (talent_user_id);
