# 08 — Test Execution Checklist

The simplest, fastest day-to-day UAT document. Print/copy this page, work through it top to bottom, and check boxes as you go. Full detail for any item lives in the referenced document.

## SMOKE (10–15 min) — see `02_QUICK_GOLDEN_FLOW_UAT.md` §A

- [ ] Application starts
- [ ] Database connects
- [ ] Login page loads
- [ ] Login works (CEO account)
- [ ] Correct landing page loads (Content Pipeline for CEO/MM)
- [ ] All nav links load without error
- [ ] Idea Queue loads
- [ ] A deliverable can be opened
- [ ] Logged-out access to a protected page redirects to Login
- [ ] Logout works

**If any smoke box is unchecked → STOP. Fix before continuing.**

## GOLDEN FLOW — see `02_QUICK_GOLDEN_FLOW_UAT.md` §B

- [ ] Login
- [ ] Idea submitted
- [ ] Idea approved (Content ID + Content Plan + Marks created)
- [ ] Content ID visible
- [ ] Planning parameters saved
- [ ] Standard schedule set
- [ ] Planned Outputs added (Photography, Reel+Type, Video)
- [ ] Publication scope mapped
- [ ] Cameraperson assigned
- [ ] Planning Review submitted
- [ ] Planning Review approved → Shoot Assigned
- [ ] Shooting started
- [ ] Shoot Review submitted
- [ ] Shoot Review approved → Cameraperson Mark attributed
- [ ] Editor assigned
- [ ] Editing started
- [ ] Edit Review submitted
- [ ] Edit Review approved → Editor Mark attributed → Ready for Publishing
- [ ] Publishing started
- [ ] Actual Publication recorded → scope resolved → Performance Pending
- [ ] Scorecard draft saved (after Performance Due Date)
- [ ] Scorecard submitted
- [ ] **Completed**

## STAGE-BY-STAGE FUNCTIONAL (see `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md`)

- [ ] §A Authentication (6 tests)
- [ ] §B Business Roles (4 tests)
- [ ] §C Operational Permissions — positive path (5 tests)
- [ ] §D Idea Submission (4 tests)
- [ ] §E Idea Review incl. Reject/Retain/Reopen/self-review (9 tests)
- [ ] §F Planning incl. Standard/Urgent/Outputs/Scope/Assignment (18 tests)
- [ ] §G Shooting incl. multi-Cameraperson Marks (6 tests)
- [ ] §H Editing incl. multi-Editor Marks (8 tests)
- [ ] §I Publishing incl. Repost/Target N/A/Evidence Correction (11 tests)
- [ ] §J Performance incl. Due Date gate/N/A metrics/Correction (8 tests)
- [ ] §K Administrative Actions: Hold/Resume/Reschedule/Reassign/Cancel/Reopen (10 tests)
- [ ] §L Employee Self-Service (3 tests)
- [ ] §M Management Pipeline (3 tests)
- [ ] §N Users/Roles/Permission Administration (5 tests)
- [ ] §O Audit History (3 tests)
- [ ] §P Reporting/KPI/Export (10 tests)

## ROLE / PERMISSION / SECURITY (see `04_ROLE_PERMISSION_AND_SECURITY_TESTS.md`)

- [ ] §1 Access Class native authority (3 tests)
- [ ] §3/§4 Protected-action matrix — at minimum, run the full 11-dimension check for Permission #1 and #2 (already automated) plus **at least 3 more permissions manually** this session
- [ ] §5 Employee Privacy (10 tests)
- [ ] §6 UI-hidden vs. server-enforced spot-check on any failed item above

## EDGE CASE / NEGATIVE (see `05_EDGE_CASE_AND_NEGATIVE_TESTS.md`)

- [ ] All 29 edge-case rows (UAT-EDGE-001 through 029)

## REGRESSION

- [ ] Re-run the full automated suite: `mvn clean verify -Dspring.profiles.active=test` — confirm all tests still pass
- [ ] Re-run Smoke + Golden Flow after any defect fix

## SIGN-OFF

- [ ] All P0 tests attempted
- [ ] All P0 tests passing, or every failing P0 has a logged, understood defect
- [ ] `09_DEFECT_LOG.md` up to date
- [ ] `10_REQUIREMENT_TEST_TRACEABILITY.md` statuses updated
- [ ] `11_FINAL_UAT_REPORT_TEMPLATE.md` filled in and a recommendation made

---

**Today's session date:** ______________
**Tester name:** ______________
**Environment (dev / other):** ______________
**Database reset before this session? (Y/N):** ______________
