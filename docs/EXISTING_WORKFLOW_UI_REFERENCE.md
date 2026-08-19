# KCPC Bandhani — Existing Workflow & UI Reference

**Purpose:** this document is a factual snapshot of the application's *current, actual* behavior —
backend rules, statuses, permissions, screens, and JS patterns — so a future UI redesign can change
look-and-feel without accidentally breaking a working business rule. It documents what **is**, not
what should be. No redesign suggestions appear anywhere in this file.

**How to read the action tables below:** every workflow action is documented as

> **Who** → **Permission** → **Assignment required** → **Status/guard** → **Resulting status**

with a note on whether the rule is **backend-enforced** (checked in a `@Service` class, cannot be
bypassed by calling the API/form directly) or **UI-only** (only a JSP `<c:if>`/JS convenience — the
backend does not re-check it). Every concrete claim below cites the source file so it can be
independently re-verified; `file.java:123` means "see that line in that file."

Sections marked **NEEDS CONFIRMATION** are genuine open questions found during this audit — not
guesses. Treat them as questions for the product owner before a redesign encodes new assumptions
around them.

---

## 1. High-level architecture

- **Stack**: Spring Boot 3.3.5, JSP+JSTL views rendered server-side (`src/main/webapp/WEB-INF/views/*.jsp`), Spring MVC + Spring Security (JWT-in-cookie), Spring Data JPA, PostgreSQL, Flyway migrations (`V1`…`V19`).
- **Two parallel controller families** expose the same service layer: `web.mvc.*Controller` (JSP screens, form POSTs, redirect+flash-message pattern) and `web.rest.*Controller` (`/api/v1/**`, JSON, used by both the JS/AJAX layer and any future API client). Both call the **same** `@Service` classes — the business rules described in this document apply identically no matter which controller family is used.
- **Every workflow status change goes through one method**: `WorkflowTransitionService.transition(instance, to, actor, actingGrant, triggerCommand, reason)` (`workflow/service/WorkflowTransitionService.java:38-45`), which atomically updates `WorkflowInstance.currentStatusCode` and appends a `WorkflowTransitionHistory` row in the same transaction. **No service updates status any other way.** This is the single mechanism a redesign can rely on for "what actually changed the status and when."
- **One `WorkflowInstance` per deliverable, shared across its entire life**: an `Idea` and the `ContentPlan` created when it's approved point at the **same** `WorkflowInstance` row (confirmed via `ContentPlan`'s constructor, `planning/domain/ContentPlan.java:113-117`, which takes the idea's own `workflowInstance`). This is why an idea's own status field is really "the current status of everything downstream" unless the UI layer deliberately re-labels it (see §5, Idea Status vs Planning Status).

---

## 2. The 22 workflow statuses (`WorkflowStatus` enum)

Source: `workflow/domain/WorkflowStatus.java:9-30`. 17 Active, 1 Dormant, 2 Terminal, 1 Closed
(reopenable), 1 Supplementary Flag.

| Code | Name | Classification | Stage |
|---|---|---|---|
| `IS` | Idea Submitted | Active | Idea (transient — see §5) |
| `PA` | Pending Approval | Active | Idea |
| `RJ` | Rejected | **Terminal** | Idea |
| `RET` | Retained | **Dormant** | Idea |
| `PL` | Planning | Active | Planning |
| `PLRV` | Planning Review | Active | Planning |
| `PLAP` | Planning Approved | Active | Planning (transient — see §7) |
| `SA` | Shoot Assigned | Active | Shoot |
| `SIP` | Shoot In Progress | Active | Shoot |
| `SRV` | Shoot Review | Active | Shoot |
| `SAP` | Shoot Approved | Active | Shoot |
| `EA` | Edit Assigned | Active | Edit |
| `ED` | Editing | Active | Edit |
| `ERV` | Edit Review | Active | Edit |
| `EAP` | Edit Approved | Active | Edit (transient — see §8) |
| `RFP` | Ready for Publishing | Active | Publishing |
| `PUBG` | Publishing | Active | Publishing |
| `PP` | Performance Pending | Active | Performance |
| `PFUP` | Performance Update | Active | Performance |
| `COMP` | Completed | **Closed** (reopenable) | — |
| `DLY` | Delayed | **Supplementary flag** | never a primary status |
| `CAN` | Cancelled | **Terminal** | — |

`DLY` can **never** be assigned as `currentStatusCode` — `WorkflowInstance`'s constructor and
`transitionTo()` both throw if asked to (`workflow/domain/WorkflowInstance.java:49-51, 56-62`).
"Delayed" is always a UI-computed overlay (comparing a planned date to today), never a real stored
status — see §14 (My Work) for where this is computed.

`WorkflowInstance.firstCompletedAt` is set once, the first time status reaches `COMP`, and is then
**permanently immutable** (`WorkflowInstance.java:64-70`, throws `IllegalStateException` if set
twice) — it survives even a later Reopen (§11), so "was this ever completed" and "is this
currently completed" are two different, both-queryable facts.

### 2.1 Full status-transition map (every trigger command, in order)

| From | To | Trigger command | Fired by |
|---|---|---|---|
| — | `IS` | (instance creation) | `IdeaService.submit` |
| `IS` | `PA` | `SUBMIT_IDEA` | `IdeaService.submit` (same call, `IS` is never actually observed) |
| `PA` | `PL` | `APPROVE_IDEA` | `IdeaService.decide` (Approve) |
| `PA` | `RJ` | `REJECT_IDEA` | `IdeaService.decide` (Reject) |
| `PA` | `RET` | `RETAIN_IDEA` | `IdeaService.decide` (Retain) |
| `RET` | `PA` | `REOPEN_IDEA` | `IdeaService.reopen` |
| `PL` | `PLRV` | `SUBMIT_PLANNING_REVIEW` | `PlanningService.submitPlanningReview` |
| `PLRV` | `PLAP` | `APPROVE_PLANNING` | `PlanningService.decidePlanningReview` (Approve) |
| `PLAP` | `SA` | `ACTIVATE_SHOOTING` | same call, immediately after (`PLAP` never independently observed) |
| `PLRV` | `PL` | `REQUEST_REWORK_PLANNING` | `PlanningService.decidePlanningReview` (Rework) |
| `SA` | `SIP` | `START_SHOOTING` | `ShootingService.startShooting` |
| `SIP` | `SRV` | `SUBMIT_SHOOT_REVIEW` | `ShootingService.submitShootReview` |
| `SRV` | `SAP` | `APPROVE_SHOOT` | `ShootingService.decideShootReview` (Approve) |
| `SRV` | `SIP` | `REQUEST_REWORK_SHOOT` | `ShootingService.decideShootReview` (Rework) |
| `SAP` | `EA` | `ASSIGN_EDITOR` | `EditingService.assignEditor` (first editor only) |
| `EA` | `ED` | `START_EDITING` | `EditingService.startEditing` |
| `ED` | `ERV` | `SUBMIT_EDIT_REVIEW` | `EditingService.submitEditReview` |
| `ERV` | `EAP` | `APPROVE_EDIT` | `EditingService.decideEditReview` (Approve) |
| `EAP` | `RFP` | `READY_FOR_PUBLISHING` | same call, immediately after (`EAP` never independently observed) |
| `ERV` | `ED` | `REQUEST_REWORK_EDIT` | `EditingService.decideEditReview` (Rework) |
| `RFP` | `PUBG` | `START_PUBLISHING` | `PublishingService.startPublishing` |
| `PUBG` | `PP` | `PUBLICATION_SCOPE_RESOLVED` | auto-fires inside `recordActualPublication`/`designateTargetNA` once scope resolved |
| `PP` | `PFUP` | `BEGIN_PERFORMANCE_UPDATE` | `PerformanceService.maybeAdvanceToPerformanceUpdate`, auto-fires on/after the Performance Due Date |
| `PFUP` | `COMP` | `COMPLETE_DELIVERABLE` | `PerformanceService.maybeComplete`, auto-fires once every obligation is completed |
| `COMP` | `PUBG` | `REOPEN_COMPLETED` | `AdminActionService.reopenForPublishing` |
| `COMP` | `PFUP` | `REOPEN_COMPLETED` | `AdminActionService.reopenForPerformance` |
| any non-closed | `CAN` | `CANCEL` | `AdminActionService.cancel` |

**Two statuses are "transient" — they exist as a concept but a user will never see them persisted**,
because the approving service immediately fires a second transition in the same call/transaction:
`PLAP` (Planning Approved) and `EAP` (Edit Approved). `IS` (Idea Submitted) is transient the same
way. A UI redesign should not build a screen state around "what does PLAP/EAP/IS look like" — it
never renders.

---

## 3. Idea workflow — Submit / Approve / Retain / Reject / Reopen

Service: `idea/service/IdeaService.java` (full file read). Entity: `idea/domain/Idea.java`.

### 3.1 Submit
- **Who**: any authenticated user of any `AccessClass` — "no permission gate on submission itself" (`IdeaService.java:76`, BRS-REQ-014).
- **Fields**: `title` (mandatory, blank → 400 `VALIDATION_FAILED`), `referenceLink` (optional, but if present must parse as an absolute `http`/`https` URI or 400 — `IdeaService.java:83-85, 97-104`), `notesRemarks` ("Idea Description / Details", optional), `additionalNote` (optional, added in `V19`).
- **Result**: creates a `WorkflowInstance(IS)`, immediately transitions to `PA`, allocates a human-readable `businessIdeaCode` (`IDEA-YYYYMMDD-NNNN`, sequential per day, `IdeaService.java:106-111`).
- Audited as `IDEA_SUBMITTED`.

### 3.2 Decide (Approve / Reject / Retain) — the single `decide()` entry point
- **Status guard**: must be exactly `PA`, else 409 (`IdeaService.java:119-122`).
- **Permission**: `PERM_01_IDEA_REVIEW`, stage `IDEA_MANAGEMENT` (`IdeaService.java:124-125`).
- **Self-review conflict (backend-enforced)**: the reviewer cannot be the idea's own submitter **if** acting under a delegated grant; CEO/MM's native authority is exempt from this check (`AuthorizationService.requireNoSelfReviewConflict`, confirmed by `SelfReviewConflictTest`).
- A new `ReviewCycle(IDEA_REVIEW)` row is always created first, decision recorded onto it.

| Decision | Reason required? | Extra requirement | Result |
|---|---|---|---|
| **Approve** | No | `cameramanMark` + `editorMark` both mandatory, values from the controlled list `{0, 0.5, 1.0, 2.0, 3.0}` | Allocates a Content ID, creates the `ContentPlan` + `PredefinedRoleMarks` row, transitions `PA→PL`. Audited `IDEA_APPROVED`. |
| **Reject** | **Yes** (400 if blank, AC-017.1) | — | `PA→RJ` (terminal — no further review possible on this idea). Audited `IDEA_REJECTED`. |
| **Retain** | No (optional comment) | — | `PA→RET` (dormant, awaiting Reopen). Audited `IDEA_RETAINED`. |

### 3.3 Reopen
- **Who**: `PERM_01_IDEA_REVIEW` holder (native or delegated) — administrative action, BRS-REQ-019.
- **Status guard**: must be exactly `RET`, else 409 (`IdeaService.java:185-188`).
- **Result**: `RET→PA` (trigger `REOPEN_IDEA`) — the idea goes back into the SAME review queue a brand-new submission would be in. **The system cannot natively tell "brand-new PA" from "reopened-back-to-PA" from the status alone** — the UI layer built in ENG-061 disambiguates this by checking whether the most recent idea-lifecycle transition was `REOPEN_IDEA` (see §14.2). This is a UI-layer inference on top of backend data, not a backend-tracked "Reopened" status.

### 3.4 Predefined-Mark correction (Idea-approval-time marks, corrected later)
- **Who**: `PERM_01_IDEA_REVIEW` holder. Mandatory reason. Appends an immutable `PredefinedMarkCorrection` row (chained via `supersedesCorrection`), updates the live `PredefinedRoleMarks` row in the same transaction (`IdeaService.java:202-232`).

### 3.5 Idea Status vs Planning Status — the business rule that must never be violated by the UI

The Idea's own lifecycle (Submitted → Approved/Retained/Rejected/Reopened) is conceptually
**separate** from the downstream Planning/Shoot/Edit/Publishing pipeline that begins once it's
approved, even though they share one `WorkflowInstance` row. **Once approved, "Idea Status" must
read "Approved" forever**, regardless of how far the resulting `ContentPlan` has progressed (this
was a real bug found and fixed in ENG-061 — the original `IdeaMvcController` mapping bucketed
every post-approval status into a fabricated "In Planning" label, and one screen leaked the raw
`WorkflowStatus.statusName` — e.g. "Shoot Assigned" — directly). This rule is currently
**UI-layer-only** (computed in `web/mvc/IdeaMvcController.java`'s `statusLabel` helper, not stored
anywhere) — a redesign must keep re-deriving "Approved" the same way (any status `PL` or later ⇒
"Approved") rather than showing the raw `WorkflowStatus` on any Idea-facing screen.

---

## 4. Core review-cycle mechanics (shared by all 4 gates)

`GateType` enum (`workflow/domain/GateType.java`): `IDEA_REVIEW`, `PLANNING_REVIEW`,
`SHOOT_REVIEW`, `EDIT_REVIEW`. Every gate uses the same `ReviewCycle` entity
(`workflow/domain/ReviewCycle.java`): a submission row (`submittedBy`, `submittedAt`) that later
gets a decision (`reviewer`, `decision`, `decisionReason`, `decidedAt`) written onto it exactly
once (`decide()` throws `IllegalStateException` if called twice — decisions are immutable once
made, ERD-CON-039, also DB-trigger-enforced). Cycle numbers increment per `(workflowInstance,
gateType)` pair — every Rework loop creates a brand-new cycle row rather than mutating the old one,
so the full history of every submit/decide round for every gate is permanently queryable.
**No cap on rework cycles was found anywhere in the code** — an idea/plan/shoot/edit can be sent
back for rework indefinitely.

Publishing has **no** `ReviewCycle`/gate of its own — it is not a review-gated stage (there's no
"Publishing Review" concept; see §9).

---

## 5. Planning flow

Service: `planning/service/PlanningService.java`. Entity: `planning/domain/ContentPlan.java`.

### 5.1 Content ID
Allocated exactly once, atomically, inside `IdeaService.approve()` — `ContentIdAllocationService.allocateContentId()` — and is then immutable (`ContentPlan.java:46-48`, no setter).

### 5.2 Standard vs Urgent scheduling
| | Standard | Urgent |
|---|---|---|
| Endpoint/method | `setStandardSchedule` | `setUrgentSchedule` |
| Permission | `PERM_02_PLANNING_EXECUTION` | same |
| Live date | Must not be in the past | same |
| **5-day floor** | If `plannedLiveDate < today + 5 days` → 400 "requires Urgent Planning Mode" (BRS-REQ-093) — **backend-enforced** | No floor |
| Shoot/Edit date defaults | `shoot = live − 5d`, `edit = live − 2d`, both overridable | Caller must supply both explicitly |
| Urgency reason | Cleared/not applicable | **Mandatory**, blank → 400 (ERD-CON-065) |
| Chronology | `shoot ≤ edit ≤ live` (equal allowed) always re-validated (`ContentPlan.validateChronology`, ERD-CON-066) | same |

**Reschedule** (a separate, later action — Permission #10, §11) only ever changes the three dates,
never `planningMode`/`urgencyReason`, and its own validation does **not** re-check the 5-day floor
(`ContentPlan.applyReschedule`) — so a Reschedule can legally move a Standard plan's live date to
inside 5 days without forcing Urgent mode. **NEEDS CONFIRMATION**: whether that's intentional
(Reschedule is a distinct, already-approved-plan action so the initial-scheduling gate arguably
shouldn't reapply) or a gap.

### 5.3 Planned Outputs, Reel Types, Reel Groups
- `OutputType`: `PHOTOGRAPHY`, `REEL`, `VIDEO` (planning/domain/OutputType.java).
- `ReelType`: `VERY_SHORT`, `SHORT`, `LONG` — **mandatory when `OutputType == REEL`, must be null otherwise**, enforced both in `PlannedOutput.setTypeAndReelType` and by a DB CHECK constraint (`V8__planning_stage.sql:72-74`).
- 1..N Planned Outputs per plan.
- **Reel Group**: selecting multiple Reel Types in one "+ Add Output" submission creates **one separate `PlannedOutput` row per Reel Type** (never one row holding multiple types, ERD-CON-008), all sharing one `reelGroupId` (`V14__planned_output_reel_groups.sql`). A non-REEL output, or a single-Reel-Type output, is a "group of one."
- Editing a group (`syncReelGroup`) reconciles membership (adds/removes member rows to match the newly-selected Reel Type set) and applies shared fields (Output Type, Description) to every member atomically. Removing a group deletes every member and their target mappings.
- **No explicit `WorkflowStatus == PL` guard was found** on add/edit/remove of Planned Outputs — only the `PERM_02_PLANNING_EXECUTION` check. **NEEDS CONFIRMATION**: whether Planned Outputs are genuinely editable at any status (unlike scheduling/assignment, which are status-gated) or this is an oversight.

### 5.4 Publication Targets / Platforms / Channels ("Publication Scope")
- `Platform` (masterdata) — 6 seeded: Instagram, Threads, YouTube, Facebook, Moj, TikTok.
- `CompanyChannel` (masterdata) — 8 seeded handles (e.g. `kcpcbandhani`, `kcpcsikar`, …).
- `PublicationTarget` = **a specific (Platform × Channel) pairing** with its own name, unique per pairing (`uq_publication_targets_platform_channel`) — only 3 starter targets are seeded; CEO/MM (or a `PERM_17` grant holder) extends the catalogue (§16).
- `PlannedOutputPublicationTargetMapping` = many-to-many join between one `PlannedOutput` and one `PublicationTarget`.
- **Group-wide propagation (backend-enforced)**: mapping/unmapping a target on any member of a Reel Group applies to **every** member sharing that `reelGroupId`, not just the one clicked — there is no per-Reel-Type override of publication scope within a group.
- Idempotent add (mapping an already-mapped target is a silent no-op).

### 5.5 Model(s) / Talent
`ContentPlanTalentEntry` stores talent as **plain free-text names** (`talentName VARCHAR(100)`) —
**not** a foreign key to any `User`/"Model" Business Role. The whole list is replaced wholesale on
every `updateParameters` call (delete-all-then-reinsert). The UI's Model(s) picker may draw its
*suggestion* checklist from Model-Business-Role users, but what's actually **persisted** is just
text strings, not user references. **NEEDS CONFIRMATION**: exactly how the picker's suggestion
list is sourced (not traced in this audit).

### 5.6 Cameraperson assignment during Planning
- **Status guard**: must be exactly `PL` — this is the ONLY window an initial Cameraperson can be assigned or removed via `PlanningService` (`PlanningService.java:387-427`); outside `PL`, 409.
- **Permission**: `PERM_04_SHOOT_ASSIGNMENT`.
- Idempotent add (re-assigning an already-active person returns the existing row, no duplicate).
- **No server-side Business-Role check** was found on this initial-assignment path (unlike Reassign, §11, which explicitly enforces "Camera Person" role server-side). **NEEDS CONFIRMATION**: this looks like an asymmetry worth flagging — initial assignment appears to trust the UI's role-filtered picker, while Reassign was later hardened (ENG-054) to check server-side too.

### 5.7 Shoot Lead
- Not in the frozen ERD (ENG-036, additive).
- **Status guard**: `PL` only. **Permission**: `PERM_04_SHOOT_ASSIGNMENT`.
- Setting Lead to `null` clears it; a non-null value **must be one of the plan's currently-active Camerapersons**, else 400.
- **At most one active Lead per plan**, enforced by a partial unique DB index (`ux_shooting_assignments_one_lead`, `V16__assignment_leads.sql`), not just app logic.
- **No effect on marks was found** — mark attribution (§10) is driven purely by the "qualifying recipients" chosen at Shoot Review approval time, independent of who is/was Lead. Lead appears to be a purely informational/organizational designation. **NEEDS CONFIRMATION** (this is what the code shows; confirm it's the intended design, not a missing feature).
- Reassignment does **not** appear to re-set Lead on the new team — if the reassigned-out person was Lead, the plan can end up with no active Lead until someone explicitly sets a new one. **NEEDS CONFIRMATION**.

### 5.8 Shoot Instructions (Description) during Planning
Editable by whoever holds **either** `PERM_04_SHOOT_ASSIGNMENT` **or** `PERM_03_PLANNING_REVIEW`
(dual-authority carve-out, not status-gated) — e.g. the Planning Reviewer can correct instructions
at review time even without holding the assignment permission. (Edit's equivalent Description field
does **not** have this dual-authority carve-out — see §8.3 — a possible asymmetry, **NEEDS
CONFIRMATION**.)

### 5.9 Planning Review — submit & decide
- **Submit** (`submitPlanningReview`): status must be `PL`, `PERM_02_PLANNING_EXECUTION`. **Mandatory-fields gate** (ERD-CON-026, backend-enforced via `ContentPlan.isReadyForPlanningReview()`): `contentPriority`, `plannedLiveDate`, `plannedShootDate`, `plannedEditDate`, and a non-blank `folderLink` must ALL be present, else 400. Result: `PL→PLRV`.
- **Decide** (`decidePlanningReview`): status must be `PLRV`, `PERM_03_PLANNING_REVIEW`. Self-review conflict: if the reviewer is the plan's own `PlanningPreparer` **and** acted under a delegated grant (not native CEO/MM), 403.
  - **Approve**: requires ≥1 active Shooting assignment already exists, else 400. Result: `PLRV→PLAP→SA` (both in the same call — `PLAP` never independently observed).
  - **Rework**: reason mandatory. Result: `PLRV→PL` (loops back; unlimited cycles).

### 5.10 Combined "Submit for Planning Review" single-form flow (ENG-045/047)
One button does, in **one transaction** (`savePlanAssignAndSubmit`): save Planning Details → reconcile Cameraperson(s)+Shoot Lead (full reconciliation — removes anyone unchecked, not additive-only, if the actor holds `PERM_04`) → set Shoot Instructions → submit Planning Review. If any step (including the readiness check) fails, **nothing** is persisted — full atomic rollback (proven by `PlanningSingleFormTest.combinedSubmitRollsBackEverythingWhenSubmissionIsRejected`).

---

## 6. Master data: Platforms, Channels, Publication Targets, Reel/Output Types

Already detailed in §5.3–5.4 for how they're *used*; administratively:

- `MasterCatalogueService` — every create/update/deactivate requires `PERM_17_PLATFORM_CATALOGUE_MANAGE` (stage `ADMINISTRATIVE`) **and** a mandatory free-text reason (BRS-REQ-061).
- **Deactivation is always soft** (`is_active=false`), never a hard delete — "historical data referencing a catalogue object is preserved" (class javadoc).
- Duplicate-active-pairing guard on creating a `PublicationTarget`: only checks for an existing **active** Platform×Channel pairing before allowing a new one — but the DB's own unique constraint on `(platform_id, channel_id)` is unconditional (not partial), so a deactivated pairing being "re-created" may actually be blocked at the DB layer despite the service-layer check being active-only. **NEEDS CONFIRMATION** — possible inconsistency between the service-layer duplicate check and the DB constraint's scope.
- Managed at `/app/admin/catalogue` (§16) — the one admin screen genuinely delegable to a non-CEO/MM Employee holding `PERM_17` (every other admin screen is hard CEO-only, see §15.4).

---

## 7. Shoot flow

Service: `production/service/ShootingService.java`. Entities: `ShootingAssignment` (has
`isActive`/`endedAt`/`isLead`), `ShootingExecutionParticipant` (a **snapshot**, not the live
assignment table — see below).

| Action | Who | Permission | Assignment required | Status guard | Result |
|---|---|---|---|---|---|
| Start Shooting | Actively-assigned Cameraperson **only** — explicitly **not** bypassable by CEO/MM native authority (ENG-013/ENG-043: "native authority covers management, not hands-on execution of someone else's task") | none (pure assignment gate, no `OperationalPermission` exists for this act) | Active `ShootingAssignment` | `SA` | `SA→SIP` |
| Submit for Shoot Review | same active-assignee gate | none | same | `SIP`, **no open Hold** (§11.3), **Drive/Folder Link must be non-blank** | Snapshots every currently-active assignee into a `ShootingExecutionParticipant` row (this snapshot — not the live table — is what "who participated in this cycle" means from now on); `SIP→SRV` |
| Shoot Review Decide | reviewer | `PERM_05_SHOOT_REVIEW` | n/a (reviewer, not executor) | `SRV`; self-review conflict if reviewer is a recorded participant AND acting via delegated grant | see below |
| — Approve | | | requires ≥1 `qualifyingRecipientUserIds`, each must be a recorded participant | `SRV→SAP`; awards full predefined mark to each qualifying recipient (§10) |
| — Rework | | | reason mandatory | `SRV→SIP` (loop, unlimited cycles) |

Reassignment / Hold / Reschedule / Cancel for Shoot are all handled by the shared, stage-agnostic
`AdminActionService`/`HoldService` — see §11.

---

## 8. Edit flow

Service: `production/service/EditingService.java`. Mirrors Shoot almost exactly; differences noted.

| Action | Who | Permission | Assignment required | Status guard | Result |
|---|---|---|---|---|---|
| Editor assignment | assigner | `PERM_06_EDIT_ASSIGNMENT` | — | **`SAP` or `EA`** (wider window than Shoot's `PL`-only — an editor can be added both at the moment of Shoot Approval and any time after while still `EA`) | first assignment while status is exactly `SAP` also fires `SAP→EA`; later additions while already `EA` don't re-trigger |
| Edit Lead | assigner | `PERM_06_EDIT_ASSIGNMENT` | must already be an active Editor | `SAP`/`EA` | same one-active-Lead-per-plan DB constraint as Shoot |
| Edit Description | assigner | **`PERM_06_EDIT_ASSIGNMENT` only** (no PERM_03-equivalent dual-authority carve-out — asymmetric vs Shoot's, §5.8) | — | not status-gated | — |
| Start Editing | active Editor only, not CEO/MM-native-bypassable (same ENG-013/043 rule) | none | active `EditingAssignment` | `EA` | `EA→ED` |
| Submit for Edit Review | active Editor only | none | same | `ED`; no open Hold (Hold is valid at `ED`, not `EA`/`ERV`); Drive Link required | snapshots `EditingExecutionParticipant`; `ED→ERV` |
| Edit Review Decide — Approve | reviewer | `PERM_07_EDIT_REVIEW` | — | `ERV`; self-review conflict same rule; ≥1 qualifying recipient (each a recorded participant) | `ERV→EAP→RFP` (both same call); awards full predefined mark to each |
| Edit Review Decide — Rework | reviewer | `PERM_07_EDIT_REVIEW` | — | `ERV`; reason mandatory | `ERV→ED` (loop, unlimited) |

Reassignment for `TaskStage.EDITING` requires the new assignee to hold the "Video Editor" Business
Role (server-side, §11). Reassignment/Hold/Reschedule/Cancel logic itself is identical to Shoot's
(shared `AdminActionService`/`HoldService`).

---

## 9. Publishing flow

Service: `publishing/service/PublishingService.java`. **Publishing has no review gate/`ReviewCycle`
— there is no "Publishing Review" concept**, unlike Idea/Planning/Shoot/Edit.

### 9.1 Publisher assignment
- **Who**: **native CEO/MM authority only** — explicitly **not** available to a `PERM_08` grant holder (`authorizationService.requireNativeAuthority`, with a code comment explaining PERM_08 is for executing *your own* assigned task, not assigning others).
- **Status guard**: `RFP` only.
- Idempotent add; `PublishingAssignment` (`publishing_assignments` table, `V15`) is **not in the frozen ERD** — an intentional additive tracking table (ENG-033/ENG-035), does **not** itself gate Start Publishing (that's PERM_08 + active-assignee, below).

### 9.2 Start Publishing / recording an event
| Action | Who | Permission | Assignment required | Status guard | Result |
|---|---|---|---|---|---|
| Start Publishing | | `PERM_08_PUBLISHING_EXECUTION` | **active Publisher assignee** (required in addition to the permission — even CEO/MM's native PERM_08-equivalent bypass does not exempt from the active-assignee check for hands-on execution, same ENG-013/043 pattern as Shoot/Edit start) | `RFP` | `RFP→PUBG` |
| Record Actual Publication event | | `PERM_08` | active assignee | `PUBG`; valid `plannedOutputId`/`publicationTargetId` pair; **Evidence URL mandatory** | Creates `ActualPublicationEvent`; a duplicate `ORIGINAL` event for an already-live (output,target) pair is **rejected server-side** ("use Repost instead") — not just UI-hidden |
| Record `REPOST` event | same | same | same | same pair, but **no uniqueness guard** — repostable indefinitely | new event row each time |

Each recorded event auto-creates a `PerformanceObligation` (1:1,
`performanceDueDate = actualPublicationTimestamp + 2 days`, immutable once set).

### 9.3 Bulk Publishing checklist
One row per real (Planned Output × Publication Target) pair (never grouped by output alone),
excluding pairs already Target-N/A. The bulk endpoint loops the same single-row
`recordActualPublication` guarantees per pair — no separate rule. Server auto-stamps the
publication timestamp (`Instant.now()`) — there is no client-supplied "Actual Publication Date"
field (removed by ENG-056; see §17 gaps for the history of this decision).

### 9.4 Target N/A
- **Who**: `PERM_08` holder.
- **Guard (ERD-CON-017, backend-enforced)**: designating a target N/A is blocked if it would leave
  the **entire plan** with zero live posts and zero remaining non-N/A targets — at least one target
  must always stay live-or-eligible.
- **Reversal**: creates a **new** `PublicationTargetNaRecord` (type `REVERSED`) linked to the prior
  one — the original record is never mutated (append-style, though this specific table is **not**
  one of the 3 DB-trigger-guarded correction-ledger tables, see §9.6).

### 9.5 Scope resolution → auto-advance to Performance Pending
`isScopeResolved`: true when every mapped (output, target) pair is either (a) has a live `ORIGINAL`
post or (b) is designated N/A, **and** at least one pair has a live post. Checked after every
publish/N/A action; auto-fires `PUBG→PP` the moment it becomes true.

### 9.6 Evidence correction
`PERM_08` holder, mandatory reason + mandatory corrected URL. Appends an immutable
`PublicationEvidenceCorrection` row (chained); the original `ActualPublicationEvent.evidenceUrl` is
never mutated.

### 9.7 Publishing Description & Comments
- **Description**: CEO/MM-**native**-only (even the correctly-assigned Publisher cannot set it —
  confirmed by `StageDiscussionTest.publishingDescriptionIsNativeOnlyButAssignedPublisherCanComment`).
- **Comments**: native authority **or** the currently-active Publisher assignee may comment (same
  general Stage Discussion mechanism, §12).

### 9.8 Completion
No explicit "Complete" button anywhere — `PFUP→COMP` fires automatically the moment every
`PerformanceObligation` on the plan is marked completed (§10.2). `firstCompletedAt` is stamped.

### 9.9 Reopen a Completed deliverable (`AdminActionService.reopenCompleted`)
| Purpose | Permission | Target status |
|---|---|---|
| `PUBLISHING_REOPEN` | `PERM_08_PUBLISHING_EXECUTION` | `COMP → PUBG` |
| `METRIC_CORRECTION_REOPEN` | `PERM_09_PERFORMANCE_UPDATE` | `COMP → PFUP` |

Status guard: must be exactly `COMP`. Reason mandatory. (One code comment at
`AdminActionService.java:221` states "COMP→RFP" for the publishing case — this is a **stale/incorrect
comment**; the actual code and the corresponding test (`WorkflowVariantsE2ETest.
completedDeliverableReopensForPublishingOntoPubgNotRfp`) both confirm the real target is `PUBG`, not
`RFP`. Trivial doc-only inconsistency, not a functional bug — noted in §17.)

---

## 10. Performance flow & Marks / Qualifying employees

### 10.1 Performance Obligation & Scorecard
Service: `performance/service/PerformanceService.java`.
- **Due-date gate (backend-enforced)**: metric entry (draft OR submit) is blocked with 400 if
  `performanceDueDate` (immutable, = publication date + 2 days) is still in the future.
- **Scorecard draft**: `PERM_09_PERFORMANCE_UPDATE`. Repeatedly re-savable pre-submit. Fields:
  `views3sec`, `plays`, `averageWatchTimeSeconds`, `videoLengthSeconds`, `linkClicks`,
  `impressions` — each of the first four also carries its own "N/A" flag. Computed rates
  (hook-rate %, hold-rate %, CTR %): any null/N-A numerator or zero/null denominator produces
  `null` ("N/A"), **never** `0` or a divide error (SC-REQ-001).
- **Scorecard submit**: requires an existing draft; seals it (immutable once sealed at the app
  layer — `requireNotSealed()`); marks the obligation completed; re-checks `PFUP→COMP`.
- **Metric correction**: `PERM_09` holder, mandatory reason, **only on an already-sealed scorecard**
  (409 otherwise). Chains onto prior corrections to compute the "current effective value" before
  applying a new delta — append-only ledger, original scorecard row never mutated.

### 10.2 Marks model
- **`PredefinedRoleMarks`**: one row per `ContentPlan`, set **at Idea Approval time** (both
  Cameraperson and Editor marks are mandatory inputs to the Approve decision, §3.2), values
  constrained to `{0, 0.5, 1.0, 2.0, 3.0}`.
- **`PersonalMarkAttribution`**: the actual per-person award, created **only at Shoot/Edit Approval
  time** (never at Rework, Publishing, or Repost). Each row: recipient, role type
  (CAMERAPERSON/EDITOR), the review cycle that earned it, and the full predefined mark value.
  **Never split or averaged** — if 2 people both qualify on the same plan, each gets the FULL mark
  (proven by `HighPriorityEdgeCaseTest.twoQualifyingCamerapersonsEachReceiveTheFullMarkNeverSplit`
  and the Editor equivalent). Unique per `(recipient, reviewCycle)` — prevents double-award within
  one approval event.
- **Qualifying selection**: at Shoot/Edit Review Approve time, the reviewer picks a
  `qualifyingRecipientUserIds` set from among that cycle's recorded `...ExecutionParticipant`s
  (the submit-time snapshot, not the live assignment table). Non-qualifying
  assignees/participants simply receive no mark row — there's no explicit rejection action, just
  silent omission. After a Rework cycle causes the same person to submit twice, the "Qualifying"
  picker still shows them exactly once (deduplicated), not twice
  (`HighPriorityEdgeCaseTest.qualifyingCamerapersonListNeverShowsDuplicatesAfterAReworkCycle`).
- **`PredefinedMarkCorrection`**: append-only, chained, mandatory reason, `PERM_01_IDEA_REVIEW`
  (per the entity's own javadoc — **NEEDS CONFIRMATION**: the exact calling controller/service enforcing this wasn't independently traced in this pass beyond the entity comment and `IdeaService.correctPredefinedMarks`, which does confirm `PERM_01`).

### 10.3 Correction-ledger / append-only pattern (general, cross-cutting)
Three tables (`predefined_mark_corrections`, `publication_evidence_corrections`,
`performance_metric_corrections`) get **genuine database-level** immutability via `V12`'s generic
`trg_reject_update_delete()` function, installed as BEFORE UPDATE and BEFORE DELETE triggers on
each — not just application-layer discipline. `V13` extends the same guarantee with
statement-level TRUNCATE-reject triggers (row-level triggers don't fire on TRUNCATE) and splits DB
privileges so the app's runtime role has no UPDATE/DELETE grant at all on these tables (belt and
suspenders beneath the trigger layer). The same append-only pattern generally applies to
`workflow_transition_history` and `system_audit_log` too. **`publication_target_na_records` is
NOT one of the 3 DB-trigger-guarded tables** even though its reversal pattern looks the same at the
application layer (§9.4) — worth knowing the DB-level guarantee doesn't extend there.
**NEEDS CONFIRMATION**: whether `creative_performance_scorecards`' own "sealed" immutability has a
DB trigger backing it beyond the app-layer `requireNotSealed()` check — not verified.

---

## 11. Cross-cutting Admin Actions: Reassignment, Hold/Resume, Reschedule, Cancel

Service: `workflow/service/AdminActionService.java` (Reassign/Reschedule/Cancel/ReopenCompleted),
`workflow/service/HoldService.java` (Hold/Resume). `TaskStage` enum has exactly 2 values:
`SHOOTING`, `EDITING` — **Reassignment does not apply to Publishing** (Publisher assignment is
managed directly via `PublishingService.assignPublisher`/`removePublisher`, §9.1, not through this
shared mechanism).

### 11.1 Reassignment (`PERM_11_REASSIGN`)
- Reason mandatory; ≥1 new assignee required.
- **Blocked once closed**: status `CAN`, `COMP`, or `RJ` → rejected. Otherwise valid at **any**
  other status — not scoped to "only during the matching stage" (e.g. callable even before the
  corresponding stage has technically started, since the only guard is "not closed").
- **Business-Role check (backend-enforced, ENG-054)**: every new SHOOTING assignee must hold the
  "Camera Person" Business Role; every new EDITING assignee must hold "Video Editor" — checked
  server-side, not just UI-filtered (`HighPriorityEdgeCaseTest.
  reassignRejectsANewAssigneeWithoutTheMatchingBusinessRoleButAcceptsAMatchingOne`).
- **Full swap, not additive**: ends every currently-active assignment for that stage (recorded as
  `ReassignmentAssignee(PREVIOUS)`), creates new ones for every submitted ID (`NEW`) — all wrapped
  in one permanent `ReassignmentRecord`.
- Does not appear to re-set Lead on the new team (§5.7 NEEDS CONFIRMATION).

### 11.2 Reschedule (`PERM_10_RESCHEDULE`)
Reason mandatory; blocked once closed. Applies new Shoot/Edit/Live dates (any `null` param keeps
the prior value); re-validates chronology but not the 5-day Standard/Urgent floor (§5.2). Never
touches the Performance Due Date (that's separately immutable, §10.1).

### 11.3 Hold / Resume
- **Who**: **native CEO/MM authority only** — not an `OperationalPermission`-gated action at all,
  cannot be delegated via a grant.
- **Status guard**: valid only while `SIP` (Shoot) or `ED` (Edit) — ERD-CON-061.
- **Only one open Hold at a time** per workflow instance — a second concurrent Hold attempt is
  rejected (ERD-CON-062).
- Reason mandatory.
- **Effect**: does **not** change `WorkflowStatus` — it's a parallel record that only blocks the
  Submit-for-Review step (`requireNoOpenHold` inside `submitShootReview`/`submitEditReview`).
  **Starting** Shoot/Edit work is NOT blocked by an open Hold — only *submitting for review* is.

### 11.4 Cancel (`PERM_12_CANCEL`)
Reason mandatory. **Permanently blocked if the deliverable has ever been Completed**
(`workflowInstance.everCompleted()`, ERD-CON-006) — even after a later Reopen, Cancel stays blocked
forever once `everCompleted` was ever true. Also blocked if already closed. Result: `→CAN`
(terminal).

---

## 12. Comments & Descriptions (Stage Discussion)

Service: `discussion/service/StageCommentService.java`. Applies to **Shoot, Edit, and Publishing
only** — there is no Planning-stage or Idea-stage Description/Comment thread.

### 12.1 Description — one shared field per stage per plan
A single mutable text column per stage (`shoot_description`/`edit_description`/
`publishing_description` on `content_plans`) — not a growing list, not per-assignee. Who can edit:

| Stage | Who can edit | Status-gated? |
|---|---|---|
| Shoot | `PERM_04_SHOOT_ASSIGNMENT` **or** `PERM_03_PLANNING_REVIEW` (dual-authority) | No |
| Edit | `PERM_06_EDIT_ASSIGNMENT` only | No |
| Publishing | native CEO/MM only (not even PERM_08 holders / the assigned Publisher) | No |

### 12.2 Comments — Jira-style thread, one per (plan, stage)
- **Who can post** (backend-enforced): native CEO/MM authority **or** whoever is currently an
  **active assignee on that exact stage** — anyone else (including an Employee holding an unrelated
  delegated permission but no active assignment on this stage) is forbidden. Reading a thread is
  not separately gated (page-level visibility).
- **Edit/Delete — own-comment-only, even for CEO/MM** (`ENG-050`, deliberate, explicit exception to
  the usual native-authority override): a comment's author is the only one who can ever edit or
  delete it, full stop — `StageDiscussionTest.
  onlyCommentAuthorCanEditOrDeleteAndDeleteIsSoftNotHard` confirms even CEO gets 403 on someone
  else's comment.
- **Delete is soft** (`isDeleted`/`deletedAt` flags, text stays in the DB) — hard `DELETE` remains
  permanently DB-rejected by trigger even after edit/soft-delete was introduced (`V18`,
  `DbIntegrityEnforcementTest.stageCommentHardDeleteIsStillRejectedAtTheDatabaseLevelAfterEng050`).
- Every edit/delete is separately captured in the generic audit log with old→new value, so no
  history is lost even though the row itself became mutable.
- Threads never mix across stages — Shoot and Edit comments on the same plan are fully independent.

### 12.3 AJAX behavior
Both Description and Comments save/post via AJAX with no page reload
(`static/js/stage-discussion.js`), with a fully-functional plain-`<form>` no-JS fallback (POST +
redirect-back).

---

## 13. Roles & Permissions

### 13.1 Access Classes (the real authorization boundary)
3 values (`identity/domain/AccessClass.java`): `CEO_OWNER`, `MARKETING_MANAGER`, `EMPLOYEE`.

### 13.2 Business Roles
`BusinessRole` is the visible organizational title; it **never itself grants permissions**
(ERD-CON-063) — it only maps to exactly one `AccessClass`. A `User`→`BusinessRole` link is a
**hard 1:1** (`nullable=false` `@ManyToOne`) — **a user holds exactly one Business Role at a time,
never multiple.** 17 seeded roles (`V1__reference_data.sql`):

| Role | Access Class |
|---|---|
| CEO | CEO_OWNER |
| Marketing Manager | MARKETING_MANAGER |
| HR Manager | EMPLOYEE |
| Camera Person | EMPLOYEE |
| Video Editor | EMPLOYEE |
| Marketing Coordinator | EMPLOYEE |
| CEO's Executive Assistant | EMPLOYEE |
| Publisher | EMPLOYEE |
| Model | EMPLOYEE |
| Senior Manager | EMPLOYEE |
| SEO Executive | EMPLOYEE |
| SEO Intern | EMPLOYEE |
| Marketing Intern | EMPLOYEE |
| Sales Manager | EMPLOYEE |
| CRM Manager | EMPLOYEE |
| Customer Support Executive | EMPLOYEE |
| Marketing Data Operator | EMPLOYEE |

Business Roles are a CEO-manageable, expandable catalogue (new roles can be created at
`/app/admin/business-roles`), not a hardcoded enum — only the 3 `AccessClass` values are fixed (DB
CHECK constraint).

### 13.3 The 17 Operational Permissions (fixed, frozen catalogue)

| # | Code | Meaning | Stage |
|---|---|---|---|
| 1 | `PERM_01_IDEA_REVIEW` | Approve (with predefined Marks)/Reject/Retain an idea | IDEA_MANAGEMENT |
| 2 | `PERM_02_PLANNING_EXECUTION` | Prepare planning parameters, submit for Planning Review | PLANNING |
| 3 | `PERM_03_PLANNING_REVIEW` | Approve/Rework submitted planning parameters | PLANNING |
| 4 | `PERM_04_SHOOT_ASSIGNMENT` | Assign initial Cameraperson(s) | SHOOTING |
| 5 | `PERM_05_SHOOT_REVIEW` | Approve/Rework shoot output; attribute Cameraperson marks | SHOOTING |
| 6 | `PERM_06_EDIT_ASSIGNMENT` | Assign Editor(s) after Shoot Approval | EDITING |
| 7 | `PERM_07_EDIT_REVIEW` | Approve/Rework edited output; attribute Editor marks | EDITING |
| 8 | `PERM_08_PUBLISHING_EXECUTION` | Record Actual Publication events; manage Target N/A | PUBLISHING |
| 9 | `PERM_09_PERFORMANCE_UPDATE` | Enter/submit Performance Scorecard metrics | PERFORMANCE |
| 10 | `PERM_10_RESCHEDULE` | Modify approved production dates (reason mandatory) | cross-cutting |
| 11 | `PERM_11_REASSIGN` | Replace existing Shoot/Edit assignees (reason mandatory) | cross-cutting |
| 12 | `PERM_12_CANCEL` | Cancel a pre-completion deliverable (reason mandatory) | cross-cutting |
| 13 | `PERM_13_FOLDER_LINK_MANAGE` | Create/replace the asset folder link | cross-cutting — **defined but never actually checked anywhere**, see §17 |
| 14 | `PERM_14_TEAM_WORKLOAD_VIEW` | View team workload summaries | view-only |
| 15 | `PERM_15_TEAM_KPI_VIEW` | View team KPI dashboards | view-only |
| 16 | `PERM_16_AUDIT_HISTORY_VIEW` | View audit-history records | view-only |
| 17 | `PERM_17_PLATFORM_CATALOGUE_MANAGE` | Administer Platform/Channel/Target catalogue | ADMINISTRATIVE |

7 `LifecycleStage` values: `IDEA_MANAGEMENT`, `PLANNING`, `SHOOTING`, `EDITING`, `PUBLISHING`,
`PERFORMANCE`, `ADMINISTRATIVE`.

### 13.4 PermissionGrant scope model
`PermissionScopeType`: `GLOBAL` (covers every stage/item), `STAGE_RESTRICTED` (covers only the
`LifecycleStage`(s) explicitly listed on the grant), `ITEM_SPECIFIC` (covers only specific
`WorkflowInstance`s explicitly listed). A grant with an empty stage/item list for a restricted
scope type is rejected at grant-creation time. Grants are **soft-revoked only** (`active=false` +
`revokedAt`, row never deleted, ERD-CON-042) and carry an optional `effectiveFrom`/`effectiveUntil`
temporal window.

### 13.5 AuthorizationService — the single decision point
`identity/service/AuthorizationService.java`. Called by every domain service before a governed
action:
1. **`hasNativeAuthority(user)`** = true iff `CEO_OWNER` or `MARKETING_MANAGER`. Native authority
   holders **always** pass `requireAuthority(...)` — no grant row is ever consulted for them.
2. Otherwise, the user's active grants for that exact permission are checked for temporal validity
   **and** scope coverage of the requested stage/item. First matching grant wins.
3. On failure, one of three distinct errors: `PERM_OPERATIONAL_PERMISSION_EXPIRED` (had a grant,
   now expired/revoked), `PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE` (valid grant, wrong
   stage/item), `PERM_OPERATIONAL_PERMISSION_REQUIRED` (no grant at all).
4. **Self-review conflict**: only checked on the delegated-grant path — native CEO/MM authority is
   explicitly exempt.

**Two independent gates, not one.** For an Employee to perform an *execution* act (Start Shoot,
Start Edit, Start Publishing, submit-for-review), it is not enough to hold the matching
`OperationalPermission` — **there is no such permission for the raw "start work" act at all**; the
gate is purely "are you an active assignee on this item," checked in the domain service, entirely
separate from `AuthorizationService`. Governed `OperationalPermission`s (04/05/06/07/08/09) instead
gate the *assignment* and *review/decision* acts. CEO/MM's native authority does **not** bypass the
active-assignee execution gate either (ENG-013/ENG-043) — a CEO cannot click "Start Shoot" on a
plan they are not themselves assigned to as Cameraperson.

### 13.6 Admin screens

| Screen | Route | Who | Delegable via grant? |
|---|---|---|---|
| Users | `/app/admin/users` | CEO only (hard `AccessClass` check) | **No** — not an Operational Permission at all |
| Permissions | `/app/admin/permissions` | CEO only | No |
| Business Roles | `/app/admin/business-roles` | CEO only | No |
| Catalogue (Platforms/Channels/Targets) | `/app/admin/catalogue` | Native CEO/MM **or** any `PERM_17` grant holder | **Yes** — the one exception |

Deactivating a user immediately revokes all their active JWT sessions (not just future logins) via
`TokenRegistryService`.

### 13.7 `accessClass` — the mechanism the whole UI branches on
`web/mvc/MvcNavigationAdvice.java` is a `@ControllerAdvice` that puts `accessClass` into every MVC
model automatically, regardless of the specific controller. Every role-branched JSP (`nav.jsp`,
`idea-detail.jsp`, etc.) relies on this exact attribute name. **A redesign should keep using this
mechanism** — renaming/removing it would silently break every existing role branch.

**Important**: there is **no route-level Spring Security restriction** on `/app/admin/**` or any
other path pattern — the security filter chain only enforces "authenticated or not." All actual
role/permission gating happens in the controller method body (a redirect-if-unauthorized
convenience check) **and, authoritatively, in the service layer**
(`AuthorizationService`/`requireAccessClass`/`requireNativeAuthority` calls). A redesign changing
routing must preserve these per-handler checks — the URL space itself is not protected by any
config-level allow-list.

---

## 14. Employee-facing screens: My Work / My Ideas / Submit Idea

*(This section reflects work done this session — ENG-057 through ENG-061 — and is the most
current, directly-verified part of this document.)*

### 14.1 My Work (`/app/my-work`, `LandingMvcController`)
Same route serves different content by `AccessClass`, branched server-side (established
"same-route, role-branched controller" house pattern, also used by `/app/pipeline` and `/app/ideas`).

- **Visibility rule**: a task appears in "Active Work" only once the relevant stage is actually
  reachable — e.g. a Cameraperson assigned during Planning does **not** see it in My Work until the
  plan reaches `SA` (Shoot Assigned), even though the assignment row already exists earlier
  (`MyWorkVisibilityTest.camerapersonSeesTaskOnlyAfterPlanningIsApprovedNotAtAssignmentTime`).
- Once a stage's own review decides and the plan moves on, the task disappears from Active Work and
  appears only in read-only Completed Work/History — never in both places at once.
- **Camera Person-specific enhanced dashboard** (every other Business Role gets a simpler generic
  Active/Completed table): 4 KPI cards (Active Shoots / Rework Required / Delayed / Completed),
  computed by filtering the **exact same** row list the tables render (never a separate count
  query — structurally guarantees the counts and the table can't drift apart). Tabs: Active
  Work / History / Marks (pure client-side show/hide, `my-work-tabs.js`, no fetch).
  - "Delayed" (the `DLY` supplementary flag, §2) is computed **client/server-view-side only** —
    `plannedShootDate.isBefore(today) && not yet completed` — never stored as the real
    `WorkflowStatus`, and rendered as its own pill overriding the normal status pill.
  - Action column is **pure navigation**, never an inline POST — a plain link to the existing
    `/app/deliverables/{id}` detail page, where the real Start/Submit buttons live. Per-status
    label varies (Start Shoot / Continue / Resume / View Feedback / no button while In Review).
  - "Marks" tab reuses the existing predefined-mark logic scoped to the viewer's own awards.
- The deliverable detail page (`/app/deliverables/{id}`) is the **one shared** "task detail" screen
  for every stage — there is no separate bespoke detail screen per stage. It shows a Content
  Summary block, prominent (non-muted) Shoot/Edit/Publishing Instructions, the Comments thread, a
  Rework Feedback box (shown only when the most recently DECIDED review cycle for that stage was a
  Rework — separate from, and narrower than, the permanent Timeline which keeps the old reason
  forever as history even after the box itself disappears on approval).

### 14.2 My Ideas (`/app/ideas` for `EMPLOYEE`, same route as CEO/MM's Idea Queue)
- **Own-ideas-only**, enforced **at the backend**, not just hidden from the table: a direct
  URL/ID guess at another employee's idea `redirect`s to `/app/ideas` with a flash error — treated
  identically to a genuinely-nonexistent ID, never renders the other idea's content, never a raw
  500 (an earlier version of this code did leak a stack trace on this exact path; fixed — see §17
  for the general pattern this exposed).
- 5 KPI cards (Total / Under Review / Approved / Retained / Rejected), same
  same-source-as-the-table guarantee as My Work.
- Search (title/ID contains) + Status filter (Under Review/Approved/Retained/Rejected/Reopened) +
  Submitted-On date range (7/30/90/All days) + pagination — implemented as plain `GET` query-param
  form submissions (full page reload), **not** AJAX — deliberately, since no client-side
  table-filtering JS pattern exists anywhere else in this app to safely reuse.
- **"Idea Status" values are strictly**: Under Review, Approved, Retained, Rejected, Reopened —
  **never** a raw Planning/Shoot/Edit/Publishing status name, per the rule in §3.5. "Reopened" is
  disambiguated from "Under Review" (both are really `PA` underneath) by checking whether the most
  recent idea-lifecycle transition was `REOPEN_IDEA`.
- Feedback column shows the latest **decided** `IDEA_REVIEW` cycle's `decisionReason` verbatim, or
  a literal "—" if none — never fabricated.
- View Details (shared `idea-detail.jsp`, also used by CEO/MM): Idea Details, Review Feedback (if
  any), "Idea Decision History" (idea-lifecycle events only — Submitted/Approved/Rejected/
  Retained/Reopened, re-labeled from the raw trigger commands so no Planning-stage status name
  leaks into this list either), and — only if the idea has been approved into a `ContentPlan` — a
  small informational sentence ("This approved idea has moved to Planning.") with an operational
  "Open the deliverable" link shown **only to CEO/MM**, never to the Employee viewer (an Employee
  gets the sentence only, per "do not expose Planning/Shoot/Edit/Publishing operational details on
  the Idea screen").
- No employee-facing "correction"/resubmit control exists for a Retained idea — Reopen is
  `PERM_01`-gated, CEO/MM only, with no employee-authorized equivalent anywhere in the codebase.

### 14.3 Submit Idea (`/app/ideas/new` GET, `/app/ideas` POST)
4 distinct fields: Idea Title (required, 120 chars, live counter), Reference Link (optional, URL
format validated both client- and server-side), Additional Note (optional, short), Idea
Description/Details (optional, 500 chars, live counter). Client-side validation
(`idea-submit.js`) blocks invalid submission without a page reload and shows inline errors that
clear live as the field becomes valid; on a **backend** validation failure (the authoritative
check — e.g. the no-JS fallback path), the form re-renders with every entered value preserved and
the error attached to the specific field, not just a generic banner. On success: flash "Idea
submitted successfully." then redirect to My Ideas (where that message renders).

---

## 15. CEO/MM screens

All routes below live in `ReportingMvcController`/`AdminMvcController`, gated by
`AuthorizationService.requireAuthority(user, PERMISSION, null, null)` (stage-independent — no
per-item scoping) inside a private `allowed()` helper; failure redirects to `/app/home`.

| Screen | Route | Gate | Data source | Notes |
|---|---|---|---|---|
| Content Pipeline | `/app/pipeline` | Hard `AccessClass` (CEO_OWNER/MARKETING_MANAGER only — not permission-delegable) | `PipelineDashboardService` — one row per Content ID across all plans, joining assignments/talent/targets/events/review cycles | 18-column dashboard; Employees hard-redirected to `/app/home` |
| Team Workload | `/app/reports/workload` | `PERM_14_TEAM_WORKLOAD_VIEW` | `KpiService.teamWorkload` | |
| Team KPI | `/app/reports/team-kpis` | `PERM_15_TEAM_KPI_VIEW` | `KpiService.teamKpis` | |
| KPI Console | `/app/reports/kpis` | `PERM_15_TEAM_KPI_VIEW` | `KpiService.queryGovernedKpis` | Exactly 30 governed KPIs, computed on demand (no persisted KPI table), 5 fixed categories |
| Admin Actions report | `/app/reports/admin-actions` | `PERM_16_AUDIT_HISTORY_VIEW` | `AdminReportingService` | |
| Delayed Deliverables | `/app/reports/delayed` | **None** — all authenticated roles, self-scoped server-side | `AdminReportingService.delayedDeliverables` | Only screen in this group with no route-level permission gate |
| Audit History | `/app/audit` | `PERM_16_AUDIT_HISTORY_VIEW` | Raw `SystemAuditLogRepository`, capped at 500 rows | |
| Export | `/app/export` | Hard `AccessClass` (CEO_OWNER/MARKETING_MANAGER only) | `MultiFormatExportService` — full governed-table union, synchronous, JSON/CSV/XLSX | No export-job table (accepted MVP simplification, ENG-030) |

All reporting/export screens are read-only apart from the export trigger itself.

### 15.1 Home / login dispatch
`/app/home` is a **pure dispatcher**, never rendering its own view: CEO_OWNER/MARKETING_MANAGER →
redirect to `/app/pipeline`; EMPLOYEE → redirect to `/app/my-work`. (`home.jsp` exists as a file but
no controller returns that view name — orphaned template, see §17.)

### 15.2 Nav differences
EMPLOYEE gets a minimal 3-link nav (My Work / My Ideas / Submit Idea). CEO_OWNER/MARKETING_MANAGER
get the full nav (Pipeline, My Work, Idea Queue, Submit Idea, Team Workload, Team KPI, KPI Console,
Delayed Deliverables, Admin Actions, Logs, Export). CEO_OWNER additionally sees Users/Business
Roles/Permissions/Publishing Catalogue admin links — **Marketing Manager does not see the Catalogue
admin link in nav even though they could reach it via URL** if separately granted `PERM_17` (nav
visibility and route authorization are two independent mechanisms — the nav link list is not the
same thing as the actual access gate).

---

## 16. AJAX / no-page-reload behavior catalog

Every JS file in `static/js/` follows the same convention: **the server always renders a fully
functional plain `<form method="post">` first**; JS only intercepts to add either (a) a real
`fetch` AJAX POST (with an `X-Requested-With: fetch` header the server checks to decide whether to
respond with JSON/200 instead of a redirect+flash-message), or (b) purely client-side UX with zero
network calls.

| File | AJAX (`fetch`)? | What it powers |
|---|---|---|
| `model-picker.js` | No | Model(s)/Cameraperson(s) checkbox→chip UX in Planning; plain checkboxes underneath still submit normally |
| `assignment-picker.js` | Yes (batch) | Shoot/Edit/Publishing "...(s)" assignment chip picker; one batch AJAX POST per "Assign" click, saving all staged chips + Lead together |
| `idea-submit.js` | No | Character counters + required/URL client validation on Submit Idea; valid submits are a normal POST |
| `my-work-tabs.js` | No | Pure tab show/hide on My Work; everything already server-rendered |
| `planning-submit.js` | Yes | "Submit for Planning Review": client validation blocks invalid submits inline; valid ones go via AJAX so a backend failure returns inline, not a page nav |
| `publication-scope.js` | Yes | Publication Scope target add/remove + Planned Output add/edit/remove, all via AJAX; Platform→Channel filtering and Output/Reel-Type visibility are pure client-side |
| `publishing-checklist.js` | No (fetch-free) | Enables/disables checklist row fields so only checked rows submit; the actual bulk submit is a plain form POST |
| `reassign-form.js` | No | Toggles between Shoot/Edit assignee pickers by selected stage; clears the inactive picker |
| `review-decision.js` | No | Toggles Reason (Rework) vs Qualifying-recipients (Approve) visibility on review decision forms; comment explicitly notes the server still enforces the mandatory-for-rework rule independently |
| `stage-discussion.js` | Yes | Shoot/Edit/Publishing Description (inline edit) and Comments (post/edit/delete) — all no-reload |

**Every one of these has a working no-JS fallback** — disabling JS degrades to a plain form POST
that still functions (validated less richly, and Planning/Discussion/Publication-Scope/Planned-
Output actions fall back to full-page-reload redirects instead of in-place updates), never a dead
button. A redesign that keeps this progressive-enhancement contract intact is safe; one that
assumes JS is always available is a behavior change from the current app.

---

## 17. Known gaps, inconsistencies, and unclear behavior

These are genuine findings from reading the code, not speculation. Recorded here so a redesign
doesn't accidentally "fix" or paper over something the backend team may have intentionally deferred
— confirm with the product owner before building UI around either interpretation.

1. **Permission #13 (`PERM_13_FOLDER_LINK_MANAGE`) is defined in the frozen 17-permission catalogue
   but never actually checked anywhere.** The folder link is just one field inside
   `PlanningService.updateParameters`, gated on `PERM_02_PLANNING_EXECUTION` instead. Recorded as
   `DISC-002` in `docs/IMPLEMENTATION_DISCREPANCIES.md`. Effect: an Employee holding only PERM_02
   can change the folder link without PERM_13; an Employee holding only PERM_13 cannot use it at
   all. If a redesign adds a dedicated "manage folder link" UI, it should gate on PERM_02 to match
   actual current enforcement, not PERM_13 (which would be UI-only and not actually match backend
   behavior until/unless the backend is changed).
2. **Every Domain-8 endpoint (Hold/Resume/Reschedule/Reassign/Cancel/Reopen-Completed) lives at
   `/api/v1/content-plans/{id}/...`, not the frozen spec's literal `/api/v1/workflows/{id}/...`.**
   Recorded as `DISC-001`. Purely a URL-shape deviation — request/response/semantics all match the
   spec; every other domain in the app uses the same `content-plans`-nested convention.
3. **An uncaught `DomainException` thrown from a plain `@Controller` (JSP/MVC) GET handler produces
   a raw HTTP 500 with an exposed stack trace, not a clean 404/redirect** — confirmed live against
   `DeliverableMvcController#requirePlan` (`GET /app/deliverables/{bogus-id}`). Only
   `RestExceptionHandler` exists, and it only applies to `@RestController` JSON responses; there is
   no MVC-side equivalent. `IdeaMvcController#detail` was specifically fixed to avoid this pattern
   (catches the not-found/ownership-denied case and redirects with a flash message instead of
   throwing), but this is a **fix at one call site only** — other MVC GET handlers using the same
   `.orElseThrow(DomainException.notFound(...))` pattern (e.g. `DeliverableMvcController`) still
   have the underlying issue. A redesign touching any MVC "not found" page should be aware this
   pre-existing gap is codebase-wide, not fixed globally.
4. `home.jsp` exists as a template file but **no controller anywhere returns that view name** —
   confirmed via a full-codebase grep. `/app/home` always redirects onward (§15.1) before any view
   renders. The file's own body still contains stale "subsequent build phases" placeholder text.
   Orphaned/dead — not wired into any route.
5. **Planned Output add/edit/remove has no explicit `WorkflowStatus == PL` guard** in
   `PlanningService` — only the `PERM_02_PLANNING_EXECUTION` check — unlike scheduling and
   assignment actions, which are status-gated. Confirm whether outputs are genuinely meant to be
   editable at any status.
6. **Initial Cameraperson assignment during Planning has no server-side Business-Role check**,
   while Reassign explicitly does (ENG-054 hardened Reassign specifically). The initial-assignment
   path appears to trust the UI's role-filtered picker alone.
7. **Shoot/Edit Lead has no observed effect on marks computation** — appears to be a purely
   informational/organizational designation (DB-enforced to be unique and to always be a subset of
   active assignees), not a multiplier or gate on `PersonalMarkAttribution`. Confirm this is by
   design.
8. **Reassignment does not appear to re-set/clear Lead status explicitly** on the new team — if the
   reassigned-out person held Lead, the plan may be left with no active Lead until someone
   explicitly sets a new one.
9. **Shoot Description has a dual-authority edit rule** (PERM_04 *or* PERM_03) while **Edit
   Description only allows PERM_06** — no PERM_07-equivalent carve-out for Edit Reviewers. Possibly
   intentional (Shoot Instructions may need correction during Planning Review, before Shoot even
   starts, in a way Edit doesn't need), but the asymmetry itself is confirmed in code and not
   commented as deliberate at the Edit side.
10. **Reschedule does not re-check the Standard/Urgent 5-day floor** when moving the live date
    closer than 5 days out — only the initial `setStandardSchedule` call enforces that floor.
11. **`MasterCatalogueService`'s duplicate-`PublicationTarget` check is active-pairing-only, while
    the underlying DB unique constraint on `(platform_id, channel_id)` is unconditional (not
    partial)** — re-creating a target on a previously-deactivated Platform×Channel pairing may be
    blocked at the DB layer even though the service-layer check alone would allow it. Not
    independently verified end-to-end in this audit.
12. **A stale code comment** at `AdminActionService.java:221` says Publishing Reopen goes
    "COMP→RFP"; the actual code and its own test both confirm the real target is `COMP→PUBG`.
    Comment-only inconsistency, not a functional bug.
13. Whether `creative_performance_scorecards`' "sealed" immutability has a DB-trigger backing it
    (like the 3 named correction-ledger tables) or is enforced only at the application layer
    (`requireNotSealed()`) was not independently confirmed in this audit.
14. The exact source of the Model(s) picker's *suggestion* list (whether it's filtered to
    Model-Business-Role users or shows something broader) was not traced — what's actually
    **persisted** (`ContentPlanTalentEntry.talentName`) is confirmed to be plain free text either
    way, not a `User` reference.
15. Whether CEO/MM's native authority is ever required to *additionally* hold an active assignment
    for any execution act beyond what's documented above (i.e., are there any execution acts where
    CEO/MM native authority alone IS sufficient, bypassing the assignee check) was not exhaustively
    cross-checked across every single action in this audit — the confirmed pattern (§13.5) is that
    Start Shoot/Start Edit/Start Publishing all require active assignment even for CEO/MM, but this
    was verified for those three specifically, not asserted as a universal rule with 100% coverage
    of every possible action in the app.

---

## 18. Important entities & their UI-relevant relationships

| Entity | Table | Key relationships |
|---|---|---|
| `Idea` | `ideas` | 1:1 `WorkflowInstance`; `submittedBy` → `User` |
| `ContentPlan` | `content_plans` | 1:1 `Idea`; 1:1 `WorkflowInstance` (**same instance as the Idea**); holds all Planning-stage fields + the 3 per-stage Description columns |
| `WorkflowInstance` | `workflow_instances` | `currentStatusCode`; shared by an Idea and its resulting ContentPlan for the entire deliverable's life |
| `WorkflowTransitionHistory` | `workflow_transition_history` | append-only; every single status change, ever, with trigger command + reason + actor |
| `ReviewCycle` | `review_cycles` | one per submit/decide round per gate (`IDEA_REVIEW`/`PLANNING_REVIEW`/`SHOOT_REVIEW`/`EDIT_REVIEW`); decision immutable once made |
| `PlannedOutput` | `planned_outputs` | belongs to a `ContentPlan`; groups via `reelGroupId`; maps to N `PublicationTarget`s |
| `PlannedOutputPublicationTargetMapping` | (join table) | `PlannedOutput` × `PublicationTarget` |
| `Platform` / `CompanyChannel` / `PublicationTarget` | masterdata tables | `PublicationTarget` = a specific Platform×Channel pairing |
| `ContentPlanTalentEntry` | — | free-text talent names per plan (not a User FK) |
| `ShootingAssignment` / `EditingAssignment` / `PublishingAssignment` | — | active/ended assignment rows per stage, `isLead` flag on the first two |
| `ShootingExecutionParticipant` / `EditingExecutionParticipant` | — | snapshot of active assignees taken at Submit-for-Review time; this, not the live assignment table, defines "who participated in this review cycle" |
| `PredefinedRoleMarks` | `predefined_role_marks` | 1:1 per `ContentPlan`, set at Idea Approval |
| `PersonalMarkAttribution` | `personal_mark_attributions` | per-person award, created at Shoot/Edit Approve time only |
| `PredefinedMarkCorrection` / `PublicationEvidenceCorrection` / `PerformanceMetricCorrection` | — | append-only, DB-trigger-guarded ledgers |
| `ActualPublicationEvent` | — | `ORIGINAL`/`REPOST`, per (output, target) pair |
| `PublicationTargetNaRecord` | — | `DESIGNATED`/`REVERSED`, chained |
| `PerformanceObligation` | — | 1:1 per publication event; immutable due date |
| `CreativePerformanceScorecard` | — | draft→sealed lifecycle |
| `StageComment` / (Description columns on `ContentPlan`) | `stage_comments` | one thread per (plan, stage ∈ {SHOOTING,EDITING,PUBLISHING}); mutable-own-comment-only since `V18` |
| `User` / `BusinessRole` / `AccessClass` | — | User→BusinessRole hard 1:1; BusinessRole→AccessClass N:1 |
| `PermissionGrant` / `PermissionGrantStageScope` / `PermissionGrantItemScope` | — | delegated permission model, scope type determines which child table (if any) is populated |
| `WorkHoldRecord` | `work_hold_records` | parallel to WorkflowStatus, doesn't change it |
| `ReassignmentRecord` / `ReassignmentAssignee` | — | one record per reassignment action, PREVIOUS/NEW rows for the swap |
| `RescheduleRecord` / `CancellationRecord` / `ReopenRecord` | — | permanent audit trail for each admin action type |
| `SystemAuditLog` | `system_audit_log` | generic append-only audit trail, used across every domain |

---

## 19. Existing tests that protect the workflow

22 test files, **94 test methods total**. `support/TestApiClient.java` is the shared HTTP test
client (real cookie-based JWT auth + CSRF token handling, JSON and HTML-form POST helpers) used by
every test below.

### KcpcMktApplicationTests.java (1 test)
- `contextLoads` — Spring context (security config, JPA mappings, Flyway migrations) must start cleanly.

### AuthenticationFlowTest.java (5 tests)
- `badCredentialsAreRejected` — wrong password → 401.
- `meWithoutCookieIsUnauthorized` — no JWT cookie → 401.
- `postWithoutCsrfTokenIsRejected` — missing CSRF header → 403 (double-submit CSRF).
- `validLoginThenLogoutRevokesTheToken` — logout revokes server-side; a replayed pre-logout JWT is rejected regardless of client-side cookie deletion.
- `unmappedAppUrlReturnsA404NotALoginRedirectWhileAuthenticated` — unmapped `/app/*` URL returns a real 404, session stays valid elsewhere.

### PermissionBoundaryTest.java (4 tests)
- `plainEmployeeCannotDecideIdeaReview` — no grant → 403 on Idea Review.
- `plainEmployeeCannotExecutePlanningActions` — no PERM_02 grant → 403 on schedule/parameters writes.
- `workflowGuardRejectsOutOfOrderShootingAndPublishingStarts` — starting Shooting before `SA` or Publishing before `RFP` → 409.
- `deactivatingAUserRevokesTheirSessionImmediately` — deactivation immediately invalidates the live session and blocks a fresh login.

### SelfReviewConflictTest.java (1 test)
- `delegatedEmployeeCannotApproveOwnIdea_butAnotherDelegatedReviewerCan` — self-approval blocked for a delegated reviewer, allowed for a different one.

### HighPriorityEdgeCaseTest.java (7 tests)
- `twoQualifyingCamerapersonsEachReceiveTheFullMarkNeverSplit` / `twoQualifyingEditorsEachReceiveTheFullMarkNeverSplit` — full mark to each qualifying person, never split.
- `qualifyingCamerapersonListNeverShowsDuplicatesAfterAReworkCycle` — dedup after a rework cycle.
- `reassignRejectsANewAssigneeWithoutTheMatchingBusinessRoleButAcceptsAMatchingOne` — server-side Business-Role check on Reassign.
- `expiredDelegatedPermissionGrantIsRejected` / `revokedDelegatedPermissionGrantIsRejected` — expired vs revoked grants both rejected, with distinct error codes.
- `stageRestrictedGrantOutsideItsScopeIsRejected` — a STAGE_RESTRICTED grant doesn't authorize outside its scoped stage.

### DbIntegrityEnforcementTest.java (3 tests)
- `workHoldRecordHardDeleteIsRejectedAtTheDatabaseLevel` — DB trigger rejects raw hard DELETE.
- `stageCommentHardDeleteIsStillRejectedAtTheDatabaseLevelAfterEng050` — hard delete still blocked even after comments became editable.
- `truncateIsRejectedOnAppendOnlyTables` — TRUNCATE rejected on append-only tables.

### CorrectionLedgerFlowTest.java (2 tests)
- `predefinedMarkCorrectionAppendsLedgerAndUpdatesActiveValues` — ledger chains, active value updates, rejects out-of-range mark/blank reason.
- `evidenceAndMetricCorrectionsPreserveOriginalImmutableRecords` — both correction types leave the original record untouched; raw JDBC UPDATE/DELETE is DB-rejected.

### GoldenEndToEndFlowTest.java (1 test)
- `ideaToCompletedGoldenPath` — full Idea→Planning→Shoot→Edit→Publishing→Performance→Completed happy path in one continuous run, including the hook-rate computation and N/A-stays-null behavior.

### MvcScreenSmokeTest.java (1 test)
- `mvcScreensRenderWithoutErrorAcrossTheGoldenPath` — the same golden path driven through real HTML form POSTs against every JSP screen at every status, asserting no server error at any point.

### PlanningSingleFormTest.java (7 tests)
- Save-only vs combined-submit endpoints; full-transaction rollback on a failed combined submit; no standalone "Save" button exists in the JSP; native-authority self-review exemption; exact-reconciliation (not additive) of the Cameraperson set; Shoot Instructions save together with the team.

### AssignmentPickerTest.java (11 tests)
- Idempotent add/remove for Shoot/Edit assignment; the SAP→EA auto-transition doesn't hide the picker/Lead dropdown afterward; Publisher assignment is RFP-only, CEO/MM-native-only, and separate from PERM_08 execution authority; Lead-must-be-active-assignee and auto-clear-on-removal for both Shoot and Edit; combined team+lead single-request endpoints, including rollback-on-invalid-lead.

### StageDiscussionTest.java (7 tests)
- PERM_04-gated Shoot Description; the PERM_03 dual-authority carve-out with audit trail; comment posting restricted to active-assignee-or-native; own-comment-only edit/delete (CEO not exempt); soft-delete; Shoot/Edit thread isolation; Publishing Description native-only vs assigned-Publisher-can-comment.

### WorkflowVariantsE2ETest.java (9 tests)
- Idea Reject (reason mandatory, terminal); Idea Retain→Reopen round-trip; Planning Review rework loop; the exact 5-day Standard/Urgent scheduling boundary; Hold blocking Submit (not Start); scorecard entry blocked before the due date; multiple publication events/Target N/A/reversal/Repost; the bulk publishing checklist (duplicate-guard, auto-stamped timestamp, auto-completion); Reopen-Completed-for-Publishing lands on `PUBG`.

### PlannedOutputsTableTest.java (17 tests)
- Reel Group creation/edit/shrink/grow/remove semantics; shared Publication Target propagation across a group; AJAX-header vs no-header response shape (200 JSON vs 302 redirect) for every Planned-Output and Publication-Scope action, proving the progressive-enhancement contract server-side.

### MyWorkVisibilityTest.java (3 tests)
- Assignment-vs-visibility timing (not visible until `SA`); Active→Completed transition exclusivity; KPI-counts-match-table + Rework Feedback show/clear behavior.

### MyIdeasVisibilityTest.java (2 tests)
- Own-ideas-only + backend-enforced ownership on direct URL access (redirect, not leak, not 500); Idea Status stays "Approved" through Planning progression + Reopened is distinguished from Under Review + KPI counts.

### SubmitIdeaFormTest.java (3 tests)
- All 4 fields persist distinctly; invalid Reference Link rejected server-side with every value preserved; blank Title rejected server-side with other values preserved.

### CeoPipelineDashboardTest.java (2 tests)
- 18-column dashboard renders one row per Content ID even with multi-valued fields; Employees redirected away, MM sees the same view as CEO; minimal not-yet-planned Content IDs render safely with "N/A".

### AdminMvcScreenSmokeTest.java (1 test)
- CEO can render and write through Users/Business-Roles/Catalogue admin screens end-to-end.

### ReportingApiSecurityTest.java (2 tests)
- Audit log never leaks a password hash; all governed KPI/report endpoints render without error, exactly 30 KPIs.

### ReportsGroupBMvcScreenSmokeTest.java (1 test)
- Every reporting/export JSP renders without a JSP-EL/lazy-init error; KPI Console renders exactly 30 tiles.

### ExportApiTest.java (4 tests)
- Governed-table-union JSON export never includes identity/permission tables or password hashes; CSV requires exactly one table; XLSX returns a real workbook; export is management-only (403 for a plain Employee).

---

## 20. Rules for Future UI Redesign

These are the things that **must not change** while redesigning the UI — restyling, relayout,
rewording, and reorganizing screens is fine; changing any of the following is a backend/business-
rule change, not a UI change, and is out of scope for a pure redesign.

1. **Never call a domain service or DB table directly from a new view — always go through the
   existing `@Service` methods** (`IdeaService`, `PlanningService`, `ShootingService`,
   `EditingService`, `PublishingService`, `PerformanceService`, `AdminActionService`,
   `HoldService`, `StageCommentService`, `MasterCatalogueService`, `*AdminService`). Every guard
   documented in this file (status checks, permission checks, assignment checks, mandatory-reason
   checks, the mandatory-fields-before-Planning-Review-submission gate, the qualifying-recipient
   marks rule, etc.) lives inside these methods. A redesigned screen that posts to a new/ad-hoc
   endpoint bypassing them would silently lose these guarantees.
2. **Keep using `WorkflowTransitionService.transition(...)` as the only way status ever changes** —
   never write `currentStatusCode` directly. This is already structurally enforced (no setter
   exists on `WorkflowInstance` other than `transitionTo`), so as long as new UI code stays inside
   the existing service layer this is automatic.
3. **Never surface a raw `WorkflowStatus.statusName()` on an Idea-facing screen.** Idea Status must
   always be one of: Under Review, Approved, Retained, Rejected, Reopened — computed via the
   existing `IdeaMvcController.statusLabel`-equivalent logic (§3.5, §14.2), not the raw enum. This
   was a real, already-fixed bug (ENG-061) — a redesign must not reintroduce it.
4. **Preserve the "same route, branch server-side by `accessClass`" pattern** for `/app/my-work`,
   `/app/pipeline`, `/app/ideas` — do not split these into separate URLs per role without updating
   every place that currently assumes one canonical URL per concept (bookmarks, nav links, redirect
   targets after login).
5. **Preserve the `accessClass` model attribute** (`MvcNavigationAdvice`) as the mechanism every
   role-branched view relies on — a redesign can restyle the branches but should not invent a
   second, parallel way of exposing the same information.
6. **Preserve backend-side ownership/authorization checks independent of anything the UI hides.**
   Specifically: an Employee must never be able to reach another employee's Idea data by any means
   (§14.2); execution actions (Start Shoot/Edit/Publishing, Submit-for-Review) must stay gated on
   active assignment even for CEO/MM (§13.5); comment edit/delete must stay author-only even for
   CEO/MM (§12.2). None of these are UI conveniences — they are server-enforced and must stay that
   way regardless of how the screen looks.
7. **Preserve the progressive-enhancement contract**: every form must remain a genuine, working
   plain `<form method="post">` first, with JS as a layered enhancement (AJAX or client-only UX) —
   not a JS-only interactive component with no server-rendered fallback. This is a consistent
   pattern across all 10 existing JS files (§16) and multiple tests explicitly assert the no-JS
   (non-AJAX-header) path still works (`PlannedOutputsTableTest.
   noJsFallbackStillRedirectsWhenAjaxHeaderIsAbsent` and similar).
8. **Preserve the "same backend source for KPI counts and the table beneath them" pattern** on My
   Work and My Ideas (§14.1, §14.2) — counts must keep being derived from the exact same
   already-filtered/already-loaded row list the table renders, never a separate `COUNT` query, so a
   redesign cannot introduce a count/table mismatch.
9. **Preserve the append-only/immutable guarantees** on every correction ledger, `stage_comments`
   hard-delete, `workflow_transition_history`, and `system_audit_log` — these are enforced at the
   database trigger level (§10.3) and cannot be worked around from a new UI even if someone tried; a
   redesign should not attempt to add an "undo" or "hard delete" affordance for any of these, since
   the backend will reject it.
10. **Preserve the exact set of mandatory-reason actions** — Reject (Idea), Rework (every gate),
    Reassign, Reschedule, Cancel, Reopen-Completed, every correction type, catalogue writes. A
    redesign must keep requiring and submitting a reason field for all of these; omitting the field
    from a new form would produce a 400 from the existing, unchanged backend validation.
11. **Preserve the 5 idea statuses / 4 review gates / 22 workflow statuses / 17 permissions / 7
    lifecycle stages / 3 access classes as fixed, closed sets** — do not invent new status values,
    new gate types, or new permission numbers in the UI layer; anything not in §2/§4/§13 does not
    exist in the backend and a screen referencing it would have no data behind it.
12. **Do not assume any of the items in §17 are bugs to silently "fix" via the UI** (e.g. don't
    build a UI affordance implying `PERM_13_FOLDER_LINK_MANAGE` is checked, since it currently
    isn't) — if any of those should change, that's a backend decision to make explicitly and
    separately from a redesign, not something a new screen should paper over or rely on being
    different from documented-here reality.
