-- V26: Performance tracking becomes Meta-only (Instagram/Facebook), direct-entry metrics from Meta
-- Ads Manager, replacing the old 6-field/derived-Hook/Hold/CTR model for NEW scorecards.
--
-- Purely additive - per the approved migration strategy, nothing is dropped or renamed:
--   * All 6 legacy metric columns (views_3sec, plays, average_watch_time_seconds,
--     video_length_seconds, link_clicks, impressions) and their derived hook_rate_percent/
--     hold_rate_percent/ctr_percent stay exactly as they are, forever readable for historical rows.
--   * New "meta_*" columns hold the new direct-entry model. Reusing the old hook_rate_percent/
--     hold_rate_percent column names for direct entry would make the same column mean "derived"
--     for old rows and "directly entered" for new rows - kept separate instead so provenance is
--     never ambiguous.
--   * uses_meta_metric_model discriminates which model a given scorecard row actually uses -
--     explicit, not inferred from which columns happen to be null (a legitimately-all-N/A legacy
--     scorecard would otherwise be indistinguishable from a not-yet-started new one). Existing rows
--     backfill to FALSE (legacy); every scorecard created after this migration is constructed with
--     it TRUE (CreativePerformanceScorecard's single constructor - see that class).
--
-- Business rule (approved): Views is always required for any eligible Meta record - no N/A flag.
-- Hook Rate / Hold Rate / Average View Duration are video-specific Meta metrics - N/A-eligible for
-- non-video (PHOTOGRAPHY) outputs, matching the existing per-field N/A pattern already used by
-- views_3sec/watch_time/video_length/clicks on this same table.

ALTER TABLE creative_performance_scorecards
    ADD COLUMN uses_meta_metric_model BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN meta_hook_rate_percent NUMERIC(5, 2),
    ADD COLUMN meta_hook_rate_is_na BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN meta_hold_rate_percent NUMERIC(5, 2),
    ADD COLUMN meta_hold_rate_is_na BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN meta_views BIGINT,
    ADD COLUMN meta_average_view_duration_seconds NUMERIC(8, 2),
    ADD COLUMN meta_avg_view_duration_is_na BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE performance_metric_corrections
    ADD COLUMN prior_meta_hook_rate NUMERIC(5, 2),
    ADD COLUMN new_meta_hook_rate NUMERIC(5, 2),
    ADD COLUMN prior_meta_hook_rate_is_na BOOLEAN,
    ADD COLUMN new_meta_hook_rate_is_na BOOLEAN,
    ADD COLUMN prior_meta_hold_rate NUMERIC(5, 2),
    ADD COLUMN new_meta_hold_rate NUMERIC(5, 2),
    ADD COLUMN prior_meta_hold_rate_is_na BOOLEAN,
    ADD COLUMN new_meta_hold_rate_is_na BOOLEAN,
    ADD COLUMN prior_meta_views BIGINT,
    ADD COLUMN new_meta_views BIGINT,
    ADD COLUMN prior_meta_avg_view_duration NUMERIC(8, 2),
    ADD COLUMN new_meta_avg_view_duration NUMERIC(8, 2),
    ADD COLUMN prior_meta_avg_view_duration_is_na BOOLEAN,
    ADD COLUMN new_meta_avg_view_duration_is_na BOOLEAN;

-- No new privilege grant needed (unlike V25, which added a brand-new table) - these are ALTER
-- TABLE ADD COLUMN statements against tables V13 already granted kcpc_app SELECT/INSERT/UPDATE on.
