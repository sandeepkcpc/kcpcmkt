# KPI Dashboard Redesign — Open Questions (Phase 1 Capability Audit)

Source request: `Claude Prompt — Transform Reports → KPI Dashboard into Stakeholder Analytics Dashboard.md`

This file lists every ambiguity found while auditing whether the current KCPC workflow system has
enough reliable data to calculate each proposed KPI. No implementation has started. Answer inline
(replace `_(your answer)_` under each question) and hand the file back to continue to Phase 2.

---

## Q1 — On-Time Delivery / Delayed Deliverables SLA baseline

**Data available:** `ContentPlan.plannedShootDate` / `plannedEditDate` / `plannedLiveDate` are
mutable columns. An approved Reschedule (`RescheduleRecord`) overwrites them directly and only
keeps the prior/new values as a separate audit trail.

**Ambiguity:** The *existing* KPI-002 (Delayed Work) and KPI-030 (On-Time Delivery Rate) already
compare against whichever planned date is *currently* on the row — so an approved reschedule
silently resets the SLA clock today.

**Options:**
- **A.** Keep this behavior — always compare against the current approved planned date (what the
  system already does).
- **B.** Compare against the *original* pre-reschedule baseline date instead (would require
  walking `RescheduleRecord` history for every plan).

**Your answer:** _(Option A / Option B)_

---

## Q2 — On-Time/SLA treatment of reopened & repost cycles

**Ambiguity:** Once a Completed deliverable is reopened for Publishing and reposted, is the
repost's timeline compared against `plannedLiveDate` again (the same, now-stale column), a new
target date (none currently exists for a repost cycle), or excluded from On-Time/Delay entirely?
None of these is implemented today.

**Your answer:** _(compare against plannedLiveDate again / needs a new target date field / exclude repost cycles from SLA KPIs entirely / other — describe)_

---

## Q3 — Idea Rejection Rate / Idea→Publish funnel and Retained ideas

**Data available:** Idea Review decisions are three-way: `APPROVED` / `REJECTED` / `RETAINED`.

**Finding:** The *existing* Idea Approval Rate formula already excludes Retained from the
denominator entirely (`approved / (approved + rejected)`).

**Ambiguity:** Should the new Rejection Rate / funnel keep excluding Retained ideas (as today), or
should Retained show as its own third funnel branch (and/or eventually convert into
Approved-or-Rejected once reopened)?

**Your answer:** _(keep excluding Retained / show Retained as a separate branch / other — describe)_

---

## Q4 — Evidence Correction Rate denominator

**Data available:** `PublicationEvidenceCorrection` records exist and can be counted.

**Ambiguity:** A *rate* needs a denominator. Options:
- Corrections ÷ total ORIGINAL publication events
- Corrections ÷ all publication events (ORIGINAL + REPOST)
- Corrections ÷ total published Content IDs
- No rate — show only the raw correction count (safest default if left unanswered)

**Your answer:** _(pick one, or confirm "raw count only, no rate")_

---

## Q5 — What do "Avg Engagement %" and "Avg Reach" actually map to?

**Critical finding:** The real scorecard entity (`CreativePerformanceScorecard`) has **no field
literally called Engagement or Reach**. It stores: `views3sec`, `plays`, `averageWatchTimeSeconds`,
`linkClicks`, `impressions`, and three derived rates: `hookRatePercent`, `holdRatePercent`,
`ctrPercent`.

**I will not invent a mapping for a metric name that doesn't exist in the schema.**

**Your answer — map each reference-mockup label to an existing field:**
- "Avg Engagement %" → _(hookRatePercent / holdRatePercent / ctrPercent / a new combination — describe)_
- "Avg Reach" → _(views3sec / plays / impressions / not trackable today, remove this card — describe)_

---

## Q6 — Cross-platform / cross-metric comparability (N/A handling)

**Finding:** Every scorecard metric has its own `IsNa` flag (`views3secIsNa`, `watchTimeIsNa`,
`videoLengthIsNa`, `clicksIsNa`) — the system already tracks that some metrics are legitimately not
applicable for a given platform/output combination.

**My default assumption (please confirm or override):** N/A-flagged values are excluded from every
average — never treated as zero, never silently included.

**Your answer:** _(confirm default / describe a different treatment)_

---

## Q7 — Ranking metric for "Top Performing Content Type" / "Top Platform" / "Top Channel"

**Finding:** No governed ranking metric exists anywhere in the codebase today — not CTR, not views,
not engagement, nothing is currently designated as "the" ranking criterion.

**Your answer:** _(views / CTR / the "Engagement %" field resolved in Q5 / published volume / other — describe)_

---

## Q8 — On-Time Delivery denominator scope for the date-range filter

**Finding:** The existing KPI-030 denominator is *every* ORIGINAL event ever recorded, with no
date-range filter applied even when the dashboard has one selected elsewhere.

**Ambiguity:** For the new date-range-scoped dashboard, should the denominator be:
- **A.** ORIGINAL events actually published within the selected date range, or
- **B.** All Content whose *planned* live date falls within the selected date range (these can
  produce different populations — e.g. a plan delayed past its planned date but published inside
  the window would appear in A but count as "not yet published in-window" logic under B).

**Your answer:** _(Option A / Option B)_

---

## Summary of defaults I will use if a question is left unanswered

| # | Default if unanswered |
|---|---|
| Q1 | Option A (current approved date, matches existing KPI-002/030 behavior) |
| Q2 | Exclude reopened/repost cycles from On-Time/SLA KPIs entirely |
| Q3 | Keep excluding Retained ideas (matches existing Idea Approval Rate formula) |
| Q4 | Raw count only, no rate |
| Q5 | **No default — I will not implement these two cards until answered** |
| Q6 | Exclude N/A-flagged values from every average |
| Q7 | **No default — I will not implement Top Performing/Platform/Channel ranking until answered** |
| Q8 | Option A (published-in-range) |

Once you've answered (or accepted the defaults), send this file back and I'll produce the Phase 2
confirmed KPI specification.
