# KCPC Permission + KPI Workflow — Current-State Architecture Audit

**Scope:** Read-only inspection of the current codebase (`com.kcpc.mkt`) as it exists on disk at audit time. No code, schema, tests, or configuration were modified in the course of this audit. No git commands were run. Findings not fully determinable from the code are explicitly marked **UNKNOWN / NOT DETERMINABLE**.

**Note on repo state:** At audit time, the working tree had uncommitted local modifications to `PerformanceService.java`, `ActualPublicationEvent.java`, `ActualPublicationEventRepository.java`, `PublishingService.java`, `DeliverableMvcController.java`, `ReopenRecord.java`, `ReopenRecordRepository.java`, `AdminActionService.java`, `app.css`, `deliverable-detail.jsp`, `publish-task-detail.jsp`, and `WorkflowVariantsE2ETest.java`. All of these were read **as-is** (working-tree state, not last-committed state), and this document reflects that. Where the distinction matters, it is called out inline.

---

## 1. Executive Summary

The stakeholder's two goals — (1) making operational eligibility (who can be assigned/execute Shoot, Edit, Publishing work) permission-driven rather than Business-Role-driven, and (2) making KPI/reporting attribution reflect actual per-stage contribution rather than Business Role — are **already substantially supported by the core architecture**, but are **blocked in a specific, narrow, and consistent way** by candidate-list queries, one report dashboard, and one JSP view.

**What already works today, with no changes needed:**
- `AccessClass` (3-value enum: `CEO_OWNER`, `MARKETING_MANAGER`, `EMPLOYEE`) and `BusinessRole` (open-ended, admin-created) are already cleanly separated. Camera Person, Editor, Publisher, Model, HR, etc. are all plain `BusinessRole` rows under the single `EMPLOYEE` Access Class — there is no separate Access Class per job function.
- `OperationalPermission` / `PermissionGrant` is already **100% individual-user-scoped** and Business-Role-agnostic. A permission can be granted to any user regardless of their Business Role, with `GLOBAL`/`STAGE_RESTRICTED`/`ITEM_SPECIFIC` scoping, time bounds, and live revocation.
- Shoot and Edit **execution** (Start/Submit) is already governed purely by active-assignment-row membership — not by Business Role, not by any permission, and (deliberately, by documented design) not bypassable even by CEO/MM native authority.
- Publishing execution already implements exactly the target formula: `PERM_08_PUBLISHING_EXECUTION` (live-re-checked on every call) **+** active `PublishingAssignment` **+** correct workflow state.
- Review-contributor selection (`ShootingExecutionParticipant`/`EditingExecutionParticipant`) and marks (`PersonalMarkAttribution`) are already assignment-based snapshots, not Business-Role-based — an employee's marks are already keyed by `(recipient_user_id, role_type)`, not by their current Business Role, and the same person can already receive both `CAMERAPERSON` and `EDITOR` marks.
- The database schema imposes essentially **one** hard multi-function constraint: `users.business_role_id` is a mandatory, single-valued FK (one Business Role per user). Every other relevant table (assignments, participants, marks, permission grants, publication events) is already keyed by plain `user_id` FKs with no Business-Role coupling at the schema level. **No migration is required** to support one user holding Shoot + Edit + Publishing assignments and marks concurrently.

**What is currently Business-Role-name coupled, and is the actual blocker:**
- Every assignee **candidate-list dropdown** (Shoot, Edit, Publisher, Model) is populated by a hardcoded `BusinessRole.roleName` string-equality query (`"Camera Person"`, `"Video Editor"`, `"Publisher"`, `"Model"`), duplicated independently across 5+ call sites with no shared constant.
- The **My Work** page picks one of three role-flavored dashboards (or a generic fallback) by hard string match on `businessRoleName` — an employee whose Business Role isn't one of the three canonical names, but who holds real active work in a stage, has that stage's tasks folded into a generic combined view instead of a dedicated flavor; conversely, an employee whose role *is* one of the three names only sees that one stage even if they hold assignments in another.
- `TeamWorkloadService` (the actual Team → Workload dashboard) **enumerates candidate employees by hardcoded Business Role name before ever looking at their real assignment rows** — a genuinely multi-function employee whose Business Role doesn't match a stage's canonical name is invisible on this dashboard for that stage, even though the same employee is correctly counted by other, assignment-only KPIs (KPI-006, KPI-008, legacy `teamWorkload()`).
- `AdminActionService.reassign` is the **one** place in the app that re-validates Business Role server-side (added specifically because, per its own code comment, ENG-054, nothing else did) — everywhere else (initial Shoot assignment, initial Edit assignment, initial Publisher assignment), **the backend accepts any active user as an assignee with no Business-Role or permission check on the assignee at all**, which is simultaneously (a) proof that permission/assignment-driven eligibility is not structurally blocked today, and (b) a genuine, currently-unintentional security/data-integrity gap (Section S, findings S-2/S-3/S-4).

**Net assessment:** the target model (`Access Class → workspace`, `Business Role → org label`, `Permission → operational authority`, `Assignment → per-Content-ID responsibility`, `Workflow State → gating`, `Contribution → stage-specific attribution`) is **already implemented at the schema and core-service layer**. The work required is concentrated in a well-defined, relatively small set of query/view-layer locations: candidate-list queries, the My Work JSP routing, and the Team Workload dashboard's candidate enumeration — not a redesign of the authorization or data model.

---

## 2. Current Architecture Map

```
User ──(N:1, mandatory, NOT NULL FK)──> BusinessRole ──(column)──> AccessClass (CEO_OWNER | MARKETING_MANAGER | EMPLOYEE)
User ──(1:N)──> PermissionGrant ──(N:1)──> OperationalPermission (17-value fixed enum, PERM_01..PERM_17)
                       │
                       └─(scope)─> PermissionScopeType: GLOBAL | STAGE_RESTRICTED (→ PermissionGrantStageScope) | ITEM_SPECIFIC (→ PermissionGrantItemScope)

ContentPlan (Planning) ──1:N──> ShootingAssignment ──(FK)──> User (cameraperson)
                        ──1:N──> EditingAssignment  ──(FK)──> User (editor)
                        ──1:N──> PublishingAssignment ──(FK)──> User (publisher)      [additive tracking only — does not gate execution]
                        ──1:1──> ContentPlanTalentEntry ──(optional FK)──> User        [Model — talent lookup, NOT an assignment]

ShootingAssignment (active, at submit-review time) ──snapshot──> ShootingExecutionParticipant ──> PersonalMarkAttribution (role_type=CAMERAPERSON)
EditingAssignment  (active, at submit-review time) ──snapshot──> EditingExecutionParticipant  ──> PersonalMarkAttribution (role_type=EDITOR)

WorkflowInstance ──(status: PL/SA/SIP/SAP/EA/ED/EAP/RFP/PUBG/PP/COMP/CAN/RJ/...)──> gates every service-layer action
ReviewCycle (gateType, cycleNumber, decision, submittedBy, reviewer) ──> Planning/Shoot/Edit/Idea review decisions

ActualPublicationEvent (eventType: ORIGINAL|REPOST, publishedBy FK to User, recordedAt immutable) ──belongs to──> PlannedOutput × PublicationTarget
ReopenRecord (reopenPurpose: RETAINED_REOPEN|PUBLISHING_REOPEN|METRIC_CORRECTION_REOPEN) ──> defines "current publishing cycle start" for repost-scoping

AuthorizationService.requireAuthority(User, OperationalPermission, LifecycleStage, WorkflowInstance)   ← central permission check (native-authority bypass OR live PermissionGrant lookup)
AuthorizationService.isNonProductionEmployee(User)                                                      ← separate, non-communicating nav/route gate keyed on BusinessRole.participatesInWorkflow
WorkflowParticipationInterceptor                                                                          ← enforces the above on every /app/** route server-side
```

**Two independent authorization axes exist today, and this is the single most important structural fact for the redesign:**
1. **Permission axis** (`AuthorizationService.requireAuthority` + `PermissionGrant`) — fully individual-user-scoped, Business-Role-agnostic, live-re-checked on every call.
2. **Navigation/participation axis** (`isNonProductionEmployee` / `WorkflowParticipationInterceptor`, driven by `BusinessRole.participatesInWorkflow`) — an all-or-nothing, Business-Role-level switch for reaching `/app/**` at all, independent of what permissions a user holds.

A permission grant to a user whose Business Role has `participates_in_workflow = false` is **currently unreachable through the UI** — not because the permission system rejects it, but because the navigation gate blocks the route before any permission check runs (Section B3).

---

## Section A — Access Class and Workspace Architecture

### A1. Access Classes

| Access Class | Defined In | Current Purpose | Main Navigation / Workspace |
|---|---|---|---|
| `CEO_OWNER` | `identity/domain/AccessClass.java` (enum, 3 values) | Full native authority; the only class that can administer Users/Business Roles/Permissions | Content Pipeline, Reviews, Team, Reports, My Ideas, Submit Idea, Administration |
| `MARKETING_MANAGER` | same enum | Native management authority equal to CEO for workflow actions (Assign/Review/Reassign/etc.), but cannot administer Users/Business Roles/Permissions | Same as CEO_OWNER minus full Administration (only Catalogue if separately granted PERM_17) |
| `EMPLOYEE` | same enum | Rank-and-file; gets only delegated `PermissionGrant`s, never native authority | My Work / My Shoots, My Ideas, Submit Idea |

`AccessClass` is a fixed 3-value Java enum, doc-commented as "the exactly-3 internal access classes... the actual authorization boundary." It is **not** a separate table/entity — it is a column on `BusinessRole` (`business_roles.access_class_code`); `User` never stores it directly (`User.resolvedAccessClass()` returns `businessRole.getAccessClassCode()`).

Confirmed: **no** separate Access Class exists for Camera Person, Editor, Publisher, Model, or HR. All are `BusinessRole` rows whose `accessClassCode` is `EMPLOYEE` (new roles default to `EMPLOYEE` unless explicitly designated otherwise). `BusinessRole.roleName` is free-text, admin-created, unique — **not** a fixed enum/catalogue.

**Business-rule implication:** Access Class is a coarse 3-bucket authorization tier; Business Role is an open-ended organizational label. This is already exactly the target model's 2-layer separation (Access Class = broad workspace, Business Role = organizational designation).

**Potential risk/inconsistency:** None at this layer — the separation is clean and consistently enforced via the single derivation point `User.resolvedAccessClass()`.

### A2. Top-level navigation decision logic

Nav is rendered entirely by `fragments/nav.jsp`, driven by request attributes from `MvcNavigationAdvice` (`@ControllerAdvice`): `accessClass`, `businessRoleName` (raw string), `nonProductionEmployee` (delegates to `isNonProductionEmployee`), `canSeeAdministration` (native authority OR valid `PERM_17` grant).

| Nav Item | Visible when | Dependency |
|---|---|---|
| Content Pipeline, Reviews, Team, Reports | `accessClass != 'EMPLOYEE'` | **Access Class only** — hard equality/branch |
| My Work / My Shoots | `accessClass == 'EMPLOYEE' && !nonProductionEmployee` | Access Class **+** `participates_in_workflow` |
| My Work vs. My Shoots label | Same visibility test; branches on `businessRoleName == 'Model'` | **Hard-coded Business Role name** |
| My Ideas / Submit Idea | Always shown | none (universal) |
| Administration | `canSeeAdministration` (only inside the non-EMPLOYEE nav branch) | Access Class (native) **OR** Permission (PERM_17), but structurally CEO/MM-branch-only |

**Current implemented behavior:** Visibility for Content Pipeline/Reviews/Team/Reports is a hard `accessClass == 'EMPLOYEE'` branch — an EMPLOYEE never sees these regardless of any `PermissionGrant` held (e.g. `PERM_14_TEAM_WORKLOAD_VIEW`).

**Potential risk/inconsistency:** `MvcNavigationAdvice#canSeeAdministration`'s own javadoc claims a non-CEO/MM Employee holding `PERM_17` sees Administration — but `nav.jsp` only evaluates `canSeeAdministration` inside the `accessClass != 'EMPLOYEE'` branch. An EMPLOYEE holding PERM_17 has **backend route access** to `/app/admin/catalogue` (confirmed in `AdminMvcController.catalogue()`) but **no nav link** to reach it — a documented-vs-actual mismatch. Low/Medium severity (under-exposes, doesn't over-expose).

### A3. Meaning of `EMPLOYEE`

**Current implemented behavior:** `EMPLOYEE` is purely a broad Access-Class tier; it carries **no** assumption anywhere that a user can only have one fixed operational function. `AuthorizationService` never inspects `businessRoleName` at all — it works purely off `AccessClass` + `PermissionGrant`, both per-user and unrestricted in count/type.

Where a fixed-function assumption **does** leak in is the UI/query layer (candidate-list queries, My Work routing, reporting), not the Access-Class/authorization layer — see B4.

**Business-rule implication:** The Access Class/Permission layer is already multi-function-safe; Business-Role-name-keyed UI/query logic is not.

---

## Section B — User, Business Role and Workflow Participation Model

### B1. User ↔ Business Role relationship

- **Cardinality:** exactly one Business Role per User — `User.businessRole` is `@ManyToOne(optional = false)` on `business_role_id`. No join table; no way to hold two Business Roles simultaneously.
- **Storage:** direct FK on `User`.
- **Mutability:** yes — `User.reassignBusinessRole(BusinessRole)` repoints the FK; exposed via `UserAdminService.changeBusinessRole` (CEO-only, mandatory reason, audited).
- **Historical assignments on role change:** not retroactive. `ShootingAssignment`/`EditingAssignment`/`PublishingAssignment` store a direct FK to the assigned `User`, not to the Business Role — historical assignment rows are unaffected by a later role change (whether the *user* can still act on that historical assignment is answered by H2).

**Business-rule implication:** the schema is a strict one-role-per-user model; "multi-function" cannot be expressed by giving one user two simultaneous Business Roles — it must be expressed via permission grants and/or multi-stage assignments instead.

### B2. Every usage of `business_roles.participates_in_workflow`

Field: `BusinessRole.participatesInWorkflow` (default `true`). Single point of consumption: **`AuthorizationService.isNonProductionEmployee(User)`**:
```java
public boolean isNonProductionEmployee(User user) {
    if (user.resolvedAccessClass() != AccessClass.EMPLOYEE) return false;
    var role = user.getBusinessRole();
    return role == null || !role.isParticipatesInWorkflow();
}
```
Every other consumer calls this method, never the flag directly — `MvcNavigationAdvice.nonProductionEmployee()` (nav visibility) and `WorkflowParticipationInterceptor.preHandle` (server-side deny-by-default on every `/app/**` route, redirect to `/app/ideas`).

**What it controls:** nav visibility + landing/route access only. It does **not** control assignment eligibility, review eligibility, `OperationalPermission` grants/authorization, or reporting/KPI — `requireAuthority` and all domain services never reference it.

### B3. Can `participates_in_workflow = false` still receive workflow permissions?

**Yes.** Nothing in `PermissionGrantAdminService.grant(...)` checks `participatesInWorkflow`, the grantee's Business Role, or workflow participation — the only gate is `requireCeo(actor)`.

Traced (HR Manager, `participatesInWorkflow=false`, granted `PERM_04_SHOOT_ASSIGNMENT`):
1. Grant succeeds — no validation blocks it.
2. `AuthorizationService.requireAuthority` now succeeds for that HR user (100% permission-row-based, never touches `participatesInWorkflow`/role name).
3. **However**, `isNonProductionEmployee(hrUser)` still returns `true`, so `WorkflowParticipationInterceptor` redirects this user away from every `/app/**` route except `/app/ideas*`, on every GET/POST — the nav also never shows a path there.
4. **Net effect: the permission grant becomes practically unusable through the UI.** The nav/route gate and the action gate are two independent, non-communicating systems.

**Potential risk/inconsistency:** This is a UI/authorization split worth flagging explicitly: if the target design is "any permission holder should be able to act regardless of Business Role," `WorkflowParticipationInterceptor`/the nav gate is the actual blocker — not the permission model, which already supports this.

### B4. Hard-coded "production role" concept

The `AuthorizationService`/`PermissionGrant` core is 100% Business-Role-name-agnostic. Business-Role-**name** string literals gate real behavior elsewhere:

| Location | Role name(s) checked | What it gates |
|---|---|---|
| `fragments/nav.jsp` | `"Model"` | My Shoots vs My Work nav label |
| `LandingMvcController` (multiple lines) | `"Model"`, `"Publisher"`, `"Camera Person"`, `"Video Editor"` | Home routing, My Work item labeling/routing, action-label text |
| `DeliverableMvcController` (candidate lists) | `"Model"`, `"Camera Person"`, `"Video Editor"`, `"Publisher"` | **Assignment dropdown candidate lists** (`findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc`) |
| `DeliverableMvcController` (view variant) | `"Camera Person"`, `"Video Editor"`, `"Publisher"` | Whether a redesigned single-role task-detail view renders |
| `TeamWorkloadService` | `"Camera Person"`, `"Video Editor"`, `"Publisher"`, `"Model"` | **Team Workload report row population** — enumerates candidates by role name before assignment lookup |
| `AdminActionService.requireBusinessRole` | `"Camera Person"`, `"Video Editor"` | **Reassignment candidate validation — backend-enforced**, the one server-side Business-Role check in the app |
| `marks/domain/RoleType` (enum, not a string) | `CAMERAPERSON`, `EDITOR` | Mark/participant role tagging — independent of `BusinessRole` |

**Business-rule implication:** Permission is already the source of truth for whether an *authorized* action succeeds; Business-Role name is the (separate, currently hard-coded) source of truth for *who is offered as a candidate* and *how reports group people* — changing "who is eligible to be assigned" requires changes in this query/candidate-list/reporting layer, not in `AuthorizationService`.

**Potential risk/inconsistency:** `AdminActionService.requireBusinessRole` is **backend-enforced**, not just UI — so even a direct POST for a reassignment is rejected on Business-Role mismatch, while the corresponding *initial*-assignment paths have no equivalent check (Section D/S).

---

## Section C — Permission Model

### C1. Complete permission inventory

All 17 are defined once in `identity/domain/OperationalPermission.java` as a fixed, frozen enum (governed reference data, not admin-creatable). There is no separate stored human-readable "Description" field per permission in the codebase — descriptions below are inferred from naming/call sites; a longer business description may exist in an external spec document not in this repo (**UNKNOWN / NOT DETERMINABLE** for that).

| Code | Name | Currently Used By | Backend Enforcement | UI Visibility |
|---|---|---|---|---|
| PERM_01 | IDEA_REVIEW | `IdeaService` | `IdeaService` (approve/reject/edit idea) | Reviews→Ideas tab |
| PERM_02 | PLANNING_EXECUTION | `PlanningService` | create/submit plan | `canPlanningExecute` in deliverable-detail.jsp |
| PERM_03 | PLANNING_REVIEW | `PlanningService` | approve/reject plan; co-gates Shoot Instructions edit | `canDecidePlanningReview` |
| PERM_04 | SHOOT_ASSIGNMENT | `PlanningService` | assign/reassign Cameraperson (Planning-time), edit Shoot Description | `canAssignCameraperson` |
| PERM_05 | SHOOT_REVIEW | `ShootingService` | approve/reject shoot | `canDecideShootReview` |
| PERM_06 | EDIT_ASSIGNMENT | `EditingService` | assign/reassign Editor, edit Edit Description | `canAssignEditor` |
| PERM_07 | EDIT_REVIEW | `EditingService` | approve/reject edit | `canDecideEditReview` |
| PERM_08 | PUBLISHING_EXECUTION | `PublishingService`, `AdminActionService` | Start/record/correct publishing events | `canPublishingExecute` |
| PERM_09 | PERFORMANCE_UPDATE | `PerformanceService`, `AdminActionService` | scorecard submit/correct | `canPerformanceUpdate` |
| PERM_10 | RESCHEDULE | `AdminActionService` | reschedule | `canReschedule` |
| PERM_11 | REASSIGN | `AdminActionService` | reassign Shoot/Edit | `canReassign` |
| PERM_12 | CANCEL | `AdminActionService` | cancel content | `canCancel` |
| PERM_13 | FOLDER_LINK_MANAGE | **None found** | **UNKNOWN — appears unenforced/unused** | none found |
| PERM_14 | TEAM_WORKLOAD_VIEW | `TeamWorkloadService`, `KpiService` | — | `canViewTeamWorkload` |
| PERM_15 | TEAM_KPI_VIEW | `KpiService` | — | `canViewTeamKpi` |
| PERM_16 | AUDIT_HISTORY_VIEW | `AdminReportingService`, `AuditRestController` | — | Reports/Audit tabs |
| PERM_17 | PLATFORM_CATALOGUE_MANAGE | `AdminMvcController`, `MasterCatalogueService` | catalogue CRUD | Administration nav (see A2 mismatch) |

**Potential risk/inconsistency:** PERM_13 (`FOLDER_LINK_MANAGE`) is grantable (the grant service accepts any enum value with no allow-list) but has **zero enforcement or UI consumption found**. Flag for stakeholder confirmation — dead/planned-but-unbuilt, or folder-link editing is gated elsewhere not located in this audit.

### C2. How permissions are assigned

- **Individual users only.** `PermissionGrant.grantee` is a direct `@ManyToOne` to `User`. There is **no** `BusinessRole`→`Permission` or `AccessClass`→`Permission` mapping table anywhere.
- Confirmed not assignable to Business Roles: `BusinessRole`'s own javadoc states it "never itself grants an Operational Permission."
- Confirmed not assignable to Access Classes: CEO_OWNER/MARKETING_MANAGER bypass the grant system entirely via native authority.
- **Scope on the grant:** `PermissionScopeType{GLOBAL, STAGE_RESTRICTED, ITEM_SPECIFIC}` — `STAGE_RESTRICTED` attaches `LifecycleStage` scope rows, `ITEM_SPECIFIC` attaches specific `WorkflowInstance` scope rows.
- Grants are temporally bounded (`effectiveFrom`/`effectiveUntil`) and soft-revocable (`revoke()` sets `revokedAt`, row never deleted).

### C3. Exact "user has permission" calculation

Central method: **`AuthorizationService.requireAuthority(User, OperationalPermission, LifecycleStage, WorkflowInstance)`**, called from every domain write-path service, plus a read-only `allowed(...)` wrapper for view-model flags.

Resolution order:
1. **`hasNativeAuthority(user)`** — `resolvedAccessClass() == CEO_OWNER || MARKETING_MANAGER` → returns immediately with no grant consulted (full bypass of the grant system for management/authorization-gated actions — see C4/F4 for the hands-on-execution exception).
2. Otherwise, fetch active grants for `(user, permission)`, check `isCurrentlyValid(now)` (active, not revoked, within effective window) **and** `scopeCovers(grant, stage, itemContext)`.
3. Denial reasons distinguished: `PERM_OPERATIONAL_PERMISSION_EXPIRED`, `PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE`, `PERM_OPERATIONAL_PERMISSION_REQUIRED`.

No "inherited permission" concept exists (no role→permission or access-class→permission chain) and no explicit deny/override mechanism beyond revocation.

Cross-cutting: `requireNoSelfReviewConflict` blocks a *delegated* reviewer from deciding on their own submitted/prepared/executed work; native CEO/MM authority is exempt from this barrier.

**Business-rule implication:** "user has permission" = native authority (Access-Class-derived) **OR** a currently-valid, scope-covering, individually-granted `PermissionGrant`. Business Role name plays zero role in this calculation.

### C4. Which permission represents "execution eligibility"?

**Finding: no permission has that exact semantic meaning for Shoot or Edit; PERM_08 has it for Publishing, with a documented carve-out.**

- **Shoot:** `PERM_04_SHOOT_ASSIGNMENT` governs who can *assign* a Cameraperson, not who is eligible to be assigned or to execute. Start-Shoot/Submit-for-Review have **no `OperationalPermission` gate at all** — enforcement is `requireActiveAssignee(actor, plan)`: pure membership against active `ShootingAssignment` rows, and this is explicitly **not bypassable even by CEO/MM native authority** (documented design decision: hands-on execution of an employee's own task is deliberately outside native authority's reach).
- **Edit:** structurally identical pattern confirmed in `EditingService` (same "Assignment ≠ Execution permission" split, same `requireActiveAssignee`-style gate, no permission check, no CEO/MM bypass).
- **Publishing:** `PERM_08_PUBLISHING_EXECUTION` **is** used as an execution-eligibility permission (gates Start Publishing/Record Actual Publication), live-re-checked on every call. But PERM_08 alone (even with its native-authority bypass) is **not sufficient** — `requireActiveAssignee` (an active `PublishingAssignment` row) is required in addition, and this holds even for CEO/MM.

**Conclusion:** execution eligibility for Shoot and Edit is governed by **assignment-record membership, not by any permission**; Publishing execution is **permission (PERM_08) + assignment**. There is no single uniform "execution eligibility" permission concept across all three stages. Per instructions, no new permission is proposed here — this is a factual finding only.

### C5. Review-only vs execution-only permission matrix

| Permission | Shoot Execute | Shoot Review | Edit Execute | Edit Review | Publish Execute | Assignment/Reassignment | Other |
|---|---:|---:|---:|---:|---:|---:|---|
| PERM_01 IDEA_REVIEW | — | — | — | — | — | — | Idea review only |
| PERM_02 PLANNING_EXECUTION | — | — | — | — | — | — | Planning execution |
| PERM_03 PLANNING_REVIEW | — | — | — | — | — | — | Planning review; co-gates Shoot Instructions edit |
| PERM_04 SHOOT_ASSIGNMENT | — | — | — | — | — | ✅ (assign Cameraperson, Planning) | Shoot Description edit |
| PERM_05 SHOOT_REVIEW | — | ✅ | — | — | — | — | — |
| PERM_06 EDIT_ASSIGNMENT | — | — | — | — | — | ✅ (assign Editor) | Edit Description edit |
| PERM_07 EDIT_REVIEW | — | — | — | ✅ | — | — | — |
| PERM_08 PUBLISHING_EXECUTION | — | — | — | — | ✅ (+ assignment) | — | — |
| PERM_09 PERFORMANCE_UPDATE | — | — | — | — | — | — | Performance scorecard |
| PERM_10 RESCHEDULE | — | — | — | — | — | — | Reschedule |
| PERM_11 REASSIGN | — | — | — | — | — | ✅ (management reassignment) | — |
| PERM_12 CANCEL | — | — | — | — | — | — | Cancel |
| PERM_13 FOLDER_LINK_MANAGE | — | — | — | — | — | — | Unused/unenforced |
| PERM_14–17 | — | — | — | — | — | — | Reporting/audit/admin only |

**Note (directly answers the stated concern):** Shoot/Edit "Execute" have no dedicated permission at all (governed by assignment membership instead), so no cell can show ✅ there — which itself confirms **no review permission (01/03/05/07) grants any execution capability**; each is enforced independently and no execution method ever checks a review permission.

---

## Section D — Assignment Eligibility

### D1. Planning → Shoot assignee dropdown

- **Candidate list:** `DeliverableMvcController` — `userRepository.findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Camera Person")`. A **hard-coded Business Role name string match** + `active = true`. No permission check, no `participates_in_workflow` check.
- **JSP:** `deliverable-detail.jsp` iterates `camerapersonUsers` for the chip-picker.
- **Write path:** `PlanningService.assignCameraperson(assigner, contentPlanId, cameraperson)` — checks workflow status `PL` and that the **assigner** holds `PERM_04_SHOOT_ASSIGNMENT`. **The assignee argument is never checked** for Business Role, permission, or participation — any active user found by ID is accepted.

**Current implemented behavior:** UI dropdown is role-name filtered; backend write is not filtered on the assignee at all.
**Business-rule implication:** "Who can become a Shoot assignee" is today a UI-only convenience filter, not an enforced rule.
**Potential risk/inconsistency:** A direct API call by anyone holding PERM_04 can assign any active user regardless of role/permission/participation (see D6, S-2).

### D2. Planning → Edit assignee dropdown

Same shape as D1: candidate list = `findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Video Editor")`; `EditingService.assignEditor` checks only the assigner's `PERM_06_EDIT_ASSIGNMENT` and workflow status (`SAP`/`EA`) — the assignee is never validated. Same risk profile as D1 (S-3).

### D3. Publisher assignment

- **Candidate list:** `findByBusinessRole_RoleNameAndActiveTrueOrderByFullNameAsc("Publisher")`.
- **Backend (`PublishingService.assignPublisher`):** workflow status must be `RFP`; authority gate is `requireNativeAuthority(actor, "Publisher assignment")` — **materially different from Shoot/Edit**: gated to native CEO/MM Access Class only, explicitly **not** `PERM_08` grant holders (a delegated PERM_08 holder can execute their own assigned task but cannot assign Publishers). The `publisher` argument itself is never checked for Business Role/permission.

**Confirmed against the questionnaire's list:** Business Role = UI-only filter; execution permission (PERM_08) = not what gates the assign action (native authority only, PERM_08 gates execution); active assignment = not applicable to the assign action itself; workflow status = yes, RFP only.

### D4. Model selection mechanism

- **Storage:** `ContentPlanTalentEntry` — a free-text `talentName` plus an **optional** `talentUserId`, written via `PlanningService.updateParameters` (gated only by the general `PERM_02_PLANNING_EXECUTION`).
- **UI candidate source:** same Business-Role-name pattern (`"Model"`) when a real user is selected.
- **Classification:** Model selection is **identity lookup + talent scheduling**, not the executor-assignment pattern — no `ModelExecutionParticipant`, no Start/Submit lifecycle, no active-flag row, no marks-attribution path.

**Conclusion:** Confirms the questionnaire's concern is valid — applying executor rules (Business-Role/permission eligibility, active-assignment execution gates) to Models would be a data-model mismatch with how they are actually represented today.

### D5. Does the backend independently validate assignment eligibility?

| Stage | Backend validates assignee's Business Role/permission? | What backend does check |
|---|---|---|
| Shoot (`assignCameraperson`) | **No** | Assigner's PERM_04; workflow status PL |
| Edit (`assignEditor`) | **No** | Assigner's PERM_06; workflow status SAP/EA |
| Publisher (`assignPublisher`) | **No** | Assigner's native CEO/MM authority; workflow status RFP |
| Reassign (`AdminActionService#reassign`) | **Yes** (see I2) | Assigner's PERM_11; `requireBusinessRole(newUser, ...)` |

**Potential risk/inconsistency (Medium/High):** Inconsistent enforcement across four structurally similar endpoints — three trust the client's candidate list entirely, one (Reassign) re-validates server-side. See Section S.

### D6. HR-Manager-with-Shoot-permission-but-wrong-role example

Walking `POST /shooting-assignments` with an HR Manager as `cameramanUserId`:
1. Target user looked up purely by ID, no role/permission filter in the query.
2. `PlanningService.assignCameraperson` checks workflow status and the **assigner's** PERM_04 — nothing touches the HR Manager's own Business Role or permissions.
3. **Result: accepted, unconditionally.** A `ShootingAssignment` row is created for the HR Manager, independent of whether they personally hold any Shoot-related permission (none exists to check — see C4).
4. Downstream: once assigned, the HR Manager becomes a legitimate `requireActiveAssignee` match — they could then actually Start and Submit Shoot work, entirely through the "wrong Business Role" identity.

**Business-rule implication:** Permission-driven eligibility for *becoming* assignable already works today for the assignee (nothing blocks it) — but this is an accidental byproduct of missing validation, not a deliberate permission-based eligibility check, because there is no dedicated "Shoot execution eligibility" permission gate on the assignee at all. The same absence of a check also means a completely unrelated/unauthorized user can be assigned, since nothing about the assignee is validated at all.

### D7. Shoot Lead / Edit Lead validation

- **Shoot Lead:** `PlanningService.setShootLead` — assigner needs PERM_04; the Lead candidate must be one of the plan's currently `active` `ShootingAssignment` rows (server-side enforced) — a **subset-of-current-assignees** constraint, not an independent Business-Role/permission check on the Lead.
- **Edit Lead:** `EditingService.setEditLead` — identical pattern against `EditingAssignment`.
- **Conclusion:** Lead eligibility = "being selected as an active assignee" only. Since assignment itself is unvalidated against role/permission (D1/D2), Lead status inherits the same gap.

---

## Section E — My Work and Employee Workspace

### E1. How does the system decide whether `My Work` appears?

- Route: `LandingMvcController.myWork()` (`GET /app/my-work`) has **no** Access-Class/Business-Role/permission gate in the controller method itself — it always returns data scoped to `principal.user()`.
- The actual reachability gate is **`WorkflowParticipationInterceptor`** (registered for `/app/**`), which calls `isNonProductionEmployee(user)`: if true, **every** `/app/**` request is redirected to `/app/ideas` (deny-by-default), regardless of assignments held.
- So visibility = **Access Class == EMPLOYEE** (implicit; CEO/MM are redirected to `/app/pipeline`) **AND** `BusinessRole.participatesInWorkflow == true`. Not dependent on any specific Permission or on holding an active assignment — a participating EMPLOYEE with zero assignments still reaches an empty My Work page.

**Business-rule implication:** `participates_in_workflow` is the single flag that turns "My Work" (and the entire `/app/**` operational surface) on/off for an Employee, independent of whether that employee actually holds a real, active assignment.

**Potential risk/inconsistency:** The interceptor is a blanket allow/deny switch keyed only on `participates_in_workflow` — all-or-nothing per Business Role, not per-permission or per-assignment (see H3).

### E2. Which My Work screen does an employee get — is routing Business-Role based? Can it show both?

- **Backend:** computes `shootActiveWork`/`shootCompletedWork`, `editActiveWork`/`editCompletedWork`, `publishActiveWork`/`publishCompletedWork` unconditionally, filtering the user's actual active assignment rows across all three stages — this part is already assignment-based, multi-function-safe.
- **View (`my-work.jsp`):** picks exactly one of four mutually-exclusive branches keyed on `businessRoleName` (exact string match):
  - `"Camera Person"` → Shoot work only rendered.
  - `"Video Editor"` → Edit work only rendered.
  - `"Publisher"` → Publish work only rendered.
  - Any other role → generic combined `activeWork`/`completedWork` across all stages together, unlabeled by dashboard flavor.
- **Home routing (`/app/home`):** hard-coded — `if ("Model".equals(businessRoleName)) redirect to /app/my-shoots` else `/app/my-work`.

**Can the UI currently show both if a user has two functions?** Only if the Business Role is something *other than* the three canonical names (falls to the generic combined view). If the role *is* one of the three, only that one stage's tasks render — any active assignment the controller computed for a *different* stage is simply not read by that branch.

### E3. What happens today if one employee has BOTH Shoot and Edit execution permission?

**Backend:** permission alone (with no assignment) produces empty rows — My Work is driven by assignment rows, not held permissions. Assuming the employee holds active assignments in both stages:
- Backend fetches and includes both stages' data regardless of Business Role (already multi-function-safe).
- Frontend outcome depends entirely on `businessRoleName`: "Camera Person" → Shoot only visible; "Video Editor" → Edit only visible; "Publisher" → Publish only visible; any other role → both (and Publish, if applicable) via the generic combined list.

**"Behavior depends entirely on Business Role"** is the accurate characterization.

**Potential risk/inconsistency:** A genuinely multi-function employee whose Business Role is (e.g.) "Video Editor" but who also holds a valid active Shoot assignment has that Shoot task **silently missing** from their My Work page. This is a **display-only** gap, not an authorization gap — the underlying assignment, marks, and audit records are unaffected, and the employee could still act on the Shoot task via direct navigation to the deliverable detail page (execution gates don't consult Business Role — see F/H).

### E4. Is there a generic task abstraction shared across Shoot/Edit/Publishing assignments?

- **No shared assignment entity** — `ShootingAssignment`, `EditingAssignment`, `PublishingAssignment` are three independent `@Entity` classes with independent repositories, independent `active` flags, independent lead semantics (Publishing has no lead concept).
- **Shared read-side DTOs:** `ActiveWorkItem`/`CompletedWorkItem` are a genuinely common projection used for all three stages in `LandingMvcController.myWork()`.
- **No shared query/service layer** — three independent repository calls and three near-identical hand-duplicated loops populate the two shared DTOs; no `AssignmentService`/`TaskService` abstraction exists.
- Execution-side services (`ShootingService`, `EditingService`, `PublishingService`) are independent, stage-specific services, not implementations of a common interface.

**Business-rule implication:** the DTO convergence gives a natural landing spot for a future multi-stage-aware My Work redesign, but the entity/service layer would need new shared abstractions or continued per-stage duplication.

---

## Section F — Task Execution Authorization

### F1. Shoot Start/Continue/Submit — full backend authorization chain

- **Start:** workflow status must be `SA`; `requireActiveAssignee(actor, plan)` — actor's user ID must match an `active = true` `ShootingAssignment.cameraperson` row. **No `OperationalPermission` check at all** — documented deliberately: hands-on execution of an employee's own task is not covered by CEO/MM native authority, so it is not bypassable.
- **"Continue":** no separate method — `SIP` (Shoot In Progress) is simply the post-Start state; no additional authorization beyond page access.
- **Submit:** workflow status `SIP`; `holdService.requireNoOpenHold`; `requireActiveAssignee` (same as Start); Drive/Folder Link must be non-blank. No Lead/non-Lead distinction — any active assignee can Start/Submit.

**Full chain:** active assignment (identity match) + workflow state + (Submit only) no open Hold + (Submit only) content-completeness. **No permission grant, no Business Role, no Access Class, no lead-only restriction.**

### F2. Edit execution

Structurally identical to F1: `requireActiveAssignee` against active `EditingAssignment`, workflow status (`EA` start / `ED` submit), `holdService.requireNoOpenHold` and Drive Link check at submit, same explicit "no permission, no CEO/MM bypass" documented rationale.

### F3. Publishing execution

`startPublishing`: workflow status `RFP` → `requirePublishingAuthority` (= `requireAuthority(actor, PERM_08_PUBLISHING_EXECUTION, PUBLISHING, ...)`) **AND** `requireActiveAssignee` (active `PublishingAssignment`). `recordActualPublication`: workflow status `PUBG`; `holdService.requireNoOpenHold`; `requirePublishingAuthority`; `requireActiveAssignee`; evidence URL non-blank.

**Confirmed exactly:** the proposed formula — `Publishing Permission (PERM_08) + Active PUBLISH Assignment + Correct State` — **is precisely what the code implements**, with the explicit code comment that even native CEO/MM authority (which normally auto-satisfies `requireAuthority`) still separately fails `requireActiveAssignee` unless CEO/MM personally holds an active Publishing assignment.

### F4. Does CEO/MM native authority bypass assignment gates anywhere?

- **Shoot execution:** No bypass — `requireActiveAssignee` has no native-authority exception.
- **Edit execution:** No bypass, same pattern.
- **Publishing execution:** No bypass, explicitly re-confirmed for Publishing specifically.
- **What native authority DOES bypass:** any `OperationalPermission`-gated *management* action (Planning execution/review, Shoot/Edit assignment, review decisions, Reschedule, Reassign, Cancel, Reopen, Target N/A, evidence correction) — because `requireAuthority` short-circuits for native-authority users.

**Business-rule implication:** The system already draws a clean, consistently-applied, well-documented line between "management authority" (bypassable) and "hands-on execution" (never bypassable, assignment-row-only).

---

## Section G — Reviews and Contributor Attribution

### G1/G2. How are qualifying Shoot/Edit contributors selected?

Contributors come from a dedicated snapshot entity, **`ShootingExecutionParticipant`**/**`EditingExecutionParticipant`** — not from the live assignment table directly, not from Business Role, not from "who holds Shoot permission." The snapshot is created at **Submit Review** time: every currently-active `ShootingAssignment`/`EditingAssignment` row becomes a participant row at that moment — a point-in-time snapshot, decoupled from later assignment/permission/role changes. `ReviewsMvcController` surfaces this list as `qualifyingParticipants`, deduped by user, not filtered by role/permission at read time.

**Business-rule implication:** Contributor eligibility is already **assignment-based** (a snapshot of who was actively assigned at submit time) — this is already the "actual assignment/contribution" model the KPI goal is asking for.

### G3. How are marks awarded?

- Entity: `PersonalMarkAttribution`, unique constraint `(recipient_user_id, review_cycle_id)` — at most one attribution per review cycle, but a user can receive attributions across **different** cycles/stages without conflict.
- Creating event: Approve decision only (never on Request Rework, Publishing, or Reposts).
- Recipients: reviewer submits a subset (or all) of the snapshotted `qualifyingRecipientUserIds`; each must match an existing participant row.
- Each selected recipient gets a **separate** row with the **same, full** predefined mark value — never split across contributors.
- `roleType` is a **hard-coded literal** passed by the calling service (`RoleType.CAMERAPERSON`/`EDITOR`) — **not** read from the recipient's `User.businessRole`. `RoleType` is a two-value enum (no `PUBLISHER` value, consistent with Publishing having no marks/review gate).
- **Can one employee receive both Shoot and Edit marks?** **Yes** — nothing in the unique constraint, `RoleType` enum, or either service prevents the same `User` from being the recipient of both a `CAMERAPERSON`-typed and an `EDITOR`-typed attribution (different `ReviewCycle`s automatically satisfy the unique constraint). The mark ledger is inherently multi-function-safe at the entity level.

### G4. If HR performs Shoot and is a qualifying contributor, would HR receive Shoot marks today?

**Yes**, provided HR is (or was, at submit time) an active `ShootingAssignment.cameraperson`. Nothing in the contributor-snapshot code, the qualifying-participant list, or the mark-award code inspects `User.getBusinessRole()` or any permission — the entire chain is keyed on assignment/participant identity only. The one guard present is a self-review/conflict-of-interest check (blocks a reviewer from deciding a review they themselves participated in), which is unrelated to Business Role.

---

## Section H — Permission Revocation / Role Change Edge Cases

### H1. What happens if execution permission is revoked after assignment?

**No effect on task mechanics for Shoot/Edit.** `requireActiveAssignee` in both `ShootingService` and `EditingService` checks only whether an **active assignment row** still names this user — it never calls `AuthorizationService`/re-checks any `PermissionGrant`. So revoking the Shoot/Edit execution permission after assignment does **not** block Start/Continue/Submit for the already-assigned user. My Work visibility, review-contributor selection, and mark eligibility are likewise assignment-only, independent of current permission state.

**Publishing is the one exception (confirmed-safe):** `requirePublishingAuthority` (PERM_08) is re-checked **live on every call** to `startPublishing`/`recordActualPublication` — so revoking PERM_08 after assignment **does** block further execution on the next call.

**Business-rule implication:** Permission is enforced at assignment time and at review-decision time (for whoever is deciding), but for Shoot/Edit it is **never re-checked** for the assignee's own Start/Continue/Submit once the assignment exists — a real gap between "permission" and "assignment" as the source of truth for ongoing execution authority (flagged in Section S as a confirmed current-behavior finding for Shoot/Edit, contrasted with Publishing's live re-check).

### H2. What happens if Business Role changes after assignment?

Same root cause as H1: `requireActiveAssignee` never reads `User.getBusinessRole()`. A user reassigned from Camera Person → HR Manager mid-task:
- **Keeps** Start/Continue/Submit access to the already-active Shoot task.
- **Keeps** appearing in `qualifyingParticipants` on a later Submit, since that too is assignment-driven.
- **My Work display changes** (per E2/E3) — a role-flavor change, not an access change.
- **One access-relevant exception:** if the *new* Business Role has `participatesInWorkflow == false`, the interceptor immediately blocks the user from **all** `/app/**` pages — including their own active Shoot task page — even though the execution-authorization logic itself has no Business-Role dependency. This is the one indirect place Business Role can lock a user out mid-task.

**Business-rule implication:** Authorization for an in-flight task is based on the **persisted assignment**, not the user's **current** Business Role — Business Role is authoritative only for (a) My Work dashboard flavor, and (b) the all-or-nothing `participates_in_workflow` navigation gate.

### H3. What happens if `participates_in_workflow` changes after assignment?

The flag is read **only** in `isNonProductionEmployee()` (used solely by the interceptor) and in the admin CRUD that sets it — never in `ShootingService`, `EditingService`, `PublishingService`, `requireAuthority`, or any assignment/marks code. If a role's flag flips `true → false` after a user already holds an active assignment: the user is **immediately** blocked from every `/app/**` route except `/app/ideas*` on their next request — including task pages they'd need to Start/Continue/Submit — while the underlying assignment/participant/mark data is completely untouched (nothing is deleted, deactivated, or reassigned; flipping the flag back restores full access instantly).

**Business-rule implication:** `participates_in_workflow` functions purely as a **navigation kill-switch** for an entire Business Role's population, not as an input to any per-task authorization decision — effectively the mirror image of H1's permission-revocation case.

**Potential risk/inconsistency:** The interceptor only covers `/app/**` — whether any REST-only endpoints outside that prefix have an equivalent guard was not independently verified in this audit (flagged as **UNKNOWN / NOT DETERMINABLE**, worth confirming directly).

---

## Section I — Reassignment, Reschedule, Hold and Governance

### I1. Who can reassign Shoot/Edit/Publishing work?

- **Shoot/Edit:** `AdminActionService.reassign` — authority = `requireAuthority(actor, PERM_11_REASSIGN, ADMINISTRATIVE, ...)` (delegable, or native bypass).
- **Publishing:** **No dedicated reassign path exists.** `AdminActionService.reassign` only branches on `TaskStage.SHOOTING` vs. an else-branch treated as Editing — Publishing's equivalent of "replace the assignee" is `removePublisher` + `assignPublisher` (native-CEO/MM-only, RFP-window-only), not the generic Reassign flow. **UNKNOWN / NOT DETERMINABLE** whether the UI can ever POST a `TaskStage.PUBLISHING` value to `/reassign` — if `TaskStage` includes a `PUBLISHING` value reachable from the UI, it would fall into the `else` branch and be mishandled as an Editing reassignment (calling `requireBusinessRole(newUser, "Video Editor")`). Flagged as a latent-bug candidate for stakeholder/engineering follow-up (see Section S cross-reference).

### I2. Does reassignment eligibility use Business Role filtering? Comparison to initial assignment?

**Yes — reassignment enforces Business Role server-side, unlike initial assignment.** `AdminActionService.requireBusinessRole(user, expectedRoleName)` is called for every new assignee in `reassign` (`"Camera Person"` for Shooting, `"Video Editor"` for Editing). The code's own comment explicitly documents the asymmetry: the Reassign UI already only offers role-matched candidates (same pattern as every other picker), "but that was UI-only; nothing on the server stopped a direct API call from reassigning any active user regardless of role. Enforced here so the constraint holds regardless of caller."

**Comparison:** Reassign is **strictly more restrictive server-side** than initial `assignCameraperson`/`assignEditor`, which have no equivalent check at all — an inconsistency the codebase itself acknowledges fixing for one path but not the other.

### I3. Who can Hold/Resume work?

`HoldService.placeHold`/`resume` — `requireNativeAuthority(actor, "Hold"/"Resume")` — CEO_OWNER/MARKETING_MANAGER Access Class **only**, not delegable via any permission grant. Stage-dependent (Hold only while `SIP`/`ED`/`PUBG`; Resume requires an existing open `WorkHoldRecord`).

**Conclusion:** Hold/Resume = native-authority-dependent + workflow-stage-dependent. Not assignment-dependent, not permission-grant-dependent, not Business-Role-dependent.

### I4. Does Reschedule depend on Business Role anywhere?

`AdminActionService.reschedule` — authority = `requireAuthority(actor, PERM_10_RESCHEDULE, ADMINISTRATIVE, ...)` (delegable or native). **No Business Role check anywhere** — operates purely on `ContentPlan`/`WorkflowInstance` dates and a `StageContext` framing enum. Reopen/repost cycles (`reopenCompleted`/`reopenForPublishing`/`reopenForPerformance`) are gated by `PERM_08`/`PERM_09` depending on purpose — also **no Business Role check**.

**Conclusion:** Reschedule is entirely Business-Role-independent across all stages and the reopen/repost path.

---

## Section J — Reopen / REPOST Publishing

### J1. How is a reopened Publishing cycle identified?

- `ReopenRecord.reopenPurpose` (`RETAINED_REOPEN`, `PUBLISHING_REOPEN`, `METRIC_CORRECTION_REOPEN`) distinguishes a Publishing reopen from other reopen types. Created by `AdminActionService.reopenForPublishing` (requires status `COMP`, `PERM_08` authority), transitioning `COMP → RFP` (not directly to `PUBG`, to preserve the Assign-Publisher gate).
- `PublishingService.currentPublishingCycleStart(WorkflowInstance)` is the single source of truth for "is this deliverable mid-repost, since when" — looks up the latest `PUBLISHING_REOPEN` `ReopenRecord`, returns its `reopenedAt` or `null`.
- `ActualPublicationEvent.eventType` (`ORIGINAL`/`REPOST`) is *derived*, not user-selected: `hasLivePost(output, target) ? REPOST : ORIGINAL`.
- Cycle-scoped resolution (`hasLivePostInCurrentCycle`) counts only events whose **immutable `recordedAt`** (not the user-editable `actualPublicationTimestamp`) is on/after the cycle start — an explicit anti-backdating safeguard. This feeds the `"PUBLICATION_SCOPE_RESOLVED"` auto-transition (`PUBG → PP`).

**Business-rule implication:** A "reopened Publishing cycle" is not a first-class row of its own — derived at read time from `ReopenRecord` + immutable `recordedAt`, deliberately so the workflow auto-advance logic and the Publisher's checklist can never disagree about which cycle is current.

### J2. How is fresh Publisher assignment enforced after reopen?

**Old assignment is not reused — it is explicitly ended.** Inside `reopenCompleted`, when purpose is `PUBLISHING_REOPEN`, every currently-active `PublishingAssignment` for the plan is ended (`assignment.end()` sets `active=false`, `endedAt=now`), folded into the single Reopen audit record rather than a separate unassignment record ("a mechanical side effect of the one Reopen action, not an independent management decision"). The reopen endpoint optionally lets the actor pick a Publisher inline in the same call, using the same `assignPublisher` method as ordinary first-cycle assignment. After reopen, status is `RFP` — identical to first-time Publishing — so assignment/execution behave exactly as on a brand-new deliverable.

**Business-rule implication:** A repost cycle always starts from an empty Publisher-assignment slate; the repost executor must be freshly (re-)assigned by a CEO/MM actor.

### J3. If a non-Publisher Business Role has Publishing execution permission, can that person receive a reopened REPOST task today?

Tracing the full chain:
1. **Picker:** `publisherUsers` model attribute (Business-Role-name-filtered, `"Publisher"`) is reused for **both** ordinary and inline reopen-time Publisher assignment. A non-"Publisher"-role user, even holding PERM_08, will **not appear** in either picker.
2. **Backend assignment guard:** `assignPublisher` validates only workflow status (`RFP`) and the actor's native CEO/MM authority — **zero check on the assignee's Business Role or permissions**. Unlike Reassign for Shooting/Editing, there is no equivalent server-side Business-Role guard here.
3. **Execution guard:** `startPublishing`/`recordActualPublication` require PERM_08 + active `PublishingAssignment` — neither inspects Business Role.
4. **My Work / task visibility for this user once assigned:** governed by Section E's routing logic (generic combined view for a non-canonical Business Role, still fully functional).

**Current implemented behavior:** **Yes, mechanically** — if a CEO/MM actor directly POSTs an arbitrary `publisherUserId` (bypassing the role-filtered picker, which won't offer that user but doesn't block a direct call), that user can be inserted as an active `PublishingAssignment` and then fully execute Start Publishing and record REPOST events, since `requireActiveAssignee` + `requirePublishingAuthority` are satisfied purely by permission + assignment, with zero Business-Role dependency at the backend.

**Business-rule implication:** Publishing assignment/execution today is already, in practice, **permission + assignment driven, not Business-Role driven** at the backend layer — only the candidate-picker query is Business-Role coupled.

---

## Section K — KPI and Reporting Attribution

### K1. Every current KPI/report and its attribution target

`KpiService` computes 30 governed KPIs (KPI-001..030) on demand — no persisted KPI tables. Most are content/plan/event-level with no employee attribution at all. Employee-level KPIs split into two attribution styles:

| KPI / Report | Attribution style |
|---|---|
| KPI-006 Editor Workload | **Employee**, plain assignment-table join, role-agnostic |
| KPI-008 Employee Productivity | **Employee**, merges mark attributions + planning-preparer records |
| KPI-009 Manager Productivity | **Employee**, but scoped by `access_class_code = 'MARKETING_MANAGER'` |
| KPI-021 Approval Turnaround | Global average, not per-reviewer (though the data supports it — see L3) |
| KPI-022/023 Approvals by Manager/CEO | **Reviewer**, scoped by Access Class, not Business Role |
| Legacy `teamWorkload()` (KpiService) | **Employee**, plain assignment-table join, no Business Role filter |
| **`TeamWorkloadService.teamWorkloadDashboard()`** (Team → Workload UI) | **Employee**, but candidates are **first enumerated by hard-coded Business Role name** before assignment lookup |
| `AdminReportingService.delayedDeliverables()` | current-stage assignee(s) |
| `PersonalMarkAttribution` | **Employee**, per stage (`role_type`), via qualifying-contributor selection |

**Current implemented behavior:** Most governed KPIs carry no employee attribution at all. The employee-level KPIs are mostly genuinely assignment/mark-based (role-agnostic) — **except** the Team → Workload dashboard's Assignee Load panel.

### K2. KPIs using Business Role as attribution source

| KPI / Report | Current Attribution Source | Risk if Employee Performs Multiple Functions |
|---|---|---|
| KPI-009, KPI-022, KPI-023 | Access Class (`MARKETING_MANAGER`/`CEO_OWNER`), not Business Role name | **Low** — these measure management/reviewer authority tier, not stage execution; out of scope for the Shoot/Edit multi-function concern |
| **Team → Workload → Assignee Load** (`TeamWorkloadService`) | Candidate set enumerated via hardcoded `BusinessRole.roleName` match (`"Camera Person"`/`"Video Editor"`/`"Publisher"`/`"Model"`) **before** real assignment rows are consulted | **High** — a user with a non-matching Business Role (e.g. HR Manager) who holds a real active `ShootingAssignment` **never appears** on this dashboard, breaking Employee+Stage attribution specifically here |
| `TeamWorkloadService.planningRows()` | `businessRoleName` used as a display label / optional filter value; underlying population (`ContentPlan.preparedBy`) is itself role-agnostic | **Low** for population; the filter dropdown itself is a UI-level Business-Role coupling |

**Potential risk/inconsistency:** KPI-006 (role-agnostic) and the Team → Workload Assignee Load panel (role-gated) both claim to show "who has active Shoot/Edit/Publish work," but use **different populations** — they can disagree today for any user whose Business Role isn't one of the four hard-coded strings. (Also an O2 duplicated-logic finding.)

### K3. Can current data distinguish, per employee, Shoot/Edit/Publishing sub-metrics?

| Metric | Source entity | Timestamp | Role-independent? |
|---|---|---|---|
| Shoot/Edit Assigned | `ShootingAssignment`/`EditingAssignment` (`user_id`, `is_active`) | `assigned_at` | Yes |
| Shoot/Edit Completed | No direct "completed" flag — inferable via `ExecutionParticipant` + `ReviewCycle.decision='APPROVED'`, or `WorkflowStatus` reaching `SAP`/`EAP` | `recordedAt`, `decidedAt` | Yes in principle, but **no single query currently does this** |
| Shoot/Edit Delayed | Not stored per employee — only derivable via join to plan status/planned-date, as `TeamWorkloadService` already does | computed | Yes in principle, only implemented today inside the role-gated path |
| Shoot/Edit Rework | **Not attributed to an employee at all.** `ReviewCycle.decision='REQUEST_REWORK'` links to `WorkflowInstance`/`GateType`/`cycleNumber` only — not to a participant or assignment row; per `PersonalMarkAttribution`'s own javadoc, no attribution is created on Request Rework | `decidedAt` | **UNKNOWN which specific contributor(s)** a rework decision is "about" — only approximable by cross-referencing whoever held an active assignment at that time (not stored as a snapshot) |
| Shoot/Edit Marks | `PersonalMarkAttribution` (`recipient_user_id`, `role_type`) | `attributed_at` | Yes |
| Publishing Assigned | `PublishingAssignment` (explicitly "additive tracking; does not gate Start Publishing") | `assigned_at` | Yes |
| Publishing Completed | `ActualPublicationEvent.publishedBy` (direct FK) + `eventType` | `actualPublicationTimestamp`, `recordedAt` | Yes — the strongest of all stage-completion signals, actor identity stored directly on the event |

### K4. Can one employee safely appear in both Shoot and Edit KPI calculations? Employee+Stage or Employee+BusinessRole?

**At the data-model level, yes.** `ShootingAssignment.cameraperson` and `EditingAssignment.editor` are independent FKs; `PersonalMarkAttribution` stores `role_type` per-row rather than trusting `User.businessRole`. KPI-006/008 query assignment/mark tables directly (`Employee + Stage`), with no Business-Role filter.

**The one path that is not Employee+Stage** is `TeamWorkloadService`'s Assignee Load panel — `Employee-enumerated-by-BusinessRole, then + Stage` — an employee whose Business Role doesn't match the stage's canonical role name is excluded before their real assignment rows are even queried, even though the same employee would correctly appear in KPI-006/legacy `teamWorkload()`.

**Business-rule implication:** the schema and most KPI queries already support `Employee + Stage` attribution without schema change; `TeamWorkloadService` is the one component actively implementing `Employee + BusinessRole (name match)` as a gate before stage attribution.

### K5. Reliable "actual contributor" source per stage

| Stage | Assigned user | Lead | Qualifying contributor | Submitter | Completer | Reviewer | What reporting code actually uses |
|---|---|---|---|---|---|---|---|
| Planning | `ContentPlan.preparedBy` | n/a | n/a | n/a | n/a | `ReviewCycle.reviewer` (PLANNING_REVIEW) | `preparedBy` |
| Shoot | `ShootingAssignment.cameraperson` (active) | `ShootingAssignment.lead` boolean | `ShootingExecutionParticipant` (snapshot at submit) | `ReviewCycle.submittedBy` | inferred from status `SAP` | `ReviewCycle.reviewer` | Workload/count KPIs use **assigned user**; **marks** use the narrower **qualifying contributor** (a manually-selected subset at Approve time) |
| Edit | mirrors Shoot | mirrors Shoot (not independently re-verified, see notes) | `EditingExecutionParticipant` | `ReviewCycle.submittedBy` | inferred from status `EAP` | `ReviewCycle.reviewer` | same split as Shoot |
| Publishing | `PublishingAssignment.publisher` (active, explicitly non-gating) | n/a | n/a (no execution-participant table for Publishing) | n/a | `ActualPublicationEvent.publishedBy` (actual actor) | n/a (no ReviewCycle gate) | `publishedBy` is the more reliable "actually did the work" source, since assignment is explicitly non-enforcing |

**Potential risk/inconsistency:** Workload/count KPIs (assigned user) and Marks (qualifying contributor) are deliberately different populations by design — an assignee who never becomes a qualifying contributor is counted in workload but never receives marks. Documented, intentional behavior, not a bug — but "who did the work" has more than one valid answer depending on which report is asked.

### K6. Delayed task attribution with multiple assignees

`TeamWorkloadService.rowFromAssignments()` increments each candidate employee's own `delayed` counter independently for each of their own delayed assignments — **one delayed row credited per active assignee**, not lead-only, not content-level-once — but this only runs for employees who passed the Business-Role-name gate (K2/K4); an HR Manager with an active `ShootingAssignment` never reaches this count. Separately, `KpiService`'s KPI-002/029 "Delayed Work" counts are **content-plan-level**, counted once per plan regardless of assignee count.

### K7. On Hold task attribution

Same mechanism and same Business-Role-name-gate caveat as K6 (`onHold` counter per assignee, via `WorkHoldRecordRepository.findByResumedAtIsNull()`). No plan-level-only "On Hold" KPI exists in the 30 governed KPIs.

### K8. What does Team → Workload currently calculate?

`TeamWorkloadService.teamWorkloadDashboard()` (gated by PERM_14) computes two deliberately separate aggregations:
1. **`stageCounts`** ("Active Tasks by Stage") — purely `WorkflowStatus`-derived, one plan counted in exactly one stage window. **Not role- or assignee-based.**
2. **`assigneeRows`** ("Assignee Load") — **candidate population enumerated by hardcoded Business Role name**, then built from real assignment rows, filtered to the user's own currently-active stage window. Filters supported: `businessRole`, `employeeId`, `stage`, `dateFrom`/`dateTo`, `delayedOnly`. **Capacity is not computed anywhere** — no capacity/quota field exists in the result.

**Current implemented behavior:** Active/Delayed/On-Hold are computed; Capacity is not. Source is a hybrid — `stageCounts` is workflow-state-based; `assigneeRows` is Business-Role-name-gated, then assignment-based.

---

## Section L — Reviews KPI Data Sources

### L1. Can First-Pass Approval be reliably calculated by stage?

`ReviewCycle` stores `gateType`, `cycleNumber` (unique per instance/gate/cycle), and `decision` (free-form string: `APPROVED`/`REQUEST_REWORK`/`REJECTED`, etc.) — "first-pass approval" per stage is reliably derivable as `count(cycleNumber=1 AND decision='APPROVED') / count(cycleNumber=1)`, filtered by `gateType`, for Planning/Shoot/Edit/Ideas alike.

**Current implemented behavior:** **No such KPI is implemented today.** KPI-024 "Rework Rate" is the closest existing metric, but computed across Planning/Shoot/Edit review gates **combined**, not broken out per stage, and counts all `REQUEST_REWORK` decisions, not specifically cycle-1 ones.

**Potential risk/inconsistency:** None — this is an unimplemented but schema-supported metric, not a coupling problem.

### L2. Can rework be attributed to actual contributors?

**No.** `ReviewCycle` links only to `WorkflowInstance` + `gateType` + `cycleNumber` + `submittedBy` (who submitted, not necessarily who executed) + `reviewer`. There is **no FK** from `ReviewCycle` to the execution-participant or assignment tables, and per `PersonalMarkAttribution`'s own comment, no attribution is created on Request Rework. The participant snapshot tables are only populated at Submit time, so a rework cycle's contemporaneous participant set *could in principle* be reconstructed by timestamp-matching — but no code does this today, and it is not a stored, queryable link.

**Business-rule implication:** rework can currently only be attributed to content + review cycle, not to specific contributors (confirms K3).

### L3. Can review turnaround be attributed to reviewer identity?

**Yes.** `ReviewCycle.reviewer`, `submittedAt`, and `decidedAt` are all present on the same row, so per-reviewer turnaround is directly computable by grouping on `reviewer_user_id`. KPI-021 already computes exactly this duration but **does not group it by reviewer** — it is a single global average today.

---

## Section M — Publishing KPI Data Sources

### M1. Can Published Content be calculated by actual publication cycle?

**Yes**, and the codebase is explicit and consistent about it. `ActualPublicationEvent` distinguishes Content ID (via `contentPlan`), Planned Output (via `plannedOutput`), Platform×Channel target (via `publicationTarget`), and ORIGINAL vs REPOST (`eventType`). KPI-012 filters `eventType=ORIGINAL` and counts distinct plans. KPI-028's inline comment documents a **fixed defect** where an earlier version of a cycle-time KPI incorrectly included REPOST rows, producing impossible negative cycle times — corrected to filter `ORIGINAL` only, consistent with KPI-030 and the pipeline dashboard's "Actual Live Date" convention. Actively relied upon and enforced consistently.

### M2. Can Publishing execution be attributed to the actual Publisher user?

**Yes, directly.** `ActualPublicationEvent.publishedBy` is a **mandatory, non-null FK to User**, captured at event-recording time, **independent** of whichever user(s) hold an active `PublishingAssignment`. This is the strongest identity signal in the whole audit — it does not need to be derived from assignments or audit logs. Note `PublishingAssignment` is explicitly documented as **not gating** Start Publishing (only PERM_08 gates it), so the assignment table and the actual-publisher-identity table are intentionally decoupled — `publishedBy` is the reliable source.

### M3. Can evidence corrections be attributed to the user who made them?

**Yes.** `PublicationEvidenceCorrection.correctedBy` is a mandatory FK to `User`, plus `correctedAt`, `mandatoryReason` (non-null, forces a reason), and an optional FK to the `PermissionGrant` under which the correction was made. Corrections are append-only and chained via `supersedesCorrection`; `latestByEventId()` picks the current row while preserving full history.

---

## Section N — Performance KPI Data Sources

### N1. Can performance scorecards be attributed to the employee who entered/submitted them?

**Yes.** `CreativePerformanceScorecard.recordedBy` is a mandatory FK to `User`. The scorecard is mutable until `submit()` seals it (further changes must go through `PerformanceMetricCorrection`, not direct mutation — enforced again by a DB trigger per the class comment). One scorecard per `PerformanceObligation` (1:1), one obligation per `ActualPublicationEvent` (1:1) — always traceable back to a specific publication event and Content ID.

### N2. Does current Performance reporting depend on Business Role?

No occurrence of Business-Role/role-name checks was found anywhere under `com.kcpc.mkt.performance` (domain/service/repository/dto) via targeted search. Based on the domain-layer evidence and the absence of any Business-Role plumbing in `PerformanceObligation`/`CreativePerformanceScorecard`/`PerformanceMetricCorrection`, Performance KPI attribution appears purely event/user-FK based. **`PerformanceService.java` (currently modified on disk) was not read line-by-line in full** — this should be spot-checked directly before being treated as fully final (**flagged, not fully verified**).

### N3. Can corrected metrics be traced to the correcting user?

**Yes.** `PerformanceMetricCorrection.correctedBy` is a mandatory FK, with `correctedAt`, `mandatoryReason` (non-null), `supersedesCorrection` chain (mirrors `PublicationEvidenceCorrection`'s pattern), and `actingPermissionGrant`. Every corrected field is stored as an explicit `prior_X`/`new_X` pair — both the correcting identity and the exact before/after values are fully traceable.

---

## Section O — Current Central Authorization Services

### O1. Central authorization/eligibility services

| Class | Method | Purpose | Business Role Dependent? | Permission Dependent? | Assignment Dependent? |
|---|---|---|---|---|---|
| `AuthorizationService` | `hasNativeAuthority(User)` | Is user CEO/MM | No | No | No |
| `AuthorizationService` | `isNonProductionEmployee(User)` | Nav/route participation gate | **Yes** (`participatesInWorkflow`) | No | No |
| `AuthorizationService` | `requireAuthority(User, Permission, Stage, Instance)` | Core "can this user do this governed action" check | No | Yes | No |
| `AuthorizationService` | `requireNativeAuthority(User, String)` | Native-only action gate | No | No | No |
| `AuthorizationService` | `requireAccessClass(User, AccessClass, String)` | Exact-class gate | No | No | No |
| `AuthorizationService` | `requireNoSelfReviewConflict(...)` | Self-review barrier | No | Indirectly (delegated path only) | No |
| `ShootingService`/`EditingService`/`PublishingService` | `requireActiveAssignee(User, plan)` (private) | Execution gate | No | No (deliberately) | **Yes — sole gate** |
| `WorkflowParticipationInterceptor` | `preHandle(...)` | Server-side route guard mirroring `isNonProductionEmployee` | Yes (via that method) | No | No |
| `MvcNavigationAdvice` | nav flags | View-model exposure for `nav.jsp` | Partially (raw role name for the "Model" branch) | Yes (PERM_17 for Administration) | No |
| `DeliverableMvcController` | private `allowed(...)` | Read-only "would `requireAuthority` succeed" wrapper for view flags | No | Yes (delegates) | No |
| `AdminActionService` | `requireBusinessRole(User, String)` (private) | Reassignment replacement-candidate validation | **Yes — hard-coded name equality** | No | No |

**No dedicated "AssignmentEligibilityService" or "StageAccessService" exists** — eligibility-for-assignment logic lives inline inside `DeliverableMvcController` (candidate queries) and `AdminActionService` (reassignment validation), not as a standalone service.

### O2. Duplicated authorization logic

- **No client-side (JavaScript) authorization/filtering duplication was found** — a repo-wide search for role-name literals in `.js` files returned zero matches. All candidate filtering is server-side (JSP model attributes / controller queries). Positive finding.
- **JSP-layer duplication of Access-Class branching:** `nav.jsp` re-implements the `accessClass == 'EMPLOYEE'` check itself as a JSP EL string comparison rather than consuming a single boolean flag — duplicated-in-form (both derive from the same model attribute), low risk.
- **Business-Role-name-equality duplicated across independent call sites**, each hard-coding the same four literal strings with **no shared constant/enum**: `nav.jsp`, `LandingMvcController`, `DeliverableMvcController`, `TeamWorkloadService`, `AdminActionService`. Because `BusinessRole.roleName` is free-text and CEO-editable at creation (whether a rename capability exists post-creation is **UNKNOWN / NOT DETERMINABLE** without further inspection of the admin UI), any future change to these role names would require updating 5+ independent call sites in lockstep — there is no single source of truth for "which Business Role name(s) mean Cameraperson."
- `AdminActionService.requireBusinessRole` duplicates, in a reassignment context, the same kind of check the candidate-list queries do at initial-assignment time — two independently-written enforcement points, one at initial assignment (not actually enforced server-side, per D5) and one at reassignment (enforced), with no shared helper. If one path is updated to allow permission-based eligibility and the other isn't, initial assignment and reassignment eligibility would silently diverge further than they already do.

---

## Section P — Candidate Lists / Dropdowns

All picker sources are populated in `DeliverableMvcController` (Content Detail/Planning tabs) unless noted; JSPs are `deliverable-detail.jsp` and `fragments/reviews-*.jspf`.

| UI | Candidate Source | Current Filter | Backend Validation (on submit) | Business Role Coupled? | Permission Coupled? |
|---|---|---|---|---|---|
| **Shoot Assignee** (initial) | `camerapersonUsers` = role-name query `"Camera Person"` | Active + role-name match | `PlanningService.assignCameraperson`: workflow status `PL`; actor's PERM_04 only. **No assignee check at all.** | Picker: **Yes**. Backend: **No** | Backend: actor only |
| **Shoot Lead** | Same picker; Lead is a boolean toggle (`is_lead`) on an existing `ShootingAssignment` row | Same | DB unique partial index enforces at most one Lead per plan; doesn't constrain Business Role | Indirectly (must already be a shoot assignee) | UNKNOWN — see D7 |
| **Edit Assignee** (initial) | `videoEditorUsers` = role-name query `"Video Editor"` | Active + role-name match | `EditingService` assignment method — no `requireBusinessRole` call found anywhere in it | Picker: **Yes**. Backend: **No** | Backend: actor-side PERM_06 presumed, not assignee |
| **Edit Lead** | Same picker; `is_lead` on `editing_assignments`, same DB unique-index pattern | Same | Same DB-level one-Lead constraint | Indirectly | UNKNOWN — see D7 |
| **Publisher Assignment** | `publisherUsers` = role-name query `"Publisher"`, reused verbatim for the Reopen-for-Publishing inline picker | Active + role-name match | `assignPublisher`: workflow status `RFP`; actor native CEO/MM authority only. **Zero check on assignee.** | Picker: **Yes**. Backend: **No** | Backend: none on assignee; native-authority gate is not permission-based |
| **Reassignment** (Shoot/Edit only — no Publishing reassignment endpoint; `TaskStage` enum = `{SHOOTING, EDITING}`) | Same role-name-filtered pickers reused | Same | `AdminActionService.reassign`: actor's PERM_11 **and** `requireBusinessRole(newUser, ...)` for each new assignee — **the one flow in the app with a genuine server-side Business-Role re-check**, added specifically (ENG-054) because "nothing on the server stopped a direct API call from reassigning any active user regardless of role" | Picker: **Yes**. Backend: **Yes (uniquely, only here)** | Actor: PERM_11. Assignee: role-checked, not permission-checked |
| **Model selection** | `modelUsers` = role-name query `"Model"` | Active + role-name match | `ContentPlanTalentEntry` save — **no Business Role or permission validation** on the linked user; this is talent/identity linkage, not an execution assignment | Picker: **Yes**. Backend: **No** | None — confirms D4's premise that Models must not be treated as executor assignees |
| **Qualifying Shoot contributor** (Shoot Review) | `qualifyingParticipants` = deduped `ShootingExecutionParticipant` rows for the plan | Not role-filtered — every distinct participant snapshotted at Submit Review time | Checkbox selection feeds mark attribution (Section G/K) | **No** — purely assignment/execution-derived | N/A |
| **Qualifying Edit contributor** (Edit Review) | Same pattern via `EditingExecutionParticipant` | Same | Same | **No** | N/A |
| **Reviewer selection** | **No dropdown/picker exists** — any user navigating to Reviews who holds the relevant review permission (PERM_05/PERM_07-equivalent) can act as reviewer | N/A | Central `requireAuthority` check | **No** | **Yes** — self-selection is purely permission-gated |

**Cross-cutting finding:** Across every row except Reassignment, the picker's Business-Role filter is enforced **only at the query that builds the dropdown's option list** — not re-validated when the form posts. Reassignment is the sole exception, and its own code comment explicitly frames it as closing a gap that exists everywhere else in the app. A direct API/form POST with a user id outside the picker's role filter is **not rejected by the server** based on Business Role for initial Shoot/Edit assignment, Publisher assignment (including post-reopen), or Model linkage.

---

## Section Q — Database / Schema Capability

Schema source of truth: Flyway migrations under `src/main/resources/db/migration/` (`ddl-auto: validate` confirms Hibernate never generates DDL — SQL files are authoritative).

### Q1. Does the current schema already support permission-driven multi-function employees without schema changes?

Answered per capability, independently:

- **Receive Shoot assignment: Yes.** `shooting_assignments.cameraperson_user_id UUID NOT NULL REFERENCES users(user_id)` — no FK/CHECK/enum ties this column to `business_roles`. Any `users.user_id` can be inserted here regardless of `business_role_id`.
- **Receive Edit assignment: Yes**, identically — `editing_assignments.editor_user_id`, no role constraint.
- **Receive Publishing assignment: Yes** — `publishing_assignments.publisher_user_id`, no role constraint (confirmed further: the service layer for this table has literally zero Business-Role check on the assignee today, per J3/P).
- **Receive stage-specific marks: Yes.** `personal_mark_attributions.role_type VARCHAR(20) CHECK (role_type IN ('CAMERAPERSON','EDITOR'))` is a column **on the mark row itself**, independent of the recipient's `business_role_id`. The only uniqueness constraint is scoped to `(recipient_user_id, review_cycle_id)` — nothing stops the same recipient holding both a `CAMERAPERSON`-typed row and an `EDITOR`-typed row from different review cycles.
- **Appear in stage-specific KPI calculations: Likely yes**, structurally sufficient — the underlying tables are keyed by `user_id` with stage identified by which table/column, not by `business_role_id`. Confirmed against actual KPI query behavior by the K-series findings above: **true for KPI-006/008/marks/publishing attribution; false today specifically for the `TeamWorkloadService` Assignee Load dashboard**, which gates by role name in application code (not schema) before ever consulting these otherwise-sufficient tables.

### Q2. What schema constraints would block this?

Searched `shooting_assignments`, `editing_assignments`, `publishing_assignments`, `personal_mark_attributions`, `users`, `business_roles`, `permission_grants` definitions:

- **`users.business_role_id`** — `@ManyToOne(optional=false) @JoinColumn(nullable=false)`. This is the **one real structural constraint** in the whole audit: a User has **exactly one** Business Role at the schema level (mandatory, scalar FK, not a join/mapping table) — the schema cannot represent a user holding two Business Roles simultaneously (Section B1).
- **No enum restriction** ties any assignment or mark table to a required Business Role of the referenced user.
- **No unique constraint** prevents one user appearing across multiple stage-assignment tables simultaneously — the only uniqueness constraints found are active-row idempotency partial indexes and the one-Lead-per-plan indexes, none cross-table or role-based.
- **`business_roles.participates_in_workflow`** is a structural attribute of the Business Role, not of any permission-grant or assignment row — schema-wise it does not block assignment/marks/KPI attribution for a user whose role has this flag `false` (none of those tables join through it).
- **`permission_grants`** has no FK or CHECK referencing `business_roles` at all — grants are already fully user-scoped at the schema level, independent of Business Role.

**Business-rule implication:** The database schema imposes essentially **one** hard multi-function constraint (`users.business_role_id NOT NULL`, single-valued). Every other piece of data needed for a permission-driven, multi-function-employee model — stage assignments, stage-specific marks, permission grants — is **already** modeled independently of Business Role and would need **no migration** to support one employee holding Shoot + Edit (+ Publishing) assignments and marks concurrently. The actual blockers are in **application code** (candidate-picker queries hardcoded to role-name strings, and the one deliberate reassignment-only re-check) — not in the schema.

---

## Section R — Tests

### R1. Existing tests relevant to the audit scope

Source: `src/test/java/com/kcpc/mkt/*.java` — 33 top-level `@SpringBootTest` full-stack HTTP test classes.

| Area | Test Class | What It Actually Verifies |
|---|---|---|
| Permissions (no grant) | `PermissionBoundaryTest` | No-permission user gets 403 on Idea Review/Planning actions; workflow-state ordering rejected even for CEO; deactivating a user immediately revokes sessions |
| Grant expiry/revocation/scope | `HighPriorityEdgeCaseTest` | Expired grant → rejected; revoked grant → rejected (proven by success-before/failure-after); stage-restricted grant rejected outside its scope. Confirms `AuthorizationService` re-evaluates the grant **live, on every call** |
| Marks / multi-contributor | `HighPriorityEdgeCaseTest` | Multiple qualifying contributors each get the **full** mark (never split), one row per contributor |
| Qualifying-contributor dedup | `HighPriorityEdgeCaseTest` | List shows a re-submitted contributor exactly once after a rework cycle |
| Reassignment Business-Role gate | `HighPriorityEdgeCaseTest` | Direct POST reassigning a role-mismatched user is **silently rejected server-side**; a role-matched one succeeds — confirms `AdminActionService.reassign`'s server-side Business-Role check works as coded |
| Assignment picker/lead/idempotency | `AssignmentPickerTest` | Assign/remove idempotency; Lead must be an active assignee (rolls back on invalid lead); combined assign+lead endpoint is transactional. **No test in this file exercises an assignment of a wrong-Business-Role or no-permission user** — every fixture user is pre-created with the matching role. Real coverage gap. |
| Publisher assignment gate | `AssignmentPickerTest` | Publisher assignment only valid at `RFP`; a PERM_08-holding assigned Publisher can execute but cannot self-manage the Publisher-assignment list |
| Business Role / participation matrix | `WorkflowParticipationRegressionTest` | Per-role workspace routing (CEO/MM full nav; Camera Person/Video Editor/Publisher → My Work; Model → My Shoots; HR Manager/SEO Intern/other non-participating roles → idea-only workspace). Proves a **direct URL hit to `/app/my-work` is backend-denied**, not just hidden from nav, for a non-participating role. Confirms a new Business Role defaults to non-participating unless explicitly configured. |
| My Work visibility timing | `MyWorkVisibilityTest` | Task visibility gated on workflow state, not raw assignment time; KPI counters cross-checked against table state |
| Model workspace | `MyShootsTest` | Model's My Shoots view scoped to own linked shoots, split Upcoming/Past |
| Review parity | `ShootEditReviewParityTest` | CEO and MM see identical qualifying-contributor lists/actions |
| Self-review conflict | `SelfReviewConflictTest` | Delegated permission holder cannot approve their own submitted idea; a different reviewer can |
| Reopen/REPOST | `WorkflowVariantsE2ETest` | Reopening a completed deliverable for Publishing requires a **fresh** Publisher assignment for both CEO and MM actors; multiple publication events (Target N/A reversal + Repost) tracked correctly |
| Hold | `WorkflowVariantsE2ETest` | Active Hold blocks Shoot Review submit until resumed |
| KPI correctness | `KpiServiceTest` | Regression test for KPI-028 confirms REPOST-row exclusion and no-negative-cycle-time fix. **Only one of the 30 governed KPIs has a dedicated correctness test** |
| KPI/Reporting smoke + security | `ReportingApiSecurityTest` | All 30 KPIs render without error (render/shape smoke only, **not** attribution-correctness); regression-guards a previously-fixed password-hash leak via eager JPA association in `AuditRestController` |
| DB-level append-only enforcement | `DbIntegrityEnforcementTest` | Hard DELETE/TRUNCATE against append-only tables rejected at the Postgres level, not just app layer |
| Correction ledgers | `CorrectionLedgerFlowTest` | Corrections are append-only, originals preserved — but does **not** assert actor identity is captured on the correction row (see S-10) |
| Screen smoke tests | ~19 further classes | Render/200-status and form-shape smoke coverage; not authorization-focused beyond what's captured above |

**Note on `WorkflowVariantsE2ETest.java`:** this file is currently **modified on disk relative to the last commit**. It was read as-is; whether the working-tree diff adds/removes assertions relative to the committed version is **UNKNOWN / NOT DETERMINABLE** without a `git diff` (out of scope per audit instructions). Treat the reopen/REPOST coverage above as reflecting the current working tree, not necessarily the last committed state.

### R2. Missing tests for multi-function employees

| Scenario | Coverage status |
|---|---|
| HR (or any non-Camera-Person role) + Shoot permission appears in the Shoot candidate list | **Not covered.** Candidate list is a hardcoded role-name query; no test exercises this boundary either way. |
| HR can receive a Shoot **assignment** (not reassignment) despite wrong Business Role, via direct POST | **Not covered — and likely reproducible today**, per D6/S-2: `assignCameraperson` performs no check on the assignee at all. Contrast with the reassignment test, which covers exactly this for reassignment only. |
| HR without Shoot permission cannot receive Shoot assignment | **Not covered**, and per the above, likely **not true today** either — nothing in `assignCameraperson` checks the assignee's permission or role. |
| Employee with Shoot + Edit permission sees both task types in My Work | **Not covered.** Existing tests only use single-Business-Role fixtures; no fixture grants one user both a Shoot and an Edit assignment simultaneously and checks My Work rendering. |
| Permission revoked after assignment — does execution remain possible? | **Partially covered** — only for `PERM_02_PLANNING_EXECUTION`'s live re-check. No test revokes a Shoot/Edit-related permission on an already-assigned Cameraperson/Editor and checks Start/Continue/Submit. |
| Employee receives both Shoot and Edit marks | **Not covered.** Existing multi-contributor mark tests use disjoint user sets for Camera vs Editor marks; no test has the same user qualify for both. |
| KPI attributes the same employee separately by stage (Employee+Stage vs Employee+BusinessRole) | **Not covered.** No test asserts a specific KPI's attribution key. |
| Business Role changes mid-task | **Not covered.** No test changes a user's Business Role after they hold an active assignment and then checks task accessibility. |
| `participates_in_workflow` toggled after assignment | **Not covered.** Same gap, for the participation flag specifically. |

---

## Section S — Security Risks

For each finding: current behavior, exploit scenario, rating. Confirmed-safe areas are listed at the end.

### S-1. Business Role checked at Reassignment (server-side) — resolved, included for completeness
**Behavior:** `AdminActionService#reassign` → `requireBusinessRole(user, expectedRoleName)` compares Business Role name before creating a reassignment row. Documented (ENG-054) as a fix for a previously UI-only gap. Verified by test.
**Rating: Confirmed-safe (Low residual risk).** Note: resolved via a hardcoded name string, not a permission check — itself a coupling finding (B4/O2), meaning this gate would need code changes, not just data changes, to become permission-driven.

### S-2. Initial Shoot assignment has no assignee eligibility check server-side
**Behavior:** `PlanningService.assignCameraperson` authorizes only the assigner (workflow status `PL` + PERM_04); never inspects the target `cameraperson`'s Business Role or permission.
**Exploit scenario:** Any user holding PERM_04 can `POST /api/v1/content-plans/{id}/shooting-assignments` with any active user's UUID — including an HR Manager or any unrelated Business Role — and the assignment is created with no rejection. A direct-POST bypass of dropdown filtering.
**Rating: High.** (Not Critical — the *assigner* side is still permission-gated; this is an authorization-completeness gap on the assignee side, not an unauthenticated-access hole. High because it directly enables the exact scenario the questionnaire probes, today, unintentionally.)

### S-3. Initial Edit assignment has the identical gap
**Behavior:** `EditingService.assignEditor` follows the same pattern — checks workflow status and the assigner's PERM_06 only; no check on the target `editor`.
**Rating: High.** Same reasoning as S-2 — a systemic pattern across both production-assignment endpoints, not a one-off.

### S-4. Initial Publisher assignment: stronger assigner-gate, same assignee-side gap
**Behavior:** `assignPublisher` requires native CEO/MM authority (not PERM_08) — materially reduces exploitability versus S-2/S-3, since only the highest-trust actors can call it. Still performs no check on the target `publisher`'s Business Role or permission.
**Rating: Medium.** Lower than S-2/S-3 because restricted to CEO/MM, but still means the system has **no structural guarantee** anywhere that a "Publisher" is anyone in particular.

### S-5. Execution-time checks ARE re-validated live for Publishing (confirmed-safe, contrasts with S-2..S-4)
**Behavior:** `startPublishing` re-checks PERM_08 (via a live grant lookup, per `revokedDelegatedPermissionGrantIsRejected`) **and** `requireActiveAssignee` on every call — not cached from assignment time.
**Rating: N/A (confirmed-safe).** Directly answers H1 for Publishing: revoking PERM_08 after assignment **does** block further execution. Whether Shoot/Edit execution has an equivalent live-permission-check (they don't check any permission at all today, by design — see F1/F2/H1) was independently confirmed as **not** re-checked, since no permission gates those actions in the first place.

### S-6. Direct-URL bypass of a hidden nav item: tested and confirmed blocked (confirmed-safe)
**Behavior:** `seoInternGetsIdeaOnlyWorkspaceAndDirectMyWorkUrlIsDenied` proves a non-production Business Role hitting `/app/my-work` directly by URL is still denied server-side, not merely hidden from nav.
**Rating: N/A (confirmed-safe).** A real, positive counter-example to "hiding a link is the only authorization," at least for this route.

### S-7. Password hash leakage via eager JPA association — found and fixed, regression-guarded
**Behavior:** `AuditRestController` previously serialized raw `SystemAuditLog` entities whose eager `actor` association leaked `passwordHash`. Fixed via DTO projection + `@JsonIgnore` defense-in-depth. Regression-guarded by test.
**Rating: N/A (confirmed-fixed).** Flagged as a **pattern risk** worth a dedicated audit: whether any of the other ~22 REST controllers serialize a `User` entity directly (or an eagerly-fetched association containing one) was not exhaustively checked in this pass.

### S-8. CEO/MM native authority — no bypass found for hands-on execution (confirmed for Publishing, strongly implied for Shoot/Edit)
**Behavior:** Documented and corroborated by code: native authority covers management actions but is explicitly insufficient for hands-on execution — `requireActiveAssignee` is unconditional even for CEO/MM. Workflow-state ordering is also not bypassable by the CEO (tested).
**Rating: N/A (confirmed-safe for Publishing; same code pattern used in Shoot/Edit).** Directly answers F4: no, native authority does not let CEO/MM execute stage work without holding the actual assignment.

### S-9. DB-level defense-in-depth on append-only tables (confirmed-safe)
**Behavior:** Hard DELETE/TRUNCATE against append-only tables (Hold records, stage comments) rejected at the database trigger/rule level, not only by application code.
**Rating: N/A (confirmed-safe).** Reduces blast radius of a hypothetical SQL-injection or admin-tooling mistake against those specific tables; does not generalize to all tables.

### S-10. Correction-ledger actor attribution — not independently verified in the test layer
**Behavior:** `CorrectionLedgerFlowTest` proves append-only/immutable-original behavior but does not, as read, assert that the correcting user's identity is captured on the row. (The entity-level evidence gathered elsewhere in this audit — M3/N3 — **does** confirm `correctedBy`/mandatory-reason fields exist on `PublicationEvidenceCorrection` and `PerformanceMetricCorrection`.)
**Rating: N/A — reconciled.** Not a security risk; noted only because the test coverage for this fact is thinner than the entity-level guarantee.

### Additional cross-reference: latent Publishing-reassignment routing gap (from I1)
**Behavior:** `TaskStage` enum appears limited to `{SHOOTING, EDITING}` based on `AdminActionService.reassign`'s two-branch structure; if a `PUBLISHING` value exists and is reachable from the UI, it would fall into the Editing `else`-branch and be mishandled (checking `"Video Editor"` instead of `"Publisher"`).
**Rating: UNKNOWN / NOT DETERMINABLE without direct confirmation of `TaskStage.java`'s full value set and whether it's reachable from the Reassign UI** — flagged for direct engineering follow-up rather than rated, since it was not independently confirmed to be reachable.

### Summary count
- **Critical: 0**
- **High: 2** (S-2 initial Shoot assignment; S-3 initial Edit assignment — both: no assignee-eligibility check server-side)
- **Medium: 1** (S-4 initial Publisher assignment — no assignee-eligibility check, mitigated by CEO/MM-only assigner gate)
- **Low: 1** (S-1 — resolved, but still name-string-coupled rather than permission-driven)
- **Confirmed-safe / positive findings: 5** (S-5, S-6, S-7 fixed+guarded, S-8, S-9)
- **Unresolved/flagged for follow-up, not rated: 2** (S-10 reconciled as non-issue; the `TaskStage.PUBLISHING` routing question)

---

## 4. Final Capability Matrix

| Capability | Current Rule | Business Role Dependency | Permission Dependency | Assignment Dependency | Workflow State Dependency | Multi-Function Safe Today? | Change Likely Needed? |
|---|---|---|---|---|---|---|---|
| Shoot candidate eligibility (picker) | Hardcoded `roleName == "Camera Person"` + active | Yes (UI query only) | No | No | No | **No** | **Yes** — picker query |
| Shoot assignment (write) | Assigner needs PERM_04; assignee unchecked | No (backend) | Partial (assigner only) | No (assignee) | Yes (`PL`) | Yes, but accidentally (no real gate at all) | **Yes** — add assignee-eligibility check |
| Shoot execution | Active `ShootingAssignment` row only; no permission, no CEO/MM bypass | No | No | **Yes — sole gate** | Yes (`SA`/`SIP`) | **Yes — already the target model** | No |
| Shoot review contribution | `ShootingExecutionParticipant` snapshot at submit from active assignments | No | No | Yes | Yes (submit-time) | **Yes** | No |
| Shoot marks | Reviewer-selected subset of qualifying participants; `role_type` on the mark row | No (`roleType` hardcoded per calling service, not read from user's Business Role) | No | Yes (via participant chain) | Yes (Approve only) | **Yes** | No |
| Edit candidate eligibility (picker) | Mirrors Shoot | Yes (UI only) | No | No | No | **No** | **Yes** — picker query |
| Edit assignment (write) | Mirrors Shoot | No | Partial (assigner) | No (assignee) | Yes (`SAP`/`EA`) | Accidental only | **Yes** — add assignee-eligibility check |
| Edit execution | Mirrors Shoot | No | No | **Yes — sole gate** | Yes (`EA`/`ED`) | **Yes** | No |
| Edit review contribution | Mirrors Shoot | No | No | Yes | Yes | **Yes** | No |
| Edit marks | Mirrors Shoot | No | No | Yes | Yes | **Yes** | No |
| Publisher candidate eligibility (picker) | Hardcoded `roleName == "Publisher"` + active | Yes (UI only) | No | No | No | **No** | **Yes** — picker query |
| Publishing assignment (write) | Actor must be native CEO/MM; assignee unchecked | No | No (native authority, not permission) | No (assignee) | Yes (`RFP`) | Accidental only | Medium — assigner gate already strong; assignee check still missing |
| Publishing execution | PERM_08 (live-checked) + active `PublishingAssignment` + correct state | No | **Yes** | **Yes** | Yes (`RFP`→`PUBG`) | **Yes — the model for the other two stages** | No |
| My Work visibility | `AccessClass == EMPLOYEE` AND `participates_in_workflow == true` (interceptor) | Yes (via role's participation flag) | No | No (route-level; assignment not required to see an empty page) | No (route-level) | Partially — a permission holder with `participates_in_workflow=false` is completely blocked regardless of permission (B3) | **Yes** — decouple route reachability from the blanket Business-Role-level flag, or make it more granular |
| My Work task routing (dashboard flavor + item display) | Hard `businessRoleName` string branch in JSP | Yes (hard match) | No (underlying data is already multi-stage safe) | Yes (underlying data) | No | **No** — the concrete display gap for a genuinely multi-function employee | **Yes** — this is the single JSP change with the most user-visible impact |
| Team Workload attribution | `TeamWorkloadService` enumerates candidates by hardcoded Business Role name **before** joining real assignment rows | Yes (gate before assignment lookup) | Yes (once past the gate, PERM_14) | Yes (once past the gate) | No | **No** | **Yes** — likely the single highest-impact change for the stated KPI goal |
| Employee KPI attribution (general) | KPI-006/008/marks/`publishedBy` are already Employee+Stage; Team Workload's Assignee Load is the one Employee+BusinessRole-gated-then-Stage outlier | Mixed | Partial (view-gated by PERM_14/15) | Yes for most | No | Mostly **Yes**, with the one Team Workload exception | **Yes** for Team Workload specifically; No for the rest |

---

## 5. Security Risks (summary)

See full detail in Section S above. Headline: **0 Critical, 2 High (S-2, S-3), 1 Medium (S-4), 1 Low (S-1, resolved)**, plus 5 confirmed-safe/positive findings and 1 unresolved routing question flagged for direct engineering follow-up (`TaskStage.PUBLISHING` reachability via Reassign). The two High findings and the Medium finding are the same underlying pattern — **initial assignment (as opposed to reassignment) never validates the assignee** — repeated across all three stages, with Publishing's exposure reduced by its native-CEO/MM-only assigner gate.

---

## 6. Business-Role Coupling Findings

**What is currently Business-Role-name coupled** (the concrete list blocking `HR Manager + Shoot Execute Permission → eligible Shoot assignee` and `One employee → Shoot + Edit work` today):

1. All four assignee candidate-list pickers (Shoot, Edit, Publisher, Model) — hardcoded `BusinessRole.roleName` string-equality queries, duplicated across 5+ call sites with no shared constant (B4, D1–D4, P).
2. `AdminActionService.requireBusinessRole` — the one **backend-enforced** Business-Role check in the app, applied only to reassignment (I2, S-1).
3. `my-work.jsp`'s dashboard-flavor `<c:choose>` on `businessRoleName` — determines which stage(s) of an employee's real, already-multi-function-safe work actually get displayed (E2/E3).
4. `LandingMvcController`'s `/app/home` routing to My Work vs. My Shoots, keyed on `businessRoleName == "Model"` (E2).
5. `TeamWorkloadService`'s Assignee Load candidate enumeration — gates by role name **before** consulting real assignment data, the most consequential coupling for the KPI goal (K2, K4, K8).
6. `BusinessRole.participatesInWorkflow` → `isNonProductionEmployee` → `WorkflowParticipationInterceptor` — an all-or-nothing navigation gate keyed on the user's Business Role, independent of and non-communicating with the permission system (B2/B3, E1, H3).

**What is NOT Business-Role coupled** (already safe / permission-or-assignment-driven — U1's answer):

- `AuthorizationService` core (`requireAuthority`, `hasNativeAuthority`, grant resolution) — 100% Access-Class + Permission-Grant based, zero Business-Role references (C3).
- Shoot/Edit/Publishing **execution** (Start/Continue/Submit) — 100% active-assignment-row based (F1–F4).
- Review-contributor snapshotting (`ShootingExecutionParticipant`/`EditingExecutionParticipant`) and mark awarding (`PersonalMarkAttribution`) — 100% assignment/participant-identity based (G1–G4).
- KPI-006, KPI-008, legacy `teamWorkload()`, `ActualPublicationEvent.publishedBy`, `PublicationEvidenceCorrection`, `PerformanceMetricCorrection`, `CreativePerformanceScorecard` — all plain `user_id`-FK based, no Business-Role dependency found (K1–K5, M1–M3, N1–N3).
- The database schema itself, apart from the single mandatory `users.business_role_id` FK — every assignment/participant/mark/grant table is already Business-Role-agnostic (Q1/Q2).

---

## 7. Permission Coupling Findings

**Areas already using Permission as the source of truth (U3):**
- All management/administrative actions (Planning execution/review, Shoot/Edit assignment *by the assigner*, Shoot/Edit review decisions, Reschedule, Reassign, Cancel, Reopen, Performance updates, Team Workload/KPI view access, Audit view, Catalogue management) — gated by `requireAuthority` + the 17-permission catalogue (C1–C5).
- Publishing execution specifically — the one stage where PERM_08 gates both assignment-eligibility-adjacent access (indirectly, via native authority for the *assign* action) and execution itself, live-re-checked on every call (F3, S-5).
- Reviewer self-selection — any permission-holder can act as reviewer, no separate reviewer picker exists (P).

**Where Permission is notably absent from a place the questionnaire specifically asked about (C4's central finding):**
- There is **no permission that means "eligible to execute Shoot"** or **"eligible to execute Edit"** — those stages are execution-eligible purely by virtue of holding an active assignment row, with no permission check at all (by deliberate design, not an oversight — F1/F2/F4).
- There is **no permission check on the assignee** at initial-assignment time for any of the three stages (D1–D3, D6, S-2/S-3/S-4) — only the assigner is permission-checked.

---

## 8. KPI Attribution Findings

**Already Employee+Stage today, needing no change (K3–K5, M1–M3, N1–N3):**
- Shoot/Edit Assigned, Marks; Publishing Assigned, Completed (`publishedBy`); Evidence and Metric corrections; Performance scorecards. All keyed by plain `user_id` FK with a stage-identifying column/table, independent of `business_role_id`.

**Already Employee+Stage in principle but not implemented as a standalone query today (K3):**
- Shoot/Edit Completed, Delayed — computable via 2–3-table joins, but no dedicated per-employee query exists outside the Business-Role-gated `TeamWorkloadService` path.

**Not attributable to a specific contributor at all today, by design (K3, L2):**
- Rework — `ReviewCycle` links to content/cycle only, no FK to the participant/assignment layer; no attribution is created on Request Rework decisions.

**The one genuine Employee+BusinessRole-gated-then-Stage KPI surface (K2, K4, K8):**
- `TeamWorkloadService`'s Assignee Load panel (the actual Team → Workload dashboard) — enumerates candidates by hardcoded role name *before* consulting real assignment rows, meaning a multi-function employee whose Business Role doesn't match a stage's canonical name is invisible there for that stage, even though the identical employee is correctly counted by KPI-006/008/legacy `teamWorkload()`. This produces a **documented disagreement between two dashboards that both claim to show "who has active work."**

**Unimplemented but schema-supported metrics (L1, L3):**
- Per-stage First-Pass Approval rate; per-reviewer Approval Turnaround (KPI-021 currently computes only a global average, though the reviewer-grouped data already exists on `ReviewCycle`).

---

## 9. Schema Capability Assessment (U6)

Mapping the target concept model to current schema reality:

| Target Concept | Current Schema Support |
|---|---|
| `Access Class` → broad workspace | **Exists as-is** — 3-value enum on `BusinessRole`, resolved per-user via `User.resolvedAccessClass()` |
| `Business Role` → organizational designation | **Exists as-is** — free-text, admin-created, single mandatory FK per user |
| `Permission` → operational authority/capability | **Exists as-is** — fully individual-user-scoped `PermissionGrant`, GLOBAL/STAGE_RESTRICTED/ITEM_SPECIFIC scoping, time-bounded, live-revocable |
| `Assignment` → responsibility for a specific Content ID/stage | **Exists as-is** — `ShootingAssignment`/`EditingAssignment`/`PublishingAssignment`, each a plain `user_id` FK with an active flag, no Business-Role coupling |
| `Workflow State` → whether an action is valid now | **Exists as-is** — `WorkflowStatus` enum drives every service-layer gate |
| `Contribution` → stage-specific marks/KPI attribution | **Exists as-is** — `PersonalMarkAttribution.role_type`, `ActualPublicationEvent.publishedBy`, execution-participant snapshots, all independent of the recipient's current Business Role |

**Answer to U6:** The existing data model can support the target concept **without migration**. The single hard schema constraint is the mandatory one-Business-Role-per-user FK (`users.business_role_id NOT NULL`), which the target model does not actually require changing — "Business Role → organizational designation" is consistent with remaining single-valued; multi-function capability is already expressed through **Assignment** and **Permission**, both of which are already multi-valued and Business-Role-independent per user.

**What would actually change, and it is entirely application code, not schema (U5 — what could break if only the dropdown changes):**
Simply widening a candidate-list query (e.g., the Shoot Assignee picker) without also changing:
- the corresponding **write-path validation** (still absent for initial assignment — S-2/S-3/S-4), which is good news (nothing to "break" there, since there's no check to conflict with) but means the picker change alone still leaves the backend exactly as unvalidated as today, just with an intentionally wider offering instead of an accidentally-bypassable one;
- `TeamWorkloadService`'s independent Business-Role-name gate (K2/K4/K8), which would still hide the newly-eligible assignee from the Team Workload dashboard even after they're correctly assigned and executing — a downstream KPI blind spot;
- `my-work.jsp`'s hardcoded role-flavor routing (E2/E3), which would still fail to display the newly-possible multi-stage work correctly for anyone whose Business Role happens to equal one of the three canonical names;
- `AdminActionService.requireBusinessRole` (I2), which would still **reject** a reassignment of the very same newly-eligible person the initial-assignment dropdown now offers — creating a new, sharper inconsistency where a person can be *initially* assigned but never *reassigned* to the same role.

Each of these four locations independently encodes "who counts as eligible for stage X," and none of them currently share a common source of truth (O2). A dropdown-only fix would leave at least three of these four out of sync.

---

## 10. Open Questions / Unknowns

Collected from all sections above (each marked **UNKNOWN / NOT DETERMINABLE** in place, restated here for visibility):

1. **PERM_13_FOLDER_LINK_MANAGE** — defined and grantable, but no enforcement or UI consumption was found anywhere in the codebase (C1). Is this dead/planned-but-unbuilt, or does folder-link editing route through logic not located in this audit?
2. **Business Role rename capability** — whether `admin-business-roles.jsp`/`BusinessRoleAdminService` exposes a rename operation for an existing role's name post-creation was not confirmed (O2). This matters directly: if roles can be renamed, every one of the 5+ hardcoded-string call sites would silently break in lockstep.
3. **`TaskStage` enum's full value set and whether `PUBLISHING` is reachable from the Reassign UI** — if it is, `AdminActionService.reassign`'s two-branch structure would mis-route a Publishing reassignment into the Editing validation branch (I1, S-summary). Not independently confirmed either way.
4. **`EditingAssignment.lead`** — assumed structurally symmetric with `ShootingAssignment.lead` based on the codebase's own "mirrors Shoot" commenting convention, but not independently read line-by-line (K5, notes).
5. **`GateType` enum's complete value list** — only the literals actually referenced in `KpiService` (`IDEA_REVIEW`, `PLANNING_REVIEW`, `SHOOT_REVIEW`, `EDIT_REVIEW`) were confirmed; the enum file itself was not opened, so additional gate types may exist (L1, notes).
6. **`PerformanceService.java` full logic** — this file is currently modified on disk and was not read line-by-line in the KPI-audit pass; N1/N2's "no Business-Role dependency" conclusion is based on domain-layer/package-wide grep evidence, not a direct read of the service's query logic, and should be spot-checked (N2).
7. **Whether `WorkflowParticipationInterceptor`'s coverage extends to REST-only endpoints outside `/app/**`** — not independently enumerated; if any exist, they may lack the equivalent participation guard (H3).
8. **Whether other REST controllers besides the historically-fixed `AuditRestController` serialize a `User` entity or an eagerly-fetched association containing one** — only that one historical bug and its fix were confirmed; the other ~21 REST controllers were not exhaustively checked for the same pattern (S-7).
9. **Whether `WorkflowVariantsE2ETest.java`'s uncommitted working-tree changes add, remove, or alter any assertions relative to the last commit** — this audit was instructed not to run `git diff`, so the delta is unknown; all Section R/S findings sourced from this file reflect the current working tree only (R1).
10. **A longer human-readable description per `OperationalPermission` code**, if one exists outside this repository (e.g. in an external API specification document referenced by code comments as "API_Specification.md") — not verifiable from the codebase alone (C1).

---

## 11. Recommended Topics Requiring Stakeholder Decision

These are decision points, not proposed implementations — per the audit's instructions, no redesign is proposed here.

1. **Whether "eligible to be assigned + execute" should become an explicit permission concept for Shoot and Edit**, matching the Publishing model (PERM_08 + assignment), or whether the current "assignment membership alone is sufficient for execution" pattern should be preserved and only the *assignment-creation* gate should become permission-aware. These are two different design directions with different implications for whether a permission grant alone (with no assignment yet) should mean anything operationally.
2. **Whether the `participates_in_workflow` navigation gate should be relaxed or made more granular** for a user who holds a specific delegated permission but whose Business Role is marked non-participating (the HR-Manager-with-Shoot-permission scenario) — today this combination is authorized but practically unreachable through the UI (B3).
3. **Whether initial Shoot/Edit/Publisher assignment should gain a server-side assignee-eligibility check** (Sections D5/D6, S-2/S-3/S-4) — and if so, whether that check should be Business-Role-based (matching Reassignment's current behavior, I2) or permission-based (matching the questionnaire's stated goal) — these two choices would produce different behavior for the exact HR-Manager scenario the questionnaire opens with.
4. **Whether `TeamWorkloadService`'s Assignee Load panel should be changed from Business-Role-name candidate enumeration to assignment-based enumeration** (matching KPI-006/008's existing pattern) — this is flagged as the single highest-impact change for the stated KPI goal, since it is the one component where a multi-function employee is invisible today despite the underlying data already supporting them.
5. **Whether `my-work.jsp`'s three role-flavored dashboards should be replaced or supplemented with a stage-aware view** that shows every active assignment a user holds regardless of Business Role name match (Sections E2/E3) — today only users whose Business Role does *not* match one of the three canonical names get a combined multi-stage view; canonical-role users are locked to a single stage's display even if they hold work elsewhere.
6. **Whether `AdminActionService.requireBusinessRole` (reassignment) should be changed in lockstep with any change to initial-assignment eligibility** (Section I2) — leaving one Business-Role-gated and the other permission-gated would create the inverse inconsistency: a person assignable initially but not reassignable to the same role, or vice versa.
7. **Whether Publishing should gain an explicit reassignment path** (Section I1) distinct from remove+reassign, and clarification of whether `TaskStage.PUBLISHING` is or should ever be reachable through the generic Reassign endpoint (flagged as a latent routing risk, not confirmed exploitable).
8. **Whether rework should be attributed to specific contributors** going forward (Sections K3/L2) — today this is a deliberate design gap (no attribution on Request Rework), not an oversight; if per-contributor rework KPIs are wanted, a new link from `ReviewCycle` to the execution-participant tables would be a schema addition, not something derivable from current data.
9. **Whether Business Role names should be centralized into a single shared constant/lookup** (Section O2) rather than the current 5+ independently-hardcoded string literals — relevant regardless of the permission-driven-eligibility decision, since Business Role will likely remain in use for display/labeling/reporting-filter purposes even if it stops being an eligibility gate.
10. **Whether PERM_13_FOLDER_LINK_MANAGE is intentionally unused** and should be removed from the catalogue, or whether folder-link management enforcement needs to be built (Section C1) — a confirmation item, not a redesign decision.
