# KCPC R3.5 — Test Report

**Date:** 2026-08-15
**Scope of this pass:** closing the specific gaps identified when asked "have you tested all scenarios" — multi-contributor Mark attribution, delegated permission-grant expiry/revocation/out-of-scope rejection — plus an independent, hand-calculated value-correctness spot check on the KPI reporting engine (not just "does it compute without error"). This is a real test-execution report, not a UAT documentation package (that's the separate, larger ask in `Prompt (2).md` — see the last section below).

---

## 1. What was actually run, and how

Every test in this report ran as a real JUnit test against a real PostgreSQL database (`kcpc_test`) over real HTTP — the same server, cookie-jar, CSRF-token, and JSON/form request pattern a real browser or API client uses. Nothing here used MockMvc or an in-memory substitute. The KPI value-correctness check additionally ran against the live `dev` application (`kcpc_dev` database) with independent raw SQL queries used to verify the numbers the API returned.

## 2. New tests added and executed this session

**`HighPriorityEdgeCaseTest`** (5 tests, all passing):

| Test | Scenario | Result |
|---|---|---|
| `twoQualifyingCamerapersonsEachReceiveTheFullMarkNeverSplit` | Two Camerapersons assigned, both qualify at Shoot Review approval | **PASS** — both received the full `2.0` predefined Mark; no split, no averaging |
| `twoQualifyingEditorsEachReceiveTheFullMarkNeverSplit` | Two Editors assigned, both qualify at Edit Review approval | **PASS** — both received the full `3.0` predefined Mark |
| `expiredDelegatedPermissionGrantIsRejected` | Employee granted Planning Execution with `effectiveFrom`/`effectiveUntil` both already in the past | **PASS** — 403, `PERM_OPERATIONAL_PERMISSION_EXPIRED` |
| `revokedDelegatedPermissionGrantIsRejected` | Grant proven to work, then revoked via CEO, then re-attempted | **PASS** — 403, `PERM_OPERATIONAL_PERMISSION_REQUIRED` (see finding below) |
| `stageRestrictedGrantOutsideItsScopeIsRejected` | Permission #2 granted but scoped only to the Shooting stage, then used for a Planning-stage action | **PASS** — 403, `PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE` |

**One genuine finding along the way (not a defect):** a *revoked* grant and an *expired* grant return different error codes, and this wasn't obvious in advance. `AuthorizationService` fetches candidate grants with `findByGranteeAndPermissionAndActiveTrue` — once a grant is revoked (`active=false`), it's invisible to that query entirely, so the system correctly reports "never granted" (`PERM_OPERATIONAL_PERMISSION_REQUIRED`) rather than "expired." An expired-but-still-active-flagged grant *is* fetched, and only then found to be time-lapsed, producing the distinct `PERM_OPERATIONAL_PERMISSION_EXPIRED` code. Both are correct, secure behavior — just two different code paths to the same practical outcome (access denied). I verified this by first proving the grant worked *before* revocation, so the test can't accidentally pass for an unrelated reason.

## 3. KPI value-correctness spot check (not just "computes without error")

The existing automated suite (`ReportingApiSecurityTest`) only proves all 30 KPIs compute without throwing. For this report, I picked four related KPIs and checked the *actual numbers* against independent raw SQL against `kcpc_dev`:

| KPI | API Result | Independent DB Query | Match? |
|---|---|---|---|
| KPI-017 Ideas Submitted | `9` | `SELECT count(*) FROM ideas` → `9` | ✅ |
| KPI-018 Ideas Approved | `8` | `SELECT count(*) FROM review_cycles WHERE gate_type='IDEA_REVIEW' AND decision='APPROVED'` → `8` | ✅ |
| KPI-019 Ideas Rejected | `0` | same query, `decision='REJECTED'` → `0` | ✅ |
| KPI-020 Idea Approval Rate | `100.00%` | `8 / (8+0)` = 100% | ✅ |

All four match exactly. This is a genuine (if small) value-correctness proof, not a "no exception thrown" proxy.

## 4. Full regression run (all tests, this session's additions included)

```
mvn clean verify -Dspring.profiles.active=test
```

**Result: BUILD SUCCESS. 38/38 tests passing, 0 failures, 0 errors.**

| Test Class | Tests | Focus |
|---|---|---|
| `KcpcMktApplicationTests` | 1 | Spring context loads |
| `GoldenEndToEndFlowTest` | 1 | Full Idea→Completed lifecycle |
| `AuthenticationFlowTest` | 5 | Bad credentials, missing cookie/CSRF, logout revocation, unmapped-URL 404 (not false logout) |
| `SelfReviewConflictTest` | 1 | Delegated self-review conflict, both paths |
| `CorrectionLedgerFlowTest` | 2 | Correction ledgers, DB-trigger immutability |
| `DbIntegrityEnforcementTest` | 2 | Hard-DELETE + TRUNCATE rejection at the DB level |
| `MvcScreenSmokeTest` | 1 | v0.1 golden path via real HTML form POSTs |
| `AdminMvcScreenSmokeTest` | 1 | v0.2 Group A + Permissions screen |
| `ReportsGroupBMvcScreenSmokeTest` | 1 | v0.2 Group B/C screens |
| `ReportingApiSecurityTest` | 2 | No bcrypt leak; all 30 KPIs compute without error |
| `ExportApiTest` | 4 | JSON/CSV/XLSX correctness, scoping, 403 for non-management |
| `WorkflowVariantsE2ETest` | 8 | Golden-path variants (Reject, Retain/Reopen, Urgent, boundary, Hold/Resume, Due-Date gate, multi-event/Repost/N/A, Reopen) |
| `PermissionBoundaryTest` | 4 | Unauthorized 403, out-of-order 409, session revocation |
| **`HighPriorityEdgeCaseTest`** | **5** | **New this session — see section 2** |
| **Total** | **38** | |

## 5. What is still genuinely NOT tested

Being direct about this rather than overstating coverage:

- **Each of the 17 permissions individually** — only 3 now have dedicated negative-path/scope tests (Idea Review, Planning Execution ×3 scenarios). The other 14 (Shoot Review, Edit Assignment, Publishing Execution, Reschedule, Reassign, Cancel, etc.) have never been exercised under an expired/revoked/out-of-scope grant.
- **Cancel/Reschedule/Reassign as dedicated negative-path tests** — only reachable as incidental setup in other tests.
- **Editing-specific rework** as its own scenario.
- **Dedicated Employee-privacy tests** — no automated test proves an Employee genuinely cannot see peer Marks/rankings/payroll; the design prevents it structurally (self-service endpoints are scoped to the caller), but that's an inference from code, not a test result.
- **The other 26 KPIs' value-correctness** — only KPI-017/018/019/020 got the independent hand-verification in this report. The other 26 are still only proven to "compute without error," not proven numerically correct against a hand-built dataset.
- **Direct URL/API bypass tested systematically per screen** — spot-checked in a few places, not exhaustively.
- **Concurrency/race conditions** — zero coverage; every test in this suite is single-client sequential HTTP.
- **No screenshots exist anywhere** — everything so far is curl/JUnit-driven, never a browser capture.

## 6. Relationship to `Prompt (2).md`

`Prompt (2).md` (which you had me read earlier) asks for something larger and different in kind: an 11-document **UAT documentation package** meant for a non-developer project owner to execute manually — a Golden Flow script with per-step Pass/Fail checkboxes, a full requirement-traceability matrix, a defect register template, screenshot guidance, and a formal sign-off report. This report is not that; it's me actually running additional real tests myself and telling you what passed and what's still uncovered. If you want the full UAT package next, say so and I'll start on it.
