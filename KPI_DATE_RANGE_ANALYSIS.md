# KPI Dashboard Date Range Analysis

## 1. Purpose

This document was created to build a complete, verified source of truth for how the KPI
Dashboard's ("Reports → KPI Dashboard") Date Range selector actually behaves today, across all
five tabs, before any change to date-filtering logic is designed or implemented. It is an
**investigation and business-rule analysis only**. No application source code, tests, database
content, configuration, migrations, UI, or deployment files were modified to produce it. Every
technical claim below was verified by reading the current implementation directly (primarily
`KpiDashboardService.java`, its DTOs, its controller, its JSPs, `StageDelayPolicy`,
`PipelineDashboardService`, `ContentPlan`, and `IdeaService`) — not inferred from naming
conventions, not carried over from a prior design discussion, and not assumed from
`APPLICATION_BASELINE.md` without cross-checking the code it summarizes.

Section 4's "Applicable Workflow Start Date → Planned Live Date" rule and section 14's test
matrix analyze a **proposed, not-yet-implemented** rule. Nothing in this document should be read
as already built. Where the current data model or the proposed rule leaves a genuine ambiguity,
this document says so explicitly rather than resolving it — see the `BUSINESS CONFIRMATION
REQUIRED` markers throughout, and the category-C list in §12.

## 2. Current KPI Dashboard Structure

Route: `GET /app/reports/kpis` (`ReportingMvcController.kpiDashboard`), gated by
`PERM_15_TEAM_KPI_VIEW`. `view` query param selects the tab (`overview` default,
`workflow`/`content`/`quality`/`performance`); each renders the corresponding fragment inside
`reports-kpi-console.jsp` / `reports-kpi-console-content.jsp`.

### 1. Overview (`fragments/reports-kpi-overview.jspf`)
Redesigned (ENG-097) to show exactly 3 blocks — verified directly against the current JSP:
- **Current Work Ownership** — table: Employee, Role, Assigned Pending, Assigned Delayed, Oldest
  Delay, View (→ read-only drill-down).
- **Upcoming Channel Plan** — date-grouped list: Planned Live Date → Channel handle → count.
- **Idea → Publish Funnel** — Submitted → Approved (Retained X / Rejected Y) → Planned →
  Published, plus Approval Rate.

The prior 8 KPI cards (Active WIP, Delayed Deliverables, On-Time Delivery, Published Content, Avg
End-to-End Cycle Time, Rework Rate (All Stages), Pending Reviews, Performance Overdue) plus Stage
Bottleneck Summary and Attention Needed are **no longer rendered** on this tab. Their calculations
still exist in `KpiDashboardService.overview()` and are still returned on `OverviewDashboardDto`
(verified: the constructor call passes all 11 pre-existing fields through unchanged, plus 2 new
ones) — only this one JSP's presentation changed.

### 2. Workflow & SLA (`fragments/reports-kpi-workflow.jspf`)
- Stage Health table (Shoot/Edit/Publishing/Performance × Active/Delayed/Avg Age/Oldest Item)
- Within SLA % by Stage
- Shoot-to-Publish Cycle Time
- End-to-End Cycle Time
- On-Time Delivery %
- Average Delay
- On Hold Count
- Delay Aging (buckets: 0-2 / 3-5 / 6-10 / 11+ days)
- On Hold Summary
- Reopen / Repost Summary

### 3. Content & Publishing (`fragments/reports-kpi-content.jspf`)
- Published Content
- Target Completion %
- ORIGINAL / REPOST counts
- Evidence Correction Records
- Planned Output Mix
- Platform Distribution
- Channel Distribution (Top 5)
- Planned vs Published Targets

### 4. Quality & Reviews (`fragments/reports-kpi-quality.jspf`)
- First-Pass Approval Rate
- Overall Rework Rate
- Avg Review Turnaround
- Pending Reviews
- Rework Rate by Stage
- Stage-wise Review Performance
- Idea Rejection Rate
- Evidence Corrections

### 5. Performance (`fragments/reports-kpi-performance.jspf`)
- Performance Pending
- Performance Overdue
- Scorecard Completion %
- Avg Delay in Reporting
- Top Content Type by Avg Hook Rate
- Top Platform by Avg Hook Rate
- Top Channel by Avg Hook Rate
- ORIGINAL vs REPOST · Avg Hook Rate %
- ORIGINAL vs REPOST · Avg Views

**Important distinction verified**: this "Performance" tab (`KpiDashboardService.performance()`)
is a separate, CEO-facing aggregate screen from the employee-facing `/app/my-performance` page
(`LandingMvcController`, uses `Completed On`/`withinDateRange(getCompletedOn(), ...)`). They are
**not** the same feature and do not necessarily share a date rule — see §9.

## 3. Current Date-Range Implementation

All date-ranged tabs receive `startDate`/`endDate` from `ReportingMvcController.kpiDashboard()`
(`@RequestParam LocalDate startDate/endDate`, defaulting to `today.minusDays(29)` → `today` if
either is absent, `today = LocalDate.now(Asia/Kolkata)`), then call
`KpiDashboardService.buildContext(startDate, endDate)`, which produces a `DashboardContext` whose
`rangeStart`/`rangeEnd` are used by nearly every calculation. A handful of metrics deliberately do
**not** use `rangeStart`/`rangeEnd` at all — flagged explicitly below.

Every KPI's numerator/denominator SQL was read directly from `KpiDashboardService.java`; none of
this section is inferred.

### Overview tab

| KPI | Date filter applied? | Date field | Entity/Table/Column | Calculation |
|---|---|---|---|---|
| Active WIP | **No** (current-state) | — | `ContentPlan` via `WorkflowStatus` | `ctx.activePlans.size()` — every plan not in COMP/CAN/RJ, regardless of range |
| Delayed Deliverables | **No** (current-state) | — | `ContentPlan.plannedShootDate/plannedEditDate/plannedLiveDate` via `StageDelayPolicy.currentApprovedTarget` | count of `ctx.activePlans` whose current-stage target date is before **today** (not compared to the range at all) |
| On-Time Delivery (Publishing) | **Yes**, historical | Publishing cycle deadline (effective Planned Live Date per cycle) | `content_plans.planned_live_date`, `reopen_records`, `reschedule_records` | eligible = cycles whose deadline falls in `[rangeStart, min(rangeEnd, today)]`; on-time = `PUBLICATION_SCOPE_RESOLVED` transition ≤ deadline |
| Published Content | **Yes**, historical | `actual_publication_events.actual_publication_timestamp` | `ActualPublicationEvent` | distinct Content Plans with an `ORIGINAL` event whose timestamp falls in range |
| Avg End-to-End Cycle Time | **Yes**, historical | `PUBLICATION_SCOPE_RESOLVED` transition timestamp (original cycle) | `workflow_transition_history` | avg(days) from `Idea.submittedAt` to that transition, for transitions falling in range |
| Rework Rate (All Stages) | **Yes**, historical | `review_cycles.decided_at` | `ReviewCycle` | rework decisions / all decided Shoot+Edit reviews, `decided_at::date` in range |
| Pending Reviews | **No** (current-state) | — | `review_cycles.decided_at IS NULL` | plain count, no date filter of any kind — comment in code: *"Always a current-state snapshot ... never date-ranged"* |
| Performance Overdue | **No** (compared to today, not range) | — | `performance_obligations.performance_due_date` vs `today` | count where `is_completed = false and performance_due_date < today` (Instagram/Facebook-eligible only) |
| Stage Bottleneck Summary (stageHealth) | **No** (current-state, age uses "now") | — | same as Active WIP/Delayed, plus `mostRecentEntryIntoCurrentStatus` (transition timestamp) | grouped by current stage, not range-filtered |
| Attention Needed | **No** (current-state, plus a fixed 2-day cutoff independent of the range) | — | `review_cycles.submitted_at`, `work_hold_records`, `content_plans` | mixed current-state signals |
| **Current Work Ownership** | **No** (current-state) — see §8 | — | `ShootingAssignment`/`EditingAssignment`/`PublishingAssignment` (`is_active=true`) + `WorkflowStatus` | see §8 |
| **Upcoming Channel Plan** | **No** (current-state) — see §8 | — | `PlannedOutputPublicationTargetMapping`, `ContentPlan.plannedLiveDate`, `ActualPublicationEvent` | see §8 |
| **Idea → Publish Funnel** | **Yes**, historical — see §8 | `ideas.submitted_at` | `Idea` | see §8 |

### Workflow & SLA tab

| KPI | Date filter applied? | Date field | Calculation |
|---|---|---|---|
| Stage Health | No (current-state, same as Overview's) | — | shared `stageHealth(ctx)` |
| Shoot-to-Publish Cycle Time | Yes, historical | `actual_publication_events.actual_publication_timestamp` (last ORIGINAL event) | avg(days) from first `SAP` transition to last ORIGINAL publish, filtered by the publish timestamp falling in range |
| End-to-End Cycle Time | Yes, historical | same as Overview's Avg End-to-End | shared `avgEndToEndCycleTimeDays(ctx)` |
| On-Time Delivery % | Yes, historical | same as Overview's On-Time Delivery | shared `onTimeDelivery(ctx)` |
| Average Delay | No (current-state; computed over `ctx.activePlans` filtered by target date < today) | — | avg days late among currently-delayed active plans |
| Delay Aging | No (current-state) | — | buckets over the same currently-delayed population |
| On Hold Count (open) | No (current-state snapshot: `resumedAt IS NULL`) | — | `WorkHoldRecord` |
| On Hold avg/longest duration | Yes, historical | `work_hold_records.resumed_at` | only resumed holds with `resumed_at::date` in range |
| Reopen Count | Yes, historical | `reopen_records.reopened_at` | count in range |
| Repost Count | Yes, historical | `actual_publication_events.actual_publication_timestamp` (event_type=REPOST) | count in range |

### Content & Publishing tab

| KPI | Date filter applied? | Date field | Calculation |
|---|---|---|---|
| Published Content | Yes, historical | `actual_publication_events.actual_publication_timestamp` | shared `publishedContentCount` |
| ORIGINAL / REPOST counts | Yes, historical | same | filtered by `event_type` + timestamp in range |
| Evidence Correction Records | Yes, historical | `actual_publication_events.actual_publication_timestamp` (joined) | `publication_evidence_corrections` count where the linked event's timestamp is in range |
| Correction Rate | Yes, historical | same | distinct corrected events / all events in range |
| Planned Output Mix | Yes, historical | `planned_outputs.created_at` | grouped by output/reel type, created in range |
| Platform Distribution | Yes, historical | `actual_publication_events.actual_publication_timestamp` | grouped by platform |
| Channel Distribution | Yes, historical | same | grouped by channel, top 5 |
| Target Completion % | Yes, historical | `planned_outputs.created_at` | published/(published+pending) non-N/A mappings for outputs **created** in range — note this is a different date basis (creation, not publication) than the Published/ORIGINAL/REPOST cards above |

### Quality & Reviews tab

| KPI | Date filter applied? | Date field | Calculation |
|---|---|---|---|
| First-Pass Approval Rate | Yes, historical | `review_cycles.decided_at` | cycle 1 APPROVED / cycle 1 decided, Shoot+Edit gates |
| Overall Rework Rate | Yes, historical | `review_cycles.decided_at` | shared `productionReworkRate` |
| Avg Review Turnaround | Yes, historical | `review_cycles.decided_at` | avg(decided_at − submitted_at) for cycles decided in range |
| Pending Reviews | No (current-state) | — | shared `pendingReviewsCount()` |
| Rework Rate by Stage / Stage-wise Review Performance | Yes, historical | `review_cycles.decided_at` | per-gate (Shoot/Edit) breakdown |
| Idea Rejection Rate | Yes, historical | `review_cycles.decided_at` (gate=IDEA_REVIEW) | rejected / (approved+rejected) decided in range |
| Evidence Corrections | Yes, historical | same as Content & Publishing's | shared `evidenceCorrectionCount` |

### Performance tab

| KPI | Date filter applied? | Date field | Calculation |
|---|---|---|---|
| Performance Pending | No (current-state) | — | `performance_obligations.is_completed = false`, no date filter at all |
| Performance Overdue | No (compared to today) | — | same as Overview's |
| Scorecard Completion % | Yes, historical | `performance_obligations.performance_due_date` | submitted-scorecard obligations / all obligations due in range |
| Avg Delay in Reporting | Yes, historical | `performance_obligations.performance_due_date` | avg(scorecard.submitted_at − obligation.performance_due_date) for obligations due in range |
| Top Content Type / Platform / Channel by Hook Rate, ORIGINAL vs REPOST comparisons | Yes, historical | `actual_publication_events.actual_publication_timestamp` (via `PerformanceObligation.event`) | scorecards whose underlying event's actual publication falls in range — **a different date field than Scorecard Completion/Avg Delay above**, both within the same tab |

**Boundary behavior (verified across all native-SQL queries)**: every SQL date filter uses
`column::date between :from and :to` — Postgres `BETWEEN` is inclusive on both ends, so a record
whose date exactly equals `Filter From` or `Filter To` **is included**. Java-side comparisons
(`inRange`, `isBefore`) are also written inclusively (`!d.isBefore(start) && !d.isAfter(end)`).

## 4. Proposed Content Lifecycle Date Rule

This section analyzes the **proposed** rule, not an implemented one.

**Rule as stated:**
- Shoot+Edit+Publishing: Start = Planned Shoot Date, End = Planned Live Date
- Edit+Publishing: Start = Planned Edit Date, End = Planned Live Date
- Publishing only: Start = Planned Live Date, End = Planned Live Date
- Overlap test: `Applicable Workflow Start Date <= Filter To AND Planned Live Date >= Filter From`

**What this means concretely**: a Content Plan is "in range" if its lifecycle window
`[Applicable Start, Planned Live Date]` overlaps the selected `[Filter From, Filter To]` window at
all — a standard interval-overlap test, not a strict containment test. A plan does not need to
start *and* end inside the filter window; it only needs some overlap.

**Example** (Shoot+Edit+Publishing, Planned Shoot Date = 05 Aug 2026, Planned Live Date = 20 Aug
2026, Filter = 01 Aug 2026 → 31 Aug 2026): Applicable Start (05 Aug) ≤ Filter To (31 Aug) ✓, and
Planned Live (20 Aug) ≥ Filter From (01 Aug) ✓ → **included**, correctly, since the whole lifecycle
sits inside the filter window.

**Example** (lifecycle straddling the filter's start): Planned Shoot Date = 28 Jul 2026 (before the
filter window), Planned Live Date = 10 Aug 2026 (inside it). Applicable Start (28 Jul) ≤ Filter To
(31 Aug) ✓, Planned Live (10 Aug) ≥ Filter From (01 Aug) ✓ → **included**, even though the lifecycle
*started* before 01 Aug. This is the defining behavior of an overlap test versus a
"fully-contained" test — worth confirming this is the intended semantic, since it differs sharply
from every currently-implemented date filter in this app, all of which test a *single* date column
against the range, never a two-endpoint interval against a range.

**BUSINESS CONFIRMATION REQUIRED**: `ContentPlan.plannedShootDate`/`plannedEditDate` are `null`
whenever that stage is skipped by the Stages selection (verified: `IdeaService.approve()` only
assigns `shootDate`/`editDate` locals `if (shootStarts)`/`if (editIncluded)` respectively, before
passing them into `ContentPlan.setPlanningScheduleStandard/Urgent`; for Edit+Publishing,
`shootDate` is never assigned, so `plannedShootDate` stays `null`). For Publishing-only, **both**
`plannedShootDate` and `plannedEditDate` are `null`. The proposed rule's per-combo mapping (§ above)
already anticipates this by naming a different start field per combo — but this document does not
resolve what "Applicable Workflow Start Date" should read for a plan whose Stages selection is
unknown/ambiguous to the reporting layer at query time (i.e., which SQL/Java branch decides "this
plan is Shoot+Edit+Publishing" vs "Edit+Publishing" vs "Publishing only" for date-rule purposes —
see §5, which documents the 3 valid combinations and their skip-reason markers, the only currently
governed signal for this).

## 5. Stage Skipping Analysis

Confirmed (`IdeaService.approve()`, `boolean shootStarts/editStarts/publishingStarts/validCombo`):
**exactly 3 valid Stages combinations** exist in the current application. No other combination
(e.g. Shoot+Publishing without Edit) is supported, and this document does not analyze any
combination beyond these 3.

| Combination | Applicable Workflow Start Date (proposed rule) | Ignored dates | `shootStageSkipReason` | `editStageSkipReason` |
|---|---|---|---|---|
| Shoot + Edit + Publishing | Planned Shoot Date | — (none skipped) | `null` | `null` |
| Edit + Publishing (Direct Edit) | Planned Edit Date | Planned Shoot Date (always `null` for this combo) | `"Stage not selected during planning"` | `null` |
| Publishing only (Direct Publishing) | Planned Live Date | Planned Shoot Date, Planned Edit Date (both always `null`) | `"Stage not selected during planning"` | `"Stage not selected during planning"` |

**How the date range should behave**: for each combo, the proposed rule's "ignored dates" column
above is exactly the set of `null` planned-date fields — there is no calculated/fallback date
hiding behind them to accidentally reuse (verified: skipped-stage planned-date fields are `null`,
never populated with a leftover computed value from an earlier stage-set edit, since
`setPlanningScheduleStandard/Urgent` only ever runs once, at approval, with whatever
`shootDate`/`editDate` locals were computed at that single call).

**Delayed work**: `StageDelayPolicy.currentApprovedTarget` already independently selects the
*current stage's own* target date by `WorkflowStatus` (Shoot statuses → `plannedShootDate`, Edit
statuses → `plannedEditDate`, Publishing/Performance statuses → `plannedLiveDate`) — this is
**not** the same date used by the proposed lifecycle-overlap rule, and does not depend on Stages at
all (a plan currently in an Edit-window status reads `plannedEditDate` regardless of whether Shoot
was skipped, because a skipped-Shoot plan can never be in a Shoot-window status in the first
place). §6 explains this distinction in full.

**Completed work**: once a plan reaches `COMP`, `StageDelayPolicy.currentApprovedTarget` returns
`null` (its `switch` has no case for `COMP`/`CAN`/`RJ`/`IS`/`PA`/`RET` — falls to `default -> null`)
— completed plans are never flagged delayed regardless of date range. The proposed lifecycle rule
does not currently define separate completed-work handling; §7 covers how each *existing* tab
treats completed work, since the user's instruction is explicit that tabs should not be assumed to
treat completed work identically.

## 6. Delayed Work Analysis

**Current delay determination** (single source of truth, verified in `StageDelayPolicy.java`):

```java
public static LocalDate currentApprovedTarget(WorkflowStatus status, ContentPlan plan) {
    return switch (status) {
        case SA, SIP, SRV -> plan.getPlannedShootDate();
        case SAP, EA, ED, ERV -> plan.getPlannedEditDate();
        case EAP, RFP, PUBG, PP, PFUP -> plan.getPlannedLiveDate();
        default -> null; // COMP, CAN, RJ, IS, PA, RET (not applicable)
    };
}
public static boolean isDelayed(WorkflowStatus status, ContentPlan plan, LocalDate today) {
    LocalDate target = currentApprovedTarget(status, plan);
    return target != null && target.isBefore(today);
}
```

`PipelineDashboardService.delayDays(status, plan, today)` computes the same target via
`StageDelayPolicy.currentApprovedTarget` and returns `ChronoUnit.DAYS.between(target, today)` when
overdue, else `null`. This one pair of methods is reused, unmodified, by
`KpiDashboardService` (Overview, Workflow & SLA), `PipelineDashboardService` itself (Content
Pipeline → Delayed), and `TeamWorkloadService` (through `PipelineDashboardService`). Delay is
always evaluated against **today**, never against the selected Date Range's `From`/`To`.

**Does the proposed date-range rule change delay calculation?** No — and it must not, per the
user's explicit instruction not to change delay calculation unless required, and nothing in the
proposed rule requires it. The proposed rule is a **membership test** (is this plan's lifecycle
window inside/overlapping the selected range at all), entirely separate from
`StageDelayPolicy.isDelayed`, which is a **current-state flag** (is this plan's current-stage
target already in the past, relative to today). These answer different questions and must stay
independent:

- **Date filter** = "should this Content Plan appear in this report view at all, given the
  selected range?"
- **Delay calculation** = "is this Content Plan currently late?" (a supplementary flag/condition on
  an item, per the user's own §9 framing in the earlier KPI Overview request — not a new workflow
  status)

**Would the proposed rule include or exclude delayed tasks?** It would include a currently-delayed
task exactly when that task's lifecycle window overlaps the selected range — the same membership
test as for a non-delayed task. A delayed task is not specially included or excluded by the
proposed rule; delay is an orthogonal, already-existing supplementary flag computed independently
and displayed alongside whatever membership the date rule determines. The proposed rule does not
touch `WorkflowStatus`, task status, or any status-transition logic.

## 7. Completed Work Analysis

Verified per tab/screen — completed work is **not** treated uniformly, confirming the user's
caution against assuming otherwise:

- **Overview / Workflow & SLA "Active WIP" / "Delayed Deliverables" / Stage Health**: completed
  (`COMP`) plans are explicitly excluded via `NON_TERMINAL_EXCLUSIONS = {"COMP","CAN","RJ"}` — they
  never appear in `ctx.activePlans` at all, regardless of date range.
- **Idea → Publish Funnel "Published"**: counts Content Plans with an `ORIGINAL`
  `ActualPublicationEvent` whose Idea's `submitted_at` falls in range — a plan can be `COMP`
  (or any other non-terminal status past first publish) and still count here; completion status is
  irrelevant to this specific count, only the historical publication event and the idea's
  submission date matter.
- **Content & Publishing "Published Content"/ORIGINAL/REPOST/Target Completion**: also
  status-agnostic — driven by `ActualPublicationEvent`/`PlannedOutput` records directly, not by the
  plan's current `WorkflowStatus`.
- **Upcoming Channel Plan**: explicitly excludes `NON_TERMINAL_EXCLUSIONS` plans (Cancelled/
  Completed/Rejected) — a completed plan's remaining mapping rows never show as "still outstanding."
- **Current Work Ownership**: implicitly excludes completed employee-level work by virtue of
  `AssigneeActiveWindows` — once a plan leaves that role's active-window statuses (which happens
  when that stage's own review is approved, moving the plan to the next stage or to `COMP`), the
  assignee's item stops appearing, regardless of the assignment row's own `is_active` flag.
- **Performance tab**: `performance_obligations.is_completed` is a boolean the tab reads directly
  (`Performance Pending` = `is_completed = false`) — this is the KPI Dashboard Performance tab's
  own, obligation-level completion flag, **distinct** from `ContentPlan`'s `WorkflowStatus`
  completion (`COMP`) and **distinct** from My Performance's `Completed On` concept (see §9).

**Verified explicitly per the user's caution about Performance**: the KPI Dashboard's Performance
tab does **not** use "Completed On" anywhere in its date filtering — it uses
`performance_obligations.performance_due_date` for the two historical KPIs
(Scorecard Completion %, Avg Delay in Reporting) and `actual_publication_events.
actual_publication_timestamp` for the Top-N/ORIGINAL-vs-REPOST breakdowns. "Completed On" as a
concept belongs to the separate `/app/my-performance` screen — see §9 for the full distinction.

## 8. Overview Date-Range Exceptions

### Current Work Ownership
**Date Range does not apply.** Verified: `KpiDashboardService.overview()` calls
`currentWorkOwnershipSummary()` with **zero arguments** — no `ctx`, no `rangeStart`/`rangeEnd` is
passed in or consulted anywhere in `currentWorkItemsByEmployee()`/`addWorkItemIfCurrentlyPending()`.
The only date used is `LocalDate.now(BUSINESS_ZONE)` ("today"), solely to compute each item's
`delayDays` via `PipelineDashboardService.delayDays`. **Why**: this is a current-state ownership
metric by design (documented in the code's own comment: *"a CEO selecting 'last 30 days' must not
hide work that is pending or delayed RIGHT NOW"*), matching the same treatment
`TeamWorkloadService`'s Assignee Load panel already gives this exact concept.

### Upcoming Channel Plan
**Date Range does not apply.** Verified: `upcomingChannelPlan()` also takes zero arguments. It
groups by `ContentPlan.plannedLiveDate` for **display/grouping purposes only** — that date is never
compared against `rangeStart`/`rangeEnd`. **Why**: same current-state rationale, documented
identically in code — *"represents 'still-outstanding future planned publication work' right now,
not a historical window."*

### Idea → Publish Funnel
**Exact date field**: `ideas.submitted_at`, cast to `::date`, compared `between rangeStart and
rangeEnd` — used identically for all 6 counts (Submitted, Approved, Retained, Rejected, Planned,
Published) and therefore for Approval Rate (`Approved/Submitted`, both from the same date-filtered
cohort).

**Consequences of using Idea Submitted Date** (verified from the code's own comment, spec §10
"cohort-consistency fix"): every count tracks the **same cohort** of ideas submitted in the
selected window, followed forward to their **current** outcome — not a "decided in range" or
"published in range" population. Concretely:
- An idea submitted on 15 Jul 2026 but approved on 05 Aug 2026, with Content published on 20 Aug
  2026, is **entirely excluded** from a 01–31 Aug 2026 range — none of its Approved/Planned/
  Published contribution shows up in that window, because the filter is on the idea's submission
  date only.
- Conversely, an idea submitted on 20 Aug 2026 whose Content is still unpublished by report time
  contributes to Submitted/Approved/Planned in the 01–31 Aug window but shows `Published = 0` for
  it — the funnel is not claiming it published in that window, only that this idea (a member of the
  Aug cohort) has or hasn't reached each stage as of *now*.
- This means "Published" in this funnel is **not** the same population as "Published Content" on
  Content & Publishing (which is filtered by the *publication event's own* timestamp, not the
  idea's submission date) — they can legitimately show different numbers for the same range, and
  that is expected/by-design per the cohort-consistency comment, not a bug.

This document does not change this rule — the user's instruction was to document the exact field
already in use, which is `submitted_at`, verified above.

## 9. Performance Date Rule

**Two genuinely separate screens exist and were verified independently — they must not be
conflated:**

1. **`/app/my-performance`** (`LandingMvcController`, employee-facing "My Performance" page):
   verified via `withinDateRange(r.getCompletedOn(), fromDate, toDate)` (used at least twice in the
   method body, for both the Task Performance list and the KPI-card totals) — this **does** use
   `Completed On`, which per the method's own javadoc is *"the same transition-history-derived
   timestamp"* representing the employee's own personal completion of their specific task/stage
   assignment (not the Content Plan's overall status). This is a **per-employee, per-assignment**
   date, established and already governed by pre-existing My Performance business rules
   documented elsewhere in this codebase — this document does not re-derive that rule, only
   confirms its existence and that it is unrelated to the KPI Dashboard's own Performance tab.

2. **`/app/reports/kpis?view=performance`** (`KpiDashboardService.performance()`, the CEO-facing
   KPI Dashboard's fifth tab): does **not** use "Completed On" anywhere. It uses:
   - `performance_obligations.performance_due_date` for Scorecard Completion % and Avg Delay in
     Reporting (both historical, date-ranged).
   - `actual_publication_events.actual_publication_timestamp` (via
     `PerformanceObligation.event`) for the Top Content/Platform/Channel and ORIGINAL-vs-REPOST
     breakdowns (also historical, date-ranged, but a *different* date field than the two KPIs
     above — verified directly, both live in the same method body).
   - `performance_obligations.is_completed` (boolean, current-state) for Performance Pending — no
     date filter.
   - Performance Overdue compares `performance_due_date < today`, not against the selected range.

**Conclusion**: the KPI Dashboard's Performance tab uses **`performance_due_date`** (and, for one
sub-section, the publication event's own timestamp) — never `Completed On`. `Completed On` is
exclusive to the separate My Performance screen. Any future rule change to the KPI Dashboard's
Performance tab date filtering must not be assumed to also apply to My Performance, and vice
versa, without an explicit decision to unify them (not requested here).

## 10. Content Pipeline Date Rule

Content Pipeline (`GET /app/pipeline`, `LandingMvcController.pipeline`, backed by
`PipelineDashboardService`) is a **separate screen** from the 5-tab KPI Dashboard analyzed above,
though it shares `StageDelayPolicy`/`PipelineDashboardService.delayDays` for its own Delayed
filter and delay display.

**Verified current date-filter shape — fundamentally different from the KPI Dashboard's single
Date Range selector**: Content Pipeline exposes **six independent, optional date-pair filter
params**, each its own `From`/`To`, all bindable simultaneously:
- `plannedShootFrom` / `plannedShootTo`
- `plannedEditFrom` / `plannedEditTo`
- `plannedLiveFrom` / `plannedLiveTo`
- `actualShootFrom` / `actualShootTo`
- `actualEditFrom` / `actualEditTo`
- `actualLiveFrom` / `actualLiveTo`

(all `@RequestParam(required = false) @DateTimeFormat(iso = DATE) LocalDate`, verified in
`LandingMvcController.pipeline()`'s method signature). There is **no single unified "Date Range"
concept on Content Pipeline today** comparable to the KPI Dashboard's `startDate`/`endDate` pair —
each of the 6 fields filters its own specific column independently when supplied, and any
combination of the 6 can be active at once (each shown as its own `...FilterActive` model flag).

**Is the proposed Applicable-Workflow-Start-Date → Planned-Live-Date rule appropriate for Content
Pipeline?** `BUSINESS CONFIRMATION REQUIRED`. The proposed rule collapses to a single lifecycle
window per plan and a single overlap test; Content Pipeline's current model instead lets a user
filter on any one (or several) of 6 independent date columns simultaneously — e.g. "Planned Shoot
between X–Y" AND "Actual Live between A–B" at once. Replacing that with one unified
Start→Planned-Live overlap filter would be a **behavior change** to an existing, already-shipped
screen's filtering model, not a documentation-only clarification — this document flags the
conflict rather than resolving it, per the user's explicit instruction not to silently change
existing behavior.

Behavior for each requested scenario, analyzed against the **proposed** rule (not implemented):
- **Shoot+Edit+Publishing**: Start = Planned Shoot Date, End = Planned Live Date, per §4.
- **Edit+Publishing**: Start = Planned Edit Date, End = Planned Live Date, per §4. Note Content
  Pipeline's current `plannedShootFrom/To` filter would have nothing to match against for such a
  plan (`plannedShootDate` is `null`) — under the *current* implementation that simply excludes it
  if `plannedShootFrom`/`To` were supplied (a `null` column can't satisfy a `BETWEEN`); the proposed
  rule instead would use Planned Edit Date, sidestepping that null entirely. This is a genuine
  behavioral difference worth confirming is intended.
- **Publishing only**: Start = End = Planned Live Date, per §4.
- **Delayed content**: unaffected — Content Pipeline's `delayed=true` filter already uses
  `StageDelayPolicy`/`PipelineDashboardService.delayDays` independently of any of the 6 date-pair
  filters or the proposed rule; delay determination should stay untouched per §6.
- **Completed content**: `BUSINESS CONFIRMATION REQUIRED` — this document did not find an explicit
  status exclusion inside `PipelineDashboardService`'s row-building path analogous to
  `NON_TERMINAL_EXCLUSIONS` in `KpiDashboardService`; Content Pipeline appears to list all
  `ContentPlan`s including completed ones (subject to whatever `status` query param the user
  selects), so how a completed plan's lifecycle window should interact with the proposed rule was
  not verified and is not assumed here.
- **Date-range overlap / date exactly equal to From / date exactly equal to To**: per §3's verified
  boundary-inclusivity finding, any equivalent native-SQL implementation of the proposed rule would
  naturally be inclusive at both ends if written as `BETWEEN`-style comparisons, consistent with
  every other date filter in this codebase — but the proposed rule as stated uses `<=`/`>=`
  directly (not a database `BETWEEN`), which is equivalent in inclusivity, so From/To equality
  would include the record either way, *if* implemented literally as specified.

## 11. Complete KPI Date Matrix

| Tab | KPI | Date Range Applied? | Date Basis | Current/Historical | Delay Handling | Completed Handling | Stage Skip Handling |
|---|---|---|---|---|---|---|---|
| Overview | Active WIP | No | — | Current | via `StageDelayPolicy` (not this KPI itself) | Excluded (`NON_TERMINAL_EXCLUSIONS`) | N/A |
| Overview | Delayed Deliverables | No (vs. today) | — | Current | `currentApprovedTarget` < today | Excluded | Stage-specific target per current status |
| Overview | On-Time Delivery | Yes | Publishing cycle deadline | Historical | N/A (SLA metric) | N/A | N/A |
| Overview | Published Content | Yes | `actual_publication_events.actual_publication_timestamp` | Historical | N/A | N/A | N/A |
| Overview | Avg End-to-End Cycle Time | Yes | `PUBLICATION_SCOPE_RESOLVED` transition | Historical | N/A | N/A | N/A |
| Overview | Rework Rate (All Stages) | Yes | `review_cycles.decided_at` | Historical | N/A | N/A | N/A |
| Overview | Pending Reviews | No | — | Current | N/A | Excluded (decided_at IS NULL by definition) | N/A |
| Overview | Performance Overdue | No (vs. today) | — | Current | `performance_due_date` < today | `is_completed=false` filter | N/A |
| Overview | Stage Bottleneck Summary | No | — | Current | via `StageDelayPolicy` | Excluded | Per-stage grouping |
| Overview | Attention Needed | Mixed (one item uses a fixed 2-day cutoff, not the range) | — | Current | Included as one signal | N/A | N/A |
| Overview | **Current Work Ownership** | **No** | — | Current | `StageDelayPolicy`/`delayDays` | Excluded via `AssigneeActiveWindows` | Excluded from that role's window once past it |
| Overview | **Upcoming Channel Plan** | **No** | `ContentPlan.plannedLiveDate` (display only) | Current | N/A | Excluded (`NON_TERMINAL_EXCLUSIONS`) | N/A (no per-stage grouping) |
| Overview | **Idea → Publish Funnel / Approval Rate** | **Yes** | `ideas.submitted_at` | Historical | N/A | N/A (status-agnostic) | N/A |
| Workflow & SLA | Stage Health | No | — | Current | `StageDelayPolicy` | Excluded | Per-stage grouping |
| Workflow & SLA | Shoot-to-Publish Cycle Time | Yes | `actual_publication_events` (last ORIGINAL) | Historical | N/A | N/A | N/A |
| Workflow & SLA | End-to-End Cycle Time | Yes | `PUBLICATION_SCOPE_RESOLVED` transition | Historical | N/A | N/A | N/A |
| Workflow & SLA | On-Time Delivery % | Yes | Publishing cycle deadline | Historical | N/A | N/A | N/A |
| Workflow & SLA | Average Delay | No | — | Current | `currentApprovedTarget` < today, avg days | Excluded | Stage-specific |
| Workflow & SLA | Delay Aging | No | — | Current | same population as Average Delay | Excluded | Stage-specific |
| Workflow & SLA | On Hold Count (open) | No | — | Current | N/A | N/A | N/A |
| Workflow & SLA | On Hold avg/longest | Yes | `work_hold_records.resumed_at` | Historical | N/A | N/A | N/A |
| Workflow & SLA | Reopen Count | Yes | `reopen_records.reopened_at` | Historical | N/A | N/A | N/A |
| Workflow & SLA | Repost Count | Yes | `actual_publication_events` (REPOST) | Historical | N/A | N/A | N/A |
| Content & Publishing | Published Content / ORIGINAL / REPOST | Yes | `actual_publication_events.actual_publication_timestamp` | Historical | N/A | N/A | N/A |
| Content & Publishing | Evidence Correction Records / Rate | Yes | same (joined) | Historical | N/A | N/A | N/A |
| Content & Publishing | Planned Output Mix | Yes | `planned_outputs.created_at` | Historical | N/A | N/A | N/A |
| Content & Publishing | Platform / Channel Distribution | Yes | `actual_publication_events.actual_publication_timestamp` | Historical | N/A | N/A | N/A |
| Content & Publishing | Target Completion % | Yes | `planned_outputs.created_at` | Historical | N/A | N/A excluded, non-N/A only | N/A |
| Quality & Reviews | First-Pass Approval Rate | Yes | `review_cycles.decided_at` | Historical | N/A | N/A | N/A |
| Quality & Reviews | Overall Rework Rate | Yes | `review_cycles.decided_at` | Historical | N/A | N/A | N/A |
| Quality & Reviews | Avg Review Turnaround | Yes | `review_cycles.decided_at` | Historical | N/A | N/A | N/A |
| Quality & Reviews | Pending Reviews | No | — | Current | N/A | N/A | N/A |
| Quality & Reviews | Rework/Stage-wise Review Performance | Yes | `review_cycles.decided_at` | Historical | N/A | N/A | N/A |
| Quality & Reviews | Idea Rejection Rate | Yes | `review_cycles.decided_at` (IDEA_REVIEW) | Historical | N/A | N/A | N/A |
| Quality & Reviews | Evidence Corrections | Yes | `actual_publication_events.actual_publication_timestamp` (joined) | Historical | N/A | N/A | N/A |
| Performance | Performance Pending | No | — | Current | N/A | `is_completed=false` | N/A |
| Performance | Performance Overdue | No (vs. today) | — | Current | `performance_due_date` < today | `is_completed=false` | N/A |
| Performance | Scorecard Completion % | Yes | `performance_obligations.performance_due_date` | Historical | N/A | N/A | N/A |
| Performance | Avg Delay in Reporting | Yes | `performance_obligations.performance_due_date` | Historical | N/A | N/A | N/A |
| Performance | Top Content/Platform/Channel, ORIGINAL vs REPOST | Yes | `actual_publication_events.actual_publication_timestamp` | Historical | N/A | N/A | N/A |

## 12. Recommended Final Rules

### A. Already correct — do not change
- Idea → Publish Funnel's use of `ideas.submitted_at` as the sole date basis for its cohort model —
  explicitly locked by ENG-097 and functioning as documented.
- Current Work Ownership and Upcoming Channel Plan being current-state, not date-ranged — matches
  precedent (`TeamWorkloadService` Assignee Load) and the explicit rationale in the code comments;
  changing this would contradict the user's own §19 instruction in the prior KPI Overview task.
- `StageDelayPolicy`/`PipelineDashboardService.delayDays` as the single, unmodified source of delay
  truth across Overview, Workflow & SLA, Content Pipeline, and Team Workload.
- All existing per-tab date bases (documented in §3/§11) — every one already has a specific,
  intentional, code-verified rationale; none appear accidental or inconsistent within their own
  tab's stated purpose.
- Boundary inclusivity (`BETWEEN`, `!isBefore && !isAfter`) — consistent everywhere already
  checked.

### B. Requires implementation change
- None. This document is investigation-only; no implementation gap was identified that the user
  has already resolved a decision for. The proposed Applicable-Workflow-Start-Date rule is not yet
  approved for implementation anywhere (Overview, Content Pipeline, or otherwise).

### C. Requires business confirmation
1. Whether the proposed Applicable-Workflow-Start-Date → Planned-Live-Date rule is intended for
   **any currently-shipped screen at all** (Content Pipeline? A future KPI Dashboard tab? Neither
   Overview block currently uses date ranges, per §8, so it is not obviously "for Overview").
2. If intended for Content Pipeline: whether it should **replace** the existing 6-independent-
   date-pair filter model, or **add** an additional Date Range option alongside it (§10).
3. How "Applicable Workflow Start Date" should be determined server-side per plan — i.e., which
   signal decides a given `ContentPlan` is Shoot+Edit+Publishing vs Edit+Publishing vs
   Publishing-only for date-rule purposes (the skip-reason fields are the only currently-governed
   signal for this — confirm they are the intended source) (§4/§5).
4. How the proposed rule should treat a completed (`COMP`) plan whose lifecycle window overlaps the
   filter range — include, exclude, or unspecified (§7/§10).
5. Whether "Planned Live Date" as both Start and End for Publishing-only is intended to make a
   Publishing-only plan's window a single-day point-in-time (zero-width interval), and whether the
   overlap test behaves as expected for a zero-width interval (mathematically it does — `Start ≤
   Filter To AND Live ≥ Filter From` reduces to `Filter From ≤ Live ≤ Filter To` when Start = Live —
   but this reduction was not stated explicitly by the user and is worth confirming was intended).

## 13. Risk Analysis

- **KPI number changes**: any change to which date field a historical KPI uses (or whether a
  current-state KPI becomes date-ranged, or vice versa) will change the reported number for any
  non-trivial date range — several KPIs already intentionally differ from each other in date basis
  within the *same* tab (e.g. Performance tab's `performance_due_date`-based KPIs vs its
  publication-timestamp-based KPIs); conflating them would silently merge two currently-distinct
  populations.
- **Historical reporting changes**: retroactively changing a KPI's date basis changes what past
  date-range reports would have shown, which could make previously-reported numbers
  non-reproducible if anyone compares an old export/screenshot against a re-run report.
- **Delayed task visibility**: because delay is current-state (§6), any change that makes Current
  Work Ownership or Upcoming Channel Plan date-ranged risks hiding currently-delayed work from a
  CEO who has selected a range that doesn't include today — directly contradicting the rationale
  already documented in the code for keeping them current-state.
- **Completed task visibility**: §7 shows completed-work handling already varies by tab
  intentionally; a single unified rule applied indiscriminately could newly include/exclude
  completed plans in a tab that currently has a different, deliberate rule (e.g. the Funnel's
  status-agnostic Published count vs Overview/Workflow & SLA's `NON_TERMINAL_EXCLUSIONS`).
- **Stage skipping**: any date rule that assumes all 3 planned-date fields are populated will break
  for Edit+Publishing/Publishing-only plans (`plannedShootDate`/`plannedEditDate` are genuinely
  `null`, not zero/empty-string) — a naive SQL `BETWEEN` against a `null` column silently excludes
  the row rather than erroring, which could quietly under-report Edit+Publishing/Publishing-only
  content without any visible failure.
- **Boundary dates**: every existing filter in this codebase is inclusive at both ends; a new rule
  using exclusive comparisons anywhere would silently drop edge-of-range records compared to every
  other filter in the app, creating an inconsistent user experience across tabs/screens.
- **Existing dashboards/reports**: Content Pipeline's 6-independent-filter model (§10) is a
  materially different UX from a single Date Range control; users/exports relying on filtering by
  e.g. Actual Shoot Date alone (with no Planned Live Date constraint) would lose that capability if
  the 6 fields were collapsed into one range.
- **Regression risk**: `StageDelayPolicy`/`PipelineDashboardService.delayDays` are shared by
  `KpiDashboardService`, `PipelineDashboardService`, and `TeamWorkloadService` — any change to
  delay logic (even if unintentional, e.g. as a side effect of a date-rule refactor touching the
  same file) would regress all three simultaneously, per the class's own javadoc warning about the
  pre-`StageDelayPolicy` history of exactly this kind of drift
  (`docs/KPI_DATA_RECONCILIATION_REPORT.md §1`, referenced in the class's own comment — not
  independently verified in this pass, cited only because the code itself cites it).

## 14. Test Scenario Matrix

All scenarios analyze the **proposed** rule (§4) against a hypothetical Filter From = 01 Aug 2026,
Filter To = 31 Aug 2026, unless stated otherwise. These are not automated tests — no test code was
written or run, per the read-only scope of this task.

| # | Scenario | Workflow | Dates | Selected From | Selected To | Expected inclusion | Reason |
|---|---|---|---|---|---|---|---|
| 1 | Normal Shoot+Edit+Publishing | S+E+P | Shoot 05 Aug, Live 20 Aug | 01 Aug | 31 Aug | Include | Start ≤ To, Live ≥ From |
| 2 | Edit+Publishing | E+P | Edit 10 Aug, Live 25 Aug | 01 Aug | 31 Aug | Include | Start(Edit) ≤ To, Live ≥ From |
| 3 | Publishing only | P | Live 15 Aug | 01 Aug | 31 Aug | Include | Start=Live=15 Aug, within window |
| 4 | Delayed Shoot task | S+E+P | Shoot 05 Aug (past, plan still in SA/SIP/SRV), Live 20 Aug | 01 Aug | 31 Aug | Include (date rule) + flagged delayed (separate) | Date-rule membership and delay flag are independent (§6) |
| 5 | Delayed Edit task | E+P | Edit 10 Aug (past, plan still in EA/ED/ERV), Live 25 Aug | 01 Aug | 31 Aug | Include + flagged delayed | Same as #4 |
| 6 | Delayed Publishing task | any combo | Live 15 Aug (past, plan still in RFP/PUBG) | 01 Aug | 31 Aug | Include + flagged delayed | `currentApprovedTarget` reads `plannedLiveDate` for these statuses |
| 7 | Completed task | any combo, now `COMP` | lifecycle overlapping range | 01 Aug | 31 Aug | `BUSINESS CONFIRMATION REQUIRED` | §7/§12-C4 — proposed rule doesn't state completed-plan handling |
| 8 | Lifecycle completely before range | S+E+P | Shoot 01 Jun, Live 10 Jun | 01 Aug | 31 Aug | Exclude | Live (10 Jun) < From (01 Aug) — fails `Live ≥ From` |
| 9 | Lifecycle completely after range | S+E+P | Shoot 05 Sep, Live 20 Sep | 01 Aug | 31 Aug | Exclude | Start (05 Sep) > To (31 Aug) — fails `Start ≤ To` |
| 10 | Lifecycle overlapping range | S+E+P | Shoot 25 Jul, Live 05 Sep | 01 Aug | 31 Aug | Include | Start (25 Jul) ≤ To (31 Aug) ✓, Live (05 Sep) ≥ From (01 Aug) ✓ — partial overlap still counts |
| 11 | Start date exactly equal to Filter From | E+P | Edit 01 Aug, Live 15 Aug | 01 Aug | 31 Aug | Include | `Start ≤ To` true; boundary equality — inclusive per §3/§10 finding, *if* implemented as stated (`<=`) |
| 12 | Live date exactly equal to Filter To | S+E+P | Shoot 20 Aug, Live 31 Aug | 01 Aug | 31 Aug | Include | `Live ≥ From` true; boundary equality inclusive |
| 13 | Start before range + Live inside range | S+E+P | Shoot 20 Jul, Live 10 Aug | 01 Aug | 31 Aug | Include | Same shape as #10 |
| 14 | Start inside range + Live after range | E+P | Edit 20 Aug, Live 10 Sep | 01 Aug | 31 Aug | Include | Start (20 Aug) ≤ To (31 Aug) ✓, Live (10 Sep) ≥ From (01 Aug) ✓ |
| 15 | Stage skipped with old calculated date that should not be used | E+P (Shoot skipped) | `plannedShootDate = null` (never populated — verified §4/§5, not a stale leftover value) | 01 Aug | 31 Aug | Rule must use Planned Edit Date, never attempt to read the null Shoot Date | Confirmed: no fallback/default value exists for a skipped stage's date field to be accidentally reused |

## 15. Final Proposed Business Rule

**This section is a specification only — it is NOT implemented, and IMPORTANT: do not implement it
without a separate, explicit approval step.**

1. For any Content Plan, its **Applicable Workflow Start Date** is:
   - Planned Shoot Date, if Shoot is part of its Stages (Shoot+Edit+Publishing).
   - Planned Edit Date, if Shoot is skipped but Edit is included (Edit+Publishing).
   - Planned Live Date, if only Publishing is selected (Publishing only).
2. Its **Applicable Workflow End Date** is always Planned Live Date.
3. A Content Plan is considered "in range" for a selected Date Range `[From, To]` when:
   `Applicable Workflow Start Date <= To AND Planned Live Date >= From` (inclusive overlap test).
4. This rule is proposed for **whichever screen(s) get business confirmation in §12-C1/C2** — it
   is not, by this document's own findings, already the rule for Overview (Overview's Current Work
   Ownership/Upcoming Channel Plan are current-state, and the Funnel uses Idea Submitted Date, per
   §8) nor for Content Pipeline (which uses 6 independent date-pair filters today, per §10).
5. This rule must not alter `StageDelayPolicy`/`PipelineDashboardService.delayDays`, `WorkflowStatus`
   values, `NON_TERMINAL_EXCLUSIONS` handling, or any assignment/permission/marks logic — it is
   purely a membership test for report inclusion.
6. Every open item in §12-C must be resolved before implementation begins.

## 16. Verification Sources

| Claim | Java class | Method | Other files |
|---|---|---|---|
| KPI Dashboard route/params | `com.kcpc.mkt.web.mvc.ReportingMvcController` | `kpiDashboard` | `reports-kpi-console.jsp`, `reports-kpi-console-content.jspf` |
| Ownership drill-down route | `ReportingMvcController` | `ownershipDrilldown` | `reports-kpi-ownership-drilldown.jspf/.jsp` |
| Default/effective date range | `ReportingMvcController` | `kpiDashboard` (`effectiveStart`/`effectiveEnd`) | — |
| `DashboardContext`/range defaulting | `com.kcpc.mkt.reporting.service.KpiDashboardService` | `buildContext`, inner class `DashboardContext` | — |
| Overview aggregation | `KpiDashboardService` | `overview` | `OverviewDashboardDto`, `fragments/reports-kpi-overview.jspf` |
| Current Work Ownership | `KpiDashboardService` | `currentWorkItemsByEmployee`, `currentWorkOwnershipSummary`, `addWorkItemIfCurrentlyPending` | `CurrentWorkOwnershipRow`, `EmployeeWorkItemRow`, `AssigneeActiveWindows` |
| Employee drill-down | `KpiDashboardService` | `employeeWorkDrillDown` | `EmployeeWorkDrillDownDto`, `reports-kpi-ownership-drilldown.jspf` |
| Upcoming Channel Plan | `KpiDashboardService` | `upcomingChannelPlan` | `UpcomingPlanDateGroup`, `UpcomingPlanChannelCount`, `PlannedOutputPublicationTargetMapping` |
| Idea → Publish Funnel / Approval Rate | `KpiDashboardService` | `ideaFunnel`, `rate` | `IdeaFunnelDto` |
| Workflow & SLA | `KpiDashboardService` | `workflowSla`, `stageHealth`, `onTimeDelivery`, `onHoldSummary`, `delayAgingBuckets` | `WorkflowSlaDashboardDto`, `fragments/reports-kpi-workflow.jspf` |
| Content & Publishing | `KpiDashboardService` | `contentPublishing`, `contentMixByType`, `publishingTargetCompletion` | `ContentPublishingDashboardDto`, `fragments/reports-kpi-content.jspf` |
| Quality & Reviews | `KpiDashboardService` | `qualityReviews`, `reviewStageRows` | `QualityReviewsDashboardDto`, `fragments/reports-kpi-quality.jspf` |
| Performance (KPI Dashboard tab) | `KpiDashboardService` | `performance`, `submittedScorecardsInRange`, `topByHookRate`, `avgByEventType` | `PerformanceDashboardDto`, `fragments/reports-kpi-performance.jspf` |
| Delay policy (shared) | `com.kcpc.mkt.reporting.service.StageDelayPolicy` | `currentApprovedTarget`, `isDelayed` | — |
| Delay days (shared) | `com.kcpc.mkt.reporting.service.PipelineDashboardService` | `delayDays` (static) | — |
| Content Pipeline route/filters | `com.kcpc.mkt.web.mvc.LandingMvcController` | `pipeline` | — |
| Planned date fields | `com.kcpc.mkt.planning.domain.ContentPlan` | `getPlannedShootDate`, `getPlannedEditDate`, `getPlannedLiveDate`, `setPlanningScheduleStandard`, `setPlanningScheduleUrgent`, `getShootStageSkipReason`, `getEditStageSkipReason` | — |
| Stage-combo validity / date nulling | `com.kcpc.mkt.idea.service.IdeaService` | `approve` (`shootStarts`/`editStarts`/`publishingStarts`/`validCombo`) | `PlanningStage` |
| My Performance "Completed On" | `LandingMvcController` | `withinDateRange`, the My Performance builder method(s) around it | — (per this session's established, pre-existing understanding of My Performance; re-confirmed by direct grep of `withinDateRange`/`getCompletedOn` usage in this pass) |
| Database columns | — | — | `ideas.submitted_at`, `content_plans.planned_shoot_date/planned_edit_date/planned_live_date`, `performance_obligations.performance_due_date`, `performance_obligations.is_completed`, `actual_publication_events.actual_publication_timestamp`, `review_cycles.decided_at`/`submitted_at`, `work_hold_records.resumed_at`/`held_at`, `reopen_records.reopened_at`, `planned_outputs.created_at`, `publication_evidence_corrections` (joined via `event_id`) — all verified directly in the native SQL strings in `KpiDashboardService.java` |

No line numbers are cited above beyond method/class names, since this file's line offsets shift as
the working tree changes across this session; every method name listed was read directly in this
pass.
