# 04 — Role, Permission, and Security Tests

This is the access-control test suite: does every role and every one of the 17 governed Operational Permissions enforce exactly what it's supposed to, both **visibly** (the button/link isn't shown) and, more importantly, **on the server** (the action is rejected even if attempted directly, bypassing the UI). A hidden button alone never proves security — every test below has a server-side variant.

## 1. The three Access Classes

| Access Class | Native Authority | Represented by (test account) |
|---|---|---|
| CEO_OWNER | All 17 governed actions, natively, no grant needed | `ceo@kcpcbandhani.local` |
| MARKETING_MANAGER | Same set of governed actions as CEO_OWNER, natively | `mm@kcpcbandhani.local` |
| EMPLOYEE | None natively — only what is explicitly delegated via a Permission Grant | `camera@kcpcbandhani.local` (zero grants), `coordinator@kcpcbandhani.local` (Perm #1+#2 granted) |

| ID | Priority | Title | Steps | Expected | PASS/FAIL |
|---|---|---|---|---|---|
| UAT-SEC-ROLE-001 | P0 | CEO has full native authority | As CEO, perform one action from each governed domain (approve an idea, execute planning, start shooting, etc.) without any permission grant existing | All succeed | [ ] |
| UAT-SEC-ROLE-002 | P0 | Marketing Manager has full native authority | Repeat UAT-SEC-ROLE-001 logged in as `mm@kcpcbandhani.local` | All succeed | [ ] |
| UAT-SEC-ROLE-003 | P0 | Plain Employee has zero native authority | As `camera@kcpcbandhani.local` (no grants), attempt any one governed write action (e.g. approve an idea) | Rejected (403) | [ ] |

## 2. The 17 governed Operational Permissions

| # | Permission | Governs | Representative protected action to test with |
|---|---|---|---|
| 1 | Idea Review | Approve/Reject/Retain a submitted idea | `POST /api/v1/ideas/{id}/review` |
| 2 | Planning Execution | Set parameters, schedule, outputs, publication scope, folder link | `POST /api/v1/content-plans/{id}/schedule/standard` |
| 3 | Planning Review | Approve/rework a Planning submission | `POST /api/v1/content-plans/{id}/planning-review/decision` |
| 4 | Shoot Assignment | Assign Cameraperson(s) | `POST /api/v1/content-plans/{id}/shooting-assignments` |
| 5 | Shoot Review | Approve/rework a Shoot submission | `POST /api/v1/content-plans/{id}/shooting/review/decision` |
| 6 | Edit Assignment | Assign Editor(s) | `POST /api/v1/content-plans/{id}/editing/assignments` |
| 7 | Edit Review | Approve/rework an Edit submission | `POST /api/v1/content-plans/{id}/editing/review/decision` |
| 8 | Publishing Execution | Start Publishing, record events, Target N/A, evidence corrections, Reopen for Publishing | `POST /api/v1/content-plans/{id}/publishing/start` |
| 9 | Performance Update | Scorecard draft/submit, metric corrections, Reopen for Metric Correction | `POST /api/v1/performance-obligations/{id}/scorecard/draft` |
| 10 | Reschedule | Change planned dates | `POST /api/v1/content-plans/{id}/reschedule` |
| 11 | Reassign | Reassign Cameraperson/Editor task | `POST /api/v1/content-plans/{id}/reassign` |
| 12 | Cancel | Cancel a deliverable | `POST /api/v1/content-plans/{id}/cancel` |
| 13 | Folder Link Manage | (see note below) | bundled into `PUT/POST .../parameters` today |
| 14 | Team Workload View | `/app/reports/workload` and its API | `GET /api/v1/team/workload` |
| 15 | Team KPI View | `/app/reports/team-kpis`, `/app/reports/kpis` | `GET /api/v1/reports/kpis` |
| 16 | Audit History View | `/app/audit`, `/app/reports/admin-actions` | `GET /api/v1/audit/logs` |
| 17 | Platform Catalogue Manage | Publishing Catalogue admin | `POST /api/v1/publishing/platforms` |

> **Known conformance finding — Permission #13:** the catalogue defines a dedicated Folder-Link-Manage permission, but the running implementation gates the folder-link field on Permission #2 (Planning Execution) instead, with no independently-checked Permission #13 anywhere in the code. This means an Employee delegated *only* #2 can change the folder link (broader than intended), and an Employee delegated *only* #13 cannot use it at all (narrower than intended, since no endpoint checks #13). This is a pre-existing, already-logged discrepancy (`DISC-002` in `docs/IMPLEMENTATION_DISCREPANCIES.md`), not something newly found here — recorded again in `10_REQUIREMENT_TEST_TRACEABILITY.md` for completeness. **Do not write a UAT-PERM test expecting #13 to work independently — it will always fail as designed today.**

**Note on Hold/Resume and User Administration:** these are native-CEO/MM-only actions with no delegated-permission path at all (by design) — they don't fit the 17-permission grant model and are tested separately in `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md` §K and §N.

## 3. The 11-dimension protected-action test matrix

For **every** permission above, the same 11 checks apply. This section defines the checks once; Section 4 records, per permission, which of the 11 already have automated proof vs. still need a manual run.

| Dimension | What to do | Expected |
|---|---|---|
| (a) CEO | Perform the action as CEO, no grant needed | Succeeds |
| (b) Marketing Manager | Perform the action as MM, no grant needed | Succeeds |
| (c) Employee without permission | Perform the action as an Employee holding no grant for it | Rejected 403, error code `PERM_OPERATIONAL_PERMISSION_REQUIRED` |
| (d) Employee with valid permission | Grant the permission (GLOBAL scope), then perform the action | Succeeds |
| (e) Employee with expired permission | Grant with `effectiveFrom`/`effectiveUntil` both already in the past, then attempt | Rejected 403, `PERM_OPERATIONAL_PERMISSION_EXPIRED` |
| (f) Employee with revoked permission | Grant, prove it works, revoke it, then re-attempt | Rejected 403, `PERM_OPERATIONAL_PERMISSION_REQUIRED` (a revoked grant is treated as if it never existed — see note below) |
| (g) Employee with out-of-scope permission | Grant `STAGE_RESTRICTED` to a *different* lifecycle stage than the one the action needs | Rejected 403, `PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE` |
| (h) Inactive user | Deactivate the acting user, then attempt (with their still-live session if possible, and with a fresh login attempt) | Both rejected — deactivation revokes sessions immediately |
| (i) Delegated self-review | Where the action is a review/decision gate, have the same delegated Employee both submit and decide their own work | Rejected 403, `PERM_SELF_APPROVAL_PROHIBITED` |
| (j) Direct URL bypass (MVC) | With no visible button for it, navigate directly to the screen/URL that performs the action | Server still rejects it — the URL is not "security by obscurity" | 
| (k) Direct REST API bypass | Issue the raw `POST`/`PUT` request directly (e.g. via a REST client), bypassing the UI entirely | Server still rejects it under the same rule as (c)–(i) |

> **Why (f) says "treated as if it never existed":** this was verified directly this session — `AuthorizationService` fetches only *currently-active* grants before checking expiry, so a revoked grant is invisible to that lookup and produces the same error code as "never granted," not the same code as "expired." Both are correctly denied; only the error code differs. This is documented so a tester doesn't mistake the different error code for a bug.

## 4. Coverage status per permission

| # | Permission | (a) CEO | (b) MM | (c) No perm | (d) Valid | (e) Expired | (f) Revoked | (g) Out-of-scope | (h) Inactive | (i) Self-review | (j) URL bypass | (k) REST bypass |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Idea Review | AUTO | manual | AUTO | manual | manual | manual | manual | manual | AUTO | manual | AUTO |
| 2 | Planning Execution | AUTO | manual | AUTO | manual | AUTO | AUTO | AUTO | manual | n/a | manual | AUTO |
| 3 | Planning Review | AUTO | manual | manual | manual | manual | manual | manual | manual | manual (structurally same guard as #1) | manual | manual |
| 4 | Shoot Assignment | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | manual |
| 5 | Shoot Review | AUTO | manual | manual | manual | manual | manual | manual | manual | manual | manual | manual |
| 6 | Edit Assignment | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | manual |
| 7 | Edit Review | AUTO | manual | manual | manual | manual | manual | manual | manual | manual | manual | manual |
| 8 | Publishing Execution | AUTO | manual | manual | AUTO (Publisher account) | manual | manual | manual | manual | n/a | manual | manual |
| 9 | Performance Update | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | manual |
| 10 | Reschedule | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | manual |
| 11 | Reassign | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | manual |
| 12 | Cancel | manual | manual | manual | manual | manual | manual | manual | manual | n/a | manual | manual |
| 13 | Folder Link Manage | — | — | — | — | — | — | — | — | — | — | — (see conformance note above — not independently testable as designed) |
| 14 | Team Workload View | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | AUTO (200 confirmed) |
| 15 | Team KPI View | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | AUTO |
| 16 | Audit History View | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | AUTO |
| 17 | Platform Catalogue Manage | AUTO | manual | manual | manual | manual | manual | manual | manual | n/a | manual | AUTO |

**Honest summary:** dimensions (a) and (k) are reasonably well automated across most permissions (native CEO authority is exercised constantly by the existing 38-test suite, and REST-level 200/403 checks exist for several). Dimensions (e), (f), (g) — expired / revoked / out-of-scope — are **only automated for Permission #2** (`HighPriorityEdgeCaseTest`, added during this UAT-prep pass) and are genuinely **NOT VERIFIED** for the other 15. This is the single largest remaining gap in security test coverage, and manual execution of Section 3's procedure against each remaining permission is the recommended next step after this UAT package's initial run. Do not mark this "PASS" for a permission until it has actually been run — the table above tells you exactly which cells are still open.

## 5. Employee Privacy

| ID | Priority | Title | Steps | Expected | PASS/FAIL |
|---|---|---|---|---|---|
| UAT-PRIV-001 | P0 | No peer Marks visible | Log in as Rohan Kapoor; attempt to find another Cameraperson's (Vikram Rao's) Marks anywhere in the UI he can reach | Not visible anywhere | [ ] |
| UAT-PRIV-002 | P0 | No rankings/leaderboards | Browse every screen reachable by a plain Employee | No ranking, leaderboard, or "you are #N" comparison exists anywhere | [ ] |
| UAT-PRIV-003 | P0 | No identifiable peer-performance comparison | Same browse | No screen shows "your performance vs. [named colleague]" | [ ] |
| UAT-PRIV-004 | P0 | No compensation/payroll data | Same browse | No screen shows pay, salary, or compensation figures — this system does not govern payroll at all | [ ] |
| UAT-PRIV-005 | P0 | No management-only reports visible to a plain Employee | Attempt `/app/reports/workload`, `/app/reports/team-kpis`, `/app/reports/kpis`, `/app/audit`, `/app/reports/admin-actions`, `/app/export` as `camera@kcpcbandhani.local` (no grants) | Every one redirected away / denied | [ ] |
| UAT-PRIV-006 | P1 | Employee sees own assigned tasks | My Work screen | Rohan Kapoor's own Shoot tasks visible | [ ] |
| UAT-PRIV-007 | P1 | Employee sees own status/feedback/rework history | Open one of his own deliverables | Full history visible for his own work | [ ] |
| UAT-PRIV-008 | P1 | Employee sees own Marks | Same | His own attributed Marks visible | [ ] |
| UAT-PRIV-009 | P1 | Employee sees own governed personal measures | Any personal-performance view reachable to him | Shows only his own figures | [ ] |
| UAT-PRIV-010 | P2 | Delayed Deliverables privacy scoping | A non-GLOBAL-scoped Employee vs. a GLOBAL-scoped one on `/app/reports/delayed` | Non-GLOBAL sees only own assigned/participated items; this is a known conservative (under-, not over-, exposing) simplification — see `ENG-028` in `docs/IMPLEMENTATION_DECISIONS.md` | [ ] |

## 6. UI-hidden vs. server-enforced — why both matter

A control being hidden or greyed out in the UI is a usability nicety, not a security control. For every P0/P1 item in Section 3 and Section 5, confirm **both**:

1. The UI does not show the action to an unauthorized user (good UX).
2. The server rejects the action even when attempted directly (the real security boundary).

If (1) passes but (2) fails, this is a **security defect**, not a cosmetic one — log it as at least **Major**, likely **Critical**, in `09_DEFECT_LOG.md`.
