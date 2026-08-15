# 10 — Requirement / Test Traceability

**Honesty statement first:** this matrix maps the *major* governed requirement clusters this project actually touches to the tests that exercise them. It is **not** a literal one-row-per-atomic-requirement mapping of every `BRS-REQ`/`AC`/`ERD-CON` ID in the frozen documents (that would be several hundred additional rows and was not attempted). Where a cluster of related IDs is covered by the same test(s), they're grouped. **Do not read this as "100% of the specification is traced" — it isn't, and no line below claims that.** Anything not listed here should be treated as **NOT TESTED** until it's added.

**Status values:** `PASS` (verified, currently passing), `NOT TESTED` (no automated or manual evidence exists yet), `BLOCKED` (cannot be tested due to a dependency), `FAIL` (verified and currently failing — should also be in the Defect Log).

---

## Authentication & Session

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `SAD-ADR-001`, Development Handoff "Security rules" | JWT-in-cookie auth, per-request server-side revalidation | UAT-AUTH-001..006 | `AuthenticationFlowTest` (5) | PASS |
| `SRS-REQ-003`..`005` | Deactivation revokes all active sessions immediately | UAT-AUTH-005 | `AuthenticationFlowTest`, `PermissionBoundaryTest` | PASS |
| CSRF rule (Development Handoff) | CSRF enforced on every unsafe cookie-authenticated request | — | `AuthenticationFlowTest.postWithoutCsrfTokenIsRejected` | PASS |

## Business Roles / Access Classes / Permissions

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `ERD-TBL-003/044`, `ERD-CON-063`, `SRS-REQ-092` | 3 access classes, expandable Business Role catalogue | UAT-ROLE-001..004 | `AdminMvcScreenSmokeTest` | PASS (creation path); role-deactivation NOT TESTED |
| `ERD-TBL-004/005`, `SRS-REQ-006`..`013` | 17 governed permissions, scoped grants (GLOBAL/STAGE_RESTRICTED/ITEM_SPECIFIC) | UAT-PERM-001..005, `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md` §3/§4 | `PermissionBoundaryTest`, `HighPriorityEdgeCaseTest` (Permission #2 only) | PASS for #1/#2's full 11-dimension matrix; **NOT TESTED for the expired/revoked/out-of-scope/self-review dimensions on permissions #3–#12, #14–#17** — see doc 04 §4 for the exact open cells |
| `BRS-REQ-003`..`005` | Exclusive CEO user administration | UAT-ADMIN-SCREEN-001..005 | `AdminMvcScreenSmokeTest` | PASS (create/grant); deactivate/reactivate/role-change NOT TESTED |

## Idea Management

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `BRS-REQ-014/015`, `AC-014.x` | Idea Submission, ID generation | UAT-IDEA-001..004 | `GoldenEndToEndFlowTest`, `WorkflowVariantsE2ETest` | PASS |
| `BRS-REQ-016`..`019`, `ERD-CON-011/012/030/039/059` | Idea Review gate (Approve/Reject/Retain), self-review barrier, Reopen | UAT-IDEA-REV-001..009 | `GoldenEndToEndFlowTest`, `WorkflowVariantsE2ETest`, `SelfReviewConflictTest` | PASS |
| `BRS-REQ-020`, `ERD-TBL-042`, `ERD-CON-036/038` | Atomic Content ID allocation at Idea Approval | UAT-IDEA-REV-001, UAT-PLAN-003 | `GoldenEndToEndFlowTest` | PASS (allocation mechanism); format visual-confirmation NOT TESTED |
| `BRS-REQ-016`, `ERD-TBL-012`, `ERD-CON-010` | Predefined role Marks captured at Idea Approval; correction ledger | UAT-IDEA-REV-001, UAT-IDEA-REV-009 | `GoldenEndToEndFlowTest`, `CorrectionLedgerFlowTest` | PASS |

## Planning

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `BRS-REQ-021`, `ERD-CON-009/055` | Category, Priority, SKU N/A, Talent | UAT-PLAN-004..006 | — | NOT TESTED (no dedicated automated test for these three fields specifically) |
| `BRS-REQ-027/086/093`, `ERD-CON-064/065/066` | Standard/Urgent scheduling, date chronology, 5-day boundary | UAT-PLAN-001..002, UAT-EDGE-001..011 | `WorkflowVariantsE2ETest` (boundary + Urgent) | PASS for the boundary and Standard/Urgent switch itself; NOT TESTED for chronology rejection (Shoot>Edit, Edit>Live), past-live-date rejection, same-day Urgent, mandatory Urgent fields |
| `BRS-REQ-023/024`, `ERD-CON-007/008/054` | Planned Outputs, Reel Type mandatory-only-for-Reel | UAT-PLAN-007..010, UAT-EDGE-012..015 | — | NOT TESTED at HTTP level (validation confirmed by direct source-code inspection only) |
| `BRS-REQ-025`, `ERD-CON-031/032` | Publication scope mapping | UAT-PLAN-011 | `GoldenEndToEndFlowTest` | PASS |
| `BRS-REQ-022`, `ERD-CON-013` | Initial Cameraperson assignment (Planning-only window), multiple Camerapersons | UAT-PLAN-013..015 | `GoldenEndToEndFlowTest`, `HighPriorityEdgeCaseTest` (multi-cameraperson) | PASS for assignment + multi-assignment; NOT TESTED for the "blocked outside Planning" guard specifically |
| `ERD-CON-026`, `BRS-REQ-012`/`ERD-CON-011` | Planning Review submit/decide, completeness gate, self-review barrier | UAT-PLAN-016..018 | `WorkflowVariantsE2ETest` (rework variant), `GoldenEndToEndFlowTest` | PASS |

## Shooting / Editing / Marks / Hold

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `BRS-REQ-031/033`, `ERD-CON-011/059` | Shoot Review gate, self-review barrier | UAT-SHOOT-001..005 | `GoldenEndToEndFlowTest`, `PermissionBoundaryTest` (out-of-order 409) | PASS |
| `BRS-REQ-034/036/037`, `ERD-CON-013` | Editor assignment post-Shoot-Approval, Edit Review gate | UAT-EDIT-001..007 | `GoldenEndToEndFlowTest` | PASS for the golden path; the "blocked before Shoot Approval" guard specifically NOT TESTED at HTTP level |
| `BRS-REQ-016/033/037`, `ERD-CON-010/020` | Full (non-split) Mark attribution to every qualifying contributor | UAT-SHOOT-006, UAT-EDIT-008, UAT-EDGE-018/019 | `HighPriorityEdgeCaseTest` (both new this UAT-prep pass) | PASS |
| `BR-063`, `SRS-REQ-091`, `ERD-CON-061/062` | Hold/Resume, primary status untouched | UAT-ADMIN-001..004, UAT-EDGE-020 | `WorkflowVariantsE2ETest`, `DbIntegrityEnforcementTest` | PASS |

## Publishing / Performance / Completion

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `BRS-REQ-042/044`, `ERD-CON-014/015` | Actual Publication events, multi-event per Content ID, Repost | UAT-PUB-004..006, UAT-EDGE-025 | `GoldenEndToEndFlowTest`, `WorkflowVariantsE2ETest` (Repost) | PASS |
| `BRS-REQ-045`, `ERD-CON-017/040/056/057` | Target N/A designate/reverse, all-N/A prohibition | UAT-PUB-007..009, UAT-EDGE-026 | `WorkflowVariantsE2ETest` | PASS |
| API-OP-041 | Publication evidence correction | UAT-PUB-010 | `CorrectionLedgerFlowTest` | PASS |
| BFD status #16-18 | RFP→PUBG→PP transitions, scope resolution | UAT-PUB-004, UAT-PUB-011 | `GoldenEndToEndFlowTest`, `WorkflowVariantsE2ETest` | PASS |
| `BRS-REQ-048/049`, `ERD-CON-016/048` | Performance obligation, D+2 non-reschedulable due date | UAT-PERF-001..002, UAT-EDGE-022 | `WorkflowVariantsE2ETest` (due-date gate) | PASS for the gate itself; the "reschedule doesn't touch due date" negative check specifically NOT TESTED |
| `BRS-REQ-050/051`, `SC-REQ-001/002`, `ERD-CON-028/060` | Scorecard draft/submit, N/A rate derivation, seal-on-submit | UAT-PERF-003..007, UAT-EDGE-027/028 | `GoldenEndToEndFlowTest`, `CorrectionLedgerFlowTest` | PASS |
| BFD status #19-20 | PP→PFUP→COMP, `first_completed_at` | UAT-PERF-008 | `GoldenEndToEndFlowTest` | PASS |

## Administrative Actions

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `SRS-REQ-056`, `ERD-TBL-029` | Reschedule, old/new values preserved, due date untouched | UAT-ADMIN-005, UAT-EDGE-021/022 | `WorkflowVariantsE2ETest`, `AdminActionService` mechanism | PASS (mechanism); UI display of old/new pair NOT TESTED |
| `SRS-REQ-057`, `ERD-TBL-030/031`, `ERD-CON-045/046` | Reassign, previous/new assignee history | UAT-ADMIN-006, UAT-EDGE-023 | — (only reachable indirectly as setup) | NOT TESTED as a standalone assertion |
| `SRS-REQ-058/059`, `ERD-TBL-032`, `ERD-CON-006` | Cancel, blocked once ever Completed | UAT-ADMIN-007..008, UAT-EDGE-024 | — | NOT TESTED at HTTP level (guard confirmed by source-code inspection) |
| `SRS-REQ-019/053/054`, `ERD-TBL-033` | Reopen (Retained via #1; Completed via #8/#9) | UAT-IDEA-REV-005, UAT-ADMIN-009..010 | `WorkflowVariantsE2ETest` (Idea Reopen, Reopen-for-Publishing) | PASS for Idea Reopen and Reopen-for-Publishing; Reopen-for-Performance NOT TESTED at HTTP level |

## Employee Self-Service / Privacy

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| `BRS-REQ-066`..`070` | Employee self-service, strict peer privacy | UAT-SELF-001..003, UAT-PRIV-001..010 | — (structural: self-service endpoints scoped to caller by construction) | NOT TESTED as an explicit assertion — this is the single largest privacy-specific gap in the automated suite |
| `SRS-REQ-002`/`066`/`067` | Delayed Deliverables Employee read-scope | UAT-PRIV-010 | `AdminReportingService` mechanism | PASS (GLOBAL-scope path); conservative under-exposure for narrower scopes is a known, accepted simplification (`ENG-028`), not a gap |

## Reporting / KPI / Export / Audit

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| BFD §7, `API-OP-058` | All 30 governed KPIs compute | UAT-KPI-003 | `ReportingApiSecurityTest` | PASS (compute-without-error for all 30) |
| BFD §7.3, KPI-017..020 | Ideas Submitted/Approved/Rejected/Approval-Rate value correctness | UAT-KPI-004 | manual, hand-verified against raw SQL this UAT-prep pass | PASS (4 of 30 KPIs value-verified; the other 26 are compute-without-error only — see `TEST_REPORT_2026-08-15.md`) |
| `API-OP-056/057` | Team Workload, Team KPI | UAT-KPI-001..002 | `ReportsGroupBMvcScreenSmokeTest` | PASS (renders); value-correctness NOT TESTED |
| `API-OP-059` | Administrative Actions Report | UAT-KPI-006 | `ReportsGroupBMvcScreenSmokeTest` | PASS (renders) |
| `API-OP-060` | Delayed Deliverables | UAT-KPI-005 | `ReportsGroupBMvcScreenSmokeTest` | PASS (renders with real data) |
| `API-OP-061`, Permission #16 | Audit-History Viewer, no password leak | UAT-AUDIT-001..003 | `ReportingApiSecurityTest` | PASS |
| `API-OP-062`, `SRS-REQ-080/081` | Multi-format Export (JSON/CSV/XLSX), management-only | UAT-KPI-007..010 | `ExportApiTest` | PASS |

## MVC / UI Screens (existence + no-exception rendering)

| Governed ID(s) | Requirement | Manual UAT Test | Automated Test | Status |
|---|---|---|---|---|
| UI/UX v0.1 §9 | Core production-flow screens | full Golden Flow, doc 02 | `MvcScreenSmokeTest` | PASS |
| UI/UX v0.2 §6 | Group A admin screens (Users, Business Roles, Permissions, Catalogue) | UAT-ADMIN-SCREEN-*, UAT-ROLE-*, UAT-PUB-001..003 | `AdminMvcScreenSmokeTest` | PASS |
| UI/UX v0.2 §7/§8 | Group B/C reporting & export screens | UAT-KPI-* | `ReportsGroupBMvcScreenSmokeTest` | PASS |
| UI conformance — status display | Human-readable status names, not raw codes | `07_STATUS_WORKFLOW_GLOSSARY.md` §3 | — (fixed in code; no dedicated automated assertion of the *displayed text* exists, only that the screen renders without error) | PASS by direct inspection; recommend a dedicated automated assertion be added |

## Known open discrepancies (not test gaps — genuine spec-vs-implementation deviations)

| ID | Governed ID(s) | Status |
|---|---|---|
| `DISC-001` | `API_Specification.md` §17.8 Domain 8 path convention | Open, logged, low-impact (cosmetic path only) |
| `DISC-002` | Permission #13 (`PERM_13_FOLDER_LINK_MANAGE`) never independently enforced | Open, logged, real permission-model gap |

---

## Overall traceability completeness

**Do not read any summary number here as "X% of the specification is tested."** This matrix covers the requirement clusters this project's automated and manual testing has actually touched — a large majority of the *core lifecycle* (Idea through Completion, the 30 KPIs' existence, export, admin screens) is `PASS`, while a substantial and explicitly enumerated set of **boundary rules, negative paths, and 15 of 17 permissions' expired/revoked/out-of-scope dimensions remain `NOT TESTED`**. Treat every `NOT TESTED` row above as real, open work — not as an oversight in this document.
