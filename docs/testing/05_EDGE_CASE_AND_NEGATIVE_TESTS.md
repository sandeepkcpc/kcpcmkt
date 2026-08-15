# 05 — Edge Case and Negative Tests

These are the specific, governed boundary rules that are easy to get subtly wrong — and easy to test precisely, because each has an exact pass/fail line. Every rule below was confirmed directly against the actual validation code before being written here (not assumed from the specification alone).

| ID | Priority | Rule | Test | Expected | Automated? | PASS/FAIL |
|---|---|---|---|---|---|---|
| UAT-EDGE-001 | P0 | Standard Planning defaults | Set a Standard schedule with only Live Date given | Shoot Date = Live − 5 days, Edit Date = Live − 2 days, computed automatically | YES | [ ] |
| UAT-EDGE-002 | P1 | Defaults are overridable | Set a Standard schedule with explicit Shoot/Edit date overrides (still respecting chronology) | The overrides are saved, not the computed defaults | NO | [ ] |
| UAT-EDGE-003 | P0 | Urgent required under 5 days | Attempt a **Standard** schedule with Live Date < 5 days from today | Rejected — "requires Urgent Planning Mode" | YES | [ ] |
| UAT-EDGE-004 | P0 | Urgent requires a reason | Attempt an Urgent schedule with Urgency Reason blank | Rejected | NO (mandatory-field pattern confirmed in code; not yet a dedicated HTTP test) | [ ] |
| UAT-EDGE-005 | P0 | Urgent requires manual Shoot/Edit dates | Attempt Urgent without explicit Shoot/Edit dates | Rejected — both are required inputs for Urgent, no auto-default | NO | [ ] |
| UAT-EDGE-006 | P0 | Exactly 5 days permitted as Standard | Set a Standard schedule with Live Date = today + exactly 5 days | Accepted | YES | [ ] |
| UAT-EDGE-007 | P1 | Urgent still permitted at 5+ days (by choice) | Deliberately use Urgent mode even though Live Date is ≥ 5 days out | Accepted — Urgent is never blocked at a longer horizon, only required at a shorter one | NO | [ ] |
| UAT-EDGE-008 | P0 | Past Live Date rejected | Attempt any schedule (Standard or Urgent) with a Live Date in the past | Rejected | NO | [ ] |
| UAT-EDGE-009 | P0 | Date chronology — Shoot after Edit rejected | Attempt a schedule where Shoot Date is after Edit Date | Rejected | NO (validation confirmed in code; add a dedicated manual/HTTP run) | [ ] |
| UAT-EDGE-010 | P0 | Date chronology — Edit after Live rejected | Attempt a schedule where Edit Date is after Live Date | Rejected | NO | [ ] |
| UAT-EDGE-011 | P1 | Urgent same-day Shoot=Edit permitted | Urgent schedule with Shoot Date = Edit Date (same day) | Accepted — equal dates are explicitly allowed under the governed chronology rule | NO | [ ] |
| UAT-EDGE-012 | P0 | Reel output missing Reel Type rejected | Add a Planned Output, Type = Reel, Reel Type left blank | Rejected — "Reel Type is mandatory when output type is Reel" | NO (validation confirmed in code) | [ ] |
| UAT-EDGE-013 | P0 | Reel output with valid Reel Type accepted | Add Type = Reel, Reel Type = Short (or Very Short / Long) | Accepted | NO | [ ] |
| UAT-EDGE-014 | P0 | Photography output with Reel Type rejected | Add Type = Photography, Reel Type set to any value | Rejected — "Reel Type must be blank for non-Reel output types" | NO | [ ] |
| UAT-EDGE-015 | P0 | Video output with Reel Type rejected | Add Type = Video, Reel Type set to any value | Rejected — same rule as above | NO | [ ] |
| UAT-EDGE-016 | P0 | Self-review rejected | A delegated Employee reviewer attempts to decide their own submitted/prepared work | Rejected — `PERM_SELF_APPROVAL_PROHIBITED` | YES | [ ] |
| UAT-EDGE-017 | P1 | Rework awards no personal Mark | Request Rework at any review gate instead of Approve | No `personal_mark_attributions` row is created for that cycle — absence, not a zero | NO | [ ] |
| UAT-EDGE-018 | P0 | Two qualifying Camerapersons each get the FULL Mark | Both assigned Camerapersons qualify at Shoot Review approval | Both receive the identical full predefined Mark — never split, never averaged | YES | [ ] |
| UAT-EDGE-019 | P0 | Two qualifying Editors each get the FULL Mark | Both assigned Editors qualify at Edit Review approval | Both receive the identical full predefined Mark | YES | [ ] |
| UAT-EDGE-020 | P0 | Hold preserves the primary workflow status | Place a Hold while Shoot In Progress or Editing | The primary status field is untouched — Hold is a separate, parallel record, never a status value itself | YES | [ ] |
| UAT-EDGE-021 | P1 | Reschedule preserves old/new values historically | Reschedule a deliverable | Both the prior and new Shoot/Edit/Live dates are recorded in a linked history row — the old values are not simply overwritten and lost | YES (mechanism); manual check recommended to visually confirm the old/new pair is legible on screen | [ ] |
| UAT-EDGE-022 | P1 | Reschedule never affects the Performance Due Date | Reschedule a deliverable that already has a recorded Actual Publication (and therefore a due date) | The due date (Actual Publication date + 2 days) is unchanged — it is explicitly non-reschedulable | NO | [ ] |
| UAT-EDGE-023 | P1 | Reassign preserves the old assignment historically | Reassign a Cameraperson/Editor task | The previous assignee's assignment row is ended (not deleted) and remains visible in history; the new assignee becomes active | NO | [ ] |
| UAT-EDGE-024 | P0 | Cancel blocked for an invalid lifecycle state | Attempt Cancel on a deliverable that is already Rejected/Cancelled, or has ever reached Completed | Rejected | NO | [ ] |
| UAT-EDGE-025 | P0 | Repost creates no new personal Marks | Record a Repost event | No new `personal_mark_attributions` row is created — Repost, like Publishing itself, never awards Marks | YES (mechanism proven; the "no Marks" negative assertion is implied by there being no Mark-awarding code path in Publishing at all — worth a direct manual confirmation) | [ ] |
| UAT-EDGE-026 | P0 | All-targets-N/A prohibited | Attempt to designate the last remaining live-or-pending publication target N/A, leaving zero live posts and zero pending targets | Rejected | YES | [ ] |
| UAT-EDGE-027 | P0 | Zero-denominator derived rate shows N/A | Enter performance metrics where a rate's denominator is zero or marked N/A (e.g. Link Clicks marked N/A) | The derived rate (e.g. CTR) displays as **N/A** — never a numeric `0`, never a calculation error/crash | YES | [ ] |
| UAT-EDGE-028 | P0 | Submitted scorecard is immutable | Attempt to directly edit a scorecard after Final Submit | No direct-edit path exists in the UI or API — only a Correction (separate governed action) can adjust it | YES | [ ] |
| UAT-EDGE-029 | P0 | Corrections create linked history, never overwrite | Correct a predefined Mark, a publication evidence URL, or a performance metric | A new, separate correction row is created, linked to the original; the original evidence/Mark/metric row is never modified or deleted | YES | [ ] |

## Notes for the tester

- Every "NO (automated)" row above was still confirmed against the actual source code before being written into this table — none of these are guesses about what *should* happen. They are genuine manual-testing gaps, not unknowns.
- Several of these (UAT-EDGE-004, 005, 008, 009, 010, 011, 017, 022, 023, 025) are strong candidates for the **next round of automated test-writing**, since the underlying validation logic already exists and is stable — they just haven't been wrapped in a dedicated HTTP-level test yet.
- If a P0 edge case fails, treat it as at least a **Major** defect (see `09_DEFECT_LOG.md`) — these are the exact rules a business stakeholder is most likely to notice being wrong in real use (e.g. a Mark getting split when it shouldn't, or a KPI showing `0%` instead of `N/A`).
