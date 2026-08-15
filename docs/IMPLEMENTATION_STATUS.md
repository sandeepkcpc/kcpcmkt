# Implementation Status — KCPC R3.5

Updated at each phase boundary, not per-commit. Statuses: NOT STARTED / IN PROGRESS / IMPLEMENTED / TESTED / BLOCKED.

## Phases

| Phase | Scope | Status |
|---|---|---|
| 0 | Repo inspection, source comprehension | TESTED |
| 1 | Spring Boot foundation, PostgreSQL, Flyway, MVC/JSP, REST, OpenAPI, Docker | TESTED (Docker config written, not build-verified - no Docker daemon in this environment) |
| 2 | Identity, JWT auth + token registry, Business Roles, Operational Permissions, CSRF | TESTED |
| 3 | Workflow core, Idea Submission, Idea Review, Content ID allocation | TESTED |
| 4 | Planning (Standard/Urgent, outputs, folder link, Planning Review) | TESTED |
| 5-6 | Shooting + Editing execution/review + Marks attribution + Hold/Resume | TESTED |
| 7-8 | Publishing + Performance/Scorecard + Completion | TESTED |
| 9-11 | Admin actions, Employee self-service, Administration | TESTED (core API; UI screens deferred, see below) |
| 12-13 | Reporting/KPIs + Export | TESTED (30 of 30 KPIs; JSON/CSV/XLSX export all implemented) |
| 14-15 | Hardening, automated test suite, Docker/deployment, docs | TESTED (see below - scope of hardening is partial, not exhaustive) |

## Implemented APIs

`POST/DELETE /api/v1/auth/login|logout`, `GET /api/v1/auth/me`, `GET /api/v1/csrf`,
`POST/GET /api/v1/ideas`, `POST /api/v1/ideas/{id}/review|reopen`,
`POST /api/v1/ideas/{id}/predefined-marks/corrections`,
`GET/POST /api/v1/content-plans/{id}` + `/parameters|schedule/standard|schedule/urgent|outputs|
shooting-assignments|planning-review/submit|planning-review/decision|hold|resume|reschedule|
reassign|cancel|reopen|reopen-publishing|reopen-performance`,
`POST /api/v1/content-plans/outputs/{id}/publication-scope`,
`POST /api/v1/content-plans/{id}/shooting/start|review/submit|review/decision`,
`POST /api/v1/content-plans/{id}/editing/assignments|start|review/submit|review/decision`,
`POST /api/v1/content-plans/{id}/publishing/start|events|targets/na|targets/na/{id}/reverse`,
`POST /api/v1/publishing/events/{id}/evidence-corrections`,
`POST /api/v1/performance-obligations/{id}/scorecard/draft|submit`,
`POST /api/v1/performance/scorecards/{id}/corrections`,
`POST /api/v1/admin/users`, `.../{id}/activate|deactivate|business-role`,
`GET/POST /api/v1/admin/business-roles`, `.../{id}/deactivate`,
`POST /api/v1/admin/permission-grants`, `.../{id}/modify|revoke`,
`GET /api/v1/me/marks|tasks`, `GET /api/v1/kpis/summary`, `GET /api/v1/export/content-plans/{id}`,
`GET /api/v1/publishing/platforms|channels|targets`, `POST /api/v1/publishing/platforms|channels|targets`,
`PATCH /api/v1/publishing/platforms/{id}|channels/{id}|targets/{id}`,
`GET /api/v1/team/workload`, `GET /api/v1/team/kpis`, `GET /api/v1/reports/kpis`,
`GET /api/v1/reports/administrative-actions`, `GET /api/v1/reports/delayed-deliverables`,
`GET /api/v1/audit/logs`.

## Implemented screens

Core production flow (UI/UX Design Specification v0.1, §9) is now fully built as governed MVC/JSP
screens, all calling the same service layer as the REST controllers (never an HTTP self-call):
`/login`, `/app/home` (role-appropriate dispatch), `/app/my-work` (§9.2, Employee), `/app/pipeline`
(§9.2, CEO/MM), `/app/ideas/new` (§9.3), `/app/ideas` (§9.4), `/app/ideas/{id}` (§9.5, Idea Detail
+ Review Gate + Reopen), and the single Deliverable Detail shell `/app/deliverables/{id}` (§9.6-9.15
- Planning workspace, Planning Review, Shoot task + Shoot Review, Editor Assignment, Edit task +
Edit Review, Publishing workspace, Performance workspace/scorecard draft-submit-correct, and the
cross-cutting Actions menu: Reschedule/Reassign/Cancel/Hold/Resume/Reopen, plus the append-only
Timeline) - one shell whose panels follow workflow status, no panel exposes a manual status
control (Principle 3). Self-review conflict presentation (whole decision block hidden + note,
`SRS-REQ-012`) is implemented for Planning/Shoot/Edit review gates.

v0.2 Group A (Administration - Users, Business Roles, Operational Permissions, Publishing
Catalogue) is also now built. v0.2 Groups B (Analytics & Reporting, §7) and C (Data Export, §8)
are also now built - see "UI screens — v0.1 and v0.2 Groups A/B/C, ALL CLOSED" below.

Verified: `MvcScreenSmokeTest` drives the entire golden path (Idea -> Completed) through real HTML
form POSTs across every panel; found and fixed 2 real defects along the way (ENG-024: two
`LazyInitializationException`s from JSP-read LAZY associations outside the open-in-view-disabled
session; a null-hostile `Collectors.toMap` NullPointerException building the Performance panel's
view model) - the kind of MVC-only bug `mvn compile` and REST-only tests structurally cannot catch.

## Migrations

`V1`..`V11` (core: reference data, identity/auth, workflow, idea, planning,
shooting/editing/marks/hold, publishing/performance, admin-action history), `V12` (CORR-001
correction ledgers + ERD-CON-058 append-only DB triggers on all 15 previously-unprotected
append-only tables plus the 3 new ledgers), `V13` (DB-001 closure: `work_hold_records` hard-DELETE
rejection, `TRUNCATE` guards, and the Postgres privilege/GRANT split granting the restricted
`kcpc_app` runtime role exactly `SELECT`/`INSERT` on append-only tables), plus
`db/migration-demo/V6__demo_users.sql` (dev-profile only, never applied to docker/prod).

## Automated tests

38 JUnit tests, all passing, run against a real PostgreSQL database (`kcpc_test`, the `test`
Spring profile) over real HTTP - not MockMvc shortcuts, not an in-memory substitute:

- `KcpcMktApplicationTests` — Spring context loads (schema/security/JPA mapping validity)
- `GoldenEndToEndFlowTest` — the full build-prompt §40 lifecycle, Idea → Completed, one test
- `AuthenticationFlowTest` — bad credentials, missing-cookie access, missing-CSRF rejection,
  login → logout → server-side token revocation (proven via raw-cookie replay, not just
  client-side cookie deletion); an unmapped `/app/**` URL returns a real 404 while authenticated,
  not a false login redirect (regression guard for `ENG-032`)
- `SelfReviewConflictTest` — a delegated Employee reviewer is blocked from deciding on their own
  submitted idea; a different delegated reviewer is not
- `CorrectionLedgerFlowTest` (2 tests) — predefined Mark correction chaining + active-value update
  + controlled-list/mandatory-reason validation; publication evidence correction + performance
  metric correction both preserving the original immutable record; sealed-scorecard-required guard
  on metric corrections; and a raw-JDBC proof that `UPDATE`/`DELETE` against append-only correction
  tables is rejected at the Postgres level (`ERD-CON-058`), independent of the application layer
- `DbIntegrityEnforcementTest` (2 tests) — `work_hold_records` hard DELETE rejected at the DB
  level (`ERD-CON-062` rule 9) via a real Hold placed through the API, then a raw-JDBC delete
  attempt; `TRUNCATE` rejected on append-only tables (`ERD-CON-058`)
- `MvcScreenSmokeTest` — the entire golden path driven through real HTML form POSTs against the
  MVC/JSP screens (not the JSON REST API), every Deliverable Detail panel, Idea -> Completed
- `AdminMvcScreenSmokeTest` — v0.2 Group A screens (Users, Business Roles, Operational Permissions,
  Publishing Catalogue) driven through real HTML form POSTs: create user, grant permission,
  create platform/channel, create business role
- `ReportsGroupBMvcScreenSmokeTest` — v0.2 Group B/C screens (Team Workload, Team KPI, 30-KPI
  Console, Admin-Action Report, Delayed Deliverables, Audit History, Export) driven through real
  HTTP GETs; asserts every screen returns 200 with no exception markers and that the KPI console
  renders exactly 30 `kpi-tile` elements - the permanent regression guard for ENG-031 (the
  record/JSP-EL incompatibility bug, see "UI screens" above)
- `ReportingApiSecurityTest` (2 tests) — asserts no bcrypt password hash ever appears in any
  reporting response body (regression guard for ENG-029); all 30 governed KPIs compute without
  error and every new reporting/team endpoint returns 200
- `ExportApiTest` (4 tests) — JSON export covers the governed table union and never leaks identity
  tables or password hashes; CSV requires exactly one (governed) table; XLSX returns a valid
  workbook; export is management-only (403 for a non-CEO/MM user)
- `HighPriorityEdgeCaseTest` (5 tests) — two qualifying Camerapersons/Editors each receive the
  FULL predefined Mark (never split/averaged); an expired delegated permission grant is rejected
  (403 `PERM_OPERATIONAL_PERMISSION_EXPIRED`); a revoked grant is rejected (403, correctly
  classified `PERM_OPERATIONAL_PERMISSION_REQUIRED` since a revoked grant is invisible to the
  active-grants query - not the same code path as an expired-but-still-fetched grant); a
  `STAGE_RESTRICTED` grant used outside its covered stage is rejected (403
  `PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE`)
- `WorkflowVariantsE2ETest` (8 tests) — golden-path variants: Idea Reject (mandatory-reason guard
  + closed-gate re-decision rejected 409); Idea Retain + administrative Reopen back to Pending
  Approval; Planning Review rework (Retain/Reopen at the Planning gate, mandatory-reason guard,
  resubmit-and-approve); the exactly-5-day Standard/Urgent boundary (BRS-REQ-093: `+5` days
  succeeds Standard, `+4` days is rejected 400 and requires Urgent Planning Mode); Hold blocking a
  Shoot Review submission (409), a second Hold while one is open (409, `ERD-CON-062`), then Resume
  unblocking it; the Performance Due Date gate (`ERD-CON-016`: publishing "now" leaves the due date
  in the future, and a scorecard draft attempt is rejected 400); multiple publication events on one
  output (Repost while still `PUBG`, Target N/A designation resolving scope to `PP`, then an N/A
  reversal); and a Completed deliverable's Reopen for Publishing landing on `PUBG` (regression
  guard for the `AdminActionService` target-status bug found and fixed earlier this project)
- `PermissionBoundaryTest` (4 tests) — an Employee with no delegated grant is forbidden (403) from
  deciding an Idea Review and from every Planning-execution write; Shooting/Publishing start
  attempted out of workflow order is rejected (409, before Planning Review approval / before Ready
  for Publishing); deactivating a user revokes their already-issued session cookie immediately
  (401 on the next request, not just on the next login attempt)

**This is now a substantially expanded but still not exhaustive test suite.** Golden-path variants
(Section 7) and a representative slice of permission/workflow-guard negative paths (Section 6) are
now covered end-to-end. What remains for full build-prompt §39 exhaustiveness: negative-path tests
for every remaining domain action not yet exercised here (e.g. Cancel, Reschedule, Reassign
individually; Editing-specific rework; concurrency/race conditions; every one of the 17 governed
permissions individually rather than the representative Planning/Idea-Review pair tested above).
Backfilling that last layer is tracked as remaining work, not silently claimed complete.

**A genuinely important bug was found and fixed by this test suite** (not by the extensive prior
manual `curl` testing, which structurally could not have caught it): see ENG-017 in
`docs/IMPLEMENTATION_DECISIONS.md`. CSRF tokens were silently rotated by Spring Security's
default session-authentication-strategy on every authenticated request, which would have broken
any real client that primes a CSRF token once and reuses it (the intended pattern for the
`GET /api/v1/csrf` endpoint, ENG-012) - manual `curl` scripts never revealed this because each
one re-read the cookie jar fresh immediately before every call.

## Golden end-to-end path — verified (manually, then automated)

The full lifecycle (build-prompt §40) was first exercised via scripted HTTP calls against a real
PostgreSQL instance, then captured as `GoldenEndToEndFlowTest`: Login → Submit Idea → Approve
Idea (Content ID + Content Plan + predefined Marks allocated atomically) → Planning
parameters/schedule/output/publication-scope/Cameraperson assignment → Planning Review
submit+approve (→ Shoot Assigned) → Start Shooting → Shoot Review submit+approve (Cameraperson
Mark attributed) → Editor assignment → Start Editing → Edit Review submit+approve (Editor Mark
attributed, → Ready for Publishing) → Start Publishing → record Actual Publication (scope resolved
→ Performance Pending) → Scorecard draft (correct Hook/Hold/CTR-N/A calculations once past the
due date, → Performance Update) → Scorecard submit → **Completed**
(`workflow_instances.first_completed_at` set).

The Hold/Resume-blocks-review-submit variant and the Performance-Due-Date-blocks-early-scorecard-
entry variant are exercised separately by `WorkflowVariantsE2ETest` (they are true *variants* of
the golden path, not steps every deliverable takes, so they are kept out of the one linear
golden-path test and covered as their own dedicated scenarios instead - see "Automated tests"
above for the full list of variants now covered).

## KPI coverage — 30/30 CLOSED

`GET /api/v1/reports/kpis` (`API-OP-058`) now computes all 30 governed KPIs (BFD §7), grouped
exactly as SRS-REQ-071..075 define them - Operational (001-007), Productivity (008-011), Content &
Published Unit (012-020), Approval & Review (021-024), Delay/SLA/On-Time (025-030). The legacy
`GET /api/v1/kpis/summary` (14 KPIs, bespoke non-code-labeled shape) is kept for backward
compatibility with its one existing caller; `/api/v1/reports/kpis` is the spec-exact surface.

Every duration/average KPI (021, 025, 027, 028) and every distribution KPI (006, 015, 016, 029)
that were previously deferred are now implemented via native SQL against the operational tables
(BR-039: "computed on demand... no persistent KPI tables"). `docs/IMPLEMENTATION_DECISIONS.md`
ENG-027 records the handful of genuine judgment calls this required (e.g. which timestamp anchors
KPI-028's cycle time, what "approved" means for KPI-013/014) with their BFD grounding.

Also newly implemented this pass: `GET /api/v1/team/workload` (`API-OP-056`, Perm #14),
`GET /api/v1/team/kpis` (`API-OP-057`, Perm #15), `GET /api/v1/reports/administrative-actions`
(`API-OP-059`, Perm #16, BFD §7.6), `GET /api/v1/reports/delayed-deliverables` (`API-OP-060`, with
the governed Employee read-scope restriction), and `GET /api/v1/audit/logs` (`API-OP-061`,
Perm #16). Personal-performance views (BFD §7.7) were already covered by the Phase 9-11
`/api/v1/me/*` endpoints.

**A real security defect was found and fixed while building the Audit Log endpoint**: returning
`SystemAuditLog` entities directly serialized the actor's bcrypt password hash into the JSON
response body (via the entity's EAGER `User actor` association). Fixed by (1) projecting through a
new `AuditLogResponse` DTO instead of the raw entity, and (2) marking `User.getPasswordHash()`
`@JsonIgnore` as defense-in-depth against the same mistake anywhere else in the API surface.
`ReportingApiSecurityTest` locks this in permanently. See ENG-029.

## Export formats — CLOSED

`GET /api/v1/exports` (`API-OP-062`) now implements all three governed formats (SAD-ADR-008):
JSON (one object per governed table), CSV (single-table, `text/csv`), and XLSX (multi-table
workbook, one sheet per table, via the previously-unused `poi-ooxml` dependency). Scoped to the
exact governed table union (RTM-081: `ERD-TBL-007`..`039`, `ERD-TBL-041`..`043` - workflow, idea,
planning, production, marks, publishing, performance, admin-action history) via a fixed allowlist
that is never derived from caller input (prevents SQL-injection through a table-name parameter and
guarantees identity/permission tables - `users`, `permission_grants`, `business_roles` - can never
be exported). Management-only (`SRS-REQ-081`: CEO/MM native authority; no delegated permission
exists for export). The pre-existing per-Content-Plan full-graph JSON export
(`GET /api/v1/export/content-plans/{id}`) is unchanged and kept alongside it.

Verified end-to-end (`ExportApiTest`, 4 tests) against real data: all three formats produce valid
output, CSV correctly rejects multi-table requests, an unknown/non-governed table name is
rejected, and a non-management user is forbidden.

## UI screens — v0.1 and v0.2 Groups A/B/C, ALL CLOSED

UI/UX Design Specification v0.1 (core production flow, §9) is fully built - see "Implemented
screens" above. v0.2 **Group A** (Operational Governance - write, §6) is fully built:
`/app/admin/users` (list + create, §6.1), `/app/admin/users/{id}` (detail: Business Role, account
status, and the Operational-Permission grant/modify/revoke panel, §6.1/§6.2), `/app/admin/catalogue`
(Platform/Channel/Target master-catalogue management, §6.3), `/app/admin/business-roles`
(§6.4), and `/app/admin/permissions` (read-only, all-users consolidated "who currently holds what
permission" list - an additive extension of §6.2's per-user read-through, added by user request;
see `ENG-033`). Verified end-to-end by `AdminMvcScreenSmokeTest`.

**A real backend gap was found and closed while building §6.3**: Permission #17
(`PERM_17_PLATFORM_CATALOGUE_MANAGE`) was defined in the governed catalogue but never checked
anywhere, and `API-OP-036`/`066`..`070` (Platform/Channel/Target create/update/deactivate) simply
did not exist - only read-only repository lookups were wired internally. Added
`MasterCatalogueService` (Permission #17-gated create/update/deactivate for all three master
objects, mandatory `catalogueReason`, soft deactivate only, uniqueness enforcement) and
`MasterCatalogueRestController` at the exact frozen paths, backing both the new REST surface and
the new MVC screen.

**v0.2 Group B (Analytics & Reporting, §7) and Group C (Data Export, §8) are now also fully
built**, via `ReportingMvcController`, all calling the exact same `KpiService`/
`AdminReportingService`/`SystemAuditLogRepository`/`MultiFormatExportService` application layer as
the equivalent REST controllers (`ReportingRestController`, `AuditRestController`,
`ExportRestController`) - never an HTTP self-call: `/app/reports/workload` (§7.1, Perm #14 -
Team Workload), `/app/reports/team-kpis` (§7.2, Perm #15 - Team KPI), `/app/reports/kpis` (§7.3,
Perm #15 - the full 30-governed-KPI console, grouped by the same 5 BFD categories as the API),
`/app/reports/admin-actions` (§7.4, Perm #16 - Administrative Actions Report), `/app/reports/delayed`
(§7.5 - Delayed Deliverables, server-side Employee read-scope restricted per SRS-REQ-002/066/067,
no separate MVC gate needed since the service itself scopes the rows), `/app/audit` (§7.6, Perm
#16 - Audit-History Viewer), and `/app/export` (§8, CEO/MM-only - Data Export screen offering
JSON/CSV/XLSX against the governed table allowlist).

**A real defect was found and fixed while live-testing these screens against a running server, not
by compiling or by the existing REST-focused test suite**: `/app/reports/kpis` and `/app/audit`
both returned a misleading `302` redirect to `/login?reason=auth` - looking exactly like an
authentication failure - because `KpiValue`, `AuditLogResponse`, and `DelayedDeliverableRow` were
originally Java `record`s, and classic JSP EL's `BeanELResolver` does not recognize a record's
no-prefix canonical accessors (`displayValue()`, `eventTimestamp()`) as JavaBean properties, only
`getX()`-style accessors; the resulting uncaught `PropertyNotFoundException` was swallowed by the
security filter chain and re-surfaced as what looked like an auth failure. Fixed by converting all
three JSP-rendered DTOs to plain classes with explicit getters. See ENG-031. Verified live: all 7
screens return 200 with zero exception markers; the KPI console renders exactly 30 `kpi-tile`
elements; the Delayed Deliverables screen was confirmed to render an actual populated row (not
just an empty-list false-pass) after forcing a real content plan into a delayed state.

## DB-privilege append-only enforcement — CLOSED (Docker runtime verification pending)

See DB-001 (closed) in `docs/IMPLEMENTATION_DECISIONS.md`. `V12`/`V13` add `BEFORE UPDATE/DELETE`
and `TRUNCATE` triggers rejecting mutation on every append-only table (`ERD-CON-058`) and the
`work_hold_records` hard-DELETE guard (`ERD-CON-062` rule 9), proven by `CorrectionLedgerFlowTest`
and `DbIntegrityEnforcementTest`'s raw-JDBC assertions. The Postgres role/GRANT split
(`kcpc_migrator` vs restricted `kcpc_app`) is fully configured (`docker-compose.yml`,
`db/init/01_create_app_role.sh`, `V13` Part B, split `spring.flyway.*`/`spring.datasource.*` in
the `docker` profile) and the GRANT/REVOKE mechanism itself was verified locally against a
throwaway non-superuser role. Actually exercising the real `kcpc_migrator`/`kcpc_app` split inside
a running container is UNVERIFIED — DOCKER DAEMON NOT AVAILABLE (see "Known gap — Docker build
verification" below); local dev/test intentionally keeps its pre-existing single superuser role.

## Correction ledgers — CLOSED

`predefined_mark_corrections`, `publication_evidence_corrections`, `performance_metric_corrections`
(ERD-TBL-026/027/028) implemented in `V12`, with services, REST endpoints at the exact frozen
paths, and `CorrectionLedgerFlowTest`. See CORR-001 (closed) in `docs/IMPLEMENTATION_DECISIONS.md`.

## Known gap — Docker build verification

`Dockerfile`, `docker-compose.yml`, `db/init/01_create_app_role.sh`, and `nginx.conf` were written
against the governed stack (SAD deployment topology, ADR-010) plus the DB-001 role split, but
`docker compose up --build` has not been run in this environment (no Docker daemon available).
Compilation and all runtime behavior were instead verified by running the application directly via
`mvn spring-boot:run` against local PostgreSQL, and the privilege-split *mechanism* was separately
verified with a throwaway non-superuser Postgres role (see DB-001). Docker/Compose now requires
two secrets in `.env` - `MIGRATOR_DB_PASSWORD` and `APP_DB_PASSWORD` - replacing the old single
`DB_PASSWORD`; this itself is UNVERIFIED — DOCKER DAEMON NOT AVAILABLE, same as the rest of this
section.

**Manual verification checklist for Sandeep (once Docker is available):**
```bash
cp .env.example .env   # set MIGRATOR_DB_PASSWORD, APP_DB_PASSWORD, APP_SECURITY_JWT_SECRET
docker compose up --build
docker compose exec db psql -U kcpc_migrator -d kcpc_prod -c "\du"          # expect kcpc_migrator (superuser) + kcpc_app (not superuser)
docker compose exec db psql -U kcpc_app -d kcpc_prod -c "UPDATE system_audit_log SET event_type='x' WHERE false;"  # expect: permission denied
curl -i http://localhost/api/v1/csrf                                        # through Nginx -> app
```

## Discrepancies

See `docs/IMPLEMENTATION_DISCREPANCIES.md`: DISC-001, a pre-existing (Phase 9-11), systemic
path-convention deviation in Domain 8 (Hold/Resume/Reschedule/Reassign/Cancel/Reopen) discovered
while implementing CORR-001 - functionally correct, URL shape only, does not block other work.

## Decisions

See `docs/IMPLEMENTATION_DECISIONS.md` for all engineering decisions (ENG-001..030), including
one important bug found and fixed by the automated test suite (ENG-017), one workflow-target bug
found and fixed while implementing CORR-001 (ENG-019), and deferred items (DB-001 - now closed
except Docker-runtime verification, CORR-001 - now closed, TEST-001 - now partially resolved by
the test suite above).
