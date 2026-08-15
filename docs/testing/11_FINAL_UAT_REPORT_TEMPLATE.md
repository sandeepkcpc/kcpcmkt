# 11 — Final UAT Report (Template)

Fill this in at the end of a UAT cycle (after working through docs 02–08 and keeping doc 09's Defect Log current). This is the document a business stakeholder reads to make a go/no-go call — write it for that audience, not for developers.

**UAT Cycle:** ______________ (e.g. "Cycle 1 — 2026-08-20")
**Tester(s):** ______________
**Environment:** ______________ (e.g. "local dev, database reset before this cycle: Y/N")
**Application version / commit:** ______________

---

## 1. Test Volume

The test pool defined in this package, for reference (fill Executed/Passed/etc. from your actual run):

| Priority | Total Defined | Executed | Passed | Failed | Blocked | Not Run |
|---|---|---|---|---|---|---|
| P0 | 94 | | | | | |
| P1 | 53 | | | | | |
| P2 | 11 | | | | | |
| P3 | 1 | | | | | |
| **Total** | **159** | | | | | |

*(159 = the discrete UAT-* test cases defined across `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md`, `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md`, and `05_EDGE_CASE_AND_NEGATIVE_TESTS.md`, plus the 24-step + 10-check Golden Flow in `02`. This is the defined pool, not a claim that all 159 have been run — fill in the real numbers above from your actual session.)*

## 2. Defect Summary

| Severity | Count | Of which still Open |
|---|---|---|
| Blocker | | |
| Critical | | |
| Major | | |
| Minor | | |
| Cosmetic | | |

Pull these numbers from `09_DEFECT_LOG.md`.

## 3. Findings by Category

Summarize, don't just count — a reader should understand *what kind* of issues remain, not just how many.

**Security Findings:**
______________________________________________________

**Privacy Findings:**
______________________________________________________

**Workflow Findings:**
______________________________________________________

**UI Findings:**
______________________________________________________

**API Findings:**
______________________________________________________

**Database Findings:**
______________________________________________________

**KPI Findings:**
______________________________________________________

**Export Findings:**
______________________________________________________

## 4. Overall Completion

**Overall Completion %** = (P0 Passed + P1 Passed) / (P0 Total + P1 Total), as a rough headline number — but read Section 1's real breakdown before trusting a single percentage; a high pass rate with several open P0s is not "mostly done," it's blocked.

**Calculated:** ______________%

## 5. Known, accepted (not blocking) items

List anything intentionally NOT fully tested or NOT fixed that the business has explicitly agreed is acceptable to ship with — e.g. items from `10_REQUIREMENT_TEST_TRACEABILITY.md`'s `NOT TESTED` rows that were consciously deprioritized rather than missed. Do not let this list become a place to quietly bury real gaps — every item here should be a genuine, discussed decision, cross-referenced to the Defect Log or traceability doc.

______________________________________________________

## 6. Recommendation

Choose exactly one:

- [ ] **ACCEPT** — all P0/P1 tests pass, no open Blocker/Critical defects, ready for production use
- [ ] **ACCEPT WITH MINOR ISSUES** — all P0/P1 tests pass; only Minor/Cosmetic defects remain open
- [ ] **CONDITIONAL ACCEPTANCE** — P0 tests pass, but some P1/P2 items or Major defects remain open; acceptable for a limited pilot with those specific conditions documented above
- [ ] **REJECT / REWORK REQUIRED** — one or more P0 tests fail, or a Blocker/Critical defect is open

**Rationale for the recommendation above:**
______________________________________________________

**Conditions attached (if Conditional Acceptance):**
______________________________________________________

**Sign-off:**

| Role | Name | Date | Signature |
|---|---|---|---|
| Tester | | | |
| Project Owner | | | |
