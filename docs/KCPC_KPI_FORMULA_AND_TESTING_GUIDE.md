# KCPC KPI Dashboard — Formula and Manual Testing Guide

**Scope:** `Reports → KPI Dashboard` (routes `/app/reports/kpis`, `?view=workflow|content|quality|performance`).
**Status:** Read-only audit of the code exactly as it exists in this working tree today. No formula, DTO, or query was changed to produce this document.
**Primary source file:** `src/main/java/com/kcpc/mkt/reporting/service/KpiDashboardService.java` (all line numbers below refer to this file unless stated otherwise).

> Every formula in this document was copied or paraphrased directly from the method that computes it, then cross-checked against the exact SQL/Java that runs. Where a formula could not be fully pinned down from static reading alone, that is stated explicitly instead of guessed.

---

## Document conventions

- **Implementation** blocks name the exact class/method/DTO field/JSP/test — copy-pasteable, not paraphrased.
- **Manual Test** blocks are the "no Java required" verification recipe.
- `Example only` marks synthetic numbers — never mistake these for production values.
- `IMPLEMENTATION MISMATCH` is reserved for a place where the running code provably disagrees with a previously-approved rule. Searched for throughout; each occurrence is called out at the point it's found and again in §21.

---

# 1. Executive Summary

The KPI Dashboard has **one calculation service**, `KpiDashboardService`, backing all 5 views (`overview`, `workflowSla`, `contentPublishing`, `qualityReviews`, `performance`). It is invoked from `ReportingMvcController#kpiDashboard` (`GET /app/reports/kpis`), which resolves `view`/`startDate`/`endDate`, defaults an invalid/missing view to `overview`, and puts exactly one dashboard DTO into the model per request. Every JSP fragment (`reports-kpi-{overview,workflow,content,quality,performance}.jspf`) renders that DTO's fields directly — no JSP performs its own arithmetic on a KPI value (bar/donut widths are the sole exception, and those are pure display scaling of the same number, covered in the chart-phase history, not a new calculation).

Every date-range filter in the service is `column::date between :from and :to` in raw native SQL, or the Java helper `inRange(Instant, LocalDate, LocalDate)`, which explicitly converts to `Asia/Kolkata` before comparing. **Both are inclusive on both ends.**

A handful of formulas were deliberately corrected earlier in this project's history (funnel cohort logic, Published Content wording, First-Pass Approval denominator, Hold no-data handling, duration/count display formatting) — those fixes are all still present in the code read for this document and are described as the **current, active** formula, not as history.

No `IMPLEMENTATION MISMATCH` against a previously-approved governed rule was found. Two **verification-worthy risks** (not confirmed bugs) were found and are flagged clearly in their own sections: a timezone-configuration risk (§10) and a minor non-DRY-but-identical duplicate query (§17).

---

# 2. Global Reporting Rules

## 2.1 Date Range

| Question | Answer | Where in code |
|---|---|---|
| Default range | `today - 29 days` → `today` (30 days inclusive) | `KpiDashboardService.buildContext` (line 207-212) and, independently, `ReportingMvcController.kpiDashboard` (line 222-224) compute the **same** default — both must be read together since the controller's default only matters when the browser sends no `startDate`/`endDate` at all |
| Start-date inclusion | Inclusive (`between :from and :to`, or `!d.isBefore(start)`) | every native query; `inRange()` |
| End-date inclusion | Inclusive | same |
| Compared as DATE or TIMESTAMP | **DATE.** Every native-SQL filter casts the timestamp column to `::date` before comparing (e.g. `submitted_at::date between :from and :to`). `inRange()` converts an `Instant` to a `LocalDate` in `Asia/Kolkata` before comparing. | throughout |
| Timezone used | `Asia/Kolkata`, via the constant `BUSINESS_ZONE = ZoneId.of("Asia/Kolkata")` (line 97) for every Java-side date computation (`ctx.today`, `inRange`, `mostRecentEntryIntoCurrentStatus` age, delay-day math). **The raw-SQL `::date` casts do not explicitly `SET TIME ZONE` anywhere in this codebase** — see §10 for the verification risk this creates. | line 97 |
| Is a future end date capped at today? | **Only for On-Time Delivery eligibility.** `DashboardContext.eligibleEnd = rangeEnd.isAfter(today) ? today : rangeEnd` (line 166, 176) is used exclusively inside `onTimeDelivery()` (line 401) to decide which Publishing-cycle deadlines are "due yet." Every other metric's date filter uses the raw `rangeStart`/`rangeEnd` as selected — if you pick a future `endDate`, counts like Submitted Ideas, Published Content, etc. simply return 0 for the future portion (there's no data there yet), they are **not** separately capped. | line 166, 401 |
| Does "Data as of" affect calculations? | **No — purely cosmetic.** `reportsDataAsOf` is set to `Instant.now()` in `ReportingMvcController.addReportsShellAttributes` (line 436) every time the page renders. It is not passed into `KpiDashboardService` and does not gate, filter, or snapshot anything. | `ReportingMvcController.java:436` |

**Example**
```
Selected: 26/07/2026 – 24/08/2026
Eligible: any record whose relevant timestamp, converted to Asia/Kolkata, falls
          on or between 2026-07-26 00:00:00 IST and 2026-08-24 23:59:59 IST.
```

## 2.2 Previous Period / Trend Comparison

**Not implemented anywhere in the current KPI Dashboard.** There is no previous-period calculation, no `previousStart`/`previousEnd` variable, no delta/trend arrow in any of the 5 view methods, DTOs, or JSP fragments. If a stakeholder sees a "vs last period" style comparison anywhere on these 5 screens, it is not coming from this dashboard's code — flag it as unexpected and re-verify the screen/route.

## 2.3 Empty-Denominator Behavior (global rule)

Every percentage in `KpiDashboardService` goes through one shared helper:

```java
private static BigDecimal rate(long numerator, long denominator) {
    if (denominator == 0) {
        return null;
    }
    return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
}
```
(line 616-622)

**Result: `denominator = 0` → `null` in the DTO → every JSP renders `-`.** Never `0%`, never an exception. This applies to every `rate(...)` call in the file: On-Time Delivery %, Approval Rate, Within SLA %, Rework Rate (all forms), Idea Rejection Rate, First-Pass Approval %, Evidence Correction Rate, Scorecard Completion %, Target Completion %.

Average-duration metrics (`Double`/`scalarDouble`) follow the same null-not-zero rule but via a different mechanism — Postgres `AVG()`/`MAX()` over zero matching rows returns SQL `NULL`, which `scalarDouble` (line 606-609) passes through as Java `null`. For On Hold specifically, this is additionally backed by an **explicit** `resumedHoldCountInRange` counter (line 699, `OnHoldSummaryDto`) rather than relying only on the implicit null — see §13.

**Display:** every duration value in the JSP goes through `${kcpc:days(value)}` (`DisplayNumber.days`, `src/main/java/com/kcpc/mkt/common/util/DisplayNumber.java`), which renders `null` as `-`, a whole number as `"N day"`/`"N days"` (singular only at exactly 1), and a fractional value rounded to 1 decimal (e.g. `"3.4 days"`). Average-count metrics (currently only Avg Impressions) go through `${kcpc:count(value)}` (`DisplayNumber.count`), a thousands-separated whole number, `-` for null. Neither function is ever used for percentages, which render as `${value}%` directly with their own `-`-on-empty `<c:choose>`.

---

# 3. OVERVIEW KPIs

View method: `KpiDashboardService.overview(User, LocalDate, LocalDate)` (line 428-455).
JSP: `src/main/webapp/WEB-INF/views/fragments/reports-kpi-overview.jspf`.
DTO: `OverviewDashboardDto`.

## KPI: Active WIP

**Meaning:** How many Content Plans are currently "in flight" (not finished, not dead) right now — a live snapshot, never date-ranged.

**Unit:** One **Content Plan / WorkflowInstance** (1:1 — `ContentPlan.workflowInstance` is a unique join). Not a raw event count.

**Formula**
```
Active WIP = COUNT(ContentPlan) WHERE currentStatusCode NOT IN (COMP, CAN, RJ)
```

**Numerator/Denominator:** Not a rate — a raw count.

**Included statuses:** everything not below — `IS, PA, RET, PL, PLRV, PLAP, SA, SIP, SRV, SAP, EA, ED, ERV, EAP, RFP, PUBG, PP, PFUP` (in practice, only `ContentPlan` rows exist at all once an Idea is Approved, so this is really `PL` through `PFUP`; `RET`/`PA`/`IS` have no `ContentPlan` yet so can never appear in this count — see `IdeaFunnelDto` for those).

**Excluded (terminal) statuses:** `COMP` (Completed), `CAN` (Cancelled), `RJ` (Rejected) — the constant `NON_TERMINAL_EXCLUSIONS = Set.of("COMP", "CAN", "RJ")` (line 98).

**Important — one Content ID counts in exactly one stage:** confirmed. `stageHealth()` (line 252) groups `ctx.activePlans` (already deduplicated by definition — one `ContentPlan` row per Content ID) by `stageLabel(currentStatusCode)`, a `switch` with no overlapping cases (line 222-231). A plan cannot be in two stages simultaneously.

**Date basis:** none — snapshot of `currentStatusCode` at request time, ignores selected date range entirely.

**Hold/Reschedule/Reopen/REPOST/N/A:** none of these affect Active WIP directly — a plan on Hold still counts (Hold is a parallel record, never a workflow status, ERD-CON-061). A Reopen only matters if it changes `currentStatusCode` back to a non-terminal one (e.g. reopening a Completed plan for Publishing moves it back to `RFP`/`PUBG`, making it Active WIP again).

**Empty-denominator behavior:** N/A (not a rate).

**Classification:** Snapshot.

**Implementation**
- Service: `KpiDashboardService`
- Method: `overview()` line 433 (`ctx.activePlans.size()`); population built in `DashboardContext` constructor line 178-180
- DTO field: `OverviewDashboardDto.getActiveWip()`
- View: `reports-kpi-overview.jspf`
- Tests: no dedicated `KpiDashboardServiceTest` assertion targets Active WIP directly by name (it is exercised indirectly via `multiFunctionEmployeeWorkCountsInStageHealthRegardlessOfBusinessRole`, which checks the Shoot stage's `active` count)

**Manual Test**
1. Open `/app/reports/kpis` (Overview, any date range — irrelevant here).
2. In the DB (or via the Pipeline screen), list every `content_plans` row joined to `workflow_instances.current_status_code`.
3. Count rows where `current_status_code NOT IN ('COMP','CAN','RJ')`.
4. Expected = that count.
5. UI should show that exact number on the "Active WIP" card.

---

## KPI: Delayed Deliverables

**Meaning:** Of the Active WIP plans, how many have already missed their current stage's approved target date.

**Unit:** Content Plan (subset of Active WIP).

**Formula**
```
Delayed = COUNT(active plan) WHERE currentApprovedTarget(plan) IS NOT NULL
                              AND currentApprovedTarget(plan) < today
```
(line 434-437, using the shared helper `currentApprovedTarget`, line 233-241)

**Current-stage target resolution (exact 3-branch mapping, line 233-241):**
```
Status ∈ {PL, PLRV, PLAP, SA, SIP, SRV}        → plan.plannedShootDate
Status ∈ {SAP, EA, ED, ERV}                    → plan.plannedEditDate
Status ∈ {EAP, RFP, PUBG, PP, PFUP}            → plan.plannedLiveDate
anything else (terminal/dormant)               → null (never counted delayed)
```

**Reschedule handling:** `plan.plannedShootDate/plannedEditDate/plannedLiveDate` are the CURRENT values on the `ContentPlan` row itself — a Reschedule (`AdminActionService`/`PlanningService`) updates these columns directly, so this always reflects the latest approved target, never a stale one. There is no separate "original vs current" distinction here (that distinction only exists for the historical On-Time Delivery cycle logic, §"On-Time Delivery" below — a completely different KPI).

**Today vs target comparison:** strictly `<` (line 266, `target.isBefore(ctx.today)`) — **a target of exactly today is NOT yet delayed.**
```
Target = today       → NOT delayed (isBefore is false when equal)
Target = yesterday   → delayed
```

**Terminal exclusions:** implicit — `ctx.activePlans` already excludes `COMP/CAN/RJ`, and a plan in `RET`/`PA` has no `ContentPlan` row to begin with.

**Date basis:** none — snapshot, like Active WIP.

**Empty-denominator behavior:** N/A (raw count).

**Classification:** Snapshot.

**Implementation**
- Method: `overview()` line 434-437
- DTO field: `OverviewDashboardDto.getDelayedDeliverables()`
- Also reused (identical logic) for `WorkflowSlaDashboardDto`'s `averageDelayDays`/`delayAging` population (line 657-660) and `stageHealth()`'s per-stage `delayed` column (line 265-268)
- View: `reports-kpi-overview.jspf`

**Manual Test**
```
Target = 20 Aug, Today = 21 Aug, Status = Shoot Assigned (SA)  → delayed (target < today)
Target = today                                                 → NOT delayed (target == today)
```
1. Pick one active plan; note its `currentStatusCode`.
2. Resolve its target using the 3-branch table above.
3. Compare to today's date (Asia/Kolkata) with strict `<`.
4. Cross-check the Stage Health table's "Delayed" column for that plan's stage.

---

## KPI: On-Time Delivery (Publishing)

**Meaning:** Of Publishing cycles (original launch + every subsequent REPOST cycle) whose deadline fell due in the selected period, what fraction resolved their full Publishing scope on or before that deadline. **This is a historical/cycle-based metric, unrelated to the current mutable `plannedLiveDate`.**

**Unit:** **one Publishing cycle** — never a permanent Content ID, never an individual Platform×Channel target. A single Content Plan can contribute multiple cycles (1 original + N reposts) to the same period.

### Original-cycle deadline reconstruction — exact 4-branch algorithm

`resolveOriginalCycleDeadline(plan, publishingReopensAsc, allReschedulesAsc)` (line 311-333):

```
firstReopenAt = first PUBLISHING_REOPEN's reopenedAt (or none)

Branch 1 — no PUBLISHING_REOPEN has ever happened:
    deadline = plan.plannedLiveDate                         (line 313-315)

Branch 2 — a reschedule exists BEFORE firstReopenAt (latestPreReopen):
    deadline = latestPreReopen.newPlannedLiveDate            (line 326-328)

Branch 3 — no pre-reopen reschedule, but a reschedule exists ON/AFTER firstReopenAt
           (earliestPostReopen — the pre-reopen loop still ran and found none):
    deadline = earliestPostReopen.priorPlannedLiveDate        (line 329-331)

Branch 4 — reopened, but NO reschedule at all exists (neither before nor after):
    deadline = plan.plannedLiveDate  (current, mutable value) (line 332)
```

Note the loop that classifies reschedules (line 319-325) walks ALL reschedules for the instance regardless of `stageContext` — a Shoot- or Edit-stage reschedule made before the first Publishing reopen still counts as `latestPreReopen` if it's the most recent one before `firstReopenAt` (the code comment at line 308-310 confirms this is intentional: "any stageContext considered since stageContext never restricts which date fields a reschedule changes").

### REPOST cycle deadline

`resolveRepostCycleDeadline(windowStart, windowEndExclusive, allReschedulesAsc)` (line 337-350):
```
deadline = the LATEST reschedule where:
             stageContext == PUBLISHING
             AND rescheduledAt >= windowStart
             AND (windowEndExclusive == null OR rescheduledAt < windowEndExclusive)
           → deadline = that reschedule's newPlannedLiveDate

If no such reschedule exists yet → deadline = null ("Target Pending")
```
Cycle windows (line 353-367): cycle *i*'s `windowStart` = reopen *i*'s `reopenedAt`; `windowEndExclusive` = the NEXT reopen's `reopenedAt`, or `null` for the still-open latest cycle. **Confirmed:** `stageContext = PUBLISHING` is a hard filter — a Shoot/Editing-stage reschedule made during a repost cycle's window does not count as that cycle's deadline.

**Target Pending → excluded:** confirmed at line 398-400 — `if (cycle.deadline() == null) continue;` before eligibility is even evaluated. A cycle with no fresh Publishing reschedule after its reopen never enters the denominator.

### Numerator — `PUBLICATION_SCOPE_RESOLVED` lookup

`scopeResolvedWithin(plan, windowStart, windowEndExclusive, ctx)` (line 372-389) scans `WorkflowTransitionHistory` for the row where `toStatusCode == PP` AND `triggerCommand == "PUBLICATION_SCOPE_RESOLVED"`, inside the cycle's window. This transition is fired by `PublishingService` (`doRecordActualPublication` line 290-293, `designateTargetNA` line 318-321) exactly when `isScopeResolved(plan)` (`PublishingService.java:415-435`) is true:

```
isScopeResolved = for every (PlannedOutput, PublicationTarget) mapping pair of the plan:
                     pair has a live post IN THE CURRENT PUBLISHING CYCLE
                     OR pair's latest N/A action == DESIGNATED
                   AND at least one pair exists
                   AND at least one pair is actually live (not all N/A)
```
"Current cycle" here is independently resolved by `PublishingService.currentPublishingCycleStart` (the latest `PUBLISHING_REOPEN.reopenedAt`, or `null` pre-first-reopen) and uses the event's immutable `recordedAt` (never the user-editable `actualPublicationTimestamp`) to decide whether an old event belongs to the current cycle — so a stale ORIGINAL from a prior, already-completed cycle can never satisfy a later repost cycle's resolution.

### Denominator — eligibility window
```
eligible iff cycle.deadline() != null
         AND rangeStart <= cycle.deadline() <= eligibleEnd     (eligibleEnd = min(rangeEnd, today))
```
(line 401-404) — **a future deadline is never counted eligible until it actually arrives** (confirmed by `eligibleEnd`).

### On time (numerator of the %)
```
onTime iff resolvedAt != null
       AND resolvedAt's IST date <= cycle.deadline()
```
(line 405-409) — resolution on the deadline date itself counts as on time (`<=`, not `<`).

**Result:**
```
percent = eligible == 0 ? null : round1( onTime * 100.0 / eligible )
```
(line 412-413)

### Multiple targets example
```
5 targets, 4 published, 1 unresolved (not N/A) → isScopeResolved() returns FALSE
                                                   (the unresolved non-N/A pair fails the loop)
                                                → PP transition never fires for this cycle
                                                → resolvedAt stays null for the whole selected period
                                                → this cycle counts eligible-but-NOT-on-time
                                                  once its deadline arrives (or already has)
```

### N/A effect
An N/A-designated target is **skipped** in the `isScopeResolved` AND-loop (treated as satisfied, not as blocking) — so a cycle with 4 live + 1 N/A target DOES resolve (as confirmed by the dedicated regression test below). N/A targets can never single-handedly prevent a cycle from ever resolving, but the DESIGNATE action itself is blocked if it would leave *every* target N/A (`wouldLeaveAllTargetsNa`, `PublishingService.java:308-311`).

**Empty-denominator behavior:** `eligible == 0` → percent is `null` → UI shows `-` (`reports-kpi-overview.jspf` line 24-25).

**Classification:** Historical Cycle Rate.

**Implementation**
- Service: `KpiDashboardService`
- Methods: `onTimeDelivery()` (line 393-415), `resolveOriginalCycleDeadline()` (311), `resolveRepostCycleDeadline()` (337), `publishingCyclesFor()` (353), `scopeResolvedWithin()` (372)
- Related: `PublishingService.isScopeResolved()` (fires the transition this KPI reads)
- DTO: `OnTimeDeliveryResult` (`eligibleCycles`, `onTimeCycles`, `percent`)
- View: `reports-kpi-overview.jspf` ("On-Time Delivery (Publishing)" card), `reports-kpi-workflow.jspf` ("On-Time Delivery %" card)
- Tests: `originalCycleOnTimeWhenScopeResolvedOnOrBeforeDeadline`, `originalCycleUnresolvedByDeadlineCountsAsEligibleButNotOnTime`, `futureDeadlineCycleIsNotJudgedPrematurely`, `repostCycleTargetPendingUntilFreshReschedule_thenEligibleAndOnTimeAgainstItsOwnDeadline`, `multipleConsecutiveRepostCyclesRemainIndependentlyScoped`, `onTimeDeliveryResolvesCorrectlyWithAnNaPublicationTarget`

**Manual Test**
1. Pick a Content Plan that has published ORIGINAL (query `content_plans`/`workflow_instances`).
2. Determine if it has any `reopen_records` with `reopen_purpose = 'PUBLISHING_REOPEN'`. If none → it only has the original cycle.
3. Apply the 4-branch algorithm above using its `reschedule_records` (ordered by `rescheduled_at`) to get the original deadline.
4. Find its `workflow_transition_history` row with `to_status_code = 'PP'` and `trigger_command = 'PUBLICATION_SCOPE_RESOLVED'`.
5. Compare that transition's IST date to the deadline — `<=` is on time.
6. Only count it if the deadline itself falls inside the selected date range and is not in the future.
7. Repeat per cycle (each `reopen_records` row starts a new cycle window) and aggregate `onTime / eligible`.

**Result examples** *(example only)*
```
ORIGINAL on time:        deadline 2026-08-10, resolved 2026-08-09 → on time
ORIGINAL late:            deadline 2026-08-10, resolved 2026-08-12 → eligible, not on time
unresolved overdue:       deadline 2026-08-10, never resolved      → eligible, not on time
future deadline:          deadline 2026-09-30 (range ends 2026-08-24) → NOT eligible yet
REPOST fresh target:      reopened, PUBLISHING reschedule exists → eligible against its own deadline
REPOST no target:         reopened, no PUBLISHING reschedule yet → "Target Pending", excluded entirely
two REPOST cycles:        each judged independently against its OWN deadline/resolution
```

---

## KPI: Published Content

**Meaning/Unit — verified:** `Distinct Content IDs first published in period` (the exact UI wording, `reports-kpi-overview.jspf` line 32-34) **matches the implementation exactly**:

```sql
select count(distinct content_plan_id) from actual_publication_events
where event_type = 'ORIGINAL' and actual_publication_timestamp::date between :from and :to
```
(`publishedContentCount`, line 502-508) — **distinct Content Plans**, not events, not PlannedOutputs, and REPOST is explicitly excluded (`event_type = 'ORIGINAL'`).

**Example:** one Content ID published ORIGINAL to Instagram, Facebook, and YouTube (3 `actual_publication_events` rows, same `content_plan_id`, same day) → counts as **1** here (distinct plan), but **3** in "ORIGINAL" (Content & Publishing headline card, a raw event count — see §5) and **3** in Platform Distribution. This is the single most likely place a manual tester double-counts — see §21.

**Date basis:** `actual_publication_timestamp` (the free-text, user-enterable business timestamp on the event — not the immutable `recordedAt`).

**Empty-denominator behavior:** N/A (raw count, `0` is a valid/expected value, never `-`).

**Classification:** Period Throughput.

**Implementation**
- Method: `publishedContentCount()` (line 502-508) — single source of truth, reused verbatim by `overview()` (line 441) and `contentPublishing()` (line 720) and `IdeaFunnelDto`'s `published` field (line 582-586, same ORIGINAL-only rule, additionally joined through `ideas` for the funnel's submitted-cohort scoping)
- DTO fields: `OverviewDashboardDto.getPublishedContent()`, `ContentPublishingDashboardDto.getPublishedContent()`
- View: both `reports-kpi-overview.jspf` and `reports-kpi-content.jspf`
- Tests: `publishedContentCountsDistinctContentPlansNotRawOriginalEvents`

**Manual Test**
1. Query `actual_publication_events` where `event_type='ORIGINAL'` and `actual_publication_timestamp::date` in range.
2. `COUNT(DISTINCT content_plan_id)`.
3. Compare to the card.

---

## KPI: Avg End-to-End Cycle Time

**Meaning:** Average time from an Idea's submission to its Content Plan's ORIGINAL Publishing scope fully resolving — the FIRST time it happens (cycle 0), never a later repost's resolution.

**Governed definition verification:** `Idea.submittedAt → original-cycle PUBLICATION_SCOPE_RESOLVED`. **Confirmed as implemented exactly.**

**Formula (line 461-475):**
```java
for each ContentPlan:
    resolvedAt = originalCycleResolvedAt(plan, ctx)     // scopeResolvedWithin(plan, null, firstReopenAt, ctx)
    skip if resolvedAt == null OR resolvedAt not in [rangeStart, rangeEnd]
    idea = the Idea whose workflowInstance == plan.workflowInstance
    skip if idea == null or idea.submittedAt == null
    cycleTimeDays = (resolvedAt - idea.submittedAt) in days (fractional, via ChronoUnit.SECONDS / 86400.0)
avg = mean(all cycleTimeDays), or null if the list is empty
```

- **Only completed original cycles** contribute — a plan whose ORIGINAL scope never resolved contributes nothing (correctly excluded, not counted as 0 or as still-running).
- **REPOST is excluded** — `originalCycleResolvedAt` (line 420-424) passes `windowStart=null, windowEndExclusive=firstReopenAt` into `scopeResolvedWithin`, i.e. only the transition that happened *before the first reopen* qualifies; a repost's own later resolution is never picked up here.
- **Date-range population basis:** the resolution timestamp (`resolvedAt`), not the idea's submission date. A cycle that started outside the range but resolved inside it is included; a cycle that started inside the range but hasn't resolved yet (or resolved outside the range) is excluded.

**Worked example** *(example only)*
```
Idea.submittedAt          = 2026-06-01 10:00 IST
Original PP transition    = 2026-06-15 18:30 IST   (inside selected range, so this cycle is included)
cycleTimeDays              = 14.35 days
```

**Empty-denominator behavior:** empty list → `null` → UI shows `-` via `${kcpc:days(...)}`.

**Classification:** Historical Cycle (average duration).

**Implementation**
- Method: `avgEndToEndCycleTimeDays()` (line 461-475), reused identically by `overview()` (443) and `workflowSla()` (654) — single source of truth
- DTO: `OverviewDashboardDto.getAvgEndToEndCycleTimeDays()`, `WorkflowSlaDashboardDto.getEndToEndCycleTimeDays()`
- View: `reports-kpi-overview.jspf`, `reports-kpi-workflow.jspf`
- Tests: exercised indirectly through the On-Time Delivery tests (same `originalCycleResolvedAt` boundary); no test asserts the averaged day-count value directly

**Manual Test**
1. Find plans whose original-cycle `PUBLICATION_SCOPE_RESOLVED` transition timestamp (IST date) falls in the selected range.
2. For each, find its Idea's `submitted_at`.
3. `(resolved_at - submitted_at)` in fractional days.
4. Average across all such plans.

---

## KPI: Rework Rate (All Stages)

**Formula (`productionReworkRate`, line 486-494):**
```sql
decided = count(*) from review_cycles
          where gate_type = ANY('PLANNING_REVIEW','SHOOT_REVIEW','EDIT_REVIEW')
            and decided_at is not null and decided_at::date between :from and :to
rework  = count(*) from review_cycles
          where gate_type = ANY(same 3 gates)
            and decision = 'REQUEST_REWORK' and decided_at::date between :from and :to
Rework Rate = rate(rework, decided)
```

**Stages included:** Planning, Shoot, Edit (`REVIEW_GATE_NAMES`, line 99-100). **Idea Review is explicitly excluded** — it uses a different gate (`IDEA_REVIEW`) never listed here.

**Denominator — confirmed:** **all decided ReviewCycles** for those 3 gates (any `cycle_number`), not first-cycle-only, not approved+rework-only. Rework can occur on any cycle number, so the denominator deliberately stays "every decided cycle" — this is a documented, deliberate choice (see the docstring at line 893-894: *"Rework % intentionally keeps the all-cycles denominator... matches the existing, already-governed KPI-024 shape"*).

**Example** *(example only)*
```
Planning: 33 decided cycles total, 1 REQUEST_REWORK decision → Rework Rate (Planning-only, via the
per-stage row) = 1/33 = 3.0%. "All Stages" pools Planning+Shoot+Edit numerators and denominators together.
```

**Empty-denominator:** `decided == 0` → `null` → `-`.

**Classification:** Rate (Period Throughput basis — scoped by `decided_at`, i.e. when the review was decided, not when it was submitted).

**Implementation**
- Method: `productionReworkRate()` (line 486-494), reused identically by `overview()` (445) and `qualityReviews()` (863, as `overallReworkRate`)
- DTO: `OverviewDashboardDto.getReworkRatePercent()`, `QualityReviewsDashboardDto.getOverallReworkRatePercent()`
- View: `reports-kpi-overview.jspf`, `reports-kpi-quality.jspf`

**Manual Test**
1. `SELECT count(*) FROM review_cycles WHERE gate_type IN ('PLANNING_REVIEW','SHOOT_REVIEW','EDIT_REVIEW') AND decided_at IS NOT NULL AND decided_at::date BETWEEN :from AND :to;`
2. Same query with `AND decision = 'REQUEST_REWORK'`.
3. Divide (2) by (1), ×100, round to 1 decimal.

---

## KPI: Pending Reviews

**Formula (`pendingReviewsCount`, line 498-500):**
```sql
select count(*) from review_cycles where decided_at is null
```

**Gates included:** ALL of them — `IDEA_REVIEW, PLANNING_REVIEW, SHOOT_REVIEW, EDIT_REVIEW` (no `gate_type` filter at all in this query). **Publishing has no `review_cycles` gate**, so it structurally can never appear here — not an active exclusion, just a fact about the schema (Publishing has no `ReviewCycle` row type).

**`decidedAt IS NULL` behavior:** any review submitted but not yet decided counts, regardless of age. There is no separate "stale" concept in this query — a review submitted 6 months ago and never decided still counts as pending forever (this is what feeds the separate "reviews pending longer than 2 days" Attention Needed rule, §7, which is a *different*, additionally-filtered query).

**Date-range basis:** **none — always a live, current-moment snapshot**, exactly like Active WIP. Selecting a different date range on the dashboard does not change this number.

**Classification:** Snapshot.

**Implementation**
- Method: `pendingReviewsCount()` (line 498-500), reused identically by `overview()` (446) and `qualityReviews()` (870)
- DTO: `OverviewDashboardDto.getPendingReviews()`, `QualityReviewsDashboardDto.getPendingReviews()`
- Tests: `pendingReviewsCountsUndecidedCyclesOnly`

**Manual Test**
1. `SELECT count(*) FROM review_cycles WHERE decided_at IS NULL;`
2. Compare directly — no date filter to apply.

---

## KPI: Performance Overdue

**Formula (`performanceOverdueCount`, line 510-513):**
```sql
select count(*) from performance_obligations
where is_completed = false and performance_due_date < :today
```

**Source entity:** `performance_obligations` (`PerformanceObligation`), one row per `ActualPublicationEvent` (1:1). `performance_due_date` is computed once at obligation-creation time as `actualPublicationTimestamp (IST) + 2 days` and is immutable (ERD-CON-048/016 — never reschedulable, per `PerformanceObligation` constructor and class doc).

**`completed` condition:** `PerformanceObligation.completed` is a plain boolean, set `true` only by `markCompleted()` — called when its `CreativePerformanceScorecard` is submitted (confirmed by `PerformanceService`'s submit flow, and by the `driveToCompleted`/`completeNewestObligation` test helpers which submit a scorecard and then assert the plan reaches `COMP`).

**`<` vs `<=`:** strictly **`<`** — `performance_due_date < today` — **a due date of exactly today is NOT yet overdue.** (Compare to Performance Pending, which has no date condition at all — see §7 Performance.)

**Selected-period behavior:** **none — always current-moment**, like Active WIP/Pending Reviews. The dashboard's date range does not filter this count.

**Difference from Performance Pending:** Pending = `is_completed = false` with **no date condition** (every open obligation, due or not); Overdue = `is_completed = false AND performance_due_date < today` (a strict subset of Pending — only the ones already late).

**Classification:** Snapshot.

**Implementation**
- Method: `performanceOverdueCount()` (line 510-513), reused identically by `overview()` (447), `attentionItems()` (530-533), and `performance()` (938)
- DTO: `OverviewDashboardDto.getPerformanceOverdue()`, `PerformanceDashboardDto.getPerformanceOverdue()`

**Manual Test**
1. `SELECT count(*) FROM performance_obligations WHERE is_completed = false AND performance_due_date < CURRENT_DATE;`
2. Compare directly.

---

# 4. Overview — Stage Bottleneck Summary

Shared computation: `stageHealth(DashboardContext ctx)` (line 252-287) — **the exact same method** backs Overview's "Stage Bottleneck Summary" table and Workflow & SLA's "Stage Health" table (spec §43: same-number guarantee).

**Columns:** `Stage | Active | Delayed | Avg Age | Oldest Item` (Overview) — Workflow & SLA adds `Within SLA %` between Delayed and Avg Age (documented in §6).

**Status → Stage mapping (`stageLabel`, line 222-231), identical set to the Delayed Deliverables/`StageSqlFragments` mapping used elsewhere in the app:**

| Stage | Status codes |
|---|---|
| Planning | `PL, PLRV` |
| Shoot | `PLAP, SA, SIP, SRV, SAP` |
| Edit | `EA, ED, ERV` |
| Publishing | `EAP, RFP, PUBG` |
| Performance | `PP, PFUP` |

(`COMP/CAN/RJ` never appear here — already excluded from `ctx.activePlans` before grouping.)

**Active:** count of plans currently in that stage (`plans.size()`, line 259).

**Delayed:** within that stage's plans, count where `currentApprovedTarget(plan) < today` (same rule as the Overview "Delayed Deliverables" card, line 265-268, applied per-stage).

### Avg Age

**Starting timestamp:** **the most recent transition INTO the plan's current exact status** — `mostRecentEntryIntoCurrentStatus(plan, ctx)` (line 289-299): walks `WorkflowTransitionHistory` in ascending order and keeps the LAST row whose `toStatusCode == currentStatusCode`. **Not** content creation, **not** stage-group entry (e.g. entering `SA` then later `SIP` then being sent back to `SA` by a rework loop resets the age clock to that most-recent `SA` re-entry, not the plan's very first arrival in `SA`, and not its first arrival in the "Shoot" stage group either).

**Ending timestamp:** `Instant.now()` at request time.

**Formula:** `ageDays = (now - enteredCurrentStatus) in fractional days`; `avgAgeDays = mean(ageDays across all plans in that stage)`, or `null` if the stage is empty (no rows ever had a matching transition — practically only possible for a plan that somehow has no transition history at all, which shouldn't occur for any real active plan).

### Oldest Item

```
oldestAge = max(ageDays) in that stage's plan list
oldestContentId = the plan.contentId achieving that max
```
(line 262-284) — ties are **NOT explicitly broken**; the loop keeps updating on strict `>`, so among equal ages the **first plan encountered in iteration order** wins (iteration order = `activePlans` order = `contentPlanRepository.findAllByOrderByCreatedAtDesc()`, i.e. newest-created-first — so on an exact tie, the newer plan's Content ID is shown).

**Display:** `"10 days (C-0826-0007)"` — `${kcpc:days(row.oldestItemAgeDays)}` (a `Long`, whole days already rounded via `Math.round(oldestAge)` at line 283) followed by ` (${row.oldestItemContentId})`.

**Empty-denominator behavior:** an empty stage → `active=0, delayed=0, avgAgeDays=null → "-" , oldestItemAgeDays=null → "-"`.

**Classification:** Snapshot (Active/Delayed/Oldest), Snapshot Average (Avg Age).

**Implementation**
- Method: `stageHealth()` (line 252-287), `mostRecentEntryIntoCurrentStatus()` (289-299)
- DTO: `StageHealthRow` (`stage, active, delayed, withinSlaPercent, avgAgeDays, oldestItemAgeDays, oldestItemContentId`)
- View: `reports-kpi-overview.jspf` (Stage Bottleneck Summary table), `reports-kpi-workflow.jspf` (Stage Health table)
- Tests: `multiFunctionEmployeeWorkCountsInStageHealthRegardlessOfBusinessRole`

**Manual Test**
1. Pick a stage, e.g. Shoot.
2. `SELECT cp.content_id, wi.current_status_code FROM content_plans cp JOIN workflow_instances wi ON ... WHERE wi.current_status_code IN ('PLAP','SA','SIP','SRV','SAP');` → this is "Active" for Shoot.
3. For each row, find the LAST `workflow_transition_history` row with `to_status_code = wi.current_status_code`.
4. `age = now - that transition's timestamp`.
5. Average → Avg Age; max + its content_id → Oldest Item.

---

# 5. Overview — Attention Needed

`attentionItems(ctx, stageHealth, performanceOverdue)` (line 517-548) — 4 hardcoded rules, evaluated every request, each conditionally appended only when its count is `> 0`. **All thresholds below are hardcoded literals in Java, not configurable anywhere in the UI/DB.**

| Rule | Source | Threshold | Count formula | Link |
|---|---|---|---|---|
| "N items delayed in Planning" | `stageHealth` list, filtered to `stage == "Planning"` | > 0 | `planningRow.getDelayed()` (reuses §4's Delayed) | `/app/reports/delayed?stage=Planning` |
| "N reviews pending longer than 2 days" | `review_cycles` | hardcoded 2 days | `count(*) where decided_at is null and submitted_at < now() - 2 days` | `/app/reviews` |
| "N performance scorecards overdue" | `performance_obligations` | > 0 | reuses §3's `performanceOverdueCount` verbatim | `/app/reports/kpis?view=performance` |
| "N content items on hold" | `work_hold_records` | > 0 | `workHoldRecordRepository.findByResumedAtIsNull().size()` (same live-open-Hold count as Workflow & SLA's "On Hold Count", §6) | `/app/reports/kpis?view=workflow` |
| "N high priority items delayed" | `content_plans` joined `workflow_instances`, `StageSqlFragments.STAGE_PLANNED_DATE_CASE` | > 0 | `count(*) where current_status_code NOT IN ('COMP','CAN','RJ') AND content_priority='HIGH' AND <stage target> < today` | `/app/reports/delayed?priority=HIGH` |

None of these 5 rules is date-range-scoped by the dashboard's selected range — all are live/current-moment snapshots (the "2 days" rule uses `Instant.now()`, not the selected range).

**Classification:** Snapshot (all 5).

**Implementation**
- Method: `attentionItems()` (line 517-548)
- DTO: `AttentionItem` (`message, count, linkHref`)
- View: `reports-kpi-overview.jspf`

**Manual Test** — pick the rule you want to verify and run its listed SQL directly; compare the resulting count to the number shown in that list item.

---

# 6. WORKFLOW & SLA KPIs

View method: `workflowSla()` (line 626-677). JSP: `reports-kpi-workflow.jspf`. DTO: `WorkflowSlaDashboardDto`.

## Within SLA %

**Formula (embedded in `stageHealth()`, line 279-280):**
```
withinSlaPercent = active == 0 ? null : round1( (active - delayed) * 100.0 / active )
```
**Confirmed exact match to the approved formula** `(Active - Delayed) / Active × 100`.

- **Snapshot metric, not historical SLA completion** — confirmed, this reuses the exact same live `active`/`delayed` counts from §4, evaluated at request time.
- **Completed items excluded:** yes — `active` only ever contains non-terminal plans (`ctx.activePlans`), so a plan that finished late (or on time) after leaving the active pool never affects this number either way.
- **`Active = 0` behavior:** `null` → `-`.

**Example** *(example only)*
```
Shoot stage: Active = 12, Delayed = 3 → Within SLA % = (12-3)/12 × 100 = 75.0%
```

**Classification:** Snapshot Rate.

**Implementation**
- Method: `stageHealth()` line 279-280
- DTO: `StageHealthRow.getWithinSlaPercent()`
- View: `reports-kpi-workflow.jspf` (table column + the "Within SLA % by Stage" bar chart added in the visual-analytics phase, both reading the same field)

---

## Planning Turnaround Time

**Formula (line 633-641):**
```sql
select avg(extract(epoch from (plap.ts - pl.ts)) / 86400.0)
from (select workflow_instance_id, min(transition_timestamp) ts
      from workflow_transition_history where to_status_code = 'PL' group by workflow_instance_id) pl
join (select workflow_instance_id, min(transition_timestamp) ts
      from workflow_transition_history where to_status_code = 'PLAP' group by workflow_instance_id) plap
  on plap.workflow_instance_id = pl.workflow_instance_id
where plap.ts >= pl.ts and plap.ts::date between :from and :to
```

**Start/end transitions:** first-ever entry into `PL` (Planning) → first-ever entry into `PLAP` (Planning Approved). Uses `MIN(transition_timestamp)` for each side, so a plan that was sent back to `PL` by a rework loop and re-entered `PLAP` later still uses its **very first** `PL` and **very first** `PLAP` timestamps (not the most recent).

**Inclusion:** only plans where a `PLAP` entry exists on or after its `PL` entry, and that `PLAP` timestamp's IST date falls in the selected range. Plans still stuck before `PLAP` are excluded entirely (never counted as 0 or as an outlier).

**Classification:** Average Duration.

**Implementation:** DTO `WorkflowSlaDashboardDto.getPlanningTurnaroundDays()`; View `reports-kpi-workflow.jspf` ("Planning Turnaround Time" card).

---

## Shoot-to-Publish Cycle Time

**Formula (line 643-652), the existing KPI-028 shape reused:**
```sql
select avg(extract(epoch from (last_pub.max_ts - sap.first_sap)) / 86400.0)
from (select workflow_instance_id, min(transition_timestamp) first_sap
      from workflow_transition_history where to_status_code = 'SAP' group by workflow_instance_id) sap
join content_plans cp on cp.workflow_instance_id = sap.workflow_instance_id
join (select content_plan_id, max(actual_publication_timestamp) max_ts
      from actual_publication_events where event_type = 'ORIGINAL' group by content_plan_id) last_pub
  on last_pub.content_plan_id = cp.content_plan_id
where last_pub.max_ts >= sap.first_sap and last_pub.max_ts::date between :from and :to
```

**Start:** first-ever entry into `SAP` (Shoot Approved). **End:** `MAX(actual_publication_timestamp)` among that plan's **ORIGINAL** events only. If a plan has more than one ORIGINAL event (one per required target), the LATEST one is used as "publish complete."

**ORIGINAL-only, REPOST excluded:** confirmed (`event_type = 'ORIGINAL'` filter).

**Negative-duration protection:** `last_pub.max_ts >= sap.first_sap` guards against a nonsensical negative duration entering the average (would only happen from a backdated/erroneous `actual_publication_timestamp`).

**Classification:** Average Duration.

---

## Average Delay

**Formula (line 657-664):**
```
delayedActive = activePlans where currentApprovedTarget(plan) < today       (exact same rule as §3 Delayed)
delayDays[p]  = today - currentApprovedTarget(p), in whole days (ChronoUnit.DAYS)
averageDelayDays = mean(delayDays), or null if delayedActive is empty
```

**Population — verified:** average is over **currently-delayed ACTIVE items only** (not completed-late items) — matches the expected governed definition exactly.

**Why completed late items are excluded:** they've left `activePlans` (status is `COMP`), so `currentApprovedTarget` returns `null` for them via the `default -> null` branch — structurally impossible for them to appear here, by design of the same shared helper used everywhere delay is computed.

**Classification:** Snapshot Average.

**Implementation:** DTO `WorkflowSlaDashboardDto.getAverageDelayDays()`.

---

## Delay Aging

**Buckets (`delayAgingBuckets`, line 682-689), applied to the SAME `delayDaysList` as Average Delay above:**

| Bucket | Filter |
|---|---|
| `0-2 days` | `d >= 0 && d <= 2` |
| `3-5 days` | `d >= 3 && d <= 5` |
| `6-10 days` | `d >= 6 && d <= 10` |
| `11+ days` | `d >= 11` |

**Boundary behavior (confirmed inclusive/exclusive per literal Java comparison):**
```
exactly 2 days   → bucket "0-2 days"    (2 <= 2)
exactly 3 days   → bucket "3-5 days"    (3 >= 3)
exactly 5 days   → bucket "3-5 days"    (5 <= 5)
exactly 6 days   → bucket "6-10 days"   (6 >= 6)
exactly 10 days  → bucket "6-10 days"   (10 <= 10)
exactly 11 days  → bucket "11+ days"    (11 >= 11)
```
Buckets are contiguous and non-overlapping by construction — every delayed active item lands in exactly one bucket.

**Classification:** Distribution.

**Implementation:** DTO `DelayAgingBucket` (`label, count`), `WorkflowSlaDashboardDto.getDelayAging()`.

---

## On Hold Count

**Formula (line 698, reused at line 534 for Attention Needed):**
```sql
-- via WorkHoldRecordRepository.findByResumedAtIsNull()
select * from work_hold_records where resumed_at is null
```
**Source entity:** `WorkHoldRecord` (`work_hold_records`). **Active Hold condition:** `resumedAt IS NULL` (an open, not-yet-resumed hold). **Current point-in-time, not selected-period** — same live-snapshot rule as everything else that has no date filter.

**Classification:** Snapshot.

---

## Avg Hold Duration / Longest Hold

**Formula (`onHoldSummary`, line 697-711):**
```sql
resumedCount = select count(*) from work_hold_records
               where resumed_at is not null and resumed_at::date between :from and :to

avgDuration  = resumedCount == 0 ? null :
               select avg(extract(epoch from (resumed_at - held_at)) / 86400.0) from work_hold_records
               where resumed_at is not null and resumed_at::date between :from and :to

longest      = resumedCount == 0 ? null :
               select max(extract(epoch from (resumed_at - held_at)) / 86400.0) from work_hold_records
               where resumed_at is not null and resumed_at::date between :from and :to
```

**Source records:** only **RESUMED (completed) Holds** — an open Hold has no finished duration to average, correctly excluded from both avg and longest.

**Selected-period population:** filtered by `resumed_at::date` (when the hold ENDED), not `held_at` (when it started).

**`resumedHoldCountInRange`:** an **explicit, separately-computed** `COUNT(*)` (line 699-701) that gates both the avg and longest queries — `avgDuration`/`longest` are `null` whenever this count is `0`, by direct `if` check, never by relying only on Postgres's `AVG()`-over-empty-set-returns-NULL behavior. `OnHoldSummaryDto.getResumedHoldCountInRange()` exposes this as its own field, so a tester (or future developer) can distinguish "genuinely no completed holds" from any other null cause with a single, unambiguous number.

**No-data behavior:** `-` (never `0 days`) — confirmed both at the service layer (explicit `null` when count is 0) and the display layer (`${kcpc:days(...)}` renders `null` as `-`).

**Classification:** Average Duration.

**Implementation**
- Method: `onHoldSummary()` (line 697-711)
- DTO: `OnHoldSummaryDto` (`openCount, resumedHoldCountInRange, avgHoldDurationDays, longestHoldDurationDays`)
- View: `reports-kpi-workflow.jspf`
- Tests: `holdDurationAveragesAreNullNotZeroWhenNoCompletedHoldsInRange`, `holdDurationAveragesAreAvailableOnceACompletedHoldExistsInRange`

**Manual Test**
1. `SELECT count(*) FROM work_hold_records WHERE resumed_at IS NOT NULL AND resumed_at::date BETWEEN :from AND :to;`
2. If 0 → UI must show `-` for both Avg and Longest.
3. If > 0 → `SELECT avg(extract(epoch from (resumed_at-held_at))/86400.0), max(...)` and compare.

---

## Reopened Content

**Formula (line 669-670):**
```sql
select count(*) from reopen_records where reopened_at::date between :from and :to
```
**Unit — confirmed:** raw **ReopenRecord row count**, not distinct Content IDs. **All reopen purposes included** — no `reopen_purpose` filter at all (`RETAINED_REOPEN`, `PUBLISHING_REOPEN`, and `METRIC_CORRECTION_REOPEN` are all counted together; **this is different from `publishingReopensByInstance`**, which the On-Time Delivery logic filters to `PUBLISHING_REOPEN` only — a plan reopened twice, once for Publishing and once for a metric correction, contributes **2** to this count).

**Classification:** Period Throughput.

---

## REPOST Publications

**Formula (line 671-673):**
```sql
select count(*) from actual_publication_events
where event_type = 'REPOST' and actual_publication_timestamp::date between :from and :to
```
**Unit — confirmed:** raw **event count**, not distinct Content IDs (same shape as "ORIGINAL" in Content & Publishing — see §7).

**Classification:** Period Throughput.

---

# 7. CONTENT & PUBLISHING KPIs

View method: `contentPublishing()` (line 715-761). JSP: `reports-kpi-content.jspf`. DTO: `ContentPublishingDashboardDto`.

## Target Completion %

**Exact governed formula — confirmed match:**
```
Target Completion % = Published (non-N/A) / (Published + Pending) (non-N/A) × 100
```
(`publishingTargetCompletion`, line 794-837)

**Mapping unit:** one `(PlannedOutput, PublicationTarget)` pair — i.e. `PlannedOutputPublicationTargetMapping`, **not** a raw event and **not** a Content ID.

**Population/date basis:** `PlannedOutput.createdAt` in the selected range (line 796) — **NOT** the publication's own timestamp. This is the same date basis as Planned Output Mix (§ below), and is a **different population** from Published Content/ORIGINAL/REPOST, which are filtered by `actual_publication_timestamp` — an output created in-range whose target later publishes out-of-range still counts here; conversely a publication event dated in-range whose output was created outside the range never appears in this metric at all. **Flagged as a Critical Warning in §21.**

**N/A treatment (line 815-834):** for each mapping, its target's LATEST `PublicationTargetNaRecord` (by `recordedAt`) is checked; if `actionType == DESIGNATED` → counted as `na++` and **skipped entirely** (never `published++` or `pending++`). Otherwise: if an ORIGINAL event exists for that (output, target) pair → `published++`; else → `pending++`.

**Reopened-cycle treatment:** the `published` check here is `eventsByOutput` = ANY ORIGINAL event ever recorded for that output/target (`eventRepository.findByContentPlan_IdIn(...)`, filtered `EventType.ORIGINAL`, line 808-810) — **not** cycle-aware the way On-Time Delivery is. A target published once, ever, stays "Published" here regardless of any later reopen — this KPI does not track repost-cycle freshness at all, only "has an ORIGINAL ever happened."

**Empty population:** `outputs.isEmpty()` → returns `TargetCompletionDto(0, 0, 0, null)` directly (line 798-800) — completion percent is `null` → `-`.

**Worked example** *(example only)*
```
Published = 47, Pending = 110, N/A = 4
Completion = 47 / (47 + 110) = 29.94% → displayed as 29.9%
```
(rate() rounds HALF_UP to 1 decimal — 29.9%, not 29.94%)

**Classification:** Rate.

**Implementation**
- Method: `publishingTargetCompletion()` (line 794-837)
- DTO: `TargetCompletionDto` (`publishedCount, pendingCount, naCount, completionPercent`)
- View: `reports-kpi-content.jspf` ("Target Completion %" card + "Planned vs Published Targets" donut/legend)
- Tests: `publishingTargetCompletionExcludesNaFromNumeratorAndDenominator`

---

## ORIGINAL / REPOST (headline cards)

**Formula (line 721-726):**
```sql
originalCount = count(*) from actual_publication_events where event_type='ORIGINAL' and <date range>
repostCount   = count(*) from actual_publication_events where event_type='REPOST'  and <date range>
```
**Unit — confirmed: raw publication EVENTS**, not Content IDs, not PlannedOutputs. A single Content ID with 3 targets publishing ORIGINAL the same day contributes **3** to `originalCount` (contrast with Published Content, §3, which counts that same scenario as **1**).

**Classification:** Period Throughput (both).

---

## Evidence Correction Records / Rate

**Two distinct measures, confirmed separate in both formula and label:**

```sql
rawCorrections = count(*) from publication_evidence_corrections pec
                 join actual_publication_events e on e.event_id = pec.event_id
                 where e.actual_publication_timestamp::date between :from and :to

distinctCorrectedEvents = count(DISTINCT pec.event_id) from ... (same join/filter)

correctionRate = rate(distinctCorrectedEvents, originalCount + repostCount)
```
(line 728-739)

**Worked example** *(example only)*
```
Event A → 3 correction records
Event B → 1 correction record
Total ORIGINAL+REPOST events in range = 10

Raw corrections   = 4        (3 + 1)
Distinct corrected events = 2   (A and B, each counted once)
Correction rate   = 2 / 10 = 20.0%
```
**Confirmed: the rate uses `distinctCorrectedEvents`, never `rawCorrections`, as its numerator** — a second correction on the same event adds to the raw count but never inflates the rate.

**Classification:** Count (raw) + Rate (%).

**Implementation**
- DTO: `ContentPublishingDashboardDto.getEvidenceCorrectionCount()` (raw), `getEvidenceCorrectionRatePercent()`
- View: `reports-kpi-content.jspf` ("Evidence Correction Records" card — "N correction records · X% of publication events affected")
- Tests: `evidenceCorrectionRateCountsDistinctEventsNotRawCorrectionRecords`

---

## Planned Output Mix

**Source entity:** `planned_outputs` (`PlannedOutput`), grouped by `output_type, reel_type` (line 774-786).

**Date basis:** `PlannedOutput.createdAt` in range — same caveat as Target Completion above (independent of any publication timestamp).

**Categories (actual system enum values, never invented — confirmed against `OutputType`/`ReelType` enums):**
```
PHOTOGRAPHY   (OutputType.PHOTOGRAPHY, reelType always null)
VIDEO         (OutputType.VIDEO, reelType always null)
REEL · VERY_SHORT   (OutputType.REEL, ReelType.VERY_SHORT)
REEL · SHORT        (OutputType.REEL, ReelType.SHORT)
REEL · LONG         (OutputType.REEL, ReelType.LONG)
```
Label construction: `reelType != null ? "REEL · " + reelType : outputType` (line 781-783).

**Can one output appear multiple times?** No — one `planned_outputs` row = one output = one category bucket. However, a single "+ Add Output" REEL submission with multiple reel types selected creates **multiple separate rows** sharing a `reel_group_id` (one row per reel type) — each row is counted in its OWN reel-type bucket, never merged. Non-REEL outputs are always a "group of one."

**Classification:** Distribution.

**Implementation:** DTO `ContentPublishingDashboardDto.getContentMix()` → `List<LabelCountRow>`; View "Planned Output Mix" (donut chart, visual-analytics phase — label explicitly says "Planned outputs in selected cohort" to avoid confusion with published content, per an earlier corrective pass).

---

## Platform Distribution / Channel Distribution (Top 5)

**Source (line 742-755):** both group `actual_publication_events` (an EVENT-level count, joined to `publication_targets` → `platforms`/`company_channels`), filtered by `actual_publication_timestamp` in range. **No `event_type` filter** — ORIGINAL and REPOST events are pooled together here (unlike the headline "ORIGINAL"/"REPOST" cards, which separate them).

**Dynamic Publishing Catalogue — confirmed:** platform/channel names come from `platforms.platform_name` / `company_channels.channel_handle` via joins, never a hardcoded list — whatever is configured in the Publishing Catalogue is what appears.

**Channel Distribution is server-side `LIMIT 5, ORDER BY count(*) DESC`** — Top 5 by volume; Platform Distribution has **no limit** (all platforms with at least one event in range appear).

**Classification:** Distribution.

---

## Planned vs Published Targets

Documented fully under "Target Completion %" above — `Published/Pending/N/A` are the three raw counts (`TargetCompletionDto.publishedCount/pendingCount/naCount`) that feed that percentage; the donut/legend visualization shows all three, with N/A explicitly labeled "(informational)" and visually excluded from the completion ring itself (only Published/Pending drive the ring's arc, matching the governed denominator exactly).

---

# 8. QUALITY & REVIEWS KPIs

View method: `qualityReviews()` (line 850-886). JSP: `reports-kpi-quality.jspf`. DTO: `QualityReviewsDashboardDto`.

## First-Pass Approval Rate

**Exact formula — confirmed match to governed rule (line 855-861):**
```sql
firstPassDecided  = count(*) from review_cycles
                     where gate_type = ANY(3 production gates)
                       and cycle_number = 1 and decided_at is not null and decided_at::date in range
firstPassApproved = count(*) from review_cycles
                     where gate_type = ANY(same) and cycle_number = 1
                       and decision = 'APPROVED' and decided_at::date in range
First-Pass Approval Rate = rate(firstPassApproved, firstPassDecided)
```
**Denominator is `cycle_number = 1` decided reviews — never all decided reviews.** This headline card was already correct; the bug (fixed earlier) was isolated to the *per-stage table row* (see "Stage-wise Review Performance" below).

**Worked example** *(example only)*
```
33 total review cycles (across a stage), 25 of them are cycle_number=1, 22 of those cycle_number=1 ones are APPROVED
First-Pass Approval = 22 / 25 = 88.0%   (NOT 22/33 = 66.7%)
```

**Classification:** Rate.

---

## Overall Rework Rate

Identical to Overview's "Rework Rate (All Stages)", §3 — same method (`productionReworkRate`), same denominator (all decided cycles across the 3 production gates), reused verbatim (line 863).

---

## Avg Review Turnaround

**Formula (line 865-868):**
```sql
select avg(extract(epoch from (decided_at - submitted_at)) / 86400.0)
from review_cycles where decided_at is not null and decided_at::date between :from and :to
```
**All gates** (no `gate_type` filter — includes `IDEA_REVIEW` too, unlike the Rework Rate metrics). **Unresolved reviews excluded** — `decided_at is not null` is required both for inclusion and for the subtraction to even be computable.

**Classification:** Average Duration.

---

## Rework Rate by Stage

Per-gate slice of the same `reviewStageRows()` computation documented next — `rate(rework, total)` where `total` = all decided cycles for that specific gate (line 912-914, 920). **Publishing is excluded** because it structurally has no `review_cycles` gate at all (there is no `GateType.PUBLISHING_REVIEW`).

---

## Stage-wise Review Performance

**Per-stage columns (`reviewStageRows`, line 895-923), one row per gate in `{Planning, Shoot, Edit}`:**

| Column | Formula |
|---|---|
| Total Reviews | `count(*) where gate_type=:gate and decided_at is not null and decided_at::date in range` |
| First-Cycle Reviews | `count(*) where gate_type=:gate and cycle_number=1 and decided_at is not null and decided_at::date in range` |
| First-Pass Approved | `count(*) where gate_type=:gate and cycle_number=1 and decision='APPROVED' and decided_at::date in range` |
| Rework | `count(*) where gate_type=:gate and decision='REQUEST_REWORK' and decided_at::date in range` |
| Avg Review Time | `avg(decided_at - submitted_at)` for that gate, decided-in-range |
| First-Pass Approved % | `rate(First-Pass Approved, First-Cycle Reviews)` ← **the corrected denominator** |

**Why First-Pass % uses First-Cycle Reviews, not Total Reviews:** a stage with any rework has `Total Reviews > First-Cycle Reviews` (later cycle-2+ decisions inflate Total but were never eligible to be a "first pass" at all) — dividing by Total would silently understate the rate. This was a fixed bug (see project history) and is verified still fixed in the current code (line 906-920).

**Classification:** Rate + Count (table).

**Implementation**
- Method: `reviewStageRows()` (line 895-923)
- DTO: `ReviewStageRow` (`stage, totalReviews, firstCycleReviews, firstPassApproved, rework, avgReviewTimeDays, firstPassApprovedPercent, reworkPercent`)
- View: `reports-kpi-quality.jspf` ("Stage-wise Review Performance" table)
- Tests: `firstPassApprovalVsReworkCycleAreDistinguished`

**Manual Test**
1. `SELECT count(*) FROM review_cycles WHERE gate_type='PLANNING_REVIEW' AND cycle_number=1 AND decided_at IS NOT NULL AND decided_at::date BETWEEN :from AND :to;` → First-Cycle Reviews
2. Same with `AND decision='APPROVED'` → First-Pass Approved
3. Divide (2)/(1) — compare to the displayed %.

---

## Idea Rejection Rate

**Formula (line 872-878):**
```sql
ideaApproved = count(*) from review_cycles where gate_type='IDEA_REVIEW' and decision='APPROVED' and decided_at::date in range
ideaRejected = count(*) from review_cycles where gate_type='IDEA_REVIEW' and decision='REJECTED' and decided_at::date in range
Idea Rejection Rate = rate(ideaRejected, ideaApproved + ideaRejected)
```
**Retained handling — confirmed excluded from the denominator**, exactly mirroring the funnel's Approval Rate shape (Approved+Rejected only). **Note this is decided-at-scoped**, unlike the Funnel's Approved/Retained/Rejected counts (§9), which are submitted-cohort-scoped — **these two "Approved"/"Rejected" concepts use different populations and will not generally match the Funnel's numbers for the same date range.** Flagged in §21.

**Classification:** Rate.

---

# 9. PERFORMANCE KPIs

View method: `performance()` (line 931-971). JSP: `reports-kpi-performance.jspf`. DTO: `PerformanceDashboardDto`.

## Performance Pending

```sql
select count(*) from performance_obligations where is_completed = false
```
(line 936-937) — **no date condition at all**, current-moment snapshot of every open obligation regardless of due date.

## Performance Overdue

Identical to §3's `performanceOverdueCount` — a strict subset of Pending (`is_completed=false AND performance_due_date < today`).

**Difference, confirmed:** Pending = ALL open obligations; Overdue = only the ones already past due. Every Overdue obligation is also Pending; not every Pending obligation is Overdue.

## Scorecard Completion %

**Formula (line 940-946):**
```sql
obligationsDue       = count(*) from performance_obligations where performance_due_date between :from and :to
obligationsSubmitted = count(*) from performance_obligations po
                        join creative_performance_scorecards s on s.obligation_id = po.obligation_id
                        where s.submitted_at is not null and po.performance_due_date between :from and :to
Scorecard Completion % = rate(obligationsSubmitted, obligationsDue)
```
**Population:** only obligations whose `performance_due_date` (not the publication date) falls in the selected range. **Submitted vs draft distinction:** confirmed — `s.submitted_at is not null` is required; a scorecard saved as a draft only (never submitted) does NOT count toward the numerator, even though a `creative_performance_scorecards` row exists for it. **Overdue/incomplete behavior:** an obligation due in range but never even drafted, or drafted-but-not-submitted, simply doesn't contribute to the numerator — it still counts in the denominator (`obligationsDue`), correctly dragging the % down.

**Classification:** Rate.

## Avg Delay in Reporting

**Formula (line 948-953):**
```sql
select avg(extract(epoch from (s.submitted_at - po.performance_due_date::timestamp)) / 86400.0)
from performance_obligations po join creative_performance_scorecards s on s.obligation_id = po.obligation_id
where s.submitted_at is not null and po.performance_due_date between :from and :to
```
**Confirmed:** `submittedAt - performanceDueDate`, **for every submitted scorecard in range, not "only when late."** An on-time submission (submitted before its due date) produces a **negative** number in this average, pulling the average down (potentially negative overall if most submissions are early) — it is **not excluded**, and does **not** contribute a `0`. If your intent was "average lateness of late submissions only," that is **not** what this query computes — it is a straightforward average of `(submitted − due)` across every submission, positive or negative, in range.

> This is worth a second look if the stakeholder-facing intent was ever "how late are we, on average, among the late ones" — as implemented, an org that's mostly early will show a **negative** "Avg Delay in Reporting," which is arithmetically correct for this formula but could read as confusing on the card. Not flagged as an `IMPLEMENTATION MISMATCH` (no prior governed rule was found stating it should exclude on-time submissions) — flagged here as a verification/UX item only.

**Classification:** Average Duration.

---

# 10. Timezone / Date-Basis Verification Risk (cross-cutting — read before manual testing)

- Java-side date math (`ctx.today`, `inRange()`, age/delay calculations) explicitly uses `ZoneId.of("Asia/Kolkata")` (`BUSINESS_ZONE`, line 97).
- **Every native-SQL date filter** (`column::date between :from and :to`) relies on PostgreSQL casting a `timestamptz` column to `date` **using the database session's timezone**, which this codebase does not explicitly `SET` anywhere found (no `spring.jpa.properties.hibernate.jdbc.time_zone`, no `?timezone=` JDBC URL parameter, no `TimeZone.setDefault()` call, no Docker `TZ=` env var in any file searched).
- In practice, the PostgreSQL JDBC driver negotiates the session timezone from the connecting JVM's default timezone. **In this development environment specifically, the JVM's default timezone is already `Asia/Kolkata`** (confirmed by direct check), so today the SQL-side and Java-side date boundaries agree.
- **This alignment is incidental (OS/JVM default), not an explicit application setting.** If this application is ever deployed on infrastructure whose OS/JVM default timezone differs from IST, the raw-SQL date-range filters (used by the large majority of these KPIs) would silently shift by a fixed offset relative to the Java-side `Asia/Kolkata` calculations (Active WIP, Delayed, Stage Health ages), for records near local midnight.
- **Manual test:** on the deployment server, run `java -XshowSettings:properties -version 2>&1 | grep user.timezone` (or check the container's `TZ` env var / OS setting). If it is not `Asia/Kolkata`, re-verify date-boundary KPIs (especially any count near a day boundary) against direct SQL before trusting the dashboard for that day.

This is **not** an `IMPLEMENTATION MISMATCH` against any governed rule (no rule specifies how the DB session timezone must be configured) — it is a **verification risk** worth checking once per environment.

---

# 11. Reopen / REPOST Cycle Rules (consolidated)

- **`ReopenPurpose`** has 3 values: `RETAINED_REOPEN`, `PUBLISHING_REOPEN`, `METRIC_CORRECTION_REOPEN`.
- **On-Time Delivery** and **Avg End-to-End Cycle Time** filter to `PUBLISHING_REOPEN` only (`publishingReopensByInstance`, line 185-188) — a Retained-idea reopen or a metric-correction reopen never creates a new Publishing cycle.
- **"Reopened Content"** (Workflow & SLA) counts ALL reopen purposes with no filter — the one place in the dashboard where a non-Publishing reopen is visible.
- A **REPOST** (`ActualPublicationEvent.eventType = REPOST`) can only be recorded once a plan is back in `PUBG` status, i.e. after a `PUBLISHING_REOPEN`. `hasLivePostInCurrentCycle` (`PublishingService`) uses the event's immutable `recordedAt`, not the editable `actualPublicationTimestamp`, to decide which cycle an event belongs to — an old ORIGINAL event can never satisfy a later repost cycle's resolution requirement, and a backdated `actualPublicationTimestamp` cannot misclassify which cycle an event counts toward.
- Each Publishing cycle's deadline and resolution are evaluated **independently** — confirmed by `multipleConsecutiveRepostCyclesRemainIndependentlyScoped`, which drives a plan through 2 sequential repost cycles and asserts cycle 1's on-time credit survives cycle 2 being late.

---

# 12. N/A Rules (consolidated)

| Context | N/A source | Effect |
|---|---|---|
| Publishing Scope Resolution (On-Time Delivery numerator) | `PublicationTargetNaRecord`, latest per (output, target), `actionType=DESIGNATED` | Skipped in the "every pair must be live-or-NA" AND-check — never blocks resolution, never counts as "live" either |
| Target Completion % | same | Excluded from numerator AND denominator entirely (`na++`, no further counting) |
| Performance CTR/Impressions (scorecard-level) | `CreativePerformanceScorecard.clicksIsNa` / correction's `newClicksIsNa` | `effectiveCtr` resolves to `null` (via `CreativePerformanceScorecard.computeRatePercent`'s null-numerator rule) — **excluded from every average**, never treated as 0 |
| Evidence/N/A cannot leave every target N/A | `wouldLeaveAllTargetsNa` (`PublishingService`) | Not a KPI rule — a business-rule guard preventing the DESIGNATE action itself, mentioned here for completeness |

**Universal rule, confirmed everywhere it applies:** N/A is never silently treated as `0` in an average, and never silently counted as "complete"/"published" — it is either excluded from the population entirely (Target Completion, CTR averages) or excluded from blocking a completion check (Publishing Scope Resolution).

---

# 13. Hold / Reschedule Rules (consolidated)

**Hold (`WorkHoldRecord`, `work_hold_records`):**
- Permitted only while `currentStatusCode ∈ {SIP, ED, PUBG}` (Shoot In Progress / Editing / Publishing) — enforced by `HoldService.placeHold`, not by the KPI layer.
- Never touches the primary workflow status (ERD-CON-061) — a held plan still counts in its stage's Active WIP/Stage Health, and still ages normally (Avg Age is not paused by a Hold).
- Open-hold gate: `resumedAt IS NULL`. Completed-hold population for duration averages: `resumedAt IS NOT NULL AND resumed_at::date IN range`, explicitly gated by `resumedHoldCountInRange` — see §6.

**Reschedule (`RescheduleRecord`, `reschedule_records`):**
- Carries `stageContext ∈ {SHOOTING, EDITING, PUBLISHING}` plus prior/new triples for shoot/edit/live dates.
- Directly updates `ContentPlan.plannedShootDate/plannedEditDate/plannedLiveDate` — this is why "Delayed Deliverables"/"Average Delay"/Stage-target logic always reflects the CURRENT approved target with no separate lookup needed.
- **Only `stageContext = PUBLISHING` reschedules count toward a repost cycle's deadline** — a Shooting- or Editing-context reschedule made during a repost cycle's window never sets that cycle's deadline (it stays "Target Pending").
- For the **original**-cycle deadline reconstruction specifically, the 4-branch algorithm (§3, On-Time Delivery) considers reschedules of **any** `stageContext`, by design — because a Shoot/Edit-stage reschedule can still move the live date via the shared 3-date-triple update.

---

# 14. Employee / Stage Attribution Rules

**Governing principle, confirmed in the class-level Javadoc of `KpiDashboardService` (line 87-92) and verified structurally:** every calculation reads actual assignment/contribution/event records — `shooting_assignments.is_active`, `editing_assignments.is_active`, the actor on a `ReviewCycle`/`WorkflowTransitionHistory`/`ActualPublicationEvent`, etc. **`User.businessRole` is never referenced anywhere in `KpiDashboardService.java`** (confirmed by inspection — the file has no import of, or reference to, a business-role field/column in any of its ~40 native queries or Java filters).

**Example, code-verified end to end by `multiFunctionEmployeeWorkCountsInStageHealthRegardlessOfBusinessRole`:** an employee whose `businessRole` is HR Manager (non-canonical for Shoot) is granted `PERM_18_SHOOT_EXECUTION` directly, performs real Shoot work (`shooting_assignments` row with `cameraperson_user_id` = that employee, `is_active = true`), and the plan correctly appears in the "Shoot" row of Stage Health's Active count — the stage bucket is driven entirely by `currentStatusCode`, and the assignment is driven entirely by `shooting_assignments`, neither of which ever consults `businessRole`.

**Same principle applies structurally to:**
- Shoot/Edit contribution → `shooting_assignments`/`editing_assignments` (`is_active` flag), not role
- Publishing → `PublishingAssignment` (active-assignee check in `PublishingService.requireActiveAssignee`), not role
- Marks → `PredefinedRoleMarks`, tied to the ContentPlan, not to who currently holds a role
- Performance entry → `CreativePerformanceScorecard.recordedBy`, any user with the right permission grant, not role
- Corrections → `correctedBy` on `PublicationEvidenceCorrection`/`PerformanceMetricCorrection`, same pattern

---

# 15. Permission vs KPI Attribution

**Confirmed principle:** `PERM_15_TEAM_KPI_VIEW` (checked once per top-level view method via `requireViewAuthority`, line 154-156) governs **who may view** the dashboard at all — it plays no role in which historical records are counted once a viewer is authorized.

**Historical contribution is never re-filtered by CURRENT permission state.** Example (from the class doc, line 89-92): if `PERM_18_SHOOT_EXECUTION` is revoked from an employee today, every Shoot-stage work they already completed remains fully counted in every historical KPI that reads it (Stage Health history via `WorkflowTransitionHistory`, Rework/First-Pass rates via `ReviewCycle`, etc.) — none of these queries join against `permission_grants`' *current* state; they read the immutable historical `assignment`/`transition`/`review` rows directly.

---

# 16. Manual Testing Scenarios (cross-cutting)

These combine multiple KPIs on purpose — the individual per-KPI "Manual Test" blocks above are the atomic building blocks; these scenarios chain them.

**Scenario A — Funnel consistency across a real range**
1. Pick a 30-day range with real Idea submissions.
2. Run §"Idea → Publish Funnel" (§9 below) for Submitted/Approved/Retained/Rejected.
3. Confirm `Approved + Retained + Rejected <= Submitted` (equality only if there are zero still-pending ideas).

**Scenario B — Publishing cycle isolation**
1. Find a plan that was reopened for Publishing at least twice.
2. Reconstruct each cycle's deadline (§3 On-Time Delivery) independently.
3. Confirm cycle 2's late/on-time outcome does not change cycle 1's already-recorded outcome.

**Scenario C — N/A doesn't fake completion**
1. Find (or create in a non-prod environment) an output with one target N/A and the rest published.
2. Confirm the plan's Publishing scope DID resolve (PP transition exists) once the non-N/A targets are all live.
3. Confirm Target Completion % excludes the N/A target from both numerator and denominator (§7).

**Scenario D — Evidence correction rate vs raw count**
1. Find an event with ≥2 correction records.
2. Confirm the raw count includes all of them, but the rate's numerator only advances by 1 for that event.

**Scenario E — First-Pass denominator**
1. Find a stage with at least one rework (cycle_number=2 exists).
2. Confirm `Total Reviews > First-Cycle Reviews` for that stage.
3. Confirm the displayed First-Pass % equals `First-Pass Approved / First-Cycle Reviews`, not `/ Total Reviews`.

---

# 17. SQL / Data Verification Guide

All queries below are **read-only SELECTs**. No INSERT/UPDATE/DELETE. Table/column names verified against the JPA entities read for this document (not assumed).

```sql
-- Active WIP
SELECT count(*) FROM content_plans cp
JOIN workflow_instances wi ON wi.workflow_instance_id = cp.workflow_instance_id
WHERE wi.current_status_code NOT IN ('COMP','CAN','RJ');

-- Delayed Deliverables (uses the same 3-branch target-date CASE as StageSqlFragments.STAGE_PLANNED_DATE_CASE)
SELECT count(*) FROM content_plans cp
JOIN workflow_instances wi ON wi.workflow_instance_id = cp.workflow_instance_id
WHERE wi.current_status_code NOT IN ('COMP','CAN','RJ')
  AND (CASE WHEN wi.current_status_code IN ('PL','PLRV','PLAP','SA','SIP','SRV') THEN cp.planned_shoot_date
            WHEN wi.current_status_code IN ('SAP','EA','ED','ERV') THEN cp.planned_edit_date
            WHEN wi.current_status_code IN ('EAP','RFP','PUBG','PP','PFUP') THEN cp.planned_live_date
            ELSE NULL END) < CURRENT_DATE;

-- Published Content (distinct Content Plans, ORIGINAL only, date-ranged)
SELECT count(DISTINCT content_plan_id) FROM actual_publication_events
WHERE event_type = 'ORIGINAL' AND actual_publication_timestamp::date BETWEEN :from AND :to;

-- Idea Funnel (submitted-cohort basis)
SELECT count(*) FROM ideas WHERE submitted_at::date BETWEEN :from AND :to;                             -- Submitted
SELECT count(*) FROM ideas i JOIN content_plans cp ON cp.idea_id = i.idea_id
  WHERE i.submitted_at::date BETWEEN :from AND :to;                                                     -- Approved
SELECT count(*) FROM ideas i JOIN workflow_instances wi ON wi.workflow_instance_id = i.workflow_instance_id
  WHERE wi.current_status_code = 'RET' AND i.submitted_at::date BETWEEN :from AND :to;                  -- Retained
SELECT count(*) FROM ideas i JOIN workflow_instances wi ON wi.workflow_instance_id = i.workflow_instance_id
  WHERE wi.current_status_code = 'RJ' AND i.submitted_at::date BETWEEN :from AND :to;                    -- Rejected

-- Pending Reviews (live snapshot, all gates)
SELECT count(*) FROM review_cycles WHERE decided_at IS NULL;

-- Rework Rate (all stages, decided-at scoped)
SELECT count(*) FROM review_cycles
  WHERE gate_type IN ('PLANNING_REVIEW','SHOOT_REVIEW','EDIT_REVIEW')
    AND decided_at IS NOT NULL AND decided_at::date BETWEEN :from AND :to;                              -- decided
SELECT count(*) FROM review_cycles
  WHERE gate_type IN ('PLANNING_REVIEW','SHOOT_REVIEW','EDIT_REVIEW') AND decision = 'REQUEST_REWORK'
    AND decided_at::date BETWEEN :from AND :to;                                                          -- rework

-- On Hold (live) / completed-hold durations
SELECT count(*) FROM work_hold_records WHERE resumed_at IS NULL;
SELECT count(*), avg(extract(epoch FROM (resumed_at - held_at))/86400.0), max(extract(epoch FROM (resumed_at - held_at))/86400.0)
  FROM work_hold_records WHERE resumed_at IS NOT NULL AND resumed_at::date BETWEEN :from AND :to;

-- Target Completion population (output-created-in-range basis — NOT publication-date basis)
SELECT po.planned_output_id, ptm.publication_target_id
FROM planned_outputs po
JOIN planned_output_publication_target_mappings ptm ON ptm.planned_output_id = po.planned_output_id
WHERE po.created_at::date BETWEEN :from AND :to;
-- then, per pair, check publication_target_na_records (latest by recorded_at) and
-- actual_publication_events (event_type='ORIGINAL') per the procedure in §7.
```

**Recommended verification procedure (too stateful for one SQL statement)**

> **On-Time Delivery**, **Avg End-to-End Cycle Time**: these require walking `reopen_records` (filtered `reopen_purpose='PUBLISHING_REOPEN'`, ordered by `reopened_at`) and `reschedule_records` (ordered by `rescheduled_at`) per plan to reconstruct cycle windows and deadlines, then cross-referencing `workflow_transition_history` for the `PUBLICATION_SCOPE_RESOLVED` row inside each window. Follow the step-by-step procedure in §3's "On-Time Delivery" Manual Test rather than attempting a single SQL statement — the 4-branch deadline algorithm has real conditional logic that a single query would either get wrong or make unreadable.
>
> **CTR rankings / ORIGINAL vs REPOST Performance**: require resolving each scorecard's *effective* (post-correction) metrics by walking `performance_metric_corrections` newest-first per field (`new_clicks`, `new_impressions`, `new_clicks_is_na`, ...) and falling back to the raw `creative_performance_scorecards` value only when every correction for that field is null. Follow §9's `PerformanceService.resolveEffectiveMetrics` logic manually per scorecard rather than a single aggregate query.

---

# 18. Known Limitations

1. **Timezone alignment is implicit, not configured** — see §10.
2. **`Attention Needed` thresholds are hardcoded** (2-day pending-review cutoff; every "> 0" trigger) — no admin UI or config file controls these.
3. **No previous-period/trend comparison exists** anywhere in this dashboard (§2.2).
4. **Minor non-DRY duplicate query, not a discrepancy risk:** `contentPublishing()`'s `rawCorrections` (line 731-734) and the private helper `evidenceCorrectionCount()` (line 841-846, used only by `qualityReviews()`) contain **byte-identical SQL** but are two separate method bodies rather than one shared call. Their results are guaranteed identical by construction (same text, same parameters) — flagged only for maintainability, not correctness.
5. **`Reopened Content` (Workflow & SLA) counts all reopen purposes**, while every Publishing-cycle KPI (On-Time Delivery, Avg End-to-End) filters to `PUBLISHING_REOPEN` only — these two "reopen" numbers are intentionally different populations and will not reconcile against each other.
6. **Avg Delay in Reporting can be negative** — see §9, not excluded-for-lateness-only as the name might suggest to a reader unfamiliar with the exact formula.
7. **Target Completion % / Planned Output Mix use a different date basis (`PlannedOutput.createdAt`) from Published Content / ORIGINAL / REPOST / Platform-Channel Distribution (`actual_publication_timestamp`)** within the same Content & Publishing screen — see §21, Critical Warning.

No `IMPLEMENTATION MISMATCH` against a previously-approved governed rule was found anywhere in this audit.

---

# 19. Formula Quick Reference Matrix

| Screen | KPI | Unit | Formula (short) | Numerator | Denominator | Primary Source | Date Basis | N/A Rule | Empty Rule |
|---|---|---|---|---|---|---|---|---|---|
| Overview | Active WIP | Content Plan | count(status ∉ terminal) | — | — | `content_plans`+`workflow_instances` | none (snapshot) | n/a | 0 valid |
| Overview | Delayed Deliverables | Content Plan | count(target < today) | — | — | same + stage-target CASE | none (snapshot) | n/a | 0 valid |
| Overview/Workflow | On-Time Delivery % | Publishing cycle | onTime/eligible×100 | on-time cycles | eligible cycles (deadline in range, not future) | `workflow_transition_history`, `reopen_records`, `reschedule_records` | cycle deadline | N/A skipped in scope-resolution check, never blocks | `-` |
| Overview | Published Content | distinct Content Plan | count(distinct plan, ORIGINAL) | — | — | `actual_publication_events` | `actual_publication_timestamp` | n/a | 0 valid |
| Overview/Workflow | Avg End-to-End Cycle Time | days | mean(resolvedAt−submittedAt) | — | — | `ideas`+`workflow_transition_history` | resolution date | n/a | `-` |
| Overview/Quality | Rework Rate (All Stages) | % | rework/decided×100 | REQUEST_REWORK count | all decided (3 gates) | `review_cycles` | `decided_at` | n/a | `-` |
| Overview/Quality | Pending Reviews | count | count(decided_at null) | — | — | `review_cycles` | none (snapshot) | n/a | 0 valid |
| Overview/Performance | Performance Overdue | count | count(open & due<today) | — | — | `performance_obligations` | none (snapshot) | n/a | 0 valid |
| Overview | Stage Health: Active/Delayed/Avg Age/Oldest Item | Content Plan | see §4 | — | — | `content_plans`+`workflow_transition_history` | none (snapshot ages) | n/a | `-` |
| Overview | Attention Needed (5 rules) | count | see §5 | — | — | multiple | mostly none (live) | n/a | item omitted if 0 |
| Overview | Funnel: Submitted/Approved/Retained/Rejected/Planned/Published | Idea/Content Plan | see §9(below) | — | — | `ideas`,`content_plans`,`workflow_instances`,`actual_publication_events` | `ideas.submitted_at` (cohort) | n/a | 0 valid |
| Overview | Funnel: Approval Rate | % | Approved/(Approved+Rejected)×100 | Approved | Approved+Rejected | same | same cohort | n/a | `-` |
| Workflow | Within SLA % (per stage) | % | (Active−Delayed)/Active×100 | Active−Delayed | Active | `content_plans` | none (snapshot) | n/a | `-` |
| Workflow | Planning Turnaround Time | days | mean(PLAP−PL first-entry) | — | — | `workflow_transition_history` | `PLAP` entry date | n/a | `-` |
| Workflow | Shoot-to-Publish Cycle Time | days | mean(lastORIGINAL−firstSAP) | — | — | `workflow_transition_history`+`actual_publication_events` | last ORIGINAL date | n/a | `-` |
| Workflow | Average Delay | days | mean(today−target), delayed active only | — | — | `content_plans` | none (snapshot) | n/a | `-` |
| Workflow | Delay Aging (4 buckets) | count | bucketed delay-day counts | — | — | same as Average Delay | none (snapshot) | n/a | 0 valid per bucket |
| Workflow | On Hold Count | count | resumedAt IS NULL | — | — | `work_hold_records` | none (snapshot) | n/a | 0 valid |
| Workflow | Avg/Longest Hold Duration | days | avg/max(resumed−held) | — | — | `work_hold_records` | `resumed_at` | n/a | `-` (explicit `resumedHoldCountInRange` gate) |
| Workflow | Reopened Content | count | count(reopen_records) | — | — | `reopen_records` | `reopened_at` | n/a (all purposes) | 0 valid |
| Workflow/Content | REPOST Publications | event count | count(event_type=REPOST) | — | — | `actual_publication_events` | `actual_publication_timestamp` | n/a | 0 valid |
| Content | Target Completion % | % | Published/(Published+Pending)×100 | Published (non-N/A) | Published+Pending (non-N/A) | mappings, NA records, events | `PlannedOutput.created_at` | excluded from both | `-` |
| Content | ORIGINAL / REPOST | event count | count(event_type) | — | — | `actual_publication_events` | `actual_publication_timestamp` | n/a | 0 valid |
| Content/Quality | Evidence Correction Records (raw) | count | count(corrections) | — | — | `publication_evidence_corrections` | joined event's timestamp | n/a | 0 valid |
| Content | Evidence Correction Rate % | % | distinctCorrectedEvents/allEvents×100 | distinct corrected events | ORIGINAL+REPOST events | same + `actual_publication_events` | same | n/a | `-` |
| Content | Planned Output Mix | count/% per category | group by output/reel type | — | — | `planned_outputs` | `created_at` | n/a | empty list |
| Content | Platform Distribution | event count per platform | group by platform | — | — | `actual_publication_events`+`publication_targets`+`platforms` | `actual_publication_timestamp` | n/a | empty list |
| Content | Channel Distribution (Top 5) | event count per channel | group by channel, limit 5 | — | — | same + `company_channels` | same | n/a | empty list |
| Quality | First-Pass Approval Rate | % | firstPassApproved/firstCycleDecided×100 | cycle 1 APPROVED | cycle 1 decided | `review_cycles` | `decided_at` | n/a | `-` |
| Quality | Avg Review Turnaround | days | mean(decidedAt−submittedAt), all gates | — | — | `review_cycles` | `decided_at` | n/a | `-` |
| Quality | Rework Rate by Stage | % | rework/total×100 per gate | REQUEST_REWORK | all decided that gate | `review_cycles` | `decided_at` | n/a | `-` |
| Quality | Stage-wise: Total/First-Cycle/First-Pass/Rework/Avg Time | count/days | see §8 | — | — | `review_cycles` | `decided_at`/`submitted_at` | n/a | 0/`-` |
| Quality | Idea Rejection Rate | % | rejected/(approved+rejected)×100 | Idea REJECTED | Idea APPROVED+REJECTED | `review_cycles` | `decided_at` | n/a | `-` |
| Performance | Performance Pending | count | is_completed=false | — | — | `performance_obligations` | none (snapshot) | n/a | 0 valid |
| Performance | Scorecard Completion % | % | submitted/due×100 | submitted (has submittedAt) | due in range | `performance_obligations`+`creative_performance_scorecards` | `performance_due_date` | n/a | `-` |
| Performance | Avg Delay in Reporting | days | mean(submittedAt−dueDate) | — | — | same | `performance_due_date` | n/a | `-` (can be negative) |
| Performance | Top Content Type/Platform/Channel by Avg CTR | % + n | mean(effectiveCtr), grouped, top 5 | — | — | `creative_performance_scorecards`+corrections | `actual_publication_timestamp` (via event) | N/A scorecards excluded | empty list |
| Performance | ORIGINAL/REPOST Avg CTR % | % | mean(effectiveCtr) by eventType | — | — | same | same | excluded | `-` |
| Performance | ORIGINAL/REPOST Avg Impressions | count | mean(effectiveImpressions) by eventType | — | — | same | same | excluded | `-` |

---

# 20. Formula Classification

| KPI | Classification |
|---|---|
| Active WIP | Snapshot |
| Delayed Deliverables | Snapshot |
| Stage Health (Active/Delayed/Oldest Item) | Snapshot |
| Stage Health Avg Age | Snapshot Average |
| Within SLA % | Snapshot Rate |
| On-Time Delivery % | Historical Cycle Rate |
| Published Content | Period Throughput |
| ORIGINAL / REPOST counts | Period Throughput |
| Avg End-to-End Cycle Time | Historical Cycle (Average Duration) |
| Planning Turnaround / Shoot-to-Publish Cycle Time | Average Duration |
| Rework Rate (all forms) | Rate |
| Pending Reviews | Snapshot |
| Performance Pending | Snapshot |
| Performance Overdue | Snapshot |
| Attention Needed (5 rules) | Snapshot |
| Idea Funnel counts | Period Throughput (submitted-cohort) |
| Approval Rate / Idea Rejection Rate | Rate |
| Average Delay | Snapshot Average |
| Delay Aging | Distribution |
| On Hold Count | Snapshot |
| Avg/Longest Hold Duration | Average Duration |
| Reopened Content / REPOST Publications | Period Throughput |
| Target Completion % | Rate |
| Evidence Correction Records | Count |
| Evidence Correction Rate % | Rate |
| Planned Output Mix / Platform / Channel Distribution | Distribution |
| First-Pass Approval Rate | Rate |
| Avg Review Turnaround / Avg Review Time | Average Duration |
| Stage-wise Review counts | Count |
| Scorecard Completion % | Rate |
| Avg Delay in Reporting | Average Duration |
| CTR Rankings | Ranking |
| ORIGINAL/REPOST Avg CTR / Avg Impressions | Average (comparison) |

This is why, for example, Active WIP (Snapshot) and Published Content (Period Throughput) can legitimately disagree in "shape" for the same plan — one asks "where is it right now," the other asks "did an event happen in this window."

---

# 21. Critical Warnings

1. **Content ID count ≠ publication-event count.** Published Content (distinct plans) vs ORIGINAL/REPOST cards and Platform/Channel Distribution (raw events) can differ by a large factor for a multi-target Content ID — see the worked example in §3.
2. **Never use Business Role to attribute Shoot/Edit/Publishing work** — attribution is 100% assignment/event-record-driven (§14), confirmed by direct code inspection and a dedicated regression test.
3. **N/A is never 0** — excluded from every average and from the Target Completion denominator, never silently zeroed.
4. **Old ORIGINAL events cannot satisfy a later REPOST cycle** — `PublishingService` uses each event's immutable `recordedAt`, cycle-scoped, specifically to prevent this.
5. **Never use the current mutable `plannedLiveDate` for historical ORIGINAL-cycle SLA judgment** — the On-Time Delivery original-cycle deadline is reconstructed via the 4-branch algorithm (§3), which can differ from today's `ContentPlan.plannedLiveDate` once reopens/reschedules have happened. `plannedLiveDate` IS the correct field for the *current* "Delayed Deliverables" snapshot — the two uses are deliberately different formulas over the same underlying column.
6. **First-Pass denominator is First-Cycle Reviews, never Total Reviews** — confirmed fixed at both the headline-card level and the per-stage-table level.
7. **Evidence Correction Rate uses distinct corrected events, never raw correction-record count**, as its numerator.
8. **A future deadline never counts as an SLA failure** — `eligibleEnd = min(rangeEnd, today)` excludes it until it actually arrives.
9. **No-data is never displayed as 0** — every average-type KPI in this dashboard resolves to `null` → `-` when its population is empty, verified both at the query layer and, for On Hold specifically, by an explicit availability counter.
10. **Do not mix a selected-period-activity metric with the Funnel's submitted-cohort logic.** The Funnel's Approved/Retained/Rejected (§9) are scoped by when the **Idea was submitted**, tracked forward to its **current** status — a completely different population from Quality & Reviews' "Idea Rejection Rate," which is scoped by when the review was **decided**. Two different, both-correct "Approved"/"Rejected" numbers can legitimately disagree for the same date range because they answer different questions ("ideas submitted in this window, whatever happened to them" vs "review decisions made in this window").
11. **(New, found during this audit) Target Completion % and Planned Output Mix use `PlannedOutput.createdAt` as their date basis; Published Content, ORIGINAL/REPOST, and Platform/Channel Distribution use `actual_publication_timestamp`.** These are two different date bases coexisting on the same Content & Publishing screen — an output created last month whose target published today counts toward today's ORIGINAL count but NOT toward today's Target Completion population (it was already counted, if at all, in the month it was created).

---

# 22. Worked Examples

*(All numbers below are `Example only` — synthetic, not production data.)*

**1. Active WIP**
```
content_plans where status NOT IN (COMP,CAN,RJ): 214 rows → Active WIP = 214
```

**2. Delayed Deliverables**
```
Of those 214, 31 have currentApprovedTarget < today → Delayed Deliverables = 31
```

**3. Within SLA % (Shoot stage)**
```
Shoot: Active=40, Delayed=6 → (40-6)/40×100 = 85.0%
```

**4. ORIGINAL On-Time Delivery**
```
Plan C-0715-0012: no reopen ever → deadline = plannedLiveDate = 2026-08-10
PP transition (PUBLICATION_SCOPE_RESOLVED) recorded 2026-08-09
Deadline in [rangeStart, min(rangeEnd,today)] → eligible
resolvedAt date (08-09) <= deadline (08-10) → on time
```

**5. REPOST On-Time Delivery**
```
Same plan reopened for Publishing on 2026-08-20 (PUBLISHING_REOPEN)
PUBLISHING reschedule on 2026-08-21, newPlannedLiveDate = 2026-08-25 → cycle deadline = 2026-08-25
REPOST event's PP transition recorded 2026-08-27 → resolvedAt (08-27) > deadline (08-25) → eligible, NOT on time
This cycle's outcome does not change cycle 1's already-recorded "on time" result.
```

**6. Target Completion %**
```
Published=47, Pending=110, N/A=4 → 47/(47+110) = 29.9%
```

**7. First-Pass Approval %**
```
Planning gate: Total Reviews=33 (includes cycle 2/3 reworks), First-Cycle Reviews=25,
First-Pass Approved=22 → 22/25 = 88.0%  (NOT 22/33 = 66.7%)
```

**8. Rework Rate**
```
Planning: 33 decided cycles, 1 REQUEST_REWORK → 1/33 = 3.0%
```

**9. Evidence Correction Rate**
```
10 events total in range; Event A has 3 correction records, Event B has 1 → raw=4, distinct=2
Rate = 2/10 = 20.0%
```

**10. Avg CTR ranking**
```
Scorecard 1: 100 clicks / 1000 impressions → CTR 10%
Scorecard 2: 200 clicks / 1000 impressions → CTR 20%
Scorecard 3: clicksIsNa=true → excluded entirely
Scorecard 4: raw 150 clicks, but a later correction sets newClicks=180 → effective CTR uses 180 → 18%
Eligible n = 3 (scorecard 3 excluded)
Average = (10 + 20 + 18) / 3 = 16.0%
```

**11. Scorecard Completion %**
```
obligations due in range = 60, of which 47 have a submitted (not just drafted) scorecard
47/60 = 78.3%
```

**12. End-to-End Cycle Time**
```
Idea submitted 2026-06-01 10:00 IST; original-cycle PUBLICATION_SCOPE_RESOLVED at 2026-06-15 18:30 IST
= 14.35 days → shown as "14.4 days"
```

---

# 23. Testing Priority

### Critical (verify before any deployment)
- On-Time Delivery (original + repost cycle boundaries, N/A handling)
- REPOST cycle isolation (multi-cycle independence)
- Target Completion % / N/A exclusion
- First-Pass Approval % (First-Cycle denominator)
- Evidence Correction Rate (distinct-event denominator)
- Performance CTR: N/A exclusion + correction-chain effective values

### High
- Active WIP / Delayed Deliverables
- Within SLA %
- Cycle times (Planning Turnaround, Shoot-to-Publish, End-to-End)
- Pending Reviews / Performance Pending / Performance Overdue
- Idea → Publish Funnel cohort consistency

### Medium
- Platform/Channel/Content-Mix distributions
- CTR rankings (sample-size display, ordering)
- Duration/count display formatting (`kcpc:days`/`kcpc:count`)
- Drill-down links (Attention Needed, delayed/priority filters)

---

# 24. Final Notes

This document was produced by reading the actual source files listed at the top of each section, not from memory of prior conversation summaries. Where a formula could be pinned down exactly (the overwhelming majority), the exact SQL/Java is quoted. Where a genuine ambiguity or risk was found that isn't a code bug, it is called out explicitly (Avg Delay in Reporting's sign, the timezone-configuration risk, the two-different-date-basis warning) rather than silently smoothed over.

**All documented KPI formulas match the currently implemented governed calculation logic.** No `IMPLEMENTATION MISMATCH` was found between this document's formulas and the code that actually runs.
