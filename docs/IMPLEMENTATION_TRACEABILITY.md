# Implementation Traceability — KCPC R3.5

Module/test-level tracing (build-prompt §41), not per-line.

## Phase 1-2: Foundation, Identity, Auth

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `SAD-ADR-001`, `ERD-TBL-001/002` | JWT-in-cookie auth, server-side token registry | `security/JwtService`, `security/TokenRegistryService`, `identity/domain/User`, `identity/domain/UserSession` |
| `SRS-REQ-003`..`005` | Account status, deactivation revokes tokens | `TokenRegistryService.revokeAllActiveSessionsForUser`, `identity/domain/User.deactivate` |
| `ERD-TBL-003/044`, `ERD-CON-063`, `SRS-REQ-092` | 3 access classes + expandable Business Role catalogue | `identity/domain/AccessClass`, `identity/domain/BusinessRole`, `V1__reference_data.sql` |
| `ERD-TBL-004/005`, `SRS-REQ-006`..`013` | 17 Operational Permissions, scoped grants | `identity/domain/OperationalPermission`, `PermissionGrant`, `PermissionGrantStageScope`, `PermissionGrantItemScope`, `identity/service/AuthorizationService` |
| CSRF rule (Development Handoff §"Security rules") | CSRF on every unsafe cookie-authenticated request | `security/SecurityConfig` (`CookieCsrfTokenRepository`), `web/rest/CsrfRestController` |
| `SAD-DES-006`, `ERD-TBL-025` | Append-only system audit log | `audit/domain/SystemAuditLog`, `audit/service/AuditService`, `common/repository/InsertOnlyRepository` |

## Phase 3: Workflow core, Idea, Content ID

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `ERD-TBL-006/007/008`, `ERD-CON-004/033/034` | Workflow FSM, append-only transition history | `workflow/domain/WorkflowStatus`, `WorkflowInstance`, `WorkflowTransitionHistory`, `workflow/service/WorkflowTransitionService` |
| `BRS-REQ-014/015`, `AC-014.x` | Idea Submission form/fields, Idea ID generation | `idea/domain/Idea`, `idea/service/IdeaService.submit`, `web/rest/IdeaRestController`, `web/mvc/IdeaMvcController`, `idea-submit.jsp` |
| `BRS-REQ-016`..`019`, `ERD-CON-011/012/030/039/059` | Idea Review gate (Approve/Reject/Retain), self-review barrier, Reopen | `idea/service/IdeaService.decide/reopen`, `workflow/domain/ReviewCycle`, `identity/service/AuthorizationService.requireNoSelfReviewConflict` |
| `BRS-REQ-020`, `ERD-TBL-042`, `ERD-CON-036/038` | Atomic Content ID allocation at Idea Approval | `planning/service/ContentIdAllocationService`, `planning/domain/ContentIdSequence` |
| `SAD-DES-010`, `ERD-TBL-010`, `ERD-CON-009/055/064/065/066` | Content Plan row created atomically with Content ID | `planning/domain/ContentPlan` |
| `BRS-REQ-016`, `ERD-TBL-012`, `ERD-CON-010` | Predefined role Marks captured at Idea Approval | `marks/domain/PredefinedRoleMarks` |

## Phase 4: Planning

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `BRS-REQ-021`, `ERD-CON-009/055` | Category, Priority, SKU N/A exclusion, Talent | `planning/domain/ContentPlan`, `ContentPlanTalentEntry`, `planning/service/PlanningService.updateParameters` |
| `BRS-REQ-027/086`, `ERD-CON-064/065/066` | Standard/Urgent scheduling, date chronology | `PlanningService.setStandardSchedule/setUrgentSchedule`, `ContentPlan.setPlanningSchedule*` |
| `BRS-REQ-023/024`, `ERD-CON-007/008/054` | Planned Outputs, Reel Type | `planning/domain/PlannedOutput` |
| `BRS-REQ-025`, `ERD-CON-031/032` | Publication scope mapping | `PlannedOutputPublicationTargetMapping`, `masterdata/domain/*` |
| `BRS-REQ-022`, `ERD-CON-013` | Initial Cameraperson assignment (Planning-only window) | `PlanningService.assignCameraperson`, `production/domain/ShootingAssignment` |
| `ERD-CON-026`, `BRS-REQ-012`/`ERD-CON-011` | Planning Review submit/decide, self-review barrier | `PlanningService.submitPlanningReview/decidePlanningReview`, `planning/domain/PlanningPreparer` |

## Phase 5-6: Shooting, Editing, Marks, Hold/Resume

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `BRS-REQ-031/033`, `ERD-CON-011/059` | Shoot Review gate, self-review barrier | `production/service/ShootingService`, `ShootingExecutionParticipant` |
| `BRS-REQ-034/036/037`, `ERD-CON-013` | Editor assignment post-Shoot-Approval, Edit Review gate | `production/service/EditingService`, `EditingAssignment`, `EditingExecutionParticipant` |
| `BRS-REQ-016/033/037`, `ERD-CON-010/020` | Full (non-split) predefined Mark attribution to qualifying contributors | `marks/domain/PersonalMarkAttribution`, `ShootingService.decideShootReview`, `EditingService.decideEditReview` |
| `BR-063`, `SRS-REQ-091`, `ERD-CON-061/062` | Hold/Resume, primary status untouched | `workflow/domain/WorkHoldRecord`, `workflow/service/HoldService` |

## Phase 7-8: Publishing, Performance, Completion

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `BRS-REQ-042/044`, `ERD-CON-014/015` | Actual Publication events, multi-event per Content ID | `publishing/domain/ActualPublicationEvent`, `publishing/service/PublishingService.recordActualPublication` |
| `BRS-REQ-045`, `ERD-CON-017/040/056/057` | Target N/A designate/reverse, all-N/A prohibition | `PublicationTargetNaRecord`, `PublishingService.designateTargetNA/reverseTargetNA/wouldLeaveAllTargetsNa` |
| BFD status #16-18 | RFP→PUBG→PP transitions, scope resolution | `PublishingService.startPublishing/isScopeResolved` |
| `BRS-REQ-048/049`, `ERD-CON-016/048` | Performance obligation, D+2 non-reschedulable due date | `performance/domain/PerformanceObligation` |
| `BRS-REQ-050/051`, `SC-REQ-001/002`, `ERD-CON-028/060` | Scorecard draft/submit, N/A rate derivation, seal-on-submit | `performance/domain/CreativePerformanceScorecard`, `performance/service/PerformanceService` |
| BFD status #19-20 | PP→PFUP (due-date-gated) → COMP, `first_completed_at` | `PerformanceService.maybeAdvanceToPerformanceUpdate/maybeComplete`, `WorkflowInstance.markFirstCompleted` |

## Phase 9-11: Admin actions, Employee self-service, Administration

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `SRS-REQ-056`, `ERD-TBL-029` | Reschedule (Permission #10), Performance Due Date untouched | `workflow/service/AdminActionService.reschedule`, `planning/domain/ContentPlan.applyReschedule` |
| `SRS-REQ-057`, `ERD-TBL-030/031`, `ERD-CON-045/046` | Reassign (Permission #11), previous/new assignee history | `AdminActionService.reassign`, `workflow/domain/ReassignmentRecord/ReassignmentAssignee` |
| `SRS-REQ-058/059`, `ERD-TBL-032`, `ERD-CON-006` | Cancel (Permission #12), blocked once ever Completed | `AdminActionService.cancel`, `WorkflowInstance.everCompleted` |
| `SRS-REQ-019/053/054`, `ERD-TBL-033` | Reopen (Retained via #1; Completed via #8/#9) | `IdeaService.reopen`, `AdminActionService.reopenCompleted` |
| `BRS-REQ-003..005`, `SRS-REQ-092` | Exclusive CEO user/Business-Role administration | `identity/service/UserAdminService`, `BusinessRoleAdminService` |
| `BRS-REQ-006..013`, `API-OP-065` | Exclusive CEO permission-grant lifecycle | `identity/service/PermissionGrantAdminService` |
| `BRS-REQ-066..070` | Employee self-service, strict peer privacy | `web/rest/MySelfServiceRestController` (every query scoped to the authenticated principal) |

## Phase 12-13: Reporting/KPIs, Export (superseded — see Phase 16 for the closed 30/30 state)

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| BFD §7 (14 of 30 KPIs, legacy) | KPI summary, Permission #15 | `reporting/service/KpiService.summary` (kept for backward compatibility with its one existing caller) |
| `SAD-ADR-008` (JSON half) | Full-graph export per Content ID | `reporting/service/ExportService` |

## Phase 14: Correction Ledgers (CORR-001), DB-level append-only/privilege enforcement (DB-001)

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| `ERD-TBL-026`, `API-OP-033` | Predefined-Mark correction ledger (Permission #1), original never overwritten | `marks/domain/PredefinedMarkCorrection`, `IdeaService.correctPredefinedMarks`, `V12__correction_ledgers.sql` |
| `ERD-TBL-027`, `API-OP-041` | Publication-evidence correction ledger (Permission #8) | `publishing/domain/PublicationEvidenceCorrection`, `PublishingService.correctEvidenceUrl` |
| `ERD-TBL-028`, `API-OP-046` | Performance-metric correction ledger (Permission #9), sealed-scorecard-required guard | `performance/domain/PerformanceMetricCorrection`, `PerformanceService.correctMetrics` |
| `ERD-CON-058` | `UPDATE`/`DELETE` rejected at the Postgres level on every append-only table (15 pre-existing + 3 new ledgers) | `V12__correction_ledgers.sql` (`trg_reject_update_delete()`), proven by `CorrectionLedgerFlowTest` raw-JDBC assertions |
| `ERD-CON-062` rule 9 | `work_hold_records` hard-DELETE rejected at the DB level | `V13__db_privilege_split_and_truncate_guards.sql`, proven by `DbIntegrityEnforcementTest` |
| `ERD-CON-058` (TRUNCATE) | `TRUNCATE` rejected on every append-only table (`FOR EACH STATEMENT` trigger) | `V13__db_privilege_split_and_truncate_guards.sql` (`trg_reject_truncate()`), proven by `DbIntegrityEnforcementTest` |
| DB-001 (Postgres role/GRANT split) | Restricted runtime `kcpc_app` role (SELECT/INSERT only on append-only tables) separate from `kcpc_migrator` schema owner | `docker-compose.yml`, `db/init/01_create_app_role.sh`, `V13` Part B, split `spring.flyway.*`/`spring.datasource.*` in `application.yml`'s `docker` profile — code/config-complete; **container runtime execution UNVERIFIED, no Docker daemon in this environment** |

## Phase 15: MVC/JSP UI (UI/UX v0.1 core flow + v0.2 Groups A/B/C)

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| UI/UX v0.1 §9 | Core production-flow screens: shell/nav, My Work, Pipeline, Idea submit/list/detail, single Deliverable Detail shell (all workflow-status panels + Actions bar + Timeline) | `web/mvc/LandingMvcController`, `IdeaMvcController`, `DeliverableMvcController`, corresponding JSPs — every handler calls the same service layer as its REST counterpart (no HTTP self-calls) |
| UI/UX v0.2 §6 (Group A) | User & Business Role Administration, Operational-Permission Administration, Publishing Catalogue management (Permission #17, closed as part of this work) | `web/mvc/AdminMvcController`, `masterdata/service/MasterCatalogueService`, `web/rest/MasterCatalogueRestController`, `admin-users.jsp`/`admin-user-detail.jsp`/`admin-business-roles.jsp`/`admin-catalogue.jsp` |
| UI/UX v0.2 §7 (Group B) | Team Workload, Team KPI, 30-KPI Console, Admin-Action Report, Delayed Deliverables, Audit-History Viewer | `web/mvc/ReportingMvcController`, `reports-workload.jsp`/`reports-team-kpis.jsp`/`reports-kpi-console.jsp`/`reports-admin-actions.jsp`/`reports-delayed.jsp`/`audit-history.jsp` |
| UI/UX v0.2 §8 (Group C) | Data Export screen (JSON/CSV/XLSX, governed table allowlist, CEO/MM-only) | `ReportingMvcController.exportScreen`, `export.jsp`, `reporting/service/MultiFormatExportService` |
| — | JSP EL / Java `record` incompatibility (`ENG-031`): any DTO read directly by a JSP must be a plain class with `getX()` accessors | `reporting/dto/KpiValue`, `audit/dto/AuditLogResponse`, `reporting/dto/DelayedDeliverableRow` |

## Phase 16: Full 30-KPI coverage, Team/Admin/Delayed/Audit reporting, Multi-format Export (closed)

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| BFD §7, `API-OP-058` | All 30 governed KPIs (Operational 001-007, Productivity 008-011, Content & Published Units 012-020, Approval & Review 021-024, Delay/SLA/On-Time 025-030), BR-039 stage-context planned-date `CASE` | `reporting/service/KpiService.queryGovernedKpis` (`STAGE_PLANNED_DATE_CASE`), `web/rest/ReportingRestController` |
| `API-OP-056/057` | Team Workload (Permission #14), Team KPI (Permission #15) | `KpiService.teamWorkload/teamKpis` |
| `API-OP-059`, BFD §7.6 | Administrative Actions Report (Permission #16), grouped by `system_audit_log` category | `reporting/service/AdminReportingService.administrativeActionsReport` |
| `API-OP-060` | Delayed Deliverables, Employee read-scope restricted (SRS-REQ-002/066/067, `ENG-028` documents the known GLOBAL-only scope simplification) | `AdminReportingService.delayedDeliverables` |
| `API-OP-061`, Permission #16 | Audit-History Viewer, narrow `AuditLogResponse` projection (never the raw entity, `ENG-029` security-fix context) | `web/rest/AuditRestController` |
| `API-OP-062`, `SAD-ADR-008`, `SRS-REQ-080/081` | Multi-format Export (JSON/CSV/XLSX) against the fixed governed-table allowlist, management-only | `reporting/service/MultiFormatExportService`, `web/rest/ExportRestController` |

## Testing

| Governed ID(s) | Capability | Implementation |
|---|---|---|
| Build-prompt §40 | Golden E2E flow, automated | `GoldenEndToEndFlowTest.java` |
| Build-prompt §39 (auth subset) | Bad credentials, missing cookie, missing CSRF, logout revocation (incl. server-side replay proof) | `AuthenticationFlowTest.java` |
| `BRS-REQ-012` / `ERD-CON-011` | Delegated self-review conflict, both forbidden and permitted paths | `SelfReviewConflictTest.java` |
| `ERD-CON-058`, CORR-001 | Correction-ledger chaining, original-record immutability, DB-trigger proof | `CorrectionLedgerFlowTest.java` |
| `ERD-CON-062` rule 9, `ERD-CON-058` (TRUNCATE) | Hard-DELETE and TRUNCATE rejection at the Postgres level | `DbIntegrityEnforcementTest.java` |
| UI/UX v0.1 §9 | Entire golden path driven through real HTML form POSTs against MVC/JSP screens | `MvcScreenSmokeTest.java` |
| UI/UX v0.2 §6 (Group A) | Admin screens driven through real HTML form POSTs | `AdminMvcScreenSmokeTest.java` |
| UI/UX v0.2 §7/§8 (Groups B/C) | Reporting/Export screens, ENG-031 record/JSP-EL regression guard, 30-KPI-tile render assertion | `ReportsGroupBMvcScreenSmokeTest.java` |
| `ENG-029` | No bcrypt hash ever leaks via a reporting response; all 30 KPIs + team endpoints return 200 | `ReportingApiSecurityTest.java` |
| `API-OP-062`, `SRS-REQ-080/081` | JSON/CSV/XLSX export correctness, governed-table scoping, management-only 403 | `ExportApiTest.java` |
| Build-prompt §7 (golden-path variants) | Reject, Retain/Reopen, Planning Review rework, Urgent Planning, exactly-5-day boundary, Hold/Resume blocking a review, Performance Due Date gate, multi-event/Target-N/A/Repost, Completed-deliverable Reopen for Publishing | `WorkflowVariantsE2ETest.java` |
| Build-prompt §39 (permission/workflow-guard subset) | Unauthorized-Employee 403 (Idea Review, Planning execution), out-of-order Shooting/Publishing start 409, immediate session revocation on deactivation | `PermissionBoundaryTest.java` |

Verification for Phases 1-8: manual/scripted HTTP smoke flows (see `docs/IMPLEMENTATION_STATUS.md`
"Known gap — tests"); the golden path (login -> submit idea -> approve idea -> Content ID +
Content Plan + Marks persisted) was exercised directly against PostgreSQL and confirmed. Phases
14-16 were each verified the same way this project has verified every phase: automated JUnit over
real HTTP against real PostgreSQL, *plus* live `curl`/cookie-jar smoke testing of the actually-
running application (dev profile) for every new screen and endpoint — the combination is what
caught the LazyInitializationException, NPE, Hibernate native-param-parsing, bcrypt-leak, and
record/JSP-EL defects documented in `docs/IMPLEMENTATION_DECISIONS.md` (`ENG-024`, `ENG-027`
through `ENG-031`).
