# KPI Dashboard Implementation — Capability Assessment

Pre-coding assessment required by the KPI Dashboard implementation spec (§51), based on direct
investigation of the current codebase (`ReportingMvcController`, `KpiService`,
`AdminReportingService`, `PublishingService`, `ReviewCycle`, `PerformanceObligation`,
`CreativePerformanceScorecard`, `RescheduleRecord`, `ReopenRecord`, `WorkHoldRecord`,
`WorkflowTransitionHistory`, and related repositories/JSPs/JS).

Status: **awaiting confirmation on one algorithm (§4) before implementation begins.** No code has
been written yet.

---

## 1. Existing components to reuse

- **`/app/reports/kpis`** already exists (`ReportingMvcController`) but as a flat KPI-001..030
  catalogue (`KpiService`, raw SQL). This route/tab is **replaced** by the new 5-view dashboard,
  not built alongside it.
- **`reports-workspace.js`** already implements AJAX partial swap, `history.pushState`, and a
  `popstate` listener for Back/Forward, plus date-range persistence. Extending its tracked-param
  list to include `view=` satisfies the deep-linking requirement — no new JS mechanism needed.
- **`AdminReportingService.delayedDeliverables`** — governed "current approved target" logic
  (`STAGE_PLANNED_DATE_CASE`, `STAGE_LABEL_CASE`, visibility scoping) — reused for Active WIP,
  Delayed Deliverables, and the Stage Bottleneck/SLA tables.
- **`KpiService`** KPI-020 (Idea Approval, RETAINED excluded from denominator) and KPI-028
  (Shoot-to-Publish Cycle Time, ORIGINAL-only, has an existing regression test) — reused as-is.
- **`KpiService` KPI-030 (On-Time Delivery) is discontinued** — it is per-event against the mutable
  `planned_live_date` column, which is exactly what §17 of the spec forbids.
- **`PerformanceService.resolveEffectiveMetrics(scorecard)`** already applies the correction chain
  — reused directly for CTR/Impressions everywhere; never re-derived.
- **`PublicationEvidenceCorrection.latestByEventId(...)`** already resolves distinct-event
  corrections — reused for the §27 evidence-correction rate.
- **`PublishingService.currentPublishingCycleStart`** and its `recordedAt`-based cycle-membership
  discipline — reused/extended for cycle boundaries.
- DTO convention confirmed: **plain classes with `getX()` getters, never records** — JSP EL's
  `BeanELResolver` cannot resolve record-style accessors in this codebase's Tomcat/Jasper version
  (the same issue hit and fixed this session with `AssignmentQueueRow`).

---

## 2–3. Final KPI list, formula, and source (by screen)

### Overview

| KPI | Source / Formula |
|---|---|
| Active WIP | Count ContentPlans with status not in {COMP, CAN, RJ} |
| Delayed Deliverables | `STAGE_PLANNED_DATE_CASE`-style: current approved target date < today, not terminal |
| On-Time Delivery (Publishing) | New governed cycle formula — see §17-20 in the source spec and §4 below |
| Published Content | Distinct ContentPlans with ≥1 `ActualPublicationEvent` in range |
| Avg End-to-End Cycle Time | `Idea.submittedAt` → earliest ORIGINAL `actualPublicationTimestamp` (matches KPI-028's precedent of using `actualPublicationTimestamp`, not `recordedAt`, for cycle-time math) |
| Rework Rate (All Stages) | `ReviewCycle.decision = 'REQUEST_REWORK'` / decided reviews, Planning+Shoot+Edit gates |
| Pending Reviews | `ReviewCycle` rows with `decidedAt IS NULL`, gates in {IDEA_REVIEW, PLANNING_REVIEW, SHOOT_REVIEW, EDIT_REVIEW} — Publishing excluded |
| Performance Overdue | `PerformanceObligation`: `!completed && performanceDueDate < today` |

Stage Bottleneck Summary, Attention Needed, and Idea→Publish Funnel are all composed from the
primitives above — no new data source required.

### Workflow & SLA

Same stage table (Active/Delayed/SLA %/Avg Age/Oldest Item) plus:

- Planning Turnaround Time — PL-entry → PLAP transition, via `WorkflowTransitionHistory`
- Shoot-to-Publish Cycle Time — reuse KPI-028
- End-to-End Content Cycle Time — reuse Overview's calculation
- On-Time Delivery % — reuse the governed cycle formula
- Average Delay — avg(`today - target`) over currently-delayed active items
- On Hold Count / Avg Hold Duration / Longest Hold — `WorkHoldRecord`, `findByResumedAtIsNull()` for "currently on hold"; average/longest computed over resumed (completed) holds within the period
- Delay Aging buckets (0-2 / 3-5 / 6-10 / 11+ days) — bucketed from the same delayed-item set
- Reopen/Repost Summary — `ReopenRecord` counts by purpose; REPOST via `ActualPublicationEvent.eventType`

### Content & Publishing

Published/ORIGINAL/REPOST counts, Publishing Target Completion %, Evidence Corrections, Content
Mix (`PlannedOutput.outputType`/`reelType`, actual system types only), Platform/Channel
distribution (dynamic from the Publishing Catalogue, never hardcoded), Planned vs Published
Targets (Published/Pending/N/A) — all supported directly from existing entities.

### Quality & Reviews

First-Pass Approval Rate (`cycleNumber == 1 && decision == 'APPROVED'` per gate — schema supports
it, not yet built as a query), Rework by Stage (Planning/Shoot/Edit only, no Publishing rework),
Avg Review Turnaround (`decidedAt - submittedAt`), Pending Reviews, Idea Rejection Rate,
stage-wise review performance table — all supported via `ReviewCycle`.

### Performance

Performance Pending/Overdue (as above), Scorecard Completion % (submitted / obligations due in
period), Avg Delay in Reporting (`submittedAt - performanceDueDate`, only where positive), Top
Content/Platform/Channel by Avg CTR (N/A-excluded via `resolveEffectiveMetrics`), ORIGINAL vs
REPOST performance comparison (via `PerformanceObligation.event.eventType`) — all supported.

---

## 4. Item requiring explicit sign-off before implementation

**Original Publishing-cycle deadline reconstruction.**

`ContentPlan.plannedLiveDate` has no dedicated history table, and `RescheduleRecord.stageContext`
is only a caller-supplied label — it does not restrict which date fields a given reschedule
touches (a `SHOOTING`-tagged reschedule can still carry a new `plannedLiveDate`). The deterministic
reconstruction is therefore a 3-branch algorithm:

1. If the plan has ever had a `PUBLISHING_REOPEN`: take the latest `RescheduleRecord` (**any**
   `stageContext`) with `rescheduledAt` before that first reopen → use its `newPlannedLiveDate`.
2. Else, if reschedules exist but none before that point: use the **earliest-ever**
   `RescheduleRecord.priorPlannedLiveDate` (the value in place immediately before the first change
   ever made — i.e. the untouched Planning-time value).
3. Else (no reschedule at all): current `plan.getPlannedLiveDate()` is safe (never touched).

Repost-cycle deadlines are the clean case the spec already describes: latest `RescheduleRecord`
with `stageContext = PUBLISHING` where `reopenedAt <= rescheduledAt < nextReopenedAt`; absent that
→ `Target Pending`, excluded from the denominator until a valid target exists.

The `PUBLICATION_SCOPE_RESOLVED` numerator anchor already exists as a real persisted
`WorkflowTransitionHistory` row (`toStatusCode = PP`) — no gap there.

**This algorithm needs explicit confirmation (or redirection) before implementation begins.**

---

## 5. Schema / index requirements

None. No new tables or columns. Two new **repository finder methods** are needed
(`RescheduleRecordRepository` currently has zero query methods; `ReopenRecordRepository` only has
a "latest" finder, needs an "all, ordered" finder). Existing FK indexes on `workflow_instance_id`
should be sufficient; no migration planned unless profiling later shows otherwise.

---

## 6. KPI being discontinued/replaced

`KpiService` KPI-030 (On-Time Delivery, per-event against the mutable `planned_live_date`) is
replaced by the new governed per-cycle formula. The flat KPI-001..030 catalogue console UI itself
is replaced by the new dashboard at the same route (`/app/reports/kpis`); the other individual
formulas already matching spec (KPI-020, KPI-024, KPI-028) are kept and reused, not the console
page layout.

---

## 7. Files expected to change

**New:**
- `KpiDashboardService` (central calculation layer, one source of truth reused by every view)
- Per-view report DTOs (plain classes with getters, per the established convention)
- 2 new repository finder methods (`RescheduleRecordRepository`, `ReopenRecordRepository`)
- `fragments/reports-kpi-overview.jspf` + 4 sibling fragments (workflow/content/quality/performance)
- Dashboard CSS additions (cards, funnel, donut/bar visuals as CSS/inline-SVG — no charting library)

**Modified:**
- `ReportingMvcController` (`view=` query param + safe fallback to `overview`)
- `reports-kpi-console.jsp` → rewritten as the new dashboard shell
- `reports-workspace.js` (track `view` alongside the existing date-range params)
- `app.css`

---

## 8. Test plan

New `KpiDashboardServiceTest` (service-level, controlled fixtures), covering:

- **Date range**: start boundary, end boundary, future end-date capped to today, previous-period comparison
- **On-Time Delivery**: original cycle on time, original cycle late, unresolved/overdue, REPOST with fresh target, REPOST with target pending, multiple repost cycles, multiple Platform×Channel targets, N/A target handling, scope resolves only when all required targets resolved
- **Review**: first-pass approval, rework cycle, multiple ReviewCycles, Pending Reviews, Retained idea handling
- **Publishing**: ORIGINAL, REPOST, N/A targets, evidence corrections, platform/channel aggregation
- **Performance**: pending, overdue, submitted, N/A metrics excluded, corrected metrics use effective values, CTR ranking
- **Multi-function attribution**: an employee with a non-canonical Business Role who performs real Shoot/Edit work, verifying their contribution appears correctly

Existing `KpiServiceTest` guarantee (KPI-028 never negative, REPOST-filtered) must keep passing
unmodified.

---

## Summary

Everything else in the spec is SUPPORTED or DERIVABLE with a stated formula above — no
AMBIGUOUS/UNSUPPORTED items beyond the one algorithm in §4. Awaiting confirmation on that
algorithm before proceeding with implementation in the phased order from the source spec's §48.
