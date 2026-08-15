# 01 — Testing Overview and Strategy

**System under test:** KCPC Marketing Content Production Lifecycle MVP, Development Baseline R3.5 (frozen for implementation).
**Audience:** project owner / business stakeholder running User Acceptance Testing (UAT). No coding background required to use this document set.
**Prepared:** 2026-08-15, from direct inspection of the actual running application and database — not from the specification alone.

---

## 1. Purpose of this document set

This is the complete testing package for validating that what was **actually built** matches what was **actually promised** in the frozen R3.5 specifications, before the software goes into real use. It is organized so a non-developer can run it start to finish with a web browser and this document set — no code, no terminal, no database tool required (a few optional technical checks are marked separately).

## 2. Source of truth and how disagreements are handled

Two things are being compared in this UAT:

- **Expected behaviour** = what the frozen R3.5 documents say the system must do. Precedence when documents disagree with each other: **BFD → BRS → RTM → SRS → SAD → ERD → API → UI/UX**.
- **Actual behaviour** = what the running application actually does.

**Rule:** if the specification says X and the application does Y, the test result is recorded as **FAIL — IMPLEMENTATION DEVIATION**, with both X and Y written down. The test is never quietly "fixed" to match what the software happens to do. This keeps the UAT honest — its job is to surface gaps, not hide them.

Every deviation found during this documentation pass is also cross-referenced in `docs/IMPLEMENTATION_DECISIONS.md` and `docs/IMPLEMENTATION_DISCREPANCIES.md`, where the engineering rationale (if any) is recorded.

## 3. What "already tested" means going into this UAT

Before this UAT package existed, the development team already built and ran **38 automated tests** against a real PostgreSQL database over real HTTP (not a simulation) — see `docs/IMPLEMENTATION_STATUS.md` for the full list. These cover the golden path, several workflow variants, a representative slice of permission/security checks, correction ledgers, database-level data-integrity rules, and the reporting screens.

**This UAT package does not repeat what's already automated line-for-line.** Instead, it is organized so a human tester can:
1. Confirm the automated results hold true when driven by hand through a real browser (automated tests use HTTP calls, not an actual browser — a human click-through can catch things a script wouldn't, like a confusing label or a missing visual cue).
2. Cover the large surface area that is **not yet automated** — see `06_TEST_DATA_AND_USER_ACCOUNTS.md` and `10_REQUIREMENT_TEST_TRACEABILITY.md` for exactly what that gap is.

Document `10_REQUIREMENT_TEST_TRACEABILITY.md` marks every governed requirement as **AUTOMATED VERIFIED**, **MANUAL VERIFIED**, **BOTH**, or **NOT VERIFIED** — nothing is claimed as tested unless it genuinely was.

## 4. The 9 testing levels

Testing is organized into 9 levels, run roughly in order. Each level builds confidence before moving to the next — if an early level fails badly, later levels are wasted effort until the failure is fixed.

| Level | Name | Purpose | Document |
|---|---|---|---|
| 1 | **Smoke Test** | Is the environment even healthy enough to test? (~10–15 min) | `02_QUICK_GOLDEN_FLOW_UAT.md` §A |
| 2 | **Golden End-to-End Flow** | Does one real piece of content survive the entire lifecycle, Login → Completed? | `02_QUICK_GOLDEN_FLOW_UAT.md` §B |
| 3 | **Stage-by-Stage Functional Testing** | Does every individual feature within each stage work (not just the happy path through it)? | `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md` |
| 4 | **Administrative Actions / Exceptions** | Hold/Resume, Reschedule, Reassign, Cancel, Reopen — the "something went wrong, now what" actions | `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md` (UAT-ADMIN-*) |
| 5 | **Role / Permission / Privacy** | Can each role only do what it's supposed to, and see only what it's supposed to see? | `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md` |
| 6 | **Negative / Boundary Testing** | Do the specific numeric/date edge cases behave exactly as governed (5-day boundary, zero-denominator KPIs, etc.)? | `05_EDGE_CASE_AND_NEGATIVE_TESTS.md` |
| 7 | **Reporting / KPI / Export** | Do the 30 KPIs, team reports, and data exports produce correct, complete, privacy-safe output? | `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md` (UAT-KPI-*) |
| 8 | **Regression** | After any fix, do Levels 1–7 still pass? (Re-run the same documents.) | all of the above, re-run |
| 9 | **Final UAT Sign-off** | Formal go/no-go decision | `11_FINAL_UAT_REPORT_TEMPLATE.md` |

## 5. Priority labels

Every test case in `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md`, `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md`, and `05_EDGE_CASE_AND_NEGATIVE_TESTS.md` carries one of these labels:

| Label | Meaning | If it fails... |
|---|---|---|
| **P0** | Must pass before even a limited pilot begins | Stop. This blocks any real usage. |
| **P1** | Must pass before production go-live | Can pilot with internal users, but cannot go live for real business use until fixed. |
| **P2** | Important regression-prevention coverage | Should be fixed soon, but is not by itself a launch blocker. |
| **P3** | Lower-risk / polish | Track it, fix opportunistically. |

## 6. Recording results

Every test case has a **PASS / FAIL** checkbox. When a test fails:

1. Record it immediately in `09_DEFECT_LOG.md` using the next Defect ID.
2. Classify severity (Blocker / Critical / Major / Minor / Cosmetic — defined in that document).
3. Keep going with unrelated tests where possible — don't let one failure stop the whole session unless it's a P0 that blocks everything downstream (e.g. login itself is broken).
4. Distinguish a **functional defect** from a **visual/UX improvement** — see the note in `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md`'s introduction. The application's visual design is intentionally basic for this MVP; basic styling alone is never a functional failure. Missing required-field indicators, wrong validation, unauthorized controls being visible, or a required action being unavailable **are** functional problems even though they're visual in nature.

## 7. What this UAT explicitly does NOT do

Per the instructions this package was built under:

- It does **not** modify the frozen R3.5 specification documents.
- It does **not** redesign any business process.
- It does **not** add new features (one small exception exists and is disclosed: a read-only "all active permissions" admin screen was added by explicit user request in the prior working session — this is noted wherever relevant and is not part of the frozen spec).
- It does **not** invent test accounts, routes, APIs, or data. Every user, URL, and screen named anywhere in this package was confirmed to exist in the actual codebase before being written down.

## 8. Document map

| # | Document | What it's for |
|---|---|---|
| 01 | Testing Overview and Strategy | This document |
| 02 | Quick Golden Flow UAT | The single most important test — one deliverable, start to finish |
| 03 | Complete Functional UAT Test Cases | Every governed feature, individually |
| 04 | Role, Permission and Security Tests | Access-control correctness |
| 05 | Edge Case and Negative Tests | The specific numeric/date/business-rule boundaries |
| 06 | Test Data and User Accounts | Who to log in as, and how to get/reset test data |
| 07 | Status/Workflow Glossary | What each status code (PL, RFP, COMP, ...) means |
| 08 | Test Execution Checklist | The simple day-to-day checkbox list |
| 09 | Defect Log | Where to record anything that fails |
| 10 | Requirement Test Traceability | Which governed requirement maps to which test, and its real status |
| 11 | Final UAT Report Template | The sign-off document at the end |

Start with `08_TEST_EXECUTION_CHECKLIST.md` for the fastest path into testing, or `02_QUICK_GOLDEN_FLOW_UAT.md` directly if you already know the smoke test passed.
