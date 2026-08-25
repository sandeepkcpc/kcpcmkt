# KCPC Permission-Driven Workflow — Implementation Understanding

**Status:** Pre-coding analysis — required response per Section 52 of `KCPC_PERMISSION_DRIVEN_WORKFLOW_IMPLEMENTATION_SPEC.md`
**Based on:** Direct reading of the current codebase (not solely the prior audit) — every claim below is sourced from a file/line read during this pass.
**No code has been changed.** No git commands were run.

---

## Phase 1 Preflight — Findings

1. **DB constraint status for PERM_18/PERM_19:** `operational_permissions.permission_number` has `CONSTRAINT ck_operational_permissions_number CHECK (permission_number BETWEEN 1 AND 17)` (`V1__reference_data.sql`). `permission_grants.permission_number` is `NOT NULL REFERENCES operational_permissions (permission_number)` (`V3__permission_grants.sql`). **A Flyway migration is required** — PERM_18/19 cannot be granted at all until catalogue rows exist and the CHECK is widened.
2. **`TaskStage` enum:** contains **only** `{SHOOTING, EDITING}` (`workflow/domain/TaskStage.java`). There is no reachable `PUBLISHING` value, so the "latent Publishing-reassignment misrouting" concern flagged in the prior audit does **not** exist. Confirms spec §33/§34: nothing to change here.
3. **`WorkflowParticipationInterceptor`:** registered only on `/app/**` (`WebMvcConfig.java`), calling `AuthorizationService.isNonProductionEmployee(user)` — a single all-or-nothing boolean derived only from `BusinessRole.participatesInWorkflow`.
4. **REST routes outside `/app/**`:** need no interceptor-equivalent — every write path already goes through `AuthorizationService.requireAuthority` per action at the service layer (confirmed in `PlanningService`, `ShootingService`, `EditingService`, `PublishingService`, `AdminActionService`), so they are already safe regardless of interceptor changes.
5. **Content Pipeline:** `LandingMvcController.pipeline()` has its own explicit `if (user.resolvedAccessClass() == AccessClass.EMPLOYEE) return "redirect:/app/home"`, fully independent of the interceptor and not listed anywhere in spec §16 as permission-unlockable. Loosening the interceptor cannot leak Pipeline access.
6. **Reviews/Team/Reports backend:** already permission-gated, **not** Access-Class-gated. `ReviewsMvcController` calls `authorizationService.requireAuthority(user, PERM_01/03/05/07, ...)`; `ReportingMvcController` calls `allowed(principal, PERM_14/15/16)`. Neither restricts by `AccessClass`. This means unblocking EMPLOYEE access to these screens is almost entirely an interceptor/nav change, not a controller rewrite.
7. **PERM_13 remains out of scope** — confirmed unused/unenforced in the prior audit; no new evidence found to change that.

---

## A. Implementation Map

| Requirement | Current Location | Planned Change |
|---|---|---|
| PERM_18/19 catalogue | `OperationalPermission.java` (17-value enum); `operational_permissions` table CHECK `1..17` (V1); `permission_grants.permission_number` FK to it (V3) | New Flyway `V24__add_shoot_edit_execution_permissions.sql`: widen CHECK to `1..19`, INSERT the two rows. Add 2 enum constants. |
| Explicit-grant-only resolver | `AuthorizationService.requireAuthority` always short-circuits via `hasNativeAuthority` first (line 71-73); grant/scope logic (`scopeCovers`) is private | Add a grant-only evaluation path (e.g. `hasExplicitPermissionGrant(...)`) reusing the existing scope logic, never calling `hasNativeAuthority` |
| Central eligibility service | Does not exist today | New `OperationalEligibilityService` in `identity.service`, built on the resolver above (spec §7) |
| Shoot candidate list | `DeliverableMvcController:275-276` — `findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Camera Person")` | Bulk query for active users holding a live PERM_18 grant covering PLANNING/SHOOTING (no N+1 — spec §39) |
| Edit candidate list | `DeliverableMvcController:277-278` — `"Video Editor"` query | Same pattern, PERM_19 |
| Publisher candidate list | `DeliverableMvcController:279-280` — `"Publisher"` query | Same pattern, PERM_08 (already exists — reference case) |
| Shoot assignment backend | `PlanningService.assignCameraperson` (line 412) checks only the assigner's PERM_04; `cameraperson` argument never validated | Add `requireShootExecutionEligible(cameraperson, workflowInstance)` + active-user check before creating `ShootingAssignment` |
| Edit assignment backend | `EditingService.assignEditor` (line 86), same gap | Same pattern, `requireEditExecutionEligible` |
| Publisher assignment backend | `PublishingService.assignPublisher` (line 148) checks only `requireNativeAuthority` on the actor; assignee unchecked | Add `requirePublishingExecutionEligible(publisher, ...)` on the assignee; actor-side native-authority-only gate **unchanged** (spec §9) |
| Reassignment | `AdminActionService.requireBusinessRole(newUser, "Camera Person"/"Video Editor")` (lines 176, 192) | Replace both calls with `requireShootExecutionEligible`/`requireEditExecutionEligible`; delete `requireBusinessRole` (confirmed unused elsewhere) |
| Shoot execution | `ShootingService.requireActiveAssignee` (private, line 87), used in `startShooting`/`submitShootReview` | Add `requireShootExecutionEligible(actor, workflowInstance)` alongside the existing assignee check (lines ~104, ~120) |
| Edit execution | `EditingService.requireActiveAssignee` (line 214) | Same, in `startEditing`/`submitEditReview` (lines ~231, ~245) |
| Publishing execution | Already `PERM_08` + `requireActiveAssignee` (lines 132-133, 268-269) | **Unchanged** — new service wraps the same existing check |
| Workflow participation gate | `AuthorizationService.isNonProductionEmployee` (role-flag only) + `WorkflowParticipationInterceptor` (blanket `/app/**`) | See section D |
| Nav | `MvcNavigationAdvice` + `nav.jsp` hard `accessClass == 'EMPLOYEE'` branch | Add EMPLOYEE-branch links for Reviews/Team/Reports/Administration→Catalogue, gated by new permission-checked `@ModelAttribute`s mirroring the existing `canSeeAdministration` pattern |
| Reviews/Team/Reports backend | Already permission-gated (see preflight #6) | No controller change expected — unblocking is the interceptor/nav change |
| My Work multi-stage tabs | `LandingMvcController.myWork()` already builds `shootActiveWork`/`editActiveWork`/`publishActiveWork` (+Completed) correctly from real assignment rows (lines 327-369). Only `my-work.jsp`'s `businessRoleName` `<c:choose>` throws this away | JSP-only tab redesign ("All \| Shoot \| Edit \| Publishing"); add `hasShootExecutionPermission`/`hasEditExecutionPermission`/`hasPublishingExecutionPermission` booleans so a permission-holder with zero assignments still sees an eligible (empty) tab |
| Team Workload | `TeamWorkloadService.teamWorkloadDashboard` (lines 144-168) enumerates by hardcoded role name before consulting `shootByUser`/`editByUser`/`publishByUser` (lines 127-132), which are already correctly assignment-keyed | Iterate the already-built maps directly; `businessRole` becomes a post-hoc filter/display field only |
| KPI/report Business-Role audit | Only Team Workload was flagged as truly coupled in the prior audit; KPI-009/022/023 use Access Class, not Business Role | Phase 5 targeted review; no other change currently known to be required |
| Admin Permissions UI | `admin-permissions.jsp` | Add PERM_18/19 rows + consequence copy (spec §26/27) |
| Test fixture backfill | 21 test files, each with its own locally-duplicated `createUser` helper (no shared base class) | Per-file addition: grant PERM_18/19 to fixture users later used as Shoot/Edit assignees — file list enumerated in Phase 1 execution |

---

## B. New Permission Persistence Impact

**Yes, a Flyway migration is required.** `operational_permissions.permission_number` is CHECK-restricted to `1..17`, and `permission_grants` has a hard FK to that table — PERM_18/19 literally cannot be granted until catalogue rows exist. Plan: new `V24__add_shoot_edit_execution_permissions.sql` widening the CHECK to `1..19` and inserting the two new rows in the same format as V1's seed data. No existing migration is touched.

---

## C. Existing-User Backfill Plan

Two separate populations:

- **Real users:** grant PERM_18 (GLOBAL scope) to every currently-active user whose Business Role is Camera Person, and PERM_19 to every one whose Business Role is Video Editor. This is data, not schema — mechanism is an open question (see H-1).
- **Test fixtures:** 21 test files construct users via their own local `createUser(ceo, name, email, ROLE_ID)` helper (confirmed no shared base class). Any fixture later used as a Shoot/Edit assignee/executor needs one added line granting PERM_18/19 right after creation, or it will start failing once the backend eligibility check goes live.

---

## D. Route/Interceptor Change Plan

1. Add a new check (e.g. `hasEffectiveOperationalAccess(User)`): true if the role participates by default, **or** the user holds any live PERM_18/19/08/05/07/03/01/14/15/16/17 grant, **or** holds an active Shoot/Edit/Publishing assignment.
2. Interceptor: false → unchanged (redirect to `/app/ideas`). True → request proceeds into the controller.
3. Content Pipeline stays safe automatically — it has its own independent Access-Class gate (preflight #5).
4. Reviews/Team/Reports are self-protecting once past the interceptor — they already do their own `requireAuthority`/`allowed(PERM_1x)` checks (preflight #6).
5. REST endpoints outside `/app/**` need no change — already protected at the service layer (preflight #4).

---

## E. Native-Authority Handling

**Confirmed: CEO/MM native authority will not automatically make them Shoot/Edit/Publisher execution candidates.** The new eligibility resolver never calls `hasNativeAuthority` — it only walks live `PermissionGrant` rows through the same scope-checking logic `requireAuthority` already uses, skipping the native-authority bypass branch. This extends the same boundary `requireActiveAssignee` already enforces for execution (confirmed: none of the three domain services' active-assignee checks consult native authority) to candidacy as well. A CEO/MM needing hands-on production work must be explicitly granted PERM_18/19/08 and hold a real assignment, exactly like anyone else.

---

## F. Multi-Function My Work Plan

The data side is already correct — `LandingMvcController.myWork()` computes all three stages' active/completed work from real assignment rows, unconditionally, regardless of Business Role. The only fix is in `my-work.jsp`: replace the `businessRoleName`-keyed `<c:choose>` with an "All | Shoot | Edit | Publishing" tab bar, each tab shown when its stage has active/completed items **or** the user holds the corresponding execution permission. Needs one small additive controller change (three new boolean model attributes) plus the JSP redesign — no change to the assignment-fetching logic.

---

## G. Team Workload Plan

Population becomes assignment-based. `TeamWorkloadService` already builds `shootByUser`/`editByUser`/`publishByUser` directly from `findByActiveTrue()` on the three assignment repositories — fully correct, keyed by real assignee ID. The only change is to stop enumerating candidates via the hardcoded-role-name `userRepository` calls and instead iterate those already-built maps directly. `businessRole` remains available as a filter/display field only, applied to the resulting rows using each user's real current Business Role. `planningRows()` and `modelRow()` are untouched (already role-agnostic).

---

## H. Risks / Questions

1. **Backfill mechanism for real users (needs a decision):** should the Camera-Person/Video-Editor → PERM_18/19 backfill be (a) an automatic Flyway data migration — which would have to pick a `grantor_user_id` programmatically (e.g. earliest active CEO_OWNER), baking a data decision into schema history — or (b) a one-time manual action triggered after deploy via a small idempotent bulk-grant admin endpoint? Leaning toward (b) since permission grants are operational data, not schema.
2. **Content Pipeline access:** resolved during preflight — not a risk (see preflight #5).
3. **PERM_13 / Publishing reassignment:** resolved during preflight — `TaskStage` has no `PUBLISHING` value, so the latent-misrouting concern does not exist (see preflight #2).
4. **Test-suite footprint:** 21 files with locally-duplicated `createUser` helpers; the exact subset needing a PERM_18/19 grant added can only be finalized by inspection/run in Phase 1 execution, not guessed here.

---

## Next Step

Awaiting the backfill-mechanism decision (H-1) and any other direction before starting Phase 1 execution / Phase 2 (permission foundation).
