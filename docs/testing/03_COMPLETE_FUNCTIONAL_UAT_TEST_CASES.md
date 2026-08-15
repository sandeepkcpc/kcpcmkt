# 03 — Complete Functional UAT Test Cases

Every test case below uses real, confirmed routes and real test accounts (see `06_TEST_DATA_AND_USER_ACCOUNTS.md`). **"Automated?" column:** `YES` means an existing automated JUnit test already proves this over real HTTP/DB (see `docs/IMPLEMENTATION_STATUS.md` — 38 tests) — manual execution still adds value (real browser vs. scripted HTTP) but is not the *only* evidence. `NO` means this is genuinely only covered by manual UAT today — treat these with more scrutiny.

A functional defect is different from a visual/UX improvement — see `01_TESTING_OVERVIEW_AND_STRATEGY.md` §6. This MVP's styling is intentionally basic; that alone is never a failure.

---

## A. Authentication (UAT-AUTH-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-AUTH-001 | P0 | Valid login | `POST /login` (via `/login` form) with a correct CEO email/password | Redirected to `/app/pipeline` | YES | [ ] |
| UAT-AUTH-002 | P0 | Bad credentials rejected | Submit wrong password | Login rejected, error shown, stays on `/login` | YES | [ ] |
| UAT-AUTH-003 | P0 | Logout | Click **Sign out** while logged in | Session ends, redirected to `/login`; the app is no longer reachable without logging in again | YES | [ ] |
| UAT-AUTH-004 | P1 | Direct URL access without login | While logged out, navigate directly to `/app/pipeline` | Redirected to `/login` | YES | [ ] |
| UAT-AUTH-005 | P1 | Deactivated account cannot log in / stays logged out | CEO deactivates a test user (`/app/admin/users/{id}` → Deactivate); that user's existing session (if any) is immediately invalidated, and a fresh login attempt fails | Both the live session and any new login attempt are rejected | YES | [ ] |
| UAT-AUTH-006 | P2 | A broken/unmapped URL does not falsely look like a logout | While logged in, navigate to any nonexistent `/app/...` URL | A real "not found" (404) page appears — you are still logged in immediately after (confirm by clicking a real nav link) | YES | [ ] |

## B. Business Roles and Access Classes (UAT-ROLE-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-ROLE-001 | P1 | View Business Role catalogue | CEO → `/app/admin/business-roles` | List shows all seeded roles (CEO, Marketing Manager, HR Manager, Camera Person, Video Editor, Marketing Coordinator, CEO's Executive Assistant, Publisher, Model, Senior Manager, ...) each with its resolved Access Class | NO | [ ] |
| UAT-ROLE-002 | P1 | Create a new Business Role | CEO → Create Business Role form → Role Name, Access Class = EMPLOYEE | New role appears in the list | YES (`AdminMvcScreenSmokeTest`) | [ ] |
| UAT-ROLE-003 | P2 | Deactivate a Business Role | CEO → deactivate a non-in-use role | Role marked inactive; no longer offered when creating a user | NO | [ ] |
| UAT-ROLE-004 | P0 | Access Class is read-only, derived from Business Role | Open any user's detail page | Access Class field is shown but not independently editable — only the Business Role selection changes it | NO | [ ] |

## C. Operational Permissions (17 governed permissions) (UAT-PERM-*)

See `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md` for the full permission-by-permission negative-path matrix. Functional (positive-path) coverage here:

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-PERM-001 | P0 | Grant a delegated permission | CEO → open a user → Grant Permission → choose permission, GLOBAL scope, reason → Grant | Grant appears on that user's detail page, and on the consolidated `/app/admin/permissions` screen | YES | [ ] |
| UAT-PERM-002 | P1 | Modify a grant's expiry | CEO → existing grant → Modify → new expiry date + reason | Expiry updates, grant still shows Active | NO | [ ] |
| UAT-PERM-003 | P0 | Revoke a grant | CEO → existing grant → Revoke + reason | Grant marked Revoked; the underlying row is never deleted (soft revoke) | YES | [ ] |
| UAT-PERM-004 | P1 | Consolidated Permissions screen | CEO → `/app/admin/permissions` | Every currently active grant across all users is listed with User, Permission #, Scope, Granted By, Effective dates | YES | [ ] |
| UAT-PERM-005 | P1 | Delegated Employee can perform the granted action | Log in as Sanya Kapoor (Coordinator, holds Permission #1+#2) → approve an Idea someone else submitted | Decision succeeds under delegated (not native) authority | NO (only the negative/self-review path is automated) | [ ] |

## D. Idea Submission (UAT-IDEA-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-IDEA-001 | P0 | Submit an idea | Any logged-in user → `/app/ideas/new` → Title (required) → Submit | New idea appears with a generated code `IDEA-YYYYMMDD-####` | YES | [ ] |
| UAT-IDEA-002 | P1 | Title is mandatory | Submit with Title blank | Rejected (client-side required attribute; also server-enforced) | NO (server-side rule is code-reviewed, not HTTP-tested standalone) | [ ] |
| UAT-IDEA-003 | P2 | Optional fields | Submit with Reference Link and Notes/Remarks filled | Both fields saved and visible on Idea Detail | NO | [ ] |
| UAT-IDEA-004 | P2 | Any access class may submit | Submit as CEO, as MM, and as a plain Employee | All three succeed — submission has no permission gate | NO | [ ] |

## E. Idea Review (UAT-IDEA-REV-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-IDEA-REV-001 | P0 | Approve → Content ID + Content Plan + Marks created atomically | Reviewer decides Approve with both Marks chosen | Status → Planning; Content ID generated; predefined Marks stored | YES | [ ] |
| UAT-IDEA-REV-002 | P0 | Reject requires a reason | Attempt Reject with reason blank | Rejected by the system (400) — reason is mandatory | YES | [ ] |
| UAT-IDEA-REV-003 | P0 | Reject with reason | Reject with a reason filled in | Status → Rejected (terminal) | YES | [ ] |
| UAT-IDEA-REV-004 | P1 | Retain does not require a reason | Retain with reason left blank | Succeeds; status → Retained | YES | [ ] |
| UAT-IDEA-REV-005 | P1 | Reopen a Retained idea | CEO/MM/Permission#1 holder → Reopen on a Retained idea | Status → Pending Approval again | YES | [ ] |
| UAT-IDEA-REV-006 | P0 | A decided idea's review gate is closed | Attempt a second decision on an already-Rejected idea | Rejected by the system (409 — invalid transition) | YES | [ ] |
| UAT-IDEA-REV-007 | P0 | Self-review conflict | A delegated reviewer (e.g. Sanya Kapoor) attempts to decide on an idea **they themselves submitted** | Blocked (403) — self-review is prohibited on the delegated path | YES | [ ] |
| UAT-IDEA-REV-008 | P1 | Self-review does not block a different reviewer | Sanya Kapoor submits an idea; a *different* delegated/native reviewer decides it | Succeeds | YES | [ ] |
| UAT-IDEA-REV-009 | P1 | Predefined Marks correction | CEO corrects the Cameraperson/Editor Marks on an already-approved idea (Permission #1 action), with a reason | New correction row created; original Marks preserved; current effective value updates | YES | [ ] |

## F. Planning (UAT-PLAN-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-PLAN-001 | P0 | Standard schedule | Live date ≥ 5 days out → Set Standard Schedule | Shoot = Live−5, Edit = Live−2 (defaults); both overridable | YES (defaults + boundary — see doc 05) | [ ] |
| UAT-PLAN-002 | P0 | Urgent schedule | Live date < 5 days out, provide Urgency Reason + manual Shoot/Edit dates | Accepted under Urgent mode | YES | [ ] |
| UAT-PLAN-003 | P1 | Content ID format | Inspect any approved idea's Content ID | Matches `C-MMDD-####` | NO (visual confirmation) | [ ] |
| UAT-PLAN-004 | P1 | Content Priority | Set Priority = LOW / MEDIUM / HIGH | Saved and shown on Pipeline/Deliverable Detail | NO | [ ] |
| UAT-PLAN-005 | P2 | Category (free text) | Set a Category value | Saved | NO | [ ] |
| UAT-PLAN-006 | P2 | SKU / SKU N/A | Set a SKU reference, or check SKU Not Applicable | Only one of the two is meaningfully set at a time | NO | [ ] |
| UAT-PLAN-007 | P0 | Planned Outputs — Photography | Add Output Type = Photography | Saved with no Reel Type field required | YES | [ ] |
| UAT-PLAN-008 | P0 | Planned Outputs — Reel requires Reel Type | Add Output Type = Reel, Reel Type left blank | Rejected — Reel Type is mandatory for Reel outputs | NO (see doc 05 for the governed rule) | [ ] |
| UAT-PLAN-009 | P0 | Planned Outputs — Reel with Reel Type | Add Reel + Reel Type = Short/Very Short/Long | Accepted | NO | [ ] |
| UAT-PLAN-010 | P1 | Planned Outputs — Video | Add Output Type = Video | Accepted, no Reel Type applicable | NO | [ ] |
| UAT-PLAN-011 | P0 | Publication Scope mapping | Map a Planned Output to Instagram · kcpcbandhani | Mapping appears; feeds Publishing-stage scope resolution later | YES | [ ] |
| UAT-PLAN-012 | P2 | Content Asset Folder Link | Set the Folder Link field | Saved; required before Planning Review submission | YES (as part of golden path setup) | [ ] |
| UAT-PLAN-013 | P0 | Initial Shooting Assignment window | Assign a Cameraperson while status = Planning | Succeeds | YES | [ ] |
| UAT-PLAN-014 | P1 | Initial Shooting Assignment blocked outside Planning | Attempt the same assignment once status has moved past Planning | Rejected (409) | NO (assignment guard is a different endpoint than the tested Shoot/Publish guards — worth a dedicated manual check) | [ ] |
| UAT-PLAN-015 | P1 | Multiple Camerapersons | Assign two Camerapersons to the same deliverable during Planning | Both appear as active assignments simultaneously | YES | [ ] |
| UAT-PLAN-016 | P0 | Planning Review submission requires complete parameters | Attempt Submit for Planning Review before Priority/Dates/Folder Link are all set | Rejected — all four are mandatory | YES | [ ] |
| UAT-PLAN-017 | P0 | Planning Review approval requires an active Cameraperson | Attempt to approve Planning Review with zero active Cameraperson assignments | Rejected | YES | [ ] |
| UAT-PLAN-018 | P1 | Planning Review rework | Reviewer requests rework with a reason | Status returns to Planning; parameters remain (re-submit after adjusting) | YES | [ ] |

## G. Shooting (UAT-SHOOT-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-SHOOT-001 | P0 | Start Shooting | From Shoot Assigned, click Start Shooting | Status → Shoot In Progress | YES | [ ] |
| UAT-SHOOT-002 | P0 | Start Shooting blocked out of order | Attempt Start Shooting while still in Planning | Rejected (409) | YES | [ ] |
| UAT-SHOOT-003 | P0 | Shoot Review submit | Submit for Shoot Review | Status → Shoot Review | YES | [ ] |
| UAT-SHOOT-004 | P0 | Shoot Review approval → Cameraperson Mark(s) | Approve, select qualifying Cameraperson(s) | Status → Shoot Approved; Mark(s) attributed | YES | [ ] |
| UAT-SHOOT-005 | P1 | Shoot Review rework → no Mark | Request Rework instead of Approve | No Mark attribution created for this cycle | NO (structurally implied by code — worth a direct manual check) | [ ] |
| UAT-SHOOT-006 | P0 | Two qualifying Camerapersons each get the FULL Mark | Both assigned Camerapersons qualify at approval | Both receive the identical full predefined Mark — never split or averaged | YES | [ ] |

## H. Editing (UAT-EDIT-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-EDIT-001 | P0 | Editor Assignment blocked before Shoot Approval | Attempt to assign an Editor while status < Shoot Approved | Rejected (409) | NO (implied by golden-path ordering; worth a direct manual attempt) | [ ] |
| UAT-EDIT-002 | P0 | Editor Assignment | Assign an Editor once Shoot Approved | Status → Edit Assigned | YES | [ ] |
| UAT-EDIT-003 | P1 | Multiple Editors | Assign two Editors to the same deliverable | Both appear as active assignments | YES | [ ] |
| UAT-EDIT-004 | P0 | Start Editing | From Edit Assigned, Start Editing | Status → Editing | YES | [ ] |
| UAT-EDIT-005 | P0 | Edit Review submit | Submit for Edit Review | Status → Edit Review | YES | [ ] |
| UAT-EDIT-006 | P0 | Edit Review approval → Editor Mark(s), status → Ready for Publishing | Approve, select qualifying Editor(s) | Mark(s) attributed; status → Ready for Publishing | YES | [ ] |
| UAT-EDIT-007 | P1 | Edit Review rework → no Mark | Request Rework instead of Approve | No Mark attribution for this cycle | NO | [ ] |
| UAT-EDIT-008 | P0 | Two qualifying Editors each get the FULL Mark | Both assigned Editors qualify at approval | Both receive the identical full predefined Mark — never split | YES | [ ] |

## I. Publishing (UAT-PUB-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-PUB-001 | P1 | Publishing Catalogue — create Platform | CEO/MM/Perm#17 → `/app/admin/catalogue` → add a Platform | Appears in the catalogue | YES | [ ] |
| UAT-PUB-002 | P1 | Publishing Catalogue — create Channel | Add a Company Channel | Appears in the catalogue | YES | [ ] |
| UAT-PUB-003 | P2 | Publishing Catalogue — create/activate Target | Add a Publication Target pairing a Platform + Channel | Appears, selectable in Publication Scope mapping | NO | [ ] |
| UAT-PUB-004 | P0 | Start Publishing | From Ready for Publishing, Start Publishing | Status → Publishing | YES | [ ] |
| UAT-PUB-005 | P0 | Record Original publication event | Record Actual Publication, Event Type = Original, with evidence URL | Event recorded; performance obligation auto-created (due date = event date + 2 days) | YES | [ ] |
| UAT-PUB-006 | P1 | Repost | Record a second event, Event Type = Repost, same output/target, **while still in Publishing status** | Event recorded; a new performance obligation is created; **no additional personal Marks awarded** | YES | [ ] |
| UAT-PUB-007 | P0 | Target N/A designation | Designate a mapped target N/A with a reason | Recorded; contributes to scope resolution as if that target were "done" | YES | [ ] |
| UAT-PUB-008 | P1 | Target N/A reversal | Reverse a prior N/A designation with a reason | A new linked reversal row is recorded (the original designation is not deleted) | YES | [ ] |
| UAT-PUB-009 | P0 | All-targets-N/A prohibited | Attempt to designate the *last remaining* live-or-pending target N/A, leaving zero live posts and zero pending targets | Rejected | YES | [ ] |
| UAT-PUB-010 | P1 | Evidence correction | Correct a recorded event's evidence URL, with a reason | New linked correction row created; original event row unchanged | YES | [ ] |
| UAT-PUB-011 | P0 | Scope resolution advances the deliverable | Once every mapped output/target pair is live-or-N/A | Status → Performance Pending | YES | [ ] |

## J. Performance (UAT-PERF-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-PERF-001 | P0 | Performance Due Date gate | Attempt to save a scorecard draft before the due date (event date + 2 days) | Rejected — "cannot be entered before the Performance Due Date" | YES | [ ] |
| UAT-PERF-002 | P0 | Scorecard draft after due date | Save a draft once past the due date | Draft saved; status → Performance Update on the first eligible entry | YES | [ ] |
| UAT-PERF-003 | P0 | Derived metric: Hook Rate | Enter Views(3s)=800, Plays=1000 | Hook Rate = 80.00% | YES | [ ] |
| UAT-PERF-004 | P0 | Zero-denominator metric shows N/A, not 0 | Mark Link Clicks as N/A (checkbox), or leave Impressions at a value that would divide by zero | CTR displays as **N/A**, never a numeric `0` and never a calculation error | YES | [ ] |
| UAT-PERF-005 | P0 | Scorecard final submit | Submit the completed draft | Scorecard becomes read-only/sealed; obligation marked completed | YES | [ ] |
| UAT-PERF-006 | P0 | Submitted scorecard is immutable | Attempt to edit a submitted scorecard's fields directly | No direct-edit path exists — only the Correction action (below) can adjust a submitted scorecard | YES | [ ] |
| UAT-PERF-007 | P1 | Metric correction | CEO/Perm#9 holder corrects a submitted scorecard's metric with a reason | New linked correction row created; original submitted scorecard row unchanged; correction requires the scorecard to already be submitted | YES | [ ] |
| UAT-PERF-008 | P0 | Completion | Every performance obligation on the deliverable is submitted | Status → Completed; `first_completed_at` timestamp set | YES | [ ] |

## K. Administrative Actions (UAT-ADMIN-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-ADMIN-001 | P0 | Hold | While Shoot In Progress or Editing, place a Hold with a reason | Hold recorded; primary workflow status is **not** touched | YES | [ ] |
| UAT-ADMIN-002 | P0 | Hold blocks review submission | Attempt to submit for Shoot/Edit Review while an open Hold exists | Rejected (409) | YES | [ ] |
| UAT-ADMIN-003 | P1 | Duplicate Hold rejected | Attempt to place a second Hold while one is already open | Rejected (409) | YES | [ ] |
| UAT-ADMIN-004 | P0 | Resume | Resume a held deliverable | Hold closed; blocked actions become available again | YES | [ ] |
| UAT-ADMIN-005 | P1 | Reschedule | Change planned Shoot/Edit/Live dates with a reason | Old and new values both preserved historically; the Performance Due Date (once set) is unaffected by any reschedule | YES (mechanism) / NO (dedicated old/new-value display check) | [ ] |
| UAT-ADMIN-006 | P1 | Reassign | Reassign the Cameraperson or Editor task to a different user, with a reason | Prior assignment ends (retained historically, not deleted); new assignment becomes active | NO (only reachable as setup in other automated tests, not directly asserted) | [ ] |
| UAT-ADMIN-007 | P0 | Cancel | Cancel an in-progress deliverable, with a reason | Status → Cancelled (terminal) | NO (reachable via code review; not yet a dedicated HTTP test) | [ ] |
| UAT-ADMIN-008 | P0 | Cancel blocked once ever Completed | Attempt to Cancel a deliverable that has ever reached Completed | Rejected | NO | [ ] |
| UAT-ADMIN-009 | P1 | Reopen Completed for Publishing | On a Completed deliverable, Reopen for Publishing with a reason | Status → Publishing (not Ready-for-Publishing) | YES | [ ] |
| UAT-ADMIN-010 | P1 | Reopen Completed for Metric Correction | On a Completed deliverable, Reopen for Performance with a reason | Status → Performance Update | NO (the Publishing-reopen sibling is automated; the Performance-reopen path is not yet its own test) | [ ] |

## L. Employee Self-Service (UAT-SELF-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-SELF-001 | P0 | My Work shows only my own tasks | Log in as Rohan Kapoor → `/app/my-work` | Only Rohan's assigned Shoot/Edit tasks and submitted ideas are shown | YES (structural — golden-path smoke covers rendering; privacy assertion is manual) | [ ] |
| UAT-SELF-002 | P0 | My Work never exposes another employee's data | Compare what Rohan Kapoor sees vs. what Ananya Verma sees | No overlap of "belongs to the other person" rows | NO | [ ] |
| UAT-SELF-003 | P1 | Personal Marks visible to self | Rohan Kapoor's My Work / task detail | His own attributed Marks are visible | NO | [ ] |

See `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md` §Privacy for the full negative-path privacy matrix.

## M. Management / Pipeline (UAT-MGMT-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-MGMT-001 | P0 | Content Pipeline (CEO/MM landing) | Log in as CEO or MM | Lands on `/app/pipeline`, a full table of content plans with status, priority, and Delayed/Hold flags | YES | [ ] |
| UAT-MGMT-002 | P1 | Pipeline shows Delayed flag correctly | A deliverable whose current-stage planned date has passed | Delayed flag/chip visible | NO | [ ] |
| UAT-MGMT-003 | P1 | Pipeline shows Hold flag correctly | A deliverable with an open Hold | Hold flag/chip visible | NO | [ ] |

## N. Users, Business Roles, Permission Administration (UAT-ADMIN-SCREEN-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-ADMIN-SCREEN-001 | P0 | Create a user | CEO → `/app/admin/users` → Create User (name, email, password, Business Role, reason) | New user appears, can log in | YES | [ ] |
| UAT-ADMIN-SCREEN-002 | P0 | Creation reason mandatory | Attempt Create User with Reason blank | Rejected | NO | [ ] |
| UAT-ADMIN-SCREEN-003 | P1 | Deactivate / Activate a user | CEO toggles a user's active status with a reason | Login blocked while deactivated; restored on activation | YES (deactivation path) / NO (reactivation dedicated check) | [ ] |
| UAT-ADMIN-SCREEN-004 | P1 | Change a user's Business Role | CEO changes a user's role | Access Class updates to match the new role; existing permission grants are untouched | NO | [ ] |
| UAT-ADMIN-SCREEN-005 | P0 | Non-CEO cannot reach admin screens | Log in as MM or Employee, try `/app/admin/users` directly | Redirected away (not CEO-exclusive-authorized) | NO (see doc 04 for the systematic version of this check) | [ ] |

## O. Audit History (UAT-AUDIT-*)

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-AUDIT-001 | P1 | Audit History screen loads and lists real events | `/app/audit` | A list of real recorded actions (idea approvals, grants, corrections, etc.) with actor, category, event type, timestamp | YES | [ ] |
| UAT-AUDIT-002 | P2 | Filter by action type / date range | Apply a filter | List narrows correctly | NO | [ ] |
| UAT-AUDIT-003 | P0 | No password hash ever appears | Inspect the Audit History response/page content | No bcrypt-shaped string anywhere | YES | [ ] |

## P. Reporting / KPI / Export

See the dedicated matrix in this same document's companion section below, and `10_REQUIREMENT_TEST_TRACEABILITY.md` for the full KPI-by-KPI status.

| ID | Priority | Title | Steps | Expected Result | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-KPI-001 | P0 | Team Workload | Perm#14 holder or CEO/MM → `/app/reports/workload` | Loads with real per-employee workload data | YES | [ ] |
| UAT-KPI-002 | P0 | Team KPI | Perm#15 holder or CEO/MM → `/app/reports/team-kpis` | Loads with real data | YES | [ ] |
| UAT-KPI-003 | P0 | 30-KPI Console renders all 30 | `/app/reports/kpis` | Exactly 30 KPI tiles render, grouped into the 5 governed categories | YES | [ ] |
| UAT-KPI-004 | P1 | KPI value correctness (spot check) | Compare KPI-017/018/019/020 against a manual count of Ideas/Review decisions | Values match exactly | YES (done once during this UAT-prep pass — see `TEST_REPORT_2026-08-15.md`) | [ ] |
| UAT-KPI-005 | P1 | Delayed Deliverables report | `/app/reports/delayed` | Real delayed rows shown, with Employee visibility correctly scoped (see doc 04) | YES | [ ] |
| UAT-KPI-006 | P1 | Administrative Actions report | `/app/reports/admin-actions` | Real admin-action history, grouped by category | YES | [ ] |
| UAT-KPI-007 | P0 | Export — JSON | CEO/MM → `/app/export` → JSON, all governed tables | Downloadable/renderable JSON covering the governed table union; no identity/password tables included | YES | [ ] |
| UAT-KPI-008 | P0 | Export — CSV | Same screen, CSV, exactly one table | Valid CSV; rejected if more than one table selected | YES | [ ] |
| UAT-KPI-009 | P0 | Export — XLSX | Same screen, XLSX, multiple tables | Valid multi-sheet workbook | YES | [ ] |
| UAT-KPI-010 | P0 | Export is management-only | Attempt export as a plain Employee | Rejected (403) | YES | [ ] |

---

## Summary counts (fill in after execution)

| Priority | Total | Passed | Failed | Blocked | Not Run |
|---|---|---|---|---|---|
| P0 | | | | | |
| P1 | | | | | |
| P2 | | | | | |
| P3 | | | | | |

---

## Appendix 1 — Full 30-KPI Test Matrix

Every formula below is transcribed directly from `KpiService.java` (not inferred from the KPI's name) — this is what the system actually computes, which is what UAT tests against. All 30 currently **compute without error** against real data (`ReportingApiSecurityTest`, automated). Only KPI-017 through KPI-020 have been independently **value-verified** against hand-run SQL as of this UAT-prep pass (see `TEST_REPORT_2026-08-15.md`) — every other row's "Actual Result" and "PASS/FAIL" are blank for the tester to fill in during a real UAT session, using the same technique: run the formula's underlying query directly (or count it by hand from a small dataset) and compare.

| KPI | Label | Category | Formula / Definition (as implemented) | Required Source Data | N/A Handling | Privacy Impact | Test Dataset | Expected Result | Actual Result | PASS/FAIL |
|---|---|---|---|---|---|---|---|---|---|---|
| KPI-001 | Pending Work | Operational | Count of Content Plans whose status is not Completed or Cancelled | `content_plans`, `workflow_instances` | n/a (count, never negative/undefined) | None — aggregate count only | Golden Flow dataset | count of active deliverables | | [ ] |
| KPI-002 | Delayed Work | Operational | Count of active Content Plans whose current-stage planned date (BR-039 stage-context) is before today | same + stage-context planned date | n/a | None | dataset with ≥1 deliverable past its planned date | count of delayed deliverables | | [ ] |
| KPI-003 | Upcoming Shoots (7 days) | Operational | Count of active plans with `planned_shoot_date` in [today, today+7] | `content_plans` | n/a | None | dataset with a shoot scheduled this week | count | | [ ] |
| KPI-004 | Upcoming Publishing (7 days) | Operational | Count of active plans with `planned_live_date` in [today, today+7] | `content_plans` | n/a | None | dataset with a live date this week | count | | [ ] |
| KPI-005 | Pending Approvals | Operational | Count of `review_cycles` rows not yet decided | `review_cycles` | n/a | None | any open review gate | count | | [ ] |
| KPI-006 | Editor Workload | Operational | Distribution: active editing assignments grouped by Editor full name | `editing_assignments`, `users` | empty distribution if no active assignments (not an error) | Shows Editor **names** — this is a management-only screen (Perm #15/native); never exposed to a plain Employee | dataset with ≥2 Editors assigned | per-editor counts | | [ ] |
| KPI-007 | Performance Pending Work | Operational | Count of `performance_obligations` where `completed = false` | `performance_obligations` | n/a | None | dataset with an open obligation | count | | [ ] |
| KPI-008 | Employee Productivity (period) | Productivity | Distribution: Mark attributions + Planning-Approved plans per employee, in the period (default: current IST month) | `personal_mark_attributions`, `planning_preparers`, `workflow_transition_history` | empty distribution if none in period | Per-employee — management-only screen | Golden Flow dataset within current month | per-employee counts | | [ ] |
| KPI-009 | Manager Productivity (period) | Productivity | Count of decided review cycles where the reviewer's Business Role resolves to MARKETING_MANAGER, in the period | `review_cycles`, `users`, `business_roles` | n/a | None (aggregate) | any MM decision this month | count | | [ ] |
| KPI-010 | Tasks Completed | Productivity | Count of `workflow_instances` with `first_completed_at` set (optionally period-filtered) | `workflow_instances` | n/a | None | Golden Flow dataset | count | 1 (confirmed structurally via Golden E2E) | [ ] |
| KPI-011 | Tasks Cancelled | Productivity | Count of `cancellation_records` (optionally period-filtered) | `cancellation_records` | n/a | None | a cancelled deliverable | count | | [ ] |
| KPI-012 | Published Content | Content Units | Count of distinct Content Plans with ≥1 `ORIGINAL` publication event | `actual_publication_events` | n/a | None | Golden Flow dataset | count | | [ ] |
| KPI-013 | Reels Produced | Content Units | Count of Reel-type Planned Outputs whose parent plan has passed Planning Review (status not PL/PLRV) | `planned_outputs`, `content_plans`, `workflow_instances` | n/a | None | a plan with a Reel output past Planning | count | | [ ] |
| KPI-014 | Photography Produced | Content Units | Same as KPI-013, Photography-type outputs | same | n/a | None | a plan with a Photography output past Planning | count | | [ ] |
| KPI-015 | Publication Distribution | Content Units | Distribution: `ORIGINAL` events grouped by Platform/Channel | `actual_publication_events`, `publication_targets`, `platforms`, `company_channels` | empty if no events yet | None (aggregate, no personal data) | Golden Flow dataset | per-target counts | | [ ] |
| KPI-016 | Content by Type | Content Units | Distribution: Planned Outputs grouped by Output Type | `planned_outputs` | n/a | None | Golden Flow dataset (3 output types) | 1 each of Photography/Reel/Video (plus any other test data) | | [ ] |
| KPI-017 | Ideas Submitted | Content Units | Count of all `ideas` rows | `ideas` | n/a | None | any dataset | total idea count | **9** (hand-verified against `SELECT count(*) FROM ideas`) | [x] |
| KPI-018 | Ideas Approved | Content Units | Count of `review_cycles` where gate=IDEA_REVIEW and decision=APPROVED | `review_cycles` | n/a | None | any dataset | count | **8** (hand-verified) | [x] |
| KPI-019 | Ideas Rejected | Content Units | Count of `review_cycles` where gate=IDEA_REVIEW and decision=REJECTED | `review_cycles` | n/a | None | any dataset | count | **0** (hand-verified) | [x] |
| KPI-020 | Idea Approval Rate | Content Units | Approved / (Approved + Rejected) × 100 — **Retained ideas explicitly excluded from the denominator per BFD §7.3** | derived from KPI-018/019 | shows **N/A** (never `0%`/error) when Approved+Rejected = 0 | None | any dataset | percentage | **100.00%** (hand-verified: 8/(8+0)) | [x] |
| KPI-021 | Approval Turnaround Time | Approval & Review | Average of (decided_at − submitted_at) across all decided review cycles, in days | `review_cycles` | shows N/A-equivalent (blank/zero-average) if no decided cycles exist — confirm exact rendering during UAT | None (aggregate) | any dataset with ≥1 decision | average days | | [ ] |
| KPI-022 | Approvals by Manager | Approval & Review | Count of decided review cycles where reviewer's access class = MARKETING_MANAGER | `review_cycles`, `users`, `business_roles` | n/a | None | an MM decision | count | | [ ] |
| KPI-023 | Approvals by CEO | Approval & Review | Same, access class = CEO_OWNER | same | n/a | None | a CEO decision | count | | [ ] |
| KPI-024 | Rework Rate | Approval & Review | Rework decisions / all decided Planning+Shoot+Edit review cycles × 100 | `review_cycles` | shows **N/A** if no production reviews decided yet | None | a rework decision | percentage | | [ ] |
| KPI-025 | Average Delay (days) | Delay/SLA | Average of (today − stage-context planned date) across active, currently-delayed plans | `content_plans`, `workflow_instances` | N/A-equivalent if nothing is delayed — confirm rendering | None | a delayed deliverable | average days | | [ ] |
| KPI-026 | Content Completion Rate | Delay/SLA | Completed plans / total plans × 100 | `content_plans`, `workflow_instances` | shows **N/A** if zero plans exist | None | Golden Flow dataset | percentage | | [ ] |
| KPI-027 | Content Turnaround Time | Delay/SLA | Average of (first_completed_at − content_plans.created_at) across completed plans, in days | `content_plans`, `workflow_instances` | N/A-equivalent if none completed | None | a completed deliverable | average days | | [ ] |
| KPI-028 | Shoot-to-Publish Cycle Time | Delay/SLA | Average of (latest publication timestamp − first SAP transition timestamp) per plan, in days | `workflow_transition_history`, `content_plans`, `actual_publication_events` | N/A-equivalent if no plan has both a SAP transition and a publication event | None | Golden Flow dataset | average days | | [ ] |
| KPI-029 | Delay Distribution (by stage) | Delay/SLA | Distribution: delayed active plans grouped by current stage label | `content_plans`, `workflow_instances` | empty distribution if nothing delayed | None (aggregate) | a delayed deliverable | per-stage counts | | [ ] |
| KPI-030 | On-Time Delivery Rate | Delay/SLA | On-time `ORIGINAL` events (published on/before planned_live_date) / all `ORIGINAL` events × 100 | `actual_publication_events`, `content_plans` | shows **N/A** if zero `ORIGINAL` events exist yet | None | Golden Flow dataset | percentage | | [ ] |

**Recommendation:** the next testing pass should hand-verify at least one KPI per category (5 categories) rather than all 30 in one sitting — pick the one in each category with the simplest formula (KPI-001, KPI-010, KPI-017 already done, KPI-022, KPI-026) to build confidence category-by-category.

## Appendix 2 — Export Format Test Matrix

| Format | Authorization | Scope | Dataset Completeness | Schema/Version Metadata | Privacy | Data Correctness | N/A Handling | Test | Expected | Actual | PASS/FAIL |
|---|---|---|---|---|---|---|---|---|---|---|---|
| JSON | CEO/MM only (no delegated permission exists for export — native authority required) | Fixed governed-table allowlist (~31 tables: workflow, idea, planning, production, marks, publishing, performance, admin-action history) | One JSON object per governed table in the union | No explicit schema-version field observed in the payload — **flag for confirmation during UAT**; note in Defect Log as Minor if a version marker is expected but absent | Identity/permission tables (`users`, `permission_grants`, `business_roles`) are never reachable through the allowlist; no password hash anywhere | Spot-check exported row values against the same data on-screen | N/A/null fields serialize as JSON `null`, not a fabricated value | UAT-KPI-007 | valid JSON, correct scope | | [ ] |
| CSV | CEO/MM only | Exactly one table per request (rejected if more than one is selected) | Full column set for the one selected table | N/A (CSV has no schema-metadata concept) | Same allowlist protection | Spot-check a few rows | Empty/N/A cells render as blank, not `"null"` text — **confirm during UAT** | UAT-KPI-008 | valid CSV, single-table enforcement | | [ ] |
| XLSX | CEO/MM only | Multiple tables, one worksheet per table | Same allowlist, full workbook | N/A | Same allowlist protection | Open the workbook and spot-check | Same as CSV — confirm blank vs. literal "null" during UAT | UAT-KPI-009 | valid multi-sheet workbook | | [ ] |
| (all) | Non-CEO/MM rejected | — | — | — | — | — | — | UAT-KPI-010 | 403 | | [ ] |

**Note:** the per-Content-Plan full-graph JSON export (`GET /api/v1/export/content-plans/{id}`) is a separate, older export path kept alongside the governed multi-table export above — it is not part of the `SAD-ADR-008` governed format set and is out of scope for this matrix, but exists and is reachable; confirm it still works if it's relied on by any real workflow.
