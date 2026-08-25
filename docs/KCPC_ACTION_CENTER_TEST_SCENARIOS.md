# KCPC Content Detail Action Center — Manual & Regression Test Scenarios

**Purpose:** Validate the new stage-aware, permission-aware **Content Detail → Available Actions** behavior after introducing:

- `ContentCanonicalStage`
- `AvailableActionService`
- shared UI/backend Reassign eligibility
- canonical Current Stage display
- filtered Reassign Task Stage options

**Scope:** Content Detail Action Center only.  
**Out of scope:** KPI Dashboard, Permission Administration UI, Team Workload, My Work redesign, Publishing/Performance formulas.

---

# 1. Core Rule to Validate

The Action Center must be derived from:

```text
Actual Current Workflow Stage
+ Current Workflow Status
+ Existing Assignment State
+ User Permission / Native Authority
+ Existing Backend Action Eligibility
```

The UI must never show an action merely because a user has the permission.

The backend must independently reject actions that are not valid in the current workflow state.

---

# 2. Canonical Stage Expectations

| Raw Workflow Status Example | Expected Canonical Stage |
|---|---|
| Planning | Planning |
| Planning Review | Planning |
| Shoot Assigned | Shoot |
| Shoot In Progress | Shoot |
| Shoot Review | Shoot |
| Shoot Approved | Shoot |
| Edit Assigned | Edit |
| Editing | Edit |
| Edit Review | Edit |
| Edit Approved | Edit |
| Ready for Publishing | Publishing |
| Publishing | Publishing |
| Reopened for Publishing | Publishing |
| Performance Pending | Performance |
| Performance Update | Performance |
| Completed | Completed |

If a raw status is not mapped, verify the safe fallback behavior defined by `ContentCanonicalStage`.

---

# 3. Planning Stage — Reassign

## TC-ACT-001 — Planning + Active Shoot Assignment + PERM_11

**Preconditions**
- Canonical stage = `Planning`
- Active `ShootingAssignment` exists
- User has `PERM_11_REASSIGN`

**Expected**
- `Current Stage: Planning`
- `Reassign` visible
- Reassign Task Stage shows `SHOOTING`
- Valid reassignment succeeds

## TC-ACT-002 — Planning + No Active Shoot Assignment + PERM_11

**Expected**
- Reassign hidden
- Direct POST rejected
- No assignment created/changed

## TC-ACT-003 — Planning + Active Shoot Assignment + No PERM_11

**Expected**
- Reassign hidden
- Direct POST rejected
- Existing assignment unchanged

---

# 4. Shoot Stage — Reassign

## TC-ACT-004 — Shoot Stage + Active Shoot Assignment + PERM_11

**Expected**
- Reassign visible where exact current status is still eligible
- Task Stage shows `SHOOTING`
- Valid reassignment succeeds

## TC-ACT-005 — Shoot Stage + No Active Shoot Assignment

**Expected**
- Reassign hidden
- Direct POST rejected

## TC-ACT-006 — Finalized Shoot State

**Expected**
- Reassign not available once the Shoot assignment is historically finalized
- Crafted POST rejected

---

# 5. Edit Stage — Reassign

## TC-ACT-007 — Edit Stage + Active Edit Assignment + PERM_11

**Expected**
- Reassign visible
- Task Stage shows `EDITING`
- Valid reassignment succeeds

## TC-ACT-008 — Edit Stage + No Active Edit Assignment

**Expected**
- Reassign hidden
- Direct POST rejected

## TC-ACT-009 — Planning/Shoot Stage Must Not Reassign EDITING

**Expected**
- `EDITING` not offered
- Crafted request rejected

---

# 6. Publishing / Performance / Completed

## TC-ACT-010 — Publishing + PERM_11

**Expected**
- Generic Reassign hidden
- No Publishing option in generic Reassign

## TC-ACT-011 — Performance + PERM_11

**Expected**
- Shoot/Edit Reassign absent
- Direct POST rejected

## TC-ACT-012 — Completed Content

**Expected**
- Normal Reschedule/Reassign/Cancel absent where terminal-state rules reject them
- Valid `Reopen for Publishing` shown only when authorized

## TC-ACT-013 — Completed + Unauthorized Reopen

**Expected**
- Reopen hidden
- Direct request rejected

---

# 7. Reschedule

## TC-ACT-014 — Reschedulable Open Content + PERM_10

**Expected**
- Reschedule visible
- Valid request succeeds

## TC-ACT-015 — Reschedule Permission Missing

**Expected**
- Reschedule hidden
- Direct request rejected

## TC-ACT-016 — Closed/Terminal Content + PERM_10

**Expected**
- Reschedule hidden
- Direct request rejected

---

# 8. Cancel

## TC-ACT-017 — Cancellable Content + PERM_12

**Expected**
- Cancel visible
- Valid cancel succeeds

## TC-ACT-018 — Cancel Permission Missing

**Expected**
- Cancel hidden
- Direct request rejected

## TC-ACT-019 — Cancel Invalid by Backend State

**Expected**
- Cancel hidden
- Direct request rejected

---

# 9. Permission Alone Must Not Be Enough

## TC-ACT-020 — PERM_11 at Wrong Stage
Example: Publishing + PERM_11.

**Expected**
- Reassign hidden
- Direct request rejected

## TC-ACT-021 — PERM_10 at Invalid State

**Expected**
- Reschedule hidden
- Direct request rejected

## TC-ACT-022 — PERM_12 at Invalid State

**Expected**
- Cancel hidden
- Direct request rejected

---

# 10. Native CEO/MM Authority

## TC-ACT-023 — CEO/MM + Invalid Reassign State

**Expected**
- Reassign hidden
- Direct POST rejected
- Native authority does not bypass workflow-state eligibility

## TC-ACT-024 — CEO/MM + Valid Reassign State

**Expected**
- Reassign visible
- Valid reassignment succeeds

---

# 11. Reassign Task Stage Dropdown Filtering

## TC-ACT-025 — Planning Stage

**Expected**
- If active Shoot assignment exists, only `SHOOTING` appears

## TC-ACT-026 — Edit Stage

**Expected**
- If active Edit assignment exists, only `EDITING` appears

## TC-ACT-027 — No Eligible Stage

**Expected**
- Reassign hidden
- No meaningless empty dropdown

---

# 12. Available Actions Empty State

## TC-ACT-028 — No Administrative Actions Available

**Expected UI**
```text
Available Actions
Actions available for the current workflow stage and your permissions.
No administrative actions available at this stage.
```

---

# 13. Current Stage Display

## TC-ACT-029 — Shoot Review
Expected: `Current Stage: Shoot`

## TC-ACT-030 — Edit Assigned
Expected: `Current Stage: Edit`

## TC-ACT-031 — Ready for Publishing
Expected: `Current Stage: Publishing`

---

# 14. Pipeline Context

## TC-ACT-032 — Needs Attention + Shoot Review

**Expected**
- `Needs Attention` is not treated as a stage
- Current Stage = Shoot
- Shoot-valid actions only

## TC-ACT-033 — All Filter + Edit Assigned

**Expected**
- Current Stage = Edit
- Pipeline filter does not affect action eligibility

---

# 15. UI vs Backend Consistency

## TC-ACT-034 — Visible Action Must Be Executable

For each displayed action:
1. Open action
2. Submit valid input

**Expected**
- Backend accepts subject to normal form validation
- No immediate rejection purely due to invalid stage

## TC-ACT-035 — Hidden Action Direct POST

**Expected**
- Backend rejects the request
- UI hiding is not the only protection

---

# 16. Assignment Candidate Eligibility

## TC-ACT-036 — Reassign to Ineligible Shoot/Edit User

**Expected**
- Reassignment rejected by existing execution-eligibility rules

## TC-ACT-037 — Reassign to Eligible Shoot/Edit User

**Expected**
- Reassignment succeeds

---

# 17. Compact Manual QA Matrix

| Content Stage | Active Assignment | Permission | Expected |
|---|---:|---|---|
| Planning | Shoot = Yes | PERM_11 | Reassign SHOOTING visible |
| Planning | Shoot = No | PERM_11 | Reassign hidden |
| Shoot | Shoot = Yes | PERM_11 | Reassign visible if status eligible |
| Shoot | Shoot = No | PERM_11 | Reassign hidden |
| Edit | Edit = Yes | PERM_11 | Reassign EDITING visible |
| Edit | Edit = No | PERM_11 | Reassign hidden |
| Publishing | Any | PERM_11 | Generic Reassign hidden |
| Performance | Any | PERM_11 | Generic Reassign hidden |
| Completed | Any | PERM_11 | Reassign hidden |
| Open/reschedulable | N/A | PERM_10 | Reschedule visible |
| Invalid/closed | N/A | PERM_10 | Reschedule hidden |
| Cancellable | N/A | PERM_12 | Cancel visible |
| Not cancellable | N/A | PERM_12 | Cancel hidden |

---

# 18. Test Recording Template

| Test ID | Content ID | User | Status | Expected | Actual | Pass/Fail | Notes |
|---|---|---|---|---|---|---|---|
| TC-ACT-001 | | | | | | | |
| TC-ACT-002 | | | | | | | |
| TC-ACT-004 | | | | | | | |
| TC-ACT-007 | | | | | | | |
| TC-ACT-010 | | | | | | | |
| TC-ACT-012 | | | | | | | |
| TC-ACT-023 | | | | | | | |
| TC-ACT-032 | | | | | | | |

---

# 19. Minimum Sign-Off Scenarios

These must pass before accepting the Action Center change:

1. Planning + active Shoot assignment + PERM_11 → Reassign works.
2. Planning + no Shoot assignment + PERM_11 → hidden + backend rejected.
3. Shoot + active Shoot assignment → valid Reassign works.
4. Edit + active Edit assignment → valid Reassign works.
5. Publishing + PERM_11 → generic Reassign absent.
6. Performance + PERM_11 → generic Reassign absent.
7. Completed → ordinary actions absent; Reopen only where valid.
8. CEO/MM invalid-state Reassign → backend rejected.
9. Current Stage shows canonical stage, not raw status.
10. Needs Attention uses actual underlying stage.
11. Hidden action direct POST is rejected.
12. Reassign Task Stage dropdown shows only eligible stage.

---

# 20. Automated Test Result Template

```text
Total tests:
Passed:
Failed:
Known pre-existing unrelated failures:
New Action Center failures:
```

Any new Action Center-related failure should block sign-off until investigated.
