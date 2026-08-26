# KPI Dashboard Data Reconciliation Report

Status: **IN PROGRESS.** Stage Health (the reproducible discrepancy that triggered this audit) is
fully diagnosed and reconciled below, with exact Content IDs, reconstructed directly from the live
`kcpc_dev` database. The remaining four tabs (Overview, Content & Publishing, Quality & Reviews,
Performance) have not yet been audited to the same DB-reconstruction rigor — see "Audit status by
tab" at the end of this document.

This report is evidence, not narrative: every number below was reconstructed with SQL run directly
against `kcpc_dev`, not inferred from reading code.

---

## 1. Root cause: Stage Health "Delayed" mismatch (Workflow & SLA vs Content Pipeline)

### 1.1 Three independent implementations of the same concept

Three different pieces of code answer the question "what is this Content Plan's current approved
target date, and is it delayed?" - and only two of them agree.

| Implementation | Used by | Governance |
|---|---|---|
| `StageSqlFragments.STAGE_PLANNED_DATE_CASE` | `KpiService` (30-KPI console), `AdminReportingService#delayedDeliverables` | Documented: traces to **BR-039** ("each active deliverable's delay baseline is its *current* stage's planned date - Shoot/Edit/Live - not one fixed column"), explicitly extracted to be shared "so the KPI Dashboard, the 30-KPI console, and Delayed Deliverables can never disagree about stage boundaries" |
| `KpiDashboardService.currentApprovedTarget(ContentPlan)` | Reports → KPI Dashboard (Overview, Workflow & SLA → Stage Health, Average Delay, Delay Aging) | **Not literally the same code** - an independent Java reimplementation. Verified case-by-case identical to `STAGE_PLANNED_DATE_CASE` (see 1.2), but the class comment's claim that Stage Health "reuse[s] the exact same current-approved-target logic AdminReportingService#delayedDeliverables already uses" is only true in *outcome*, not in *fact* - it is a second, separately-maintained copy of the same rule. |
| `PipelineDashboardService.delayDays(WorkflowStatus, ContentPlan, LocalDate)` | Content Pipeline → Delayed only / Attention tab; also reused verbatim by Team Workload's "Delayed Tasks" column | **Not governed by BR-039/StageSqlFragments at all.** Its own comment cites a *different* rule ("the same 'past planned date, not yet past that stage' rule `LandingMvcController`'s My Work already applies per-employee-task"). Its `switch` only has cases for `SA, SIP, SRV` / `EA, ED, ERV` / `RFP, PUBG` - 8 of the 15 non-terminal status codes. Every other status (`PL, PLRV, PLAP, SAP, EAP, PP, PFUP`) falls through to `default -> null`, meaning **a plan in one of those 7 statuses can never be shown as delayed in Pipeline, regardless of its actual planned date.** |

### 1.2 Exact status-by-status comparison

| Status | Canonical stage | `StageSqlFragments` / `currentApprovedTarget` (governed) | `PipelineDashboardService.delayDays` (Pipeline) | Match? |
|---|---|---|---|---|
| PL, PLRV | Planning | `planned_shoot_date` | **not evaluated (always "not delayed")** | **NO** |
| PLAP | Shoot | `planned_shoot_date` | **not evaluated** | **NO** |
| SA, SIP, SRV | Shoot | `planned_shoot_date` | `planned_shoot_date` | yes |
| SAP | Shoot | `planned_edit_date` | **not evaluated** | **NO** |
| EA, ED, ERV | Edit | `planned_edit_date` | `planned_edit_date` | yes |
| EAP | Publishing | `planned_live_date` | **not evaluated** | **NO** |
| RFP, PUBG | Publishing | `planned_live_date` | `planned_live_date` | yes |
| PP, PFUP | Performance | `planned_live_date` | **not evaluated** | **NO** |

This table alone predicts exactly which stages should mismatch and which should agree - Edit and
Publishing (whose Pipeline-covered statuses are complete) should match; Planning and Performance
(0% Pipeline-covered) should mismatch entirely; Shoot (3 of 5 statuses covered) should partially
mismatch. That is exactly what was observed.

### 1.3 Reconstructed numbers, live from `kcpc_dev` (today = 2026-08-25, Asia/Kolkata)

Reconstructed directly with SQL implementing both the governed CASE and Pipeline's `delayDays`
switch, grouped by canonical stage, over the same "non-terminal" population both screens use
(`current_status_code NOT IN ('COMP','CAN','RJ')`):

| Stage | Active | KPI Delayed (reconstructed) | Pipeline Delayed (reconstructed) | Reported KPI | Reported Pipeline |
|---|---:|---:|---:|---:|---:|
| Planning | 22 | **7** | **0** | 7 | 0 |
| Shoot | 11 | **6** | **3** | 6 | 3 |
| Edit | 7 | 5 | 5 | - | - |
| Publishing | 13 | 6 | 6 | - | - |
| Performance | 7 | **4** | **0** | 4 | 0 |

All three reported discrepancies reproduce exactly. Edit and Publishing (not part of the reported
discrepancy) reconstruct as equal, confirming the predicted pattern.

### 1.4 Exact Content IDs behind every mismatch

**Planning - 7 Content IDs the KPI counts as delayed that Pipeline never evaluates:**

| Content ID | Status | plannedShootDate | KPI says delayed | Pipeline says delayed | Reason |
|---|---|---|---|---|---|
| C-0826-0013 | PL | 2026-08-17 | YES | no | status PL not in Pipeline's switch |
| C-0826-0015 | PLRV | 2026-08-21 | YES | no | status PLRV not in Pipeline's switch |
| C-0826-0016 | PLRV | 2026-08-21 | YES | no | status PLRV not in Pipeline's switch |
| C-0826-0017 | PLRV | 2026-08-21 | YES | no | status PLRV not in Pipeline's switch |
| C-0826-0026 | PLRV | 2026-08-19 | YES | no | status PLRV not in Pipeline's switch |
| C-0826-0027 | PLRV | 2026-08-19 | YES | no | status PLRV not in Pipeline's switch |
| C-0826-0029 | PLRV | 2026-08-19 | YES | no | status PLRV not in Pipeline's switch |

(15 further PL-status plans have no `plannedShootDate` set yet - both screens correctly agree
"not delayed" for those; they are not part of the mismatch.)

**Shoot - 3 Content IDs the KPI counts as delayed that Pipeline never evaluates (all status SAP):**

| Content ID | Status | plannedEditDate (KPI's target for SAP) | KPI says delayed | Pipeline says delayed | Reason |
|---|---|---|---|---|---|
| C-0826-0028 | SAP | 2026-08-22 | YES | no | status SAP not in Pipeline's switch |
| C-0826-0034 | SAP | 2026-08-21 | YES | no | status SAP not in Pipeline's switch |
| C-0826-0040 | SAP | 2026-08-21 | YES | no | status SAP not in Pipeline's switch |

The other 3 KPI-delayed Shoot items (C-0826-0001 SA, C-0826-0053 SRV, C-0826-0055 SA) are correctly
flagged delayed by **both** screens - these are the "Pipeline 3" that do agree.

**Performance - 4 Content IDs the KPI counts as delayed that Pipeline never evaluates:**

| Content ID | Status | plannedLiveDate (KPI's target for PP/PFUP) | KPI says delayed | Pipeline says delayed | Reason |
|---|---|---|---|---|---|
| C-0826-0004 | PP | 2026-08-24 | YES | no | status PP not in Pipeline's switch |
| C-0826-0033 | PP | 2026-08-24 | YES | no | status PP not in Pipeline's switch |
| C-0826-0035 | PFUP | 2026-08-22 | YES | no | status PFUP not in Pipeline's switch |
| C-0826-0037 | PP | 2026-08-22 | YES | no | status PP not in Pipeline's switch |

### 1.5 Which implementation is correct?

**`StageSqlFragments`/`KpiDashboardService.currentApprovedTarget` (the KPI Dashboard's numbers) is
the governed one; `PipelineDashboardService.delayDays` is the bug**, on the following evidence -
not merely because the KPI Dashboard's number is "the one asked about first":

- `StageSqlFragments` carries an explicit governance trail to **BR-039** and a docstring stating its
  entire purpose is cross-screen consistency ("KPI Dashboard, 30-KPI console, and Delayed
  Deliverables can never disagree about stage boundaries").
- `PipelineDashboardService.delayDays`'s own comment cites a *different, narrower* rule (My Work's
  per-task delay), and its incomplete status coverage has no comment explaining *why* Planning/
  PLAP/SAP/EAP/Performance are deliberately excluded - there is no design rationale for the gap,
  which is the signature of an incomplete implementation rather than an intentional narrower
  definition.
- The gap is not "PLAP/SAP/EAP/PP/PFUP have a different, valid delay definition" - it is a
  `default -> null` catch-all that silently treats those 7 statuses as permanently non-delayable.

This is not "changing Pipeline to match the KPI's numbers" (prohibited by the task) - it is fixing
Pipeline's predicate to implement the one already-governed definition (BR-039) that it was supposed
to share and currently does not.

### 1.6 Fix - IMPLEMENTED and verified

Created `StageDelayPolicy` (`com.kcpc.mkt.reporting.service`) as the single shared Java source of
truth, reusing the already-governed `STAGE_PLANNED_DATE_CASE` shape but returning `null` for
Planning (PL, PLRV) per §2's decision below. `KpiDashboardService.currentApprovedTarget` and
`PipelineDashboardService.delayDays` both now delegate to it - the "faithful copy"
(`KpiDashboardService`) and the "incomplete divergent copy" (`PipelineDashboardService`) no longer
exist as separate implementations. This automatically fixed Team Workload's "Delayed Tasks" column
too, since it calls `PipelineDashboardService.delayDays` directly.

**Scope note:** `StageSqlFragments`/`KpiService` (the older 30-KPI console)/`AdminReportingService`
(`#delayedDeliverables`, a separate report screen) were deliberately **not** touched in this pass -
they were not named in this audit's scope, and changing their shared SQL fragment would also affect
those other screens' Planning-related figures without a decision covering them. They still use the
pre-existing `plannedShootDate`-for-Planning behavior, unchanged. Bringing them in line with §2's
decision is flagged as a follow-up, not done here.

**Verified**, live against the same `kcpc_dev` data used for the original diagnosis (2026-08-25):

| Stage | Active | KPI Delayed (after fix) | Pipeline Delayed (after fix) |
|---|---:|---:|---:|
| Planning | 22 | **-** (ungoverned, §2) | 0 (never evaluated, same reason) |
| Shoot | 11 | 6 | **6** (was 3) |
| Edit | 7 | 6 | 6 |
| Publishing | 13 | 6 | 6 |
| Performance | 7 | 4 | **4** (was 0) |

Shoot and Performance now reconcile exactly. Planning shows "-" on the KPI Dashboard (not a
fabricated 0) per §2, and Pipeline correctly never flags a Planning-stage plan either - same
outcome, achieved because both now go through one shared, documented exclusion instead of one
being a coverage gap.

**Regression tests added** (`StageDelayPolicyReconciliationTest`, 7 tests, pure unit - no DB/Spring
context, deterministic dates):
- `previouslyBrokenStatusesAreNowCorrectlyFlaggedDelayedByBothStageDelayPolicyAndPipeline` - the
  exact bug, reproduced and proven fixed, for all 5 previously-broken statuses.
- `previouslyBrokenStatusesWithFutureDatesAreNotDelayed` - future dates never flagged.
- `planningStatusesAreNeverDelayedRegardlessOfHowOverdue` - proves §2's decision holds even for a
  365-day-overdue Planning item.
- `targetDateEqualToTodayIsNotDelayed` / `targetDateOneDayBeforeTodayIsDelayed` - the `<` (not
  `<=`) boundary.
- `stageDelayPolicyAndPipelineAgreeExactlyForEveryNonPlanningStatus` - exhaustive reconciliation
  across all 15 statuses.
- `currentApprovedTargetUsesTheCorrectFieldPerStatus` - the Shoot/Edit/Live field mapping itself.

Full suite: 244/244 except the same 2 pre-existing, unrelated `EditTaskDetailTest`/
`ShootTaskDetailTest` failures already confirmed (via `git stash`) to predate this session's work.

---

## 2. Planning delay rule: NOT GOVERNED

Per §23 of the audit brief, this is a stop-and-decide finding, not a fix.

**Current Planning delay implementation:** `plannedShootDate < today`, for any Content Plan in
status `PL` or `PLRV`.

**Data field used:** `content_plans.planned_shoot_date` (Java: `ContentPlan.getPlannedShootDate()`).

**Where implemented:** `KpiDashboardService.currentApprovedTarget()` (lines ~233-241) and
`StageSqlFragments.STAGE_PLANNED_DATE_CASE` (lines 17-21) - both group `PL, PLRV` into the *same*
`CASE WHEN` branch as `PLAP, SA, SIP, SRV`, which returns `planned_shoot_date`.

**Why it currently produces 7:** 7 Content Plans in `PL`/`PLRV` status have a `planned_shoot_date`
already set (via Planning parameters) that is in the past relative to today (2026-08-25 IST) - see
the exact list in §1.4 above.

**Whether any BRS/SRS/test requirement supports it:** No. Searched every `docs/*.md` file in this
repository for any Planning-specific delay/SLA rule. The only related governance found is **BR-039**
(`docs/IMPLEMENTATION_DECISIONS.md` row ENG-027, and `docs/KCPC_R3.5_FINAL_IMPLEMENTATION_COMPLETION_REPORT.md`
line 151-153), whose own wording is: *"each active deliverable's delay baseline is its current
stage's planned date - **Shoot/Edit/Live** - not one fixed column."* BR-039 names three target
dates - Shoot, Edit, Live - and never mentions a Planning-specific baseline. Planning-stage rows
(`PL`/`PLRV`) landing on `planned_shoot_date` is an artifact of how the `CASE` expression happens to
be partitioned (Planning was grouped into the same branch as the pre-Shoot statuses so the `CASE`
would return *something* for every non-terminal status), not a documented, deliberate Planning SLA.
No test, BRS, SRS, or implementation-decision entry anywhere states that a Planning item becomes
"late" when `plannedShootDate` passes while it is still in Planning.

**Conclusion: Planning delay rule = NOT GOVERNED.**

Per the audit brief's explicit instruction, I have **not** invented or implemented a replacement
rule, and I am stopping here for that decision rather than inferring one. See the chat response for
the possible alternatives presented for stakeholder discussion, and for the explicit request to
define this rule.

Until that decision is made, per §8: **`Planning Delayed` and `Planning Within SLA %` must not be
treated as an authoritative KPI.**

---

## 3. Audit status by tab

| Tab | Status |
|---|---|
| Workflow & SLA → Stage Health | **Fully audited, fixed, and verified** (§1 above) - root cause confirmed with exact Content IDs, `StageDelayPolicy` centralized, Pipeline/Team Workload fixed, Planning shows "-" per §2, 7 regression tests added, reconciled live against `kcpc_dev` |
| Workflow & SLA → all other rows (Planning Turnaround, Shoot-to-Publish, End-to-End, On-Time Delivery %, Average Delay, Delay Aging, Hold metrics, Reopen/Repost) | Not yet audited to DB-reconstruction rigor |
| Overview | Not yet audited to DB-reconstruction rigor |
| Content & Publishing | Not yet audited to DB-reconstruction rigor |
| Quality & Reviews | Not yet audited to DB-reconstruction rigor |
| Performance | Not yet audited to DB-reconstruction rigor |

This report will be extended as the remaining tabs are audited.
