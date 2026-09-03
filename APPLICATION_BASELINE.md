# APPLICATION_BASELINE.md

Read-only technical baseline of the KCPC Bandhani marketing-content-production application, as
implemented in the current working tree (branch `dev`, includes substantial **uncommitted**
work-in-progress alongside committed history). Produced by direct code inspection; no code was
modified to produce this document. Where a claim could not be directly verified in the time
available, it is marked **NOT CONFIRMED FROM CODE**.

---

## 1. APPLICATION OVERVIEW

**Purpose / business objective**: An internal marketing-content production-pipeline system for
KCPC Bandhani. It tracks a piece of content from Idea submission through Review, Planning,
Shoot, Edit, Publishing, and Performance tracking to Completion, with role/permission-gated
actions at every stage, full audit history, and Google Drive folder provisioning per Content ID.

**Technology stack** (from `pom.xml`, parent `spring-boot-starter-parent` 3.3.5):
- Backend: Java 21, Spring Boot 3.3.5 (`spring-boot-starter-web`, `-security`, `-data-jpa`,
  `-validation`, `-actuator`).
- Frontend: server-rendered JSP + JSTL (`tomcat-embed-jasper`, `jakarta.servlet.jsp.jstl-api`
  3.0.0 / `jakarta.servlet.jsp.jstl` 3.0.1), vanilla JavaScript (no frontend framework/bundler),
  hand-written CSS (`src/main/resources/static/css/app.css`).
- Build: Maven (`pom.xml`), packaged as an executable WAR (`kcpc-mkt-mvp.war`) via
  `spring-boot-maven-plugin`.
- Database: PostgreSQL (`postgresql` JDBC driver).
- Migrations: Flyway (`flyway-core`, `flyway-database-postgresql`), `V*.sql` files under
  `src/main/resources/db/migration`, `hibernate.ddl-auto: validate` (schema is Flyway-owned;
  Hibernate never auto-generates DDL).
- Auth: Spring Security + JWT (`jjwt-api/-impl/-jackson`), cookie-carried token (not
  `Authorization` header), stateless (no `HttpSession` used for auth).
- Docs: springdoc-openapi (`/api/v1/openapi`, Swagger UI at `/api/v1/docs`).
- Other libraries: Apache POI (`poi-ooxml`) and `commons-csv` (export/import), `commons-io`,
  Google API client + `google-api-services-drive` + `google-auth-library-oauth2-http` (Drive
  integration), Lombok (compile-time only).
- Tests: `spring-boot-starter-test` + `spring-security-test` (JUnit 5/MockMvc-style, see §24), plus
  a small dependency-free Node.js `vm`-based harness for a few frontend JS files
  (`src/test/js/*.test.js`).

**Authentication mechanism**: Custom JWT issued on login (`AuthenticationApplicationService`,
`JwtService`), carried in an HttpOnly cookie (`app.security.jwt.cookie-name = KCPC_AT`),
validated per-request by `JwtAuthenticationFilter` (`src/main/java/com/kcpc/mkt/security/`).
Every valid JWT is also registered in the `user_sessions` table (SHA-256(jti) only — the raw JWT
is never persisted; see `TokenRegistryService`), enabling server-side revocation. No
`HttpSession`/`SessionManagementFilter` is used at all (see `SecurityConfig` — deliberately
disabled, not merely set to `STATELESS`, to avoid `CsrfAuthenticationStrategy` rotating the CSRF
cookie on every request).

**Authorization mechanism**: See §5. Three `AccessClass` values (`CEO_OWNER`,
`MARKETING_MANAGER`, `EMPLOYEE`) plus a governed `OperationalPermission` catalogue (20 numbered
permissions) grantable per-Employee with three scope types (`GLOBAL`/`STAGE_RESTRICTED`/
`ITEM_SPECIFIC`). `AuthorizationService` is the single authorization authority; execution
eligibility (who may actually be assigned/execute Shoot/Edit/Publishing work) is a distinct,
narrower rule in `OperationalEligibilityService` that never accepts CEO/MM native authority as a
substitute for an explicit permission grant.

**Deployment architecture**: Docker Compose, 3 containers per environment (Postgres + Spring Boot
app + Nginx reverse proxy), each of dev/prod fully isolated (separate compose project names,
container names, network, DB, `.env`). See §23.

**Environment configuration**: `src/main/resources/application.yml`, 4 Spring profiles (base +
`dev`/`test`/`docker`) — see §11/§17/§23 for details. All secrets are environment-variable
overridable; committed defaults are dev-only placeholders explicitly called out as such in
comments.

**External integrations**: Google Drive (optional, disabled by default — `app.drive.enabled` /
`DRIVE_ENABLED`), used for per-Content-ID folder provisioning only (no other Google integration).
No other third-party service integration was found in the codebase (no email/SMS provider — see
`V27__password_reset_tokens.sql`'s comment: "This app has no email/SMS delivery infrastructure").

**File/storage systems**: No local file storage found for content assets — Drive links are the
only asset-location mechanism (`ContentPlan.folderLink` / `ContentDriveProvisioning`); exports
(`ExportService`, `MultiFormatExportService`) generate files on demand (POI/commons-csv), not
persisted to disk long-term (**exact export delivery mechanism — streamed HTTP response vs.
temp-file — NOT CONFIRMED FROM CODE**, not read in this pass).

**Important URLs/routes**: see §6 and §19 for the full map. Highlights: `/login`, `/app/home`
(role-dispatch), `/app/my-work`, `/app/my-shoots`, `/app/my-performance`, `/app/reviews`,
`/app/pipeline`, `/app/deliverables/{id}`, `/app/ideas`, `/app/admin/**`, `/api/v1/**` (REST,
JSON).

---

## 2. PROJECT STRUCTURE

Backend is organized as a package-by-feature (not layer-by-layer-at-top) structure under
`src/main/java/com/kcpc/mkt/`, each feature package internally split into `domain/`, `dto/`,
`repository/`, `service/` (controllers live centrally under `web/`):

| Package | Responsibility | Key classes |
|---|---|---|
| `idea/` | Idea submission, Idea Review + Planning approval (single compound command) | `IdeaService`, `Idea`, `PlanningApprovalRequest`, `PlanningStage` |
| `identity/` | Users, Business Roles, Permission grants, authorization, execution eligibility | `AuthorizationService`, `OperationalEligibilityService`, `User`, `BusinessRole`, `OperationalPermission`, `PermissionGrant`, `UserAdminService`, `BusinessRoleAdminService`, `PermissionGrantAdminService` |
| `planning/` | Content Plan (the central "deliverable" record), Content ID allocation, Planned Outputs | `ContentPlan`, `ContentIdAllocationService`, `PlanningService`, `PlannedOutput` |
| `production/` | Shoot & Edit execution/assignment/review | `ShootingService`, `EditingService`, `ShootingAssignment`, `EditingAssignment` |
| `publishing/` | Publisher assignment, Actual Publication Events, Publication Target N/A | `PublishingService`, `PublishingAssignment`, `ActualPublicationEvent` |
| `performance/` | Performance Obligations, Creative Performance Scorecards (Meta metrics) | `PerformanceService`, `PerformanceEligibilityService`, `CreativePerformanceScorecard` |
| `marks/` | Predefined Cameraperson/Editor/Model marks, per-person mark attribution, Mark Catalogue | `PredefinedRoleMarks`, `PersonalMarkAttribution`, `MarkCatalogueService`, `MarkCatalogueEntry` |
| `masterdata/` | Platforms, Company Channels, Publication Targets, Categories | `MasterCatalogueService`, `CategoryService`, `Platform`, `CompanyChannel`, `PublicationTarget`, `Category` |
| `workflow/` | Central workflow state machine, admin actions (reschedule/reassign/cancel/hold/reopen), reachability | `WorkflowTransitionService`, `WorkflowInstance`, `WorkflowStatus`, `WorkflowTransitionHistory`, `WorkspaceAccessService`, `HoldService`, `AdminActionService` |
| `discussion/` | Per-stage discussion threads | `StageComment`, `StageCommentService` |
| `drive/` | Google Drive folder provisioning | `DriveProvisioningService`, `GoogleDriveFolderClient`, `DisabledDriveFolderClient` |
| `security/` | JWT auth, cookie handling, password reset | `SecurityConfig`, `JwtAuthenticationFilter`, `JwtService`, `AuthCookieService`, `PasswordResetService` |
| `reporting/` | Pipeline/KPI/team-workload/export dashboards | `PipelineDashboardService`, `KpiDashboardService`, `KpiService`, `TeamWorkloadService`, `ExportService` |
| `audit/` | System-wide audit log | `AuditService`, `SystemAuditLog` |
| `web/mvc/` | All server-rendered JSP page controllers (one per major area, see §19) | `LandingMvcController`, `DeliverableMvcController`, `ReviewsMvcController`, `IdeaMvcController`, `AdminMvcController`, `AuthMvcController`, `ReportingMvcController` |
| `web/rest/` | All `/api/v1/**` JSON controllers | `IdeaRestController`, `ContentPlanRestController`, `ShootingRestController`, `EditingRestController`, `PublishingRestController`, `UserAdminRestController`, etc. |
| `common/` | Cross-cutting: `BaseEntity`, `DomainException`/`ErrorCode`, `ApiErrorResponse`, small display-formatting utilities | |

**Frontend** — no build step; static assets served directly:
- `src/main/resources/static/js/` — ~27 vanilla-JS files, one per interactive feature (tab
  switching, modals, AJAX partial-fragment swaps). No shared framework; each file is a small
  self-invoking closure that queries the DOM directly (see §20).
- `src/main/resources/static/css/app.css` — single stylesheet, no preprocessor.
- `src/main/webapp/WEB-INF/views/` — JSP pages (one per route/screen) plus
  `WEB-INF/views/fragments/*.jspf` — shared, `<jsp:include>`d partials (nav, reviews tabs,
  reports tabs, admin tabs, idea submit form, idea details modal, stage comments, etc.).

**Database migrations** — `src/main/resources/db/migration/V1..V37` (see §17); heavily commented
with `ERD-CON-*`/`ERD-TBL-*`/`ENG-*`/`BRS-REQ-*` references back to a (not-in-repo) frozen spec —
these comments are treated as authoritative provenance for *why* a constraint exists.

**Tests** — `src/test/java/com/kcpc/mkt/` (83 files, flat — one class per feature/regression, not
mirroring the main package tree) + `src/test/js/` (3 files, Node `vm` harness). See §24.

**Deployment/Docker files** — root `Dockerfile`, root `docker-compose.yml` (a third,
generic/local-prod-like stack distinct from `deploy/dev` and `deploy/prod`), `deploy/dev/`,
`deploy/prod/`, `deploy/scripts/` (backup/restore/deploy/rollback shell scripts). See §23.

---

## 3. BUSINESS WORKFLOW

**Central state machine**: `WorkflowStatus` (`src/main/java/com/kcpc/mkt/workflow/domain/WorkflowStatus.java`)
— 19 governed "workflow concepts," each with a `conceptNumber`, `statusName`, and
`Classification` (ACTIVE / DORMANT / TERMINAL / CLOSED / SUPPLEMENTARY_FLAG). Every transition is
recorded by `WorkflowTransitionService.transition(...)` (the **single sanctioned entry point** —
`src/main/java/com/kcpc/mkt/workflow/service/WorkflowTransitionService.java`), which atomically
updates `WorkflowInstance.currentStatusCode` and appends an immutable
`WorkflowTransitionHistory` row in the same transaction (DB triggers block UPDATE/DELETE/TRUNCATE
on that table — `V4__workflow_core.sql`, `V12__correction_ledgers.sql`).

Planning is **not** a separate workflow stage — an approved Idea's Content Plan is created already
fully planned and transitions straight from `PA` to `SA`/`EA`/`RFP` (see `IdeaService#approve`,
class javadoc: "PL/PLRV/PLAP no longer exist as WorkflowStatus values at all").

### State-transition table

| From | To | Trigger command | Fired by | Notes |
|---|---|---|---|---|
| — | `IS` (Idea Submitted) | `SUBMIT_IDEA` (instance created) | `IdeaService.submit` | Any access class may submit |
| `IS` | `PA` (Pending Approval) | `SUBMIT_IDEA` | `IdeaService.submit` | System-derived, same call |
| `PA` | `SA`/`EA`/`RFP` | `APPROVE_IDEA` | `IdeaService.decide`→`approve` | Target depends on selected Stages (see below) |
| `PA` | `RJ` (Rejected, terminal) | `REJECT_IDEA` | `IdeaService.decide`→`reject` | Reason mandatory |
| `PA` | `RET` (Retained, dormant) | `RETAIN_IDEA` | `IdeaService.decide`→`retain` | Reason optional |
| `RET` | `PA` | `REOPEN_IDEA` | `IdeaService.reopen` | PERM_01 |
| `SA` | `SIP` (Shoot In Progress) | `START_SHOOTING` | `ShootingService.startShooting` | Active-assignee only |
| `SIP` | `SRV` (Shoot Review) | `SUBMIT_SHOOT_REVIEW` | `ShootingService.submitShootReview` | Requires Drive Link set |
| `SRV` | `SIP` | `REQUEST_REWORK_SHOOT` | `ShootingService.decideShootReview` (approve=false) | Reason mandatory |
| `SRV` | `SAP`→`EA` (same tx) | `APPROVE_SHOOT` then Editor assignment | `ShootingService.decideShootReview` (approve=true) | Attributes Cameraperson marks; folds in Editor Team assignment (`EditingService.assignEditTeam`) |
| `SA`/`SIP`/`SRV` | `EA` | `SKIP_SHOOT_STAGE` | `ShootingService.skipShootStage` | PERM_20; no marks attributed; still requires Editor Team |
| `EA` | `ED` (Editing) | `START_EDITING` | `EditingService.startEditing` | **NOT FULLY READ — assumed symmetric with Shoot; verify before relying on exact status name** |
| `ED` | `ERV` (Edit Review) | `SUBMIT_EDIT_REVIEW` (assumed name) | `EditingService.submitEditReview` | Symmetric with Shoot |
| `ERV` | `ED` | Rework (assumed `REQUEST_REWORK_EDIT`) | `EditingService.decideEditReview` (approve=false) | |
| `ERV` | `EAP`→`RFP` (same tx) | `APPROVE_EDIT` (assumed) | `EditingService.decideEditReview` (approve=true) | Attributes Editor marks; folds in Publisher Team assignment |
| `EA`/`ED`/`ERV` | `RFP` | `SKIP_EDIT_STAGE` | `EditingService.skipEditStage` | PERM_20 |
| `RFP` | `PUBG` (Publishing) | `START_PUBLISHING` (assumed) | `PublishingService.startPublishing` | No review gate on Publishing at all |
| `PUBG` | `PP` (Performance Pending) | scope-resolution auto-advance | `PublishingService` (`isScopeResolved`/`PUBLICATION_SCOPE_RESOLVED`) | Fires once every Publication Target is resolved (published or N/A) |
| `PP` | `PFUP`/`COMP` | Performance draft/submit | `PerformanceService` | **Exact PP→PFUP→COMP transition triggers NOT FULLY READ — verify in `PerformanceService`/`AdminActionService` before relying on this row** |
| any active | `CAN` (Cancelled, terminal) | admin Cancel | `AdminActionService` | PERM_12, mandatory reason |
| `PUBG`/`PP`/`COMP` | reopened | Reopen Publishing / Reopen Performance | `AdminActionService` | `ReopenPurpose.PUBLISHING_REOPEN` / `METRIC_CORRECTION_REOPEN` |

**DLY (Delayed)** is explicitly a **supplementary flag**, never a primary status
(`WorkflowStatus.isPrimaryStatus()` excludes it; `workflow_instances.current_status_code` has a DB
CHECK forbidding it — `ck_workflow_instances_status_not_delayed`). Delay is computed display-side
by comparing a planned date to today, never stored as the row's actual status.

### Valid Stages combinations (ENG-091)

Exactly 3, enforced in `IdeaService#approve` (lines ~267–284):

| Stages selected | `shootStarts` | `editStarts` | `publishingStarts` | Target status | Team required |
|---|---|---|---|---|---|
| `{SHOOT, EDIT, PUBLISHING}` | true | — | — | `SA` | ≥1 Cameraperson + Shoot Lead |
| `{EDIT, PUBLISHING}` (Direct Edit) | — | true | — | `EA` | ≥1 Editor + Editor Lead |
| `{PUBLISHING}` (Direct Publishing) | — | — | true | `RFP` | ≥1 Publisher |

`{SHOOT, PUBLISHING}` (Shoot without Edit) is explicitly **rejected** — `validCombo` requires
`shootStarts` to always come with `stageCount == 3` (i.e., Edit must also be present). Whichever
stage(s) are skipped get a plain nullable note (`ContentPlan.shootStageSkipReason` /
`editStageSkipReason`, `V35__stage_skip_reason.sql`) — never a fake `WorkflowTransitionHistory`
row, since that stage's status was never really entered.

### Reject / Retain / Rework / Reopen

- **Idea Reject** (`RJ`, terminal): mandatory reason, `PERM_01_IDEA_REVIEW`.
- **Idea Retain** (`RET`, dormant): reason optional; **Reopen** (back to `PA`) is `PERM_01`-gated,
  only valid from `RET`.
- **Shoot/Edit "Request Rework"**: sends the plan back to `SIP`/`ED` respectively; mandatory
  reason (`ERD-CON-059`); attributes no marks.
- **Self-review conflict**: `AuthorizationService.requireNoSelfReviewConflict` — an Employee
  acting under a *delegated* grant may not decide on work they personally submitted/prepared/
  participated in (checked in `IdeaService.decide`, `ShootingService.decideShootReview` via
  `ShootingExecutionParticipant` lookup, presumably `EditingService.decideEditReview`
  symmetrically — **not directly read, inferred from the shared pattern**). CEO/MM native
  authority is exempt from this barrier.

### Completion / Cancellation / Hold

- **Cancel**: `AdminActionService`, PERM_12, mandatory reason, any pre-completion status →`CAN`.
- **Hold/Resume**: `WorkHoldRecord` — NOT a `WorkflowStatus` transition at all (deliberately kept
  out of `WorkflowTransitionHistory`); at most one *open* hold per workflow instance
  (`ck_work_hold_records_status IN ('SIP','ED','PUBG')`, unique partial index), append-only once
  resumed. `HoldService.requireNoOpenHold(...)` is called from every execution-submit path
  (`submitShootReview`, `skipShootStage`, etc.) to block progress while on hold.

---

## 4. ROLES

Two distinct role concepts, deliberately never conflated (`BusinessRole` javadoc: "Never itself
grants an Operational Permission — ERD-CON-063"):

- **`AccessClass`** (3 values, the real authorization boundary): `CEO_OWNER`,
  `MARKETING_MANAGER`, `EMPLOYEE`.
- **`BusinessRole`** (expandable catalogue, 17 seeded rows — `V1__reference_data.sql`): the
  organizational designation shown in the UI, each mapped to exactly one `AccessClass`. Seeded
  roles: CEO (CEO_OWNER), Marketing Manager (MARKETING_MANAGER), and 15 EMPLOYEE-class roles: HR
  Manager, Camera Person, Video Editor, Marketing Coordinator, CEO's Executive Assistant,
  Publisher, Model, Senior Manager, SEO Executive, SEO Intern, Marketing Intern, Sales Manager,
  CRM Manager, Customer Support Executive, Marketing Data Operator.

**Every code path that matters is documented as authorizing by `AccessClass` + `OperationalPermission`
grant + real assignment record — never by Business Role name** (this is asserted repeatedly in
comments across `AuthorizationService`, `OperationalEligibilityService`, `DeliverableMvcController`,
e.g.: *"Deliberately NOT gated by Business Role name ... the real gate is permission + assignment"*).
The only 2 **verified** exceptions where Business Role name is actually checked in code:

1. **Model landing page**: `LandingMvcController.home()` — `if ("Model".equals(businessRoleName))
   redirect:/app/my-shoots` (else `/app/my-work`). Purely a *default landing page* choice, not an
   access gate — a Model can still reach `/app/my-work` if they separately hold execution
   permission/assignment (`nav.jsp`'s `employeeHasMyWorkExecutionAccess` flag).
2. **`DeliverableMvcController.view()`'s Model hard-deny**: `if ("Model".equals(businessRoleName)
   && shootTaskCompleted)` — redirects a Model whose personal Shoot task is done back to
   `/app/my-shoots`, unless they hold a *separate, still-open* Edit/Publish execution
   task+permission on the same plan (explicitly checked, not assumed).

**`BusinessRole.participatesInWorkflow`** (`V21__business_role_workflow_participation.sql`,
default `TRUE`): a CEO-settable flag, independent of permission grants, that restricts a
non-participating EMPLOYEE to My Ideas + Submit Idea only (see
`AuthorizationService.isNonProductionEmployee`, §6).

### Per-role responsibilities (traced through code, not inferred from names)

| Role (typical Business Role) | Reaches these modules via... | Executes | Reviews |
|---|---|---|---|
| **CEO** (CEO_OWNER) | Native authority — every `/app/**` route, no permission grant needed | Everything (native authority bypasses `requireAuthority`'s grant lookup entirely) | Everything |
| **Marketing Manager** (MARKETING_MANAGER) | Same native authority as CEO for all *operational* actions; **cannot** reach Users/Business Roles/Permissions admin (CEO_OWNER-only, see `AdminMvcController.isCeo`) | Everything operational | Everything operational |
| **Cameraperson** | PERM_18_SHOOT_EXECUTION grant + active `ShootingAssignment` | Shoot task (Start/Submit Review) | — (unless separately granted PERM_05) |
| **Video Editor** | PERM_19_EDIT_EXECUTION grant + active `EditingAssignment` | Edit task | — (unless PERM_07) |
| **Publisher** | PERM_08_PUBLISHING_EXECUTION grant + active `PublishingAssignment` | Publishing task (record events, Target N/A) | — |
| **Model** | Linked `ContentPlanTalentEntry` | Nothing to "execute" (no assignment record) — earns a Model Mark automatically at Idea approval time; reachability to My Shoots comes purely from talent linkage | — |
| Any EMPLOYEE holding PERM_01/05/07 | Reviews module (Idea/Shoot/Edit Review tabs, per which permission held) | — | The specific gate(s) their grant covers |
| Any EMPLOYEE holding PERM_04/06/11 | My Work → Assignment Management tier | Assign/reassign, not execute | — |
| Any EMPLOYEE holding PERM_14/15/16/17 | Team Workload / KPI Reports / Audit-Logs / Catalogue respectively | — | — |

**Model support in execution routing** is explicit and permission-driven, not role-hardcoded — a
comment in `DeliverableMvcController` states: *"a Model granted PERM_18_SHOOT_EXECUTION gets
exactly the same access a Camera Person does, and vice versa."*

---

## 5. PERMISSIONS & AUTHORIZATION

### Permission catalogue (`OperationalPermission` enum, `operational_permissions` table, 20 rows)

| # | Code | Meaning | Where checked (primary) | Typically granted to | Assignment also required? |
|---|---|---|---|---|---|
| 1 | PERM_01_IDEA_REVIEW | Approve/Reject/Retain an Idea | `IdeaService.decide` | CEO/MM native, or delegated reviewers | No (review authority, not execution) |
| 2 | PERM_02_PLANNING_EXECUTION | Manage an approved plan's Planning params | `DeliverableMvcController` (`canPlanningExecute`) | Planning managers | No |
| 4 | PERM_04_SHOOT_ASSIGNMENT | Assign initial Shoot team | `IdeaService.approve` (implicitly via Idea Review), `DeliverableMvcController` | Shoot assignment managers | No |
| 5 | PERM_05_SHOOT_REVIEW | Approve/Rework Shoot output, attribute Cameraperson marks | `ShootingService.decideShootReview` | Shoot reviewers | No — but self-review-conflict barred |
| 6 | PERM_06_EDIT_ASSIGNMENT | Assign Editor(s) | `EditingService.assignEditor`/`assignEditTeam` | Edit assignment managers | No |
| 7 | PERM_07_EDIT_REVIEW | Approve/Rework Edit output, attribute Editor marks | `EditingService.decideEditReview` | Edit reviewers | No — self-review barred |
| 8 | PERM_08_PUBLISHING_EXECUTION | Execute Publishing (record events, Target N/A) | `PublishingService` (multiple methods), `OperationalEligibilityService` | Publishers | **Yes** — active `PublishingAssignment` |
| 9 | PERM_09_PERFORMANCE_UPDATE | Enter/submit Performance Scorecard | `PerformanceService`, `DeliverableMvcController` (`canPerformanceUpdate`) | Performance trackers | No |
| 10 | PERM_10_RESCHEDULE | Modify approved production dates | `AdminActionService` | Admins | No |
| 11 | PERM_11_REASSIGN | Replace Shoot/Edit assignees | `AdminActionService` | Admins | No |
| 12 | PERM_12_CANCEL | Cancel a pre-completion deliverable | `AdminActionService` | Admins | No |
| 13 | PERM_13_FOLDER_LINK_MANAGE | Create/replace Drive folder link | `DeliverableMvcController` (`canManageDriveFolders`) | Drive admins | No |
| 14 | PERM_14_TEAM_WORKLOAD_VIEW | View team workload summaries | `WorkspaceAccessService.canReachTeamWorkload` | Managers | No |
| 15 | PERM_15_TEAM_KPI_VIEW | View team KPI dashboards | `WorkspaceAccessService.canReachKpiReports` | Managers | No |
| 16 | PERM_16_AUDIT_HISTORY_VIEW | View audit-history | `WorkspaceAccessService.canReachLogs` | Auditors | No |
| 17 | PERM_17_PLATFORM_CATALOGUE_MANAGE | Manage Platform/Channel/Target catalogue | `WorkspaceAccessService.canReachCatalogue`, `AdminMvcController` (`/catalogue`) | Delegated catalogue admins | No |
| 18 | PERM_18_SHOOT_EXECUTION | Eligible to be assigned + execute Shoot | `OperationalEligibilityService.isShootExecutionEligible` | Cameraperson (or any user granted it) | **Yes** — active `ShootingAssignment` |
| 19 | PERM_19_EDIT_EXECUTION | Eligible to be assigned + execute Edit | `OperationalEligibilityService.isEditExecutionEligible` | Video Editor (or any user granted it) | **Yes** — active `EditingAssignment` |
| 20 | PERM_20_SKIP_STAGE | Skip current Shoot/Edit stage | `ShootingService.skipShootStage`, `EditingService.skipEditStage` | CEO/MM (native) or delegated | No, but only while status is in the eligible window |

**PERM_04/06 vs PERM_18/19 — explicit, repeated distinction in comments**: PERM_04/06 authorize
the *manager who assigns* a Cameraperson/Editor; they never mean "eligible to execute." PERM_18/19
are the actual execution-eligibility permissions, mirroring what PERM_08 already meant for
Publishing (which has no separate "assignment" permission — Publisher assignment is native
CEO/MM-only, `ENG-044`).

### Scope types (`PermissionGrant.scopeType`, `ERD-CON-021`)

| Scope | Meaning | Covers |
|---|---|---|
| `GLOBAL` | Every stage/item, no restriction | Always matches |
| `STAGE_RESTRICTED` | Only the `LifecycleStage`(s) explicitly listed on the grant | `permission_grant_stage_scopes` rows |
| `ITEM_SPECIFIC` | Only the specific `WorkflowInstance`(s) explicitly listed | `permission_grant_item_scopes` rows |

### Permission-based vs Assignment-based vs Business-Role-based access

- **Permission-based** (module reachability): `WorkspaceAccessService` — one method per module,
  each an explicit `hasAnyActiveGrant(...)` check (never a blanket "any permission unlocks
  everything" rule).
- **Assignment-based** (execution reachability, on top of permission): `isShootActiveAssignee` /
  `isEditActiveAssignee` / `isPublishActiveAssignee` in `DeliverableMvcController`, and the
  equivalent `findByXAndActiveTrue(user)` repository queries in `LandingMvcController`/
  `WorkspaceAccessService`. A revoked permission does **not** hide an active assignment (task
  stays visible, execution is suppressed instead — see `ActiveWorkItem`'s `blocked` flag,
  §7).
- **Business-Role-based**: only the 2 exceptions in §4 (Model's default landing page, Model's
  post-completion hard-deny) — everything else is permission/assignment.

### UI vs backend authorization

Every `DeliverableMvcController` model flag (`canX`) is computed via the same `allowed(...)`
helper that calls `AuthorizationService.requireAuthority` and catches `DomainException` — i.e.
**the UI-visibility check and the backend-authorization check are literally the same call**,
never two independently-maintained rules. Every corresponding `@PostMapping` handler
independently re-invokes the real service-layer method (which itself calls
`authorizationService.requireAuthority`/`requireNativeAuthority` again) — confirmed pattern:
"Every POST handler here calls the SAME application/service layer as the equivalent REST
controller ... never an HTTP self-call, never duplicated business logic" (class javadoc,
`DeliverableMvcController`).

---

## 6. NAVIGATION & PAGE ACCESS

**Nav rendering**: `src/main/webapp/WEB-INF/views/fragments/nav.jsp`, driven by model attributes
populated centrally by `MvcNavigationAdvice` (a `@ControllerAdvice` over `com.kcpc.mkt.web.mvc`)
— `accessClass`, `businessRoleName`, `nonProductionEmployee`, `canSeeAdministration`, plus
per-module flags (`employeeCanSeeMyWork`, `employeeCanSeeReviews`, `employeeCanSeeTeam`,
`employeeCanSeeReports`, `employeeCanSeeMyPerformance`, `employeeHasMyWorkExecutionAccess`) —
**exact source of these last flags NOT FULLY TRACED (likely `MvcNavigationAdvice` or a similar
`@ModelAttribute`, not directly read in this pass — verify before relying on the precise
attribute name)**.

**Route guard**: `WorkflowParticipationInterceptor` (`HandlerInterceptor`, registered globally for
`/app/**` — registration point not directly confirmed, presumably `WebMvcConfig`). Deny-by-default
for a "non-production employee" (`AuthorizationService.isNonProductionEmployee`): always allows
`/app/change-password` and `/app/ideas/**`; every other module requires the matching
`WorkspaceAccessService.canReachX(user)` to return true; anything else → `redirect:/app/ideas`.
**Nav visibility and route reachability are guaranteed to agree** — both read from the exact same
`WorkspaceAccessService` methods, never re-derived.

**Login flow**: `AuthMvcController` — `GET/POST /login`, `POST /logout`,
`GET/POST /forgot-password`, `GET/POST /reset-password`, `GET/POST /app/change-password`. On
successful login the app redirects to `/app/home`, which role-dispatches (§ below). Login/
forgot-password/reset-password/`/css/**`/`/js/**` are the only unauthenticated routes in the app
filter chain (`SecurityConfig.appFilterChain`); everything else requires a valid JWT cookie or
redirects to `/login` (`MvcAuthEntryPoint`).

**Unauthorized behavior**: MVC chain → redirect to `/login` (unauthenticated) or (per
`WorkflowParticipationInterceptor`) `/app/ideas` (authenticated but module-unreachable); REST
chain (`/api/v1/**`) → JSON 401/403 via `RestAuthEntryPoint`.

**Default landing page** (`GET /app/home`, `LandingMvcController.home`):
- CEO_OWNER / MARKETING_MANAGER → `redirect:/app/pipeline`
- EMPLOYEE with Business Role name "Model" → `redirect:/app/my-shoots`
- Every other EMPLOYEE → `redirect:/app/my-work`

### Major route table

| URL | Controller#method | View / Response | Access requirement |
|---|---|---|---|
| `GET /app/my-work` | `LandingMvcController#myWork` | `my-work.jsp` | `WorkspaceAccessService.canReachMyWork` (interceptor) + always for participating employees |
| `GET /app/my-shoots` | `LandingMvcController#myShoots` | `my-shoots.jsp` | reachable to any user with a talent-linked plan |
| `GET /app/my-performance` | `LandingMvcController#myPerformance` | `my-performance.jsp` | `canReachMyPerformance` |
| `GET /app/reviews` | `ReviewsMvcController#index` (base `/app/reviews`) | `reviews.jsp` (AJAX partial `reviews-content.jsp`) | `canReachReviews` |
| `GET /app/pipeline` | `LandingMvcController#pipeline` (assumed method name) | `pipeline.jsp` | native CEO/MM only in practice (not explicitly gated by interceptor rule — always denied to non-production employees) |
| `GET /app/deliverables/{id}` | `DeliverableMvcController#view` | one of `deliverable-detail`/`shoot-task-detail`/`edit-task-detail`/`publish-task-detail` (server-branched, see §14/§15) | `canReachMyWork`, then per-tab permission+assignment inside the method |
| `GET/POST /app/ideas`, `/app/ideas/new`, `/app/ideas/{id}` | `IdeaMvcController` | `my-ideas.jsp`/`idea-queue.jsp`/`idea-detail.jsp` | always reachable |
| `GET /app/admin/**` | `AdminMvcController` | `admin-*.jsp` | CEO_OWNER-only for Users/Business Roles/Permissions/Marks/Categories; CEO/MM/PERM_17 for Catalogue |

---

## 7. MY WORK

**Route**: `GET /app/my-work` → `LandingMvcController.myWork` (lines 169–420) → `my-work.jsp`.

**Tabs**: All / Shoot / Edit / Publishing, each independently shown via `showShootTab`/
`showEditTab`/`showPublishTab` — **permission OR non-empty active/completed data**, i.e. a
PERM_18 holder with zero assignments still sees an (empty) Shoot tab, and someone with only
historical involvement still sees their history, even without a live permission grant.

**Active work determination**: an *active assignment record* exists
(`shootingAssignmentRepository.findByCamerapersonAndActiveTrue(user)`, etc.) **AND** the plan's
current `WorkflowStatus` is still inside that stage's own active window:
- Shoot: `{SA, SIP, SRV}` (`SHOOT_ACTIVE_WINDOW`)
- Edit: `{EA, ED, ERV}` (`EDIT_ACTIVE_WINDOW`)
- Publish: `{RFP, PUBG}` (`PUBLISH_ACTIVE_WINDOW`)

If the assignment is active but the status has moved past that window, the row goes to
**Completed Work** instead (`completedItem(...)`, derived from `WorkflowTransitionHistory` +
`ReviewCycle` — see §15).

**Execution permission vs assignment (the "blocked" flag)**: each Active Work row independently
computes `shootBlocked`/`editBlocked`/`publishBlocked` via
`operationalEligibilityService.isXExecutionEligible(user, workflowInstance)`. The assignment being
real is not sufficient — if the live PERM_18/19/08 grant has since been revoked, the row **stays
visible** (never hidden) but its action button is suppressed
(`ActiveWorkItem` constructed with `(onHold || xBlocked) ? null : actionLabel(...)`).

**Model behavior**: Models earn no `ShootingAssignment`/`EditingAssignment`/`PublishingAssignment`
of their own by default — their My Work reachability and rows come purely from
`ContentPlanTalentEntry` linkage (`WorkspaceAccessService.canReachMyPerformance` explicitly checks
this; `canReachMyWork` does **not** include talent entries, so a Model with no separate execution
permission does not reach My Work at all — only My Shoots + My Performance).

**Delegated permission behavior**: an "Assignment Management" tier (`showAssignmentManagementTier`)
is shown only to holders of PERM_04/06/11 — a separate, actionable queue
(`AssignmentManagementQueueService.shootQueue`/`editQueue`), distinct from execution.

**Task detail routing** (how the "right" per-stage screen is picked): see §14/§15 —
`DeliverableMvcController.view()`'s permission+assignment+status-window branches, reused
identically for both Active Work links (no `?tab=`) and Completed Work links (`?tab=shoot|edit|
publishing`).

**Decision basis summary for My Work**: role — **not** used for any gate (only for display/labels
like "Cameraperson"); permission — used everywhere (`hasAnyActiveGrant`, `isXExecutionEligible`);
assignment — used everywhere (`findByXAndActiveTrue`); stage — used to pick the active window;
task status — used to decide Active vs Completed bucket.

---

## 8. MY SHOOTS

**Route**: `GET /app/my-shoots` → `LandingMvcController.myShoots` (lines 1221–1274) →
`my-shoots.jsp`. Model's default landing page.

**Data source**: every `ContentPlanTalentEntry` linked to the current user
(`talentEntryRepository.findByTalentUser(user)`) — **not** any assignment table.

**Upcoming vs Past split**: `isShootTaskCompleted(history)` — true iff the plan's
`WorkflowTransitionHistory` contains an `APPROVE_SHOOT` or `SKIP_SHOOT_STAGE` trigger command.
Not-yet-completed → Upcoming (sorted by `plannedShootDate` ascending, nulls last); completed →
Past (sorted descending). This is the exact same static helper `DeliverableMvcController.view()`
reuses for its Model hard-deny gate — single source of truth, package-visible.

**What happens after Shoot Review approval**: the Model's task moves from Upcoming to Past
immediately (their "task" is purely presence-during-the-Shoot-phase — there is no Model-specific
review/completion action). If they were also granted a separate execution permission for a later
stage on the same plan (e.g. Edit), that access is preserved independently — the Model hard-deny
only redirects away from `/app/deliverables/{id}` when there is **no other active execution
task** on that plan.

**Shoot execution permission**: not required to *appear* on My Shoots at all — talent linkage
alone is sufficient. PERM_18 only matters if the Model is separately assigned as a
`ShootingAssignment.cameraperson` and wants the full Shoot Execution task-detail screen.

**Model/Talent participation storage**: `content_plan_talent_entries` (`talent_name` free text +
nullable `talent_user_id` FK, added `V20__talent_entry_user_link.sql` — nullable because the
frozen REST contract still accepts plain name strings with no linked user; the MVC picker always
supplies a real user).

---

## 9. MY PERFORMANCE

**Route**: `GET /app/my-performance` → `LandingMvcController.myPerformance` (lines 558–668) →
`my-performance.jsp` + `my-performance.js` (not read in this pass).

**Marks**: `PersonalMarkAttribution` (one row per recipient × review cycle, unique constraint
`(recipient_user_id, review_cycle_id)` — `ERD-CON-020`, blocking double-attribution).
`role_type IN ('CAMERAPERSON','EDITOR','MODEL')` (`V29__model_mark.sql`). **Publisher explicitly
has NO marks** — confirmed both at the DB CHECK level (role_type never includes `PUBLISHER`) and
in code (`buildPerformanceRows`'s Publishing loop passes `roleType = null`, and the class javadoc
states: *"Publishing has no mark-attribution gate and no RoleType of its own"*).

**Mark Catalogue** (`MarkCatalogueEntry`, `mark_catalogue_entries` table — `V36__mark_catalogue.sql`,
**this feature is already implemented in the current working tree**, not merely planned — backend
service `MarkCatalogueService`, admin screen `admin-marks.jsp`, controller routes
`GET/POST /app/admin/marks`, `POST /app/admin/marks/{id}`, `POST /app/admin/marks/{id}/delete`, all
CEO_OWNER-only per `AdminMvcController.isCeo`). Replaces the old hardcoded `[0, 0.5, 1.0, 2.0, 3.0]`
list; seeded with `[0.0, 0.1, 0.5, 1.0]` for CAMERAPERSON/EDITOR/MODEL. The corresponding old DB
CHECK constraints were dropped in the same migration (`ck_predefined_role_marks_*`,
`ck_predefined_mark_corrections_new_*`) — catalogue + `MarkCatalogueService.requireActiveValue`
(called from `IdeaService.approve`/`correctPredefinedMarks`) is now the sole source of truth.

**When marks are attributed**:
- **Model**: immediately at Idea Review approval time (`IdeaService.approve`, one
  `PersonalMarkAttribution` per selected talent user, using the `IDEA_REVIEW` cycle) — Models have
  no later review gate.
- **Cameraperson**: at Shoot Review Approve (`ShootingService.decideShootReview`), one attribution
  per *qualifying recipient* explicitly confirmed on that decision (not automatically every
  assignee — `qualifyingRecipientUserIds` param, must be recorded `ShootingExecutionParticipant`s).
- **Editor**: presumably symmetric at Edit Review Approve (`EditingService.decideEditReview`) —
  **not directly read in this pass, inferred from the shared architecture**.

**Total Marks / Average Mark**: `myPerformance` sums `mark`/`markMax` over the date-range-filtered,
*marked* rows only (`markedInRange = inRange.filter(r -> r.getMark() != null)`). `markMax` per row
is the **current highest active Mark Catalogue value for that role** (not the row's own mark)
— `markCatalogueEntryRepository.findByRoleTypeAndActiveTrueOrderByMarkValueAsc(roleType)`, last
element. Average = total / count, `HALF_UP` to 2 decimals.

**Completed task calculation**: `tasksCompletedCount = inRange.size()` — every row in `allRows`
already represents a *completed* involvement by construction (§10's completion definition); the
KPI is not independently re-derived.

**Delay calculation**: `delayDays = ChronoUnit.DAYS.between(plannedDate, completedDate)`;
`delayStatus`: `DELAYED` if > 0, `EARLY` if < 0, `ON_TIME` if 0. `Delayed Tasks` KPI counts rows
with `delayStatus == "DELAYED"`.

**Date filtering — Completed On, exclusively**: `withinDateRange(completedOn, fromDate, toDate)`
filters strictly on `Instant completedOn` (converted to the business-zone `LocalDate`) — never
Planned Date/Assigned Date/Created Date. Applied **backend-side** to both the KPI-card row set
(`inRange`) and the paginated Task Performance table (`filtered`), never client-side-only. A row
with `completedOn == null` matches only when *both* `fromDate` and `toDate` are also null — never
silently included/excluded by accident.

**"Completed On" semantics — per-employee, not per-content**: Shoot/Edit/Model share one
completion signal per plan (the one shared stage-review-approval event — there genuinely is no
finer-grained signal in this data model for those three). **Publisher is different**: each
Publisher's own `completedOn` is derived from their own `ActualPublicationEvent.publishedBy` +
`actualPublicationTimestamp` rows only (`toPerformanceRow`'s `publisherIdentity` branch) — never
the plan-wide "all targets published" transition a co-assigned Publisher would otherwise share. If
this specific Publisher personally recorded no event on this plan, `completedOn` stays `null` (no
fabricated date).

**Performance table**: `EmployeePerformanceRow` — Content ID, Stage, Role, Completed On, Delay,
Mark (per the current session's UI simplification — Average Mark card, On-Time Completion card,
and columns Content/Task/Planned Date/Result/Remarks/Action were removed from the JSP; **the
controller still computes them** — `marksSummary`, `delaySummary`, `averageMark`,
`onTimeRatePercent` are all still populated in `model` even though `my-performance.jsp` no longer
renders those blocks — **dead-but-computed code, not a bug, but worth knowing before touching this
method**).

**Filters** (all backend-enforced, `myPerformance` params): `stage`, `role`, `status`
(`matchesStatusFilter` — "Completed" always matches by construction; Delayed/On Time mirror the
`delay` filter), `delay`, `fromDate`/`toDate`, `q` (Content ID or title substring, case-insensitive),
pagination (`page`, `pageSize` ∈ {10,25,50}).

---

## 10. MARK CALCULATION / ATTRIBUTION

Full traced code path:

1. **Configuration**: `mark_catalogue_entries` table (§9) — admin-editable `(role_type,
   mark_value, is_active)` rows, `MarkCatalogueService`.
2. **Review input**: Idea Review Approve form collects `cameramanMark`/`editorMark`/`modelMark`
   (3 `BigDecimal` params, `IdeaMvcController`/`ReviewsMvcController` → `IdeaService.decide` →
   `approve`).
3. **Validation**: `markCatalogueService.requireActiveValue(RoleType.X, value)` for each of the 3
   roles (`IdeaService.approve` lines 453–455) — throws `VALIDATION_FAILED` if no currently-active
   catalogue row matches that exact value for that role.
4. **Storage — predefined values**: `PredefinedRoleMarks` (`predefined_role_marks` table, one row
   per Content Plan, `UNIQUE(content_plan_id)`) — the 3 predefined marks set once at Idea approval,
   correctable later via `IdeaService.correctPredefinedMarks` → immutable
   `PredefinedMarkCorrection` ledger row (`predefined_mark_corrections`, append-only, chained via
   `supersedes_correction_id`).
5. **Attribution — per-person**: `PersonalMarkAttribution` (`personal_mark_attributions`, one row
   per recipient × review cycle) — created at Model-selection time (Idea approval, immediate) or
   at Shoot/Edit Review Approve time (deferred until the reviewer confirms *which* participants
   qualify). Always references the specific `PredefinedRoleMarks` + `ReviewCycle` it came from.
6. **Display**: `LandingMvcController.buildPerformanceRows`/`toPerformanceRow` reads
   `PersonalMarkAttribution` back via `markAttributionRepository.findByRecipient(user)`, grouped by
   plan, filtered by `RoleType` per row.

**Deleting a Mark Catalogue entry never affects historical data** — `predefined_role_marks`/
`predefined_mark_corrections` store the mark *value* directly (`NUMERIC(3,1)`, no FK to the
catalogue row), and `personal_mark_attributions.attributed_mark_value` likewise has no FK or CHECK
constraint tying it to the current catalogue at all (confirmed directly in `V9`/`V29`/`V36` — no
`ck_personal_mark_attributions_*_value` constraint exists).

No formula beyond simple summation/averaging was found anywhere in this chain — marks are
**assigned values from a controlled list**, not computed/derived from any input metric.

---

## 11. DATE & TIME HANDLING

**Business timezone**: `Asia/Kolkata` (IST, UTC+5:30) — `private static final ZoneId
BUSINESS_ZONE = ZoneId.of("Asia/Kolkata")`, independently declared as an identical constant in
**at least 3 places**: `IdeaService`, `DeliverableMvcController`, `LandingMvcController` (**not a
shared constant — duplicated verbatim in each class; a future timezone change would need to touch
all of them**).

**Application/JVM timezone**: not explicitly pinned in `application.yml` — **NOT CONFIRMED FROM
CODE** whether the JVM default timezone is set via `JAVA_OPTS`/Dockerfile; all business-date logic
explicitly passes `BUSINESS_ZONE` rather than relying on JVM default, which is the safer pattern
regardless.

**Database timezone**: Hibernate configured `jdbc.time_zone: UTC` (`application.yml`) — all
`TIMESTAMPTZ` columns store/round-trip in UTC; `LocalDate` columns (`planned_live_date`,
`planned_shoot_date`, etc.) are timezone-agnostic calendar dates by design.

**LocalDate usage**: every *planned* date (`ContentPlan.plannedLiveDate/plannedShootDate/
plannedEditDate`) is `LocalDate` — a calendar date with no time-of-day/timezone component. All
comparisons against "today" explicitly call `LocalDate.now(BUSINESS_ZONE)`, never
`LocalDate.now()` bare.

**Instant usage**: every *actual/recorded* timestamp (`WorkflowTransitionHistory.transitionTimestamp`,
`ActualPublicationEvent.actualPublicationTimestamp`, `ReviewCycle.decidedAt`, etc.) is `Instant`
(UTC instant) — converted to the business-zone `LocalDate` only at the point of comparison against
a planned date (e.g. `LandingMvcController.toPerformanceRow`: `completedOn.atZone(BUSINESS_ZONE)
.toLocalDate()`).

**ZonedDateTime usage**: not directly observed as a field type anywhere — the app uses `Instant`
(absolute) + `LocalDate` (calendar) + an explicit `ZoneId` constant for the one conversion point
that needs it, rather than storing `ZonedDateTime` values.

### CALCULATION vs DISPLAY FORMATTING vs VALIDATION — the three layers, kept explicitly separate

This distinction is the subject of a bug found and fixed earlier in this session, and is worth
stating precisely as a durable rule for this codebase:

1. **CALCULATION** (server, authoritative): `IdeaService.approve` —
   `plannedLiveDate.minusDays(5)` (Shoot) / `.minusDays(2)` (Edit), pure `LocalDate` arithmetic,
   no timezone conversion involved at all (`LocalDate` has no timezone).
2. **VALIDATION** (server, authoritative, non-bypassable): same method, immediately after
   calculation — `shootDate.isBefore(LocalDate.now(BUSINESS_ZONE))` (and identically for
   `editDate`). Rule: `date < today → reject; date == today → valid; date > today → valid`. This
   backend check is the final safety layer regardless of what the client sent.
3. **DISPLAY FORMATTING** (client, UX-only preview): `reviews-workspace.js`'s
   `updateReviewsIdeaScheduleDefaults()` mirrors the same Live−5d/Live−2d formula in JS for a live
   preview, and mirrors the same `< today` validation rule (`updateReviewsIdeaScheduleErrors`) for
   an inline error. **This layer has no authority** — it exists purely so the user sees a problem
   before submitting; the backend re-validates unconditionally either way.

**The bug (fixed this session, uncommitted)**: the JS preview's DISPLAY step used
`Date.prototype.toISOString().slice(0,10)` to format the calculated date into the input field.
`toISOString()` converts to **UTC**. For any browser in a positive-UTC-offset timezone (Asia/
Kolkata, +5:30 — this app's own `BUSINESS_ZONE`), a local-midnight `Date` instant rolls back to the
**previous calendar day** once formatted this way — so the displayed Shoot/Edit Date was silently
**one day earlier** than the true formula result, even though the VALIDATION step (which compared
the *original*, un-shifted `Date` object, never the reformatted string) was already computing the
correct answer. Fixed by replacing `toISOString()` with a local-getter-based formatter
(`toLocalIsoDate(date)`, using `getFullYear()/getMonth()/getDate()`, no UTC conversion) — see
`src/main/resources/static/js/reviews-workspace.js`. This is a canonical illustration of why
CALCULATION/VALIDATION and DISPLAY FORMATTING must never share a UTC-converting code path in this
app.

### Planning date rules (consolidated)

| Rule | Standard mode | Urgent mode |
|---|---|---|
| Planned Live Date must not be in the past | Always (`plannedLiveDate.isBefore(today)` → reject) | Same |
| Minimum days-out floor on Live Date (BRS-REQ-093) | Stage-aware: 5 days if Shoot included, 2 days if only Edit included, **none** if Publishing-only | N/A (Urgent exists specifically to bypass this floor) |
| Shoot Date | Defaults `Live − 5d` if not explicitly overridden; **must not land before today** (rejected if it does) | Explicit value **required**; no past-date guard (deliberate — Urgent's premise is "already behind schedule") |
| Edit Date | Defaults `Live − 2d`; same past-date guard as Shoot | Explicit value required; no past-date guard |
| Stage-dependent calculation | Shoot/Edit Date only calculated/validated for stages actually in the pipeline — Publishing-only never touches either | Same |

---

## 12. PLANNING

Fully folded into Idea Review Approve — there is no separate "Planning" screen/workflow stage
(§3). All fields below are collected on the same Approve form (`reviews-ideas.jspf` /
`idea-detail.jsp`) and validated/persisted in one transaction by `IdeaService.approve`.

| Field | Optional/Required | Notes |
|---|---|---|
| Priority (`ContentPriority`) | Optional, defaults `LOW` | Not left null even if omitted |
| Category | Optional | Must match an active `Category` catalogue entry if non-blank (`CategoryService.requireActiveNameOrBlank`) — free text otherwise never validated |
| SKU Reference / SKU N/A | Optional | Mutually exclusive (`ck_content_plans_sku_na_exclusion`) |
| Planning Mode | Required, defaults `STANDARD` | `STANDARD` / `URGENT` |
| Stages | Optional param, defaults to all 3 | Exactly 3 valid combinations (§3) |
| Planned Live Date | **Required** | Must not be in the past |
| Shoot Date | Conditional (only if Shoot in pipeline) | See §11 |
| Edit Date | Conditional (only if Edit included) | See §11 |
| Drive Link | Required **unless** Drive auto-provisioning is enabled | `DriveProvisioningService.isDriveIntegrationEnabled()` |
| Planned Outputs (Story/Post/Reel/Long Video) | Optional | Each row → one `PlannedOutput`, mapped to selected Publication Targets |
| Platform/Channel (Publication Targets) | Optional | Picked per Output row |
| Shoot Assignment (Cameraperson(s) + Shoot Lead) | **Required if Shoot starts** | Lead must be one of the selected Camerapersons |
| Editor Assignment (Editor(s) + Editor Lead) | **Required if Edit starts directly (Direct Edit)** | Same Lead-must-be-selected rule |
| Publisher Assignment | **Required if Publishing starts directly** | No Lead concept (`ENG-036`/`ENG-044`) |
| Team Marks (Cameraperson/Editor/Model) | **Required on Approve** | Must match an active Mark Catalogue value per role |
| Models/Talent | Optional | Each gets an immediate Model Mark attribution |

Field visibility by Stages selection (frontend, `updateReviewsIdeaStagesFields` in
`reviews-workspace.js`, backed by the same 3-combination logic server-side): Shoot section shown
only if Shoot selected; Editor section only if Edit-starts (Direct Edit); Publisher section only
if Publishing-starts (Direct Publishing).

---

## 13. WORKFLOW STAGE COMBINATIONS

Verified directly in `IdeaService.approve` (lines 273–284) — **exactly 3** valid combinations,
enforced by an explicit boolean (`validCombo`), not merely "whatever passes other checks":

```java
boolean shootStarts = stages.contains(SHOOT);
boolean editStarts = !shootStarts && stages.contains(EDIT);
boolean publishingStarts = !shootStarts && !editStarts;
int stageCount = stages.size();
boolean validCombo = (shootStarts && stageCount == 3 && stages.contains(EDIT))
        || (editStarts && stageCount == 2)
        || (publishingStarts && stageCount == 1);
```

- `{Shoot, Edit, Publishing}` — valid.
- `{Edit, Publishing}` — valid (Direct Edit).
- `{Publishing}` — valid (Direct Publishing).
- `{Shoot, Publishing}` (Shoot without Edit) — **rejected**: `shootStarts` is true but
  `stageCount == 2 ≠ 3`, so `validCombo` is false → `VALIDATION_FAILED "Invalid stage selection"`.
- Any set not containing `PUBLISHING` at all is rejected earlier (`!stages.contains(PUBLISHING)`).

Three corresponding regression tests exist and are named for exactly this:
`IdeaApprovalStagesTest`, `IdeaApprovalUrgentStagesDateValidationTest`,
`StandardPlanningPastDateValidationTest` (per-combo Live Date floor cases, including
`fullPipelineStillRequiresTheFiveDayFloorUnchanged` and
`directEditAllowsALiveDateOnlyTwoDaysOutUnlikeTheFullPipeline`).

---

## 14. CONTENT DETAIL

**Route**: `GET /app/deliverables/{id}?tab=X` → `DeliverableMvcController.view` — single shared
handler for **all** deliverable-detail views (task-specific and generic).

**Tabs** (generic shell, `deliverable-detail.jsp`): Overview, Shoot, Edit, Publishing,
Performance, Timeline — `CONTENT_DETAIL_TABS` constant. Visibility per tab is computed
independently (`canSeeShootTab`/`canSeeEditTab`/`canSeePublishingTab`/`canSeePerformanceTab`/
`canSeeTimeline`), each `nativeAuthority OR (relevant permission(s)) OR (active assignment on this
plan)` — a requested tab the viewer can't see silently falls back to Overview (never a 403 for a
tab-visibility mismatch, only for the page itself).

**Current stage**: `ContentCanonicalStage.forStatus(status)` — the **one** canonical
Status→Stage resolver (`SA/SIP/SRV/SAP→SHOOTING`, `EA/ED/ERV/EAP→EDITING`, `RFP/PUBG→PUBLISHING`,
`PP/PFUP→PERFORMANCE`, `COMP→COMPLETED`, everything else→`OTHER`/"Overview") — reused by Action
Center; never a second parallel mapping.

**Action Center**: `buildAvailableActions(plan, status, model, user, nativeAuthority, openHold)` →
`availableActions` model attribute, built from `AvailableActionService` — **not fully traced in
this pass; the exact per-status action list logic was not read**.

**Permissions/assignment visibility**: every `canX` flag documented in §5's table is set on this
same model, once, at the top of `view()` — reused by every tab's rendering.

**Routing to the correct stage-specific screen**: see §15 (the routing branches are identical for
both Content Detail's own tabs and the task-detail screens — same method, same branches).

---

## 15. TASK DETAIL / COMPLETED TASK DETAIL

This is the most safety-critical routing logic in the app (explicitly flagged by prior bugs this
session — same-Content-ID multi-stage collisions). Full trace, `DeliverableMvcController.view()`
(lines 576–652):

### Active task (own stage, still in its active status window)

Three branches, checked in order — **Shoot, then Edit, then Publish** — each requires
**permission AND assignment AND status-in-window AND (`tab == null` OR the matching explicit
tab)**:

```java
if (isAssignedToShootTask && !shootTaskCompleted && (tab == null || "shoot".equals(tab))
        && allowed(PERM_18_SHOOT_EXECUTION, SHOOTING)) → shoot-task-detail

if (isEditActiveAssignee && editRelevantStatus && (tab == null || "edit".equals(tab))
        && allowed(PERM_19_EDIT_EXECUTION, EDITING)) → edit-task-detail

if (isPublishActiveAssignee && publishRelevantStatus && (tab == null || "publishing".equals(tab))
        && allowed(PERM_08_PUBLISHING_EXECUTION, PUBLISHING)) → publish-task-detail
```

- `isAssignedToShootTask` = active `ShootingAssignment.cameraperson` **OR** linked
  `ContentPlanTalentEntry.talentUser` (covers both Cameraperson and Model participation
  mechanisms).
- Status windows: Shoot uses `!shootTaskCompleted` (history-based, not live status — see §8);
  Edit uses `status ∈ {EA, ED, ERV, EAP}`; Publish uses `status ∈ {RFP, PUBG, PP}` — **PP is
  deliberately included for Publish** (its own resting state, no hand-off to another screen)
  but **not** for Shoot/Edit (whose approval hands off to the next stage's page).
- The `(tab == null || "X".equals(tab))` guard on **each** of the three branches is the fix for a
  real, previously-shipped bug: without it, a person simultaneously resting at Publishing's `PP`
  status would have *every* `?tab=` value hijacked into the Publish screen, even when explicitly
  asking for their completed Shoot/Edit work via a different `?tab=`.

### Completed task, read-only re-entry (My Work's "View Details" links)

```java
if ("shoot".equals(tab) && isAssignedToShootTask && allowed(...)) → shoot-task-detail
if ("edit".equals(tab) && isEditActiveAssignee && allowed(...)) → edit-task-detail
if ("publishing".equals(tab) && isPublishActiveAssignee && allowed(...)) → publish-task-detail
```

Reached **only** after all 3 active-task branches above have failed to match (i.e. genuinely
past that stage's active window) — same permission+assignment ownership check, same
`addXTaskDetailAttributes` model-building, **no separate/duplicate screen and no separate
authorization rule**. The explicit `tab` param disambiguates which of possibly-several
stages this exact employee worked on this exact plan.

### Routing basis — explicit answer to "is this based on Content ID, Assignment ID, Stage, Role,
Permission, or Status?"

**Content ID alone is never sufficient** and is explicitly called out as the previously-buggy
approach: routing is **permission + assignment + stage + status**, resolved via `?tab=` for the
completed case and via live status window for the active case. Role (Business Role name) is
**never** part of this decision (see §4's 2 documented exceptions, neither of which is this
routing).

**Frozen outcome, not live status, for a completed task's own display**: `addShootTaskDetailAttributes`/
`addEditTaskDetailAttributes`/`addPublishTaskDetailAttributes` each compute a boolean
(`shootApproved`/`editApproved`/`publishingComplete`) from `WorkflowTransitionHistory`
(`APPROVE_SHOOT`/`APPROVE_EDIT`/`PUBLICATION_SCOPE_RESOLVED` trigger commands) **before** deriving
the status label/CSS class/delay-note/progress-step — never `status == SAP` live-status equality,
which would show the Content Plan's *current* overall status (which may have moved on) instead of
this task's own frozen result. This was a previously-fixed bug this session, now consistent across
all three methods.

**Generic route with no ownership/stage gate that must never be used for this**: explicitly called
out in a comment (`LandingMvcController`, near `shootHistoryDetail`) — the shared
`/app/deliverables/{id}` page (without the tab-scoped branches above) "has no ownership/stage gate
at all: once a plan moves past Shoot, a Cameraperson landing on it via [a bare content-id link]
would fall through to the full generic Content Detail view and see next-stage data." The
`?tab=`-scoped branches above are exactly what prevents this.

**Legacy parallel route (still present, no longer linked from My Work)**:
`GET /app/my-work/history/{shoot,edit,publish}/{assignmentId}` — keyed by the **assignment's own
id**, not Content Plan id; enforces ownership server-side (not merely hidden in the JSP);
intentionally left intact/unused after My Work's Completed Work links were switched to the
`/app/deliverables/{id}?tab=X` route (§ prior-session summary — not deleted, "not deleted" is a
deliberate choice, not an oversight).

---

## 16. REVIEWS

**Route**: `GET /app/reviews` → `ReviewsMvcController` (`@RequestMapping("/app/reviews")`),
AJAX-partial-swap architecture (`reviews-workspace.js`) with 3 tabs: Ideas, Shoot, Edit.

| Sub-review | Who can review | Endpoint | Decision(s) | Marks | Feedback |
|---|---|---|---|---|---|
| Idea Review | PERM_01_IDEA_REVIEW (or native) | `POST /app/reviews/ideas/{id}/decision` | Approve / Reject / Retain | Sets predefined Cameraperson/Editor/Model marks (Approve only) | Reject/Retain reason |
| Shoot Review | PERM_05_SHOOT_REVIEW (or native), not a shoot participant | `POST /app/reviews/shoot/{id}/decision` | Approve (+ qualifying recipients + next Editor team) / Request Rework | Attributes Cameraperson marks to confirmed qualifying recipients (Approve) | Rework reason mandatory |
| Edit Review | PERM_07_EDIT_REVIEW (or native), not an edit participant | `POST /app/reviews/edit/{id}/decision` | Approve (+ next Publisher team, presumed) / Request Rework | Attributes Editor marks (presumed, symmetric — not directly read) | Rework reason mandatory |
| Idea Reopen | PERM_01_IDEA_REVIEW | `POST /app/reviews/ideas/{id}/reopen` | RET → PA | — | — |
| Publishing | **No review gate exists** — Publishing Execution is direct-action, not review-gated | n/a | n/a | n/a | n/a |

**Qualifying recipients**: Shoot Review Approve requires an explicit, non-empty list of qualifying
final Camerapersons (`qualifyingRecipientUserIds`) — each must be a recorded
`ShootingExecutionParticipant` (validated server-side, `ShootingService.decideShootReview`); marks
are attributed only to these confirmed recipients, not automatically to every historical
participant.

**Status changes / assignment changes**: Shoot Review Approve atomically transitions `SRV→SAP→EA`
**and** assigns the next-stage Editor team in the same `@Transactional` method (reuses
`EditingService.assignEditTeam` — never duplicated logic). Edit Review Approve is presumed
symmetric (`ERV→EAP→RFP` + Publisher team assignment) — **not directly read in this pass**.

---

## 17. DATABASE

37 Flyway migrations (`V1`–`V37`, `V6`/`V33` absent from the sequence — **gap not explained in
any comment found; NOT CONFIRMED whether V6/V33 were ever created and later removed, or simply
skipped in numbering**). Key tables (grouped by domain):

### Identity / Auth
| Table | Purpose | PK | Key FKs | Notable constraints |
|---|---|---|---|---|
| `base_roles` | The 3 fixed access classes | `role_code` | — | CHECK IN ('CEO_OWNER','MARKETING_MANAGER','EMPLOYEE') |
| `business_roles` | 17 seeded, expandable organizational roles | `business_role_id` | `access_class_code→base_roles` | `participates_in_workflow` (V21) |
| `users` | All accounts | `user_id` | `business_role_id→business_roles` | unique `email` |
| `user_sessions` | JWT registry, SHA-256(jti) only | `session_id` | `user_id` | partial index on active-only |
| `password_reset_tokens` | Forgot-password flow | `reset_token_id` | `user_id` | SHA-256(token), 30-min expiry |
| `operational_permissions` | 20 governed permissions | `permission_number` | — | number range CHECK (extended each time a perm is added) |
| `permission_grants` | Grantee↔permission, scoped | `grant_id` | `grantee_user_id`, `grantor_user_id→users` | scope_type CHECK, effective window CHECK |
| `permission_grant_stage_scopes` | STAGE_RESTRICTED detail | `scope_id` | `grant_id`, `stage_number` | unique `(grant_id, stage_number)` |
| `permission_grant_item_scopes` | ITEM_SPECIFIC detail | `scope_id` | `grant_id`, `workflow_instance_id` | unique pair |
| `permission_grant_stages` | 7 lifecycle stages | `stage_number` | — | fixed reference data |

### Workflow core
| Table | Purpose | PK | Key FKs | Notable |
|---|---|---|---|---|
| `workflow_concepts` | 19 governed statuses | `status_code` | — | classification CHECK |
| `workflow_instances` | One per Idea/Content Plan lifecycle | `workflow_instance_id` | `current_status_code→workflow_concepts` | `first_completed_at` immutable-once-set (trigger); CHECK status ≠ DLY |
| `workflow_transition_history` | Append-only audit of every status change | `transition_id` | `workflow_instance_id`, `triggered_by_user_id`, `acting_permission_grant_id` | append-only trigger (V12/V13) |
| `review_cycles` | Idea/Shoot/Edit review submissions+decisions | `review_cycle_id` | `workflow_instance_id`, `reviewer_user_id` | decision fields immutable once `decided_at` set (trigger); unique `(instance, gate_type, cycle_number)` |
| `work_hold_records` | Hold/Resume, NOT a status | `hold_record_id` | `workflow_instance_id` | held_status CHECK IN ('SIP','ED','PUBG'); at most one open per instance (partial unique index); hard DELETE blocked |

### Content / Planning
| Table | Purpose | PK | Key FKs | Notable |
|---|---|---|---|---|
| `ideas` | Idea Submission | `idea_id` | `workflow_instance_id` unique, `submitted_by_user_id` | unique `business_idea_code` |
| `idea_description_corrections` | Append-only edit ledger for Idea description | `correction_id` | `idea_id` | same-parent chain trigger |
| `content_id_sequences` | Per-business-month Content ID counter | `business_month_mmyy` | — | row-locked (`FOR UPDATE`) allocation |
| `content_plans` | **The central deliverable record** | `content_plan_id` | `idea_id` unique, `workflow_instance_id` unique | unique `content_id`, `content_id` immutable (trigger), SKU/priority/planning-mode/urgency/date-chronology CHECKs |
| `predefined_role_marks` | 3 predefined marks per plan | `mark_id` | `content_plan_id` unique | (old value-list CHECKs dropped by V36) |
| `predefined_mark_corrections` | Append-only mark-correction ledger | `correction_id` | `predefined_mark_id` | same-parent chain, reason required |
| `personal_mark_attributions` | Per-person, per-review-cycle marks | `attribution_id` | `recipient_user_id`, `content_plan_id`, `review_cycle_id`, `predefined_mark_id` | unique `(recipient, review_cycle)`; append-only |
| `planned_outputs` | Output Type/Reel Type/description | `planned_output_id` | `content_plan_id` | output_type CHECK IN ('STORY','POST','REEL','LONG_VIDEO') |
| `planned_output_publication_target_mappings` | Output↔Target | `mapping_id` | both | unique pair |
| `content_plan_talent_entries` | Model/Talent participation | `entry_id` | `content_plan_id`, nullable `talent_user_id` | |
| `planning_preparers` | Self-review-conflict provenance | `preparer_id` | `content_plan_id`, `preparer_user_id` | |

### Production / Publishing / Performance
| Table | Purpose | PK | Key FKs | Notable |
|---|---|---|---|---|
| `shooting_assignments` | Active/historical Cameraperson assignments | `assignment_id` | `content_plan_id`, `cameraperson_user_id` | `is_lead`, partial unique "one lead" index |
| `editing_assignments` | Same, Editor | `assignment_id` | | Same pattern |
| `publishing_assignments` | Same, Publisher | `assignment_id` | | No lead concept |
| `shooting_execution_participants` / `editing_execution_participants` | Fresh row per execution cycle (rework-safe) | `participant_id` | | Append-only |
| `platforms`, `company_channels`, `publication_targets` | Master catalogue | | | Target = unique (platform, channel) |
| `mark_catalogue_entries` | Admin-managed allowed mark values | `mark_catalogue_entry_id` | — | unique `(role_type, mark_value)` |
| `categories` | Admin-managed Category catalogue | `category_id` | — | unique `name`; one `is_default = TRUE` row ("N/A") |
| `actual_publication_events` | Real publication records | `event_id` | `content_plan_id`, `planned_output_id`, `publication_target_id`, `published_by_user_id` | event_type CHECK ('ORIGINAL','REPOST') |
| `publication_target_na_records` | N/A designation ledger | `na_record_id` | `planned_output_id`, `publication_target_id` | reason mandatory when DESIGNATED |
| `publication_evidence_corrections` | Append-only evidence-URL correction ledger | `correction_id` | `event_id` | same-parent chain |
| `performance_obligations` | Due-date tracking per publication event | `obligation_id` | `event_id` unique | |
| `creative_performance_scorecards` | Meta-metrics (+ legacy 6-field) scorecard | `scorecard_id` | `obligation_id` unique | sealed once submitted (trigger); `uses_meta_metric_model` discriminator |
| `performance_metric_corrections` | Append-only metric-correction ledger | `correction_id` | `scorecard_id` | same-parent chain |

### Admin / Audit
| Table | Purpose | PK |
|---|---|---|
| `reschedule_records`, `reassignment_records`, `reassignment_assignees`, `cancellation_records`, `reopen_records` | Append-only admin-action ledgers | each own `*_id` |
| `system_audit_log` | System-wide audit trail | `audit_id` | `actor_user_id`, `acting_permission_grant_id` |
| `stage_comments` | Per-stage discussion threads (author-editable, hard-delete blocked) | `comment_id` |
| `content_drive_provisioning` | One row per plan, Drive folder ids + status | `provisioning_id` | `content_plan_id` unique |

**Append-only enforcement — two layers**: (1) DB triggers (`trg_reject_update_delete`,
`trg_reject_truncate`) on every correction/history/participant/audit table (`V12`/`V13`); (2) a
DB-privilege split (`V13` Part B) — a restricted `kcpc_app` runtime role gets only `SELECT,
INSERT` on append-only tables (never UPDATE/DELETE/TRUNCATE at the grant level either), while
Flyway itself runs as a separate, higher-privileged `kcpc_migrator` role. Both layers are
guarded to no-op safely in single-role local dev/test setups.

---

## 18. ENTITY RELATIONSHIPS

```
Idea
 ├─(1:1)→ WorkflowInstance ──(1:N)→ WorkflowTransitionHistory (append-only)
 │                          ──(1:N)→ ReviewCycle (Idea/Shoot/Edit review submissions+decisions)
 │                          ──(0:1 open)→ WorkHoldRecord (Hold/Resume, not a status)
 ├─(1:N)→ IdeaDescriptionCorrection (append-only)
 └─(1:1, on approval)→ ContentPlan
                         ├─(1:1)→ PredefinedRoleMarks ──(1:N)→ PredefinedMarkCorrection (append-only)
                         │                             ──(1:N)→ PersonalMarkAttribution (per person, per review cycle)
                         ├─(1:N)→ PlanningPreparer
                         ├─(1:N)→ ContentPlanTalentEntry (Model participation; optional →User link)
                         ├─(1:N)→ PlannedOutput ──(1:N)→ PlannedOutputPublicationTargetMapping → PublicationTarget → Platform/CompanyChannel
                         ├─(1:N)→ ShootingAssignment (active + historical) ──(1:N)→ ShootingExecutionParticipant (per exec cycle)
                         ├─(1:N)→ EditingAssignment ──(1:N)→ EditingExecutionParticipant
                         ├─(1:N)→ PublishingAssignment
                         ├─(1:N)→ ActualPublicationEvent ──(1:1)→ PerformanceObligation ──(0:1)→ CreativePerformanceScorecard ──(1:N)→ PerformanceMetricCorrection (append-only)
                         │                                └─(1:N)→ PublicationEvidenceCorrection (append-only)
                         ├─(1:N)→ PublicationTargetNaRecord
                         ├─(1:N)→ StageComment (per SHOOTING/EDITING/PUBLISHING)
                         ├─(0:1)→ ContentDriveProvisioning
                         └─(shares WorkflowInstance with Idea above — same workflow_instance_id lineage the whole way through)

User ──(N:1)→ BusinessRole ──(N:1)→ base_roles (AccessClass)
User ──(1:N)→ PermissionGrant ──(0:N)→ PermissionGrantStageScope / PermissionGrantItemScope
User ──(1:N)→ UserSession, PasswordResetToken
```

---

## 19. API / CONTROLLER MAP

### MVC (JSP-rendering) controllers

| Controller | Base path | Key routes |
|---|---|---|
| `AuthMvcController` | (root) | `GET/POST /login`, `POST /logout`, `GET/POST /forgot-password`, `GET/POST /reset-password`, `GET/POST /app/change-password` |
| `LandingMvcController` | `/app` | `GET /app/home`, `/app/my-work`, `/app/my-work/history/{shoot,edit,publish}/{assignmentId}`, `/app/my-performance`, `/app/pipeline`, `/app/my-shoots` |
| `IdeaMvcController` | `/app/ideas` | `GET/POST /app/ideas/new`, `GET /app/ideas`, `GET /app/ideas/{id}`, `POST .../review`, `.../reopen`, `.../description`, `.../reference-link` |
| `ReviewsMvcController` | `/app/reviews` | `GET /app/reviews`, `POST /ideas/{id}/decision`, `/ideas/{id}/reopen`, `/shoot/{id}/decision`, `/edit/{id}/decision` |
| `DeliverableMvcController` | `/app/deliverables/{id}` | `GET` (branches, §14/15) + ~50 `POST` sub-routes covering parameters, Drive retry/relink, schedule, outputs, shooting/editing/publishing assignments+start+review+description+comments, performance draft/submit, reschedule/reassign/skip/cancel/hold/resume/reopen |
| `AdminMvcController` | `/app/admin` | `/users*`, `/permissions`, `/business-roles*`, `/catalogue*`, `/marks*`, `/categories*` — CEO_OWNER-only for Users/Business Roles/Permissions/Marks/Categories, CEO/MM/PERM_17 for Catalogue |
| `ReportingMvcController` | `/reports`, `/audit`, `/export` | `/reports/workload`, `/reports/team-kpis`, `/reports/kpis`, `/reports/delayed`, `/reports/admin-actions`, `/audit`, `/export` |

### REST (`/api/v1/**`, JSON) controllers — mirror the same service layer as their MVC counterparts

`AuthRestController` (`/api/v1/auth/*`), `CsrfRestController` (`/api/v1/csrf`),
`IdeaRestController` (`/api/v1/ideas/*`), `ContentPlanRestController` (`/api/v1/content-plans/*`),
`ShootingRestController`/`EditingRestController`/`PublishingRestController`
(`/api/v1/content-plans/{id}/{shooting,editing,publishing}/*`), `HoldRestController`,
`AdminActionRestController`, `PerformanceRestController`/`PerformanceMetricCorrectionRestController`,
`PublicationEvidenceCorrectionRestController`, `MasterCatalogueRestController`
(`/api/v1/publishing/*`), `UserAdminRestController`/`BusinessRoleAdminRestController`/
`PermissionGrantAdminRestController` (`/api/v1/admin/*`), `MySelfServiceRestController`
(`/api/v1/me/*`), `KpiRestController`, `ReportingRestController`, `AuditRestController`,
`ExportRestController`. All authenticated via the same JWT cookie (`apiFilterChain`), return
`ApiErrorResponse` JSON on failure (`RestExceptionHandler`).

---

## 20. FRONTEND BEHAVIOR

No build tooling — plain `<script src>` tags, one file per feature, `src/main/resources/static/js/`.

- **`reviews-workspace.js`**: the largest/most complex — AJAX partial-fragment-swap for the
  Reviews tabs (fetch + `innerHTML` replace + `history.pushState`, `X-Requested-With: fetch`
  header, `AbortController` for in-flight-request cancellation), event-delegated on one root
  `#reviewsDynamicRegion` element for click/submit/change/input, decision-submit flow (Idea/Shoot/
  Edit), Planned Outputs grid, and the Standard/Urgent schedule-defaulting + inline date validation
  covered in §11. Explicitly documents "No business/validation logic lives here" — a decision POST
  either succeeds or surfaces the backend's own error message verbatim.
- **`my-work-tabs.js`**: shared tab-switcher, handles both My Work's nested per-stage sub-tabs and
  flat single-tier tab bars (Content Detail, My Shoots) via a document-scoped fallback, guarded
  against double-wiring when both structures could theoretically coexist on one page.
- **`stages-picker.js`**: the Stages checkbox-group component, fires a `kcpc:stages-changed`
  custom event consumed by `reviews-workspace.js` to re-show/hide Shoot/Editor/Publisher
  assignment sections and re-run date validation.
- **`model-picker.js`**, **`assignment-picker.js`**, **`reassign-form.js`**,
  **`publisher-assignment-modal.js`**: candidate-picker UI components, re-initialized after every
  AJAX swap (`window.initModelPickers(region)` etc. pattern).
- **`content-detail.js`**: Content Detail page-specific behavior (**not read in this pass**).
- **`idea-create-modal.js`** / **`idea-submit.js`**: dual-mode (full-page submit vs. modal AJAX
  submit) idea creation.
- **`stage-discussion.js`**: shared Shoot/Edit/Publishing comment-thread widget, reused by both
  Reviews' inspector view and Content Detail.
- **`skip-stage-modal.js`**, **`publishing-checklist.js`**, **`publication-scope.js`**,
  **`performance-metric-correction.js`**, **`review-decision.js`**, **`reports-workspace.js`**,
  **`team-workload-dashboard.js`**, **`pipeline-dashboard.js`**, **`idea-queue-dashboard.js`**,
  **`admin-business-roles.js`**, **`admin-edit-user-modal.js`**, **`admin-shared.js`**,
  **`permission-checklist.js`**, **`script-description-modal.js`**,
  **`idea-details-modal.js`**, **`idea-reference-link-edit.js`** — one file per discrete
  interactive feature (**contents not individually read in this pass**; names/usage sites inferred
  from `reviews-workspace.js`'s own re-wiring calls and file listing).

**Frontend→backend interaction pattern**: plain `<form>` POSTs for most actions (page reload or,
where AJAX is used, `fetch()` + JSON error body parsing), CSRF token read from a hidden input
(`document.getElementById('reviewsCsrfToken')` pattern) or cookie
(`CookieCsrfTokenRepository.withHttpOnlyFalse()`), never a separate token-fetch round-trip beyond
`GET /api/v1/csrf` where needed.

---

## 21. SECURITY

- **Authentication**: JWT in an HttpOnly cookie (`KCPC_AT`), validated per-request by
  `JwtAuthenticationFilter`; server-side revocation via `user_sessions`
  (`SHA-256(jti)`, never the raw token). No `HttpSession` at all.
- **Session handling**: deliberately **not** session-based — `sessionManagement(...).disable`)
  on both filter chains, with an explicit comment explaining why `STATELESS` alone would still
  leave `SessionManagementFilter`/`CsrfAuthenticationStrategy` active and rotating the CSRF cookie
  unexpectedly.
- **Authorization**: `AuthorizationService` — see §5, server-authoritative, never trusts the
  client (explicit doc comment: *"The frontend is never the security authority"*).
  `RestExceptionHandler`/`MvcAuthEntryPoint`/`RestAuthEntryPoint` handle 401/403 uniformly.
- **CSRF**: enforced on every unsafe (non-GET/HEAD) cookie-authenticated request, both filter
  chains (`CookieCsrfTokenRepository.withHttpOnlyFalse()`, custom cookie/header names from
  `CsrfProperties`). Exempted only for `/api/v1/auth/login` (no cookie exists yet to ride on).
- **URL protection / direct URL access**: `WorkflowParticipationInterceptor` (deny-by-default for
  non-production employees, §6); `DeliverableMvcController`'s per-tab permission+assignment+status
  gates (§15) block a crafted `?tab=` from reaching another employee's/stage's data; `AdminMvcController.isCeo`
  gates every CEO-only admin route explicitly (not merely hidden nav).
- **Employee data isolation**: My Work/My Shoots/My Performance are explicitly documented as
  "render[ing] only the authenticated user's own tasks/marks/ideas — never another user's, and
  there is no parameterized 'view another user' path anywhere in this controller"
  (`LandingMvcController` class javadoc).
- **UI-only vs backend-enforced**: **none found** — every `canX`/`isX` UI-visibility flag traced
  in this document is backed by the identical server-side check re-invoked on the corresponding
  POST handler (§5's "UI vs backend authorization" — this is a deliberately, repeatedly-enforced
  house rule, not a coincidence).
- **Password handling**: BCrypt (`BCryptPasswordEncoder`); admin-initiated resets set
  `password_change_required = TRUE` (`V28`), forcing a change screen; self-service forgot-password
  uses single-use, 30-minute-expiry, SHA-256-hashed tokens (`V27`), **no email delivery
  infrastructure exists** — reset happens via a token the CEO/admin must relay out-of-band, or via
  Admin Password Reset directly (`UserAdminService.resetPasswordByAdmin`, **not directly read**).
- **Actuator**: only `/actuator/health` exposed, `permitAll`, `show-details: never` /
  `show-components: never` (no internal info leak to an unauthenticated caller).

---

## 22. GOOGLE DRIVE / FILE ACCESS

- **Enable flag**: `app.drive.enabled` / env `DRIVE_ENABLED` (default `false` everywhere — "no
  real service-account credentials exist in this repo," per `application.yml` comment).
- **Auth**: `google-auth-library-oauth2-http`, service-account key supplied as a flattened
  single-line JSON string via `DRIVE_SERVICE_ACCOUNT_KEY` (never a file path, never baked into the
  image) + optional domain-wide-delegation impersonation (`DRIVE_IMPERSONATE_USER`).
- **Folder hierarchy** (per Content ID, created under a configured Shared Drive):
  ```
  CONTENT_ID/
    01 - Raw Shoot-CONTENT_ID
    02 - Edit-CONTENT_ID
    03 - Final Content-CONTENT_ID
  ```
  Root folder name = the Content ID itself; each subfolder name carries the Content ID as a suffix
  for unambiguous identification outside its parent's context.
- **Idempotency**: every folder id is persisted **the instant** that folder is created, and a
  retry always trusts an already-stored id first, falling back to a name+parent lookup on Drive
  itself before ever creating — no retry path can duplicate a folder. Status machine:
  `NOT_STARTED → IN_PROGRESS → SUCCEEDED | FAILED`; retry only permitted from `NOT_STARTED`/
  `FAILED`, and commits `IN_PROGRESS` *before* any Drive API call so a concurrent second retry
  sees `IN_PROGRESS` and refuses.
- **Transaction boundary**: provisioning is explicitly **never** run inside the Content ID's own
  creation transaction — a Drive failure must never roll back or duplicate Content ID creation
  (`@TransactionalEventListener(phase = AFTER_COMMIT)` on `ContentPlanCreatedEvent`, via a
  self-proxy injection to preserve the `@Transactional` boundary across the event-listener call).
- **Employee access / permissions**: the client **never sets any explicit permission** on a
  created folder — a new folder simply inherits its parent's existing Shared-Drive/Workspace
  sharing; it is never made "anyone with the link" or otherwise public by this integration.
- **Links**: `folderUrl(folderId) = "https://drive.google.com/drive/folders/" + folderId` —
  derived on demand, never itself persisted as the canonical identifier (`content_drive_provisioning`
  stores the real folder **ids**; `content_plans.folder_link` is a separate, older free-text field
  kept in sync after successful provisioning, and remains manually editable via PERM_13 for
  repair/relinking of legacy content created before this feature existed).
- **Fallback when disabled**: `DisabledDriveFolderClient` (a no-op implementation) — with Drive
  integration off, `ContentPlan.folderLink` becomes the mandatory-at-approval manual field instead
  (§12).

---

## 23. DEPLOYMENT

**3 separate Docker Compose stacks found** — treat each independently, they are not variations of
one config:
1. Root `docker-compose.yml` + `nginx.conf` — a generic/local stack, `kcpc_prod` DB name by
   default, port `8085` exposed. **Relationship to `deploy/prod` NOT FULLY CONFIRMED** — appears
   to be an older or alternate local-prod-like setup; `deploy/prod` is described (via its own
   comments) as the actual GCP VM deployment target.
2. `deploy/dev/docker-compose.yml` + `deploy/dev/nginx.conf` + `.env.example` — dev-isolated
   stack (own project name/container names/network/DB/volume, per its own header comment,
   **contents not individually re-read in this pass beyond the header summary already captured
   from `deploy/prod`'s comment referencing it**).
3. `deploy/prod/docker-compose.yml` + `deploy/prod/nginx.conf` + `.env.example` — **the real GCP
   VM production target**. Deployed via `deploy/scripts/deploy.sh` from a GitHub Actions workflow
   (`deploy-prod.yml`, **not found/read in this pass — referenced only in a comment**). Pulls a
   pre-built, immutable image from an Artifact Registry (`AR_IMAGE`), pinned to `IMAGE_TAG` (a git
   SHA for every real deploy — the `:prod` floating tag is manual-recovery-only, never the deploy
   target). `SPRING_PROFILES_ACTIVE=docker` for both dev and prod (no separate Spring "prod"
   profile exists).

**Containers per stack**: Postgres 16, the Spring Boot app (built from root `Dockerfile`, or
pulled pre-built for `deploy/prod`), and Nginx 1.27-alpine as reverse proxy.

**Dockerfile**: 2-stage build — `maven:3.9-eclipse-temurin-21` (build) →
`eclipse-temurin:21-jre-jammy` (runtime), runs as a non-root `kcpc` user, entrypoint
`java -jar /app/app.war`, exposes 8080.

**Database privilege split** (`DB-001`, `V13__db_privilege_split_and_truncate_guards.sql`):
`kcpc_migrator` is the schema-owning superuser Postgres container is initialized with (runs Flyway
DDL only); `db/init/01_create_app_role.sh` (**not read in this pass**) then creates the separate,
restricted `kcpc_app` role the application actually connects as at request-serving time — no
UPDATE/DELETE/TRUNCATE on append-only tables even at the grant level (§17).

**Environment variables** (both stacks, `.env`-file driven): `MIGRATOR_DB_PASSWORD`,
`APP_DB_PASSWORD`, `APP_SECURITY_JWT_SECRET` (must be ≥64 bytes, distinct per environment),
`COOKIE_SECURE` (false by default — plain-HTTP-terminating Nginx would otherwise silently drop a
Secure-flagged cookie), `DB_NAME`/`DB_HOST`/`DB_PORT`, `DRIVE_ENABLED`/`DRIVE_SERVICE_ACCOUNT_KEY`/
`DRIVE_SHARED_DRIVE_ID`/`DRIVE_ROOT_FOLDER_ID`/`DRIVE_IMPERSONATE_USER` (prod's must point at "KCPC
PROD CONTENT," never DEV's Drive root — per an explicit comment).

**Persistent vs ephemeral data**:
- **Persistent**: the Postgres data volume — for prod, an **externally pre-created** Docker
  volume (`kcpc_prod_pgdata`, `docker volume create` done out-of-band), deliberately `external:
  true` in the compose file specifically so `docker compose down -v` in that directory can never
  delete it (Compose only removes volumes it created itself) — this is called out as "the primary
  safeguard against an accidental teardown destroying the production database."
- **Ephemeral**: the app container itself (stateless, rebuilt/replaced on every deploy), the Nginx
  container, and (implicitly) any in-memory state.

**Backups / rollback**: `deploy/scripts/backup-postgres.sh`, `restore-postgres.sh`, `deploy.sh`,
`rollback.sh` (**contents not read in this pass** — presence and naming only confirmed).

**Health checks**: `/actuator/health` (permitAll, aggregate UP/DOWN only) — used by both the app
container's own Docker healthcheck (`curl -f`, 30s start period) and Postgres's `pg_isready`
healthcheck; the app container `depends_on: postgres: condition: service_healthy`, and Nginx
`depends_on: kcpc-app: condition: service_healthy` — strict startup ordering.

**3 backup nginx.conf files found in the working tree** (`nginx.conf.bak.20260829_160149` at
root, `deploy/dev/`, and `deploy/prod/`, plus `nginx.conf.live-container.bak.20260829_160149` at
root) — **these are dated backup snapshots from this session's own earlier nginx work, not part of
the deployed configuration**; the live configs are the non-`.bak` `nginx.conf` files.

---

## 24. TESTING

**Java**: JUnit 5 (via `spring-boot-starter-test`) + `spring-security-test`, 83 test classes under
`src/test/java/com/kcpc/mkt/` (flat structure — one class per feature/regression scenario, not
mirroring the main source tree), plus a shared `support/TestApiClient.java` helper and a
`drive/FakeDriveFolderClient.java` test double for Drive-integration tests without real
credentials.

**Run**: `mvn test` (default `test` Spring profile → `kcpc_test` Postgres DB, fully separate from
`kcpc_dev`/`kcpc_prod`). **Known pre-existing issue**: `PlanningSingleFormTest` hangs
deterministically even run in complete isolation (confirmed via repeated kill/rerun cycles this
session, unrelated to any change made) — always excluded from full-suite runs via
`-Dtest='!PlanningSingleFormTest'`.

**Established baseline** (as of the last full-suite run this session, before today's newest
uncommitted changes): 399 tests (excluding the hung one), **exactly 4 pre-existing failures**
(`EditTaskDetailTest`, `ShootTaskDetailTest`, `SubmitIdeaFormTest`×2), 0 errors — this is the
standing "clean" bar every fix in this session was re-verified against. **This number will have
shifted** with tests added in the most recent uncommitted work (Mark Catalogue, Category
Catalogue, per-combo date-floor tests, etc.) — re-run before trusting an exact current count.

**JS tests** (`src/test/js/`, 3 files, dependency-free — no jsdom/npm, hand-rolled DOM shim, real
production JS loaded and executed via Node's built-in `vm` module):
- `my-work-tabs.test.js` — tab-switching regression coverage for `my-work-tabs.js` (nested My Work
  sub-tabs + flat Content Detail/My Shoots tab bars).
- `reviews-workspace-schedule-validation.test.js` — inline Shoot/Edit Date past-date validation
  (7 cases: past/today/future, stage-scoped, Urgent-mode exemption).
- `reviews-workspace-schedule-timezone.test.js` — regression for the `toISOString()`/`Asia/Kolkata`
  display bug fixed this session (runs under `TZ=Asia/Kolkata`).
Run individually: `node src/test/js/<file>.test.js`.

**Notable regression-test classes by concern**:
- Workflow/stage combinations: `IdeaApprovalStagesTest`, `IdeaApprovalUrgentStagesDateValidationTest`,
  `StandardPlanningPastDateValidationTest`, `SkipStageFlowTest`, `WorkflowVariantsE2ETest`,
  `GoldenEndToEndFlowTest`.
- Permissions: `PermissionBoundaryTest`, `PermissionDrivenWorkflowTest`, `PermissionQuickGrantTest`,
  `SelfReviewConflictTest`, `WorkflowParticipationRegressionTest`.
- Date validation: `StandardPlanningPastDateValidationTest`, `IdeaApprovalUrgentStagesDateValidationTest`.
- Marks: `MarkCatalogueTest`, `MyPerformanceTest`, `CorrectionLedgerFlowTest`.
- Routing (task detail): `CompletedWorkViewRoutingTest`, `ModelExecutionTaskRoutingTest`,
  `ModelPermissionBasedTabAndTaskAccessTest`, `ShootExecutionPermissionBasedAccessTest`,
  `MyShootsModelViewRoutingTest`, `EditTaskDetailTest`, `ShootTaskDetailTest`.
- Catalogues: `CategoryCatalogueTest`, `MarkCatalogueTest`.

**Coverage gaps observed**: no test file targets `EditingService`/`PublishingService` symmetry
with `ShootingService` by name (their review-decision/mark-attribution behavior was inferred by
this document from architectural symmetry, not independently confirmed by a dedicated test read in
this pass — worth verifying directly before relying on the "Edit Review" row in §16's table).
`ReportingMvcController`/`KpiService`/`ExportService`/`AdminMvcController`'s user-CSV-import flow
have corresponding test classes (`UserCsvImportTest`, `UserCsvImportMvcFlowTest`, `ExportApiTest`,
`KpiServiceTest`, `KpiDashboardServiceTest`) but their internals were not read in this pass.

---

## 25. IMPORTANT BUSINESS RULES

| Rule ID | Business Rule | Implementation Location | Current Behavior | Notes |
|---|---|---|---|---|
| ERD-CON-063 | Business Role never itself grants an Operational Permission | `BusinessRole` javadoc, `AuthorizationService` (never checks role name) | Enforced | |
| ERD-CON-020 | No duplicate mark attribution to the same recipient for the same review cycle | `uq_personal_mark_attributions_recipient_cycle` (DB) | Enforced at DB level | |
| ERD-CON-058 | Correction/history/participant/audit tables are append-only | Triggers (`V12`/`V13`) + DB privilege split | Enforced at 2 independent layers | |
| BRS-REQ-093 | A Planned Live Date too close to today requires Urgent mode | `IdeaService.approve` | Stage-aware floor: 5d (Shoot), 2d (Edit-only), none (Publishing-only) | Fixed this session from a flat 5d |
| (unnumbered, this session) | A Shoot/Edit Date landing before today is rejected under Standard mode | `IdeaService.approve` | `< today` rejected, `== today` valid | |
| ENG-091 | Exactly 3 valid Stages combinations | `IdeaService.approve` (`validCombo`) | Shoot+Edit+Publishing / Edit+Publishing / Publishing-only; Shoot-without-Edit rejected | |
| ENG-090 | Skip Stage requires PERM_20, distinct from Review authority | `ShootingService.skipShootStage`, `EditingService.skipEditStage` | Holding PERM_05/07 does not imply Skip authority | |
| BRS-REQ-012 | No self-review on delegated authority | `AuthorizationService.requireNoSelfReviewConflict` | Only applies when `actingGrant.isPresent()` — native CEO/MM exempt | |
| ERD-CON-005 | `workflow_instances.first_completed_at` is a one-way flag | DB trigger `trg_workflow_instances_completion_lock` | Enforced at DB level | |
| ENG-067 | A Model's Shoot-side task ends permanently once Shoot phase concludes | `LandingMvcController.isShootTaskCompleted`, reused everywhere | History-based (`APPROVE_SHOOT`/`SKIP_SHOOT_STAGE`), never live status | |
| ENG-036/044 | Publisher assignment has no Lead concept; is native CEO/MM-only | `PublishingService`, `DeliverableMvcController` (`canAssignPublisher = nativeAuthority`) | Unlike Shoot/Edit's Lead+delegatable-assignment model | |
| ENG-094 | Category must match an active catalogue entry if non-blank | `CategoryService.requireActiveNameOrBlank` | Blank always allowed; catalogue-only otherwise | This session, uncommitted |
| ENG-092 | Mark values must match an active Mark Catalogue entry | `MarkCatalogueService.requireActiveValue` | Replaces old hardcoded `[0,0.5,1,2,3]` list, now `[0,0.1,0.5,1]` | This session, uncommitted |

---

## 26. KNOWN RISKS / COUPLING

- **`DeliverableMvcController.view()` is a single ~500-line shared handler** for the generic
  Content Detail shell AND all 3 task-detail screens — any change to its permission/assignment/
  tab-guard branches (§15) risks a cross-stage data leak if the ordering or guard conditions are
  altered carelessly. This exact class of bug has already occurred once this session (the missing
  `tab`-guard letting Publishing's `PP` resting status hijack every `?tab=`).
- **`BUSINESS_ZONE` is duplicated verbatim in 3+ classes** (`IdeaService`, `DeliverableMvcController`,
  `LandingMvcController`) rather than a single shared constant — a future timezone change must
  touch every occurrence.
- **JS date-formatting UTC pitfall is a systemic risk, not a one-off**: any *other* place in this
  codebase that formats a JS `Date` via `.toISOString().slice(0,10)` for a positive-UTC-offset
  audience carries the same latent off-by-one bug found and fixed in
  `reviews-workspace.js` this session — worth a codebase-wide grep before assuming any other
  date-preview code is correct (a `grep -rn toISOString src/main/resources/static/js` was run and
  found only the 2 now-fixed occurrences, but new code could reintroduce the pattern).
- **Shared JSP fragments** (`nav.jsp`, `reviews-ideas.jspf`, `idea-details-modal.jspf`, etc.) are
  included across multiple pages — a change to one ripples to every including page; several are
  already explicitly reused across Reviews/Content Detail/Idea Detail (e.g. the Idea Details modal
  fragment).
- **Shared JS event-delegation root** (`reviews-workspace.js`'s single `#reviewsDynamicRegion`
  listener set) — every new interactive element added inside that region must route through the
  existing delegated listeners or add a new one; a listener added outside the delegation pattern
  would silently not survive an AJAX `innerHTML` swap.
- **Content-ID-based routing was previously a real bug source** (§15) — any new feature that adds
  another way to reach `/app/deliverables/{id}` must go through the existing `?tab=` +
  permission+assignment+status gate, never a bare Content ID link.
- **`content_id_sequences` row-locking** (`SELECT ... FOR UPDATE`, capped at 9999 per business
  month) — exhausted once already in `kcpc_test` this session after many full-suite test runs;
  not a production risk under normal volume, but a real constraint on repeated test-data creation
  in the same month.
- **DB append-only enforcement depends on `kcpc_app` existing as a genuinely separate Postgres
  role** — in a single-role local dev/test setup (the common case per every migration's own
  guard), the privilege-split half of the enforcement silently no-ops; only the trigger layer is
  live locally. Both layers only align in a real `kcpc_migrator`/`kcpc_app`-split environment
  (docker/prod).
- **`MarkCatalogueService`/`CategoryService` and their DB tables (`V36`/`V37`) are present and
  wired end-to-end in the current working tree but are UNCOMMITTED** — treat them as real,
  functioning features when reading the code, but be aware `git log`/`git diff` is the only way to
  see exactly what has and hasn't been committed at any given moment (see git status warning below).
- **My Performance's controller still computes marksSummary/delaySummary/averageMark** even though
  the JSP no longer renders them (§9) — a future JSP change re-adding those blocks would work with
  zero controller changes, but a future controller refactor might mistakenly "clean up" data the
  JSP doesn't currently use without checking whether it's about to be needed again.

---

## 27. CHANGE SAFETY MAP

| Feature | Primary files | Secondary dependencies | DB dependencies | Tests | Risk level |
|---|---|---|---|---|---|
| Idea Review + Planning approval | `IdeaService.approve` | `ContentIdAllocationService`, `DriveProvisioningService`, `MarkCatalogueService`, `CategoryService`, `OperationalEligibilityService` | `content_plans`, `predefined_role_marks`, 3× assignment tables | `IdeaApprovalStagesTest`, `StandardPlanningPastDateValidationTest`, `GoldenEndToEndFlowTest` | **High** — single compound command, many downstream effects |
| Task-detail routing | `DeliverableMvcController.view()` | `LandingMvcController.isShootTaskCompleted` | — (read-only) | `CompletedWorkViewRoutingTest`, `ModelExecutionTaskRoutingTest`, `ShootExecutionPermissionBasedAccessTest` | **High** — proven history of cross-stage leak bugs |
| Permission/authorization core | `AuthorizationService`, `OperationalEligibilityService` | Every service in the app calls through these | `permission_grants*` | `PermissionBoundaryTest`, `PermissionDrivenWorkflowTest` | **High** — touches every feature |
| My Performance calculation | `LandingMvcController.buildPerformanceRows`/`toPerformanceRow` | `MarkCatalogueEntryRepository`, `ActualPublicationEventRepository` | `personal_mark_attributions`, `actual_publication_events` | `MyPerformanceTest` | **Medium** — self-contained, well-tested |
| Reviews inline date validation (JS) | `reviews-workspace.js` | Mirrors `IdeaService.approve`'s backend rule exactly | — (display/UX only) | `reviews-workspace-schedule-*.test.js` | **Low** — backend is the real safety net regardless |
| Mark Catalogue | `MarkCatalogueService`, `AdminMvcController` (`/marks`), `admin-marks.jsp` | `IdeaService` (2 call sites) | `mark_catalogue_entries`, dropped old CHECKs | `MarkCatalogueTest` | **Medium** — CEO-only, but feeds every mark validation |
| Category Catalogue | `CategoryService`, `AdminMvcController` (`/categories`) | `IdeaService.approve` | `categories` | `CategoryCatalogueTest` | **Low** — additive, free-text fallback preserved |
| Google Drive provisioning | `DriveProvisioningService`, `GoogleDriveFolderClient` | `ContentPlanCreatedEvent` (post-commit) | `content_drive_provisioning` | `DriveProvisioningServiceTest`, `DriveProvisioningDisabledFeedbackTest` | **Low in this repo** (disabled by default everywhere); **High if ever enabled** in prod without careful credential handling |
| Deployment (nginx/compose) | `deploy/prod/*`, root `Dockerfile` | GitHub Actions workflow (not in repo scope read) | — | — (no automated test coverage found) | **High** — production-affecting, manual/scripted only |

---

## 28. CURRENT KNOWN ISSUES

Only issues directly observed in this pass or explicitly documented in-code:

1. **`PlanningSingleFormTest` hangs deterministically**, even run in complete isolation — confirmed
   pre-existing, unrelated to any recent change, currently worked around by excluding it from every
   full-suite run. Root cause not investigated (out of scope for every fix this session).
2. **4 standing test failures** (`EditTaskDetailTest`, `ShootTaskDetailTest`,
   `SubmitIdeaFormTest`×2) — treated throughout this session as the accepted "clean" baseline, not
   as bugs to fix; **whether they represent real defects or stale fixtures was not independently
   investigated in this pass**.
3. **`V6__*.sql` and `V33__*.sql` migration numbers are missing** from the sequence with no comment
   explaining the gap — **NOT CONFIRMED** whether this is intentional (e.g. a reverted/abandoned
   migration) or an artifact of this repo's history.
4. **The root `docker-compose.yml`/`nginx.conf` stack's relationship to `deploy/prod` is unclear**
   — two docker-compose configurations both plausibly targeting "production," with the `deploy/`
   directory tree appearing (per its own comments) to be the actually-deployed one. Worth
   clarifying with the team before assuming either is dead code.
5. **My Performance's controller retains dead-but-computed marksSummary/delaySummary/averageMark
   logic** after the JSP was simplified to no longer render those blocks (§9/§26) — not a bug, but
   a latent inconsistency between what's computed and what's shown.
6. **The `Edit` review/mark-attribution symmetry with `Shoot` (§16) was inferred from architectural
   pattern, not independently confirmed by reading `EditingService.decideEditReview`'s full body**
   in this pass — treat the exact trigger-command names (`APPROVE_EDIT`, rework command name) as
   unverified until checked directly.

---

## 29. SOURCE OF TRUTH

| Business rule | Authoritative layer | Notes |
|---|---|---|
| Workflow status / valid transitions | `WorkflowTransitionService` (Java, DB-backed) | The single sanctioned entry point; DB has no independent transition-validity CHECK beyond "not DLY" |
| Stages combination validity | `IdeaService.approve` (Java) | No DB constraint mirrors this — purely application-layer |
| Mark value validity | `MarkCatalogueService` + `mark_catalogue_entries` table (Java service reads DB catalogue at call time) | DB CHECK constraints for the *old* hardcoded list were explicitly dropped (`V36`) — catalogue is now the only gate |
| Category validity | `CategoryService` + `categories` table | Same pattern as Mark Catalogue |
| Date validation (Shoot/Edit/Live) | `IdeaService.approve` (Java, backend) | JS mirror in `reviews-workspace.js` is UX-only, never authoritative — confirmed identical rule, but backend is what actually enforces it |
| Permission grant validity/scope | `AuthorizationService` (Java) reading `permission_grants*` tables | DB enforces referential integrity and effective-window CHECK; scope-coverage logic itself is Java-only |
| Execution eligibility (assignable/executable) | `OperationalEligibilityService` (Java) | Never DB-enforced directly |
| Append-only-ness of correction/history ledgers | **DB triggers** (primary) + Java (never exposes update/delete repository methods, secondary) | DB is authoritative here — even a Java bug cannot bypass the trigger |
| Task-detail screen routing | `DeliverableMvcController.view()` (Java) | No DB or JS layer participates in this decision at all |
| "Completed On" per employee | `LandingMvcController.toPerformanceRow` (Java) | Reads `WorkflowTransitionHistory`/`ActualPublicationEvent` (DB), but the *per-employee* derivation logic itself is Java-only |
| Nav link visibility vs route reachability | `WorkspaceAccessService` (Java) — **same methods** consumed by both `MvcNavigationAdvice` (nav) and `WorkflowParticipationInterceptor` (routes) | Deliberately single-sourced so the two can never disagree |

---

## 30. FINAL SYSTEM MAP

```
USER (browser, JSP-rendered pages + vanilla JS)
 │  cookie-carried JWT (KCPC_AT) on every request
 ↓
NAVIGATION  (fragments/nav.jsp, driven by MvcNavigationAdvice's @ModelAttributes)
 ↓
ROUTE GUARD  (WorkflowParticipationInterceptor — deny-by-default per module, via WorkspaceAccessService)
 ↓
CONTROLLER  (web/mvc/*MvcController for JSP pages, web/rest/*RestController for /api/v1/** JSON)
 │  every handler re-validates authorization independently (never trusts the guard alone)
 ↓
SERVICE  (feature-package services: IdeaService, ShootingService, EditingService, PublishingService,
          PerformanceService, MarkCatalogueService, CategoryService, DriveProvisioningService, ...)
 │  AuthorizationService / OperationalEligibilityService consulted by every write path
 │  WorkflowTransitionService is the single sanctioned status-change entry point
 ↓
REPOSITORY  (Spring Data JPA repositories, one+ per entity)
 ↓
DATABASE  (PostgreSQL, Flyway-owned schema; append-only tables additionally trigger- and
           privilege-enforced; kcpc_dev / kcpc_test / kcpc_prod are 3 fully separate databases)
 ↓
EXTERNAL SERVICES  (Google Drive — optional, disabled by default, folder-provisioning only;
                     no other third-party integration found)
```

### Workflow map (as actually implemented — see §3 for the full state table)

```
Idea Submitted (IS) → Pending Approval (PA)
                         │
        ┌────────────────┼────────────────┐
        ▼ Approve         ▼ Reject          ▼ Retain
  [Stages decide       Rejected (RJ)     Retained (RET)
   target status]       [terminal]        [dormant] ──Reopen──→ back to PA
        │
        ├─ Shoot+Edit+Publishing → Shoot Assigned (SA) → Shoot In Progress (SIP) → Shoot Review (SRV)
        │                             ├─ Approve → Shoot Approved (SAP) → Edit Assigned (EA) [+ Editor team]
        │                             └─ Rework  → back to SIP
        │                          (or Skip Shoot Stage → straight to EA)
        │
        ├─ Direct Edit (Edit+Publishing) → Edit Assigned (EA) directly
        │
        └─ Direct Publishing (Publishing only) → Ready for Publishing (RFP) directly

  EA → Editing (ED) → Edit Review (ERV)
         ├─ Approve → Edit Approved (EAP) → Ready for Publishing (RFP) [+ Publisher team]
         └─ Rework  → back to ED
      (or Skip Edit Stage → straight to RFP)

  RFP → Publishing (PUBG) → [scope resolves] → Performance Pending (PP) → Performance Update (PFUP) → Completed (COMP)

  Any active status → Cancelled (CAN) [terminal, admin action]
  DLY = supplementary "is this late" flag only, never a real status a row can rest in.
```

---

*End of baseline. Verification performed: `git status` confirmed only `APPLICATION_BASELINE.md`
was created — no source file, database, or configuration was modified while producing this
document.*
