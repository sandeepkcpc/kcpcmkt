# 07 — Status / Workflow Glossary

Every status code the application can show, in plain business language. This exists so that anyone looking at a screen showing `PL`, `PP`, `COMP`, etc. can understand it immediately — **although, as of this UAT pass, the application now displays the human-readable name (e.g. "Planning") rather than the raw code on every screen; the codes below are the internal values you'll see in the database, in URLs, and in this documentation, not on the screen itself.** This was a real conformance finding fixed during the prior working session (raw codes were being shown to users) — see the note in Section 3.

Source: `WorkflowStatus.java` (the governed 22-concept catalogue, BFD §6.8) plus direct inspection of the workflow-transition logic in each domain service.

## 1. Full glossary

| Code | Human-Readable Status | Lifecycle Stage | Meaning | How Entered | Who Acts Next | Valid Next Action(s) | Classification |
|---|---|---|---|---|---|---|---|
| `IS` | Idea Submitted | Idea Management | Momentary system status immediately after an Idea is created | Idea Submission form | (system) | auto-advances to Pending Approval | Active |
| `PA` | Pending Approval | Idea Management | An idea is waiting for an Idea Review decision | From `IS`, or Reopen from `RET` | CEO/MM or a delegated Permission #1 Employee | Approve / Reject / Retain | Active |
| `RJ` | Rejected | Idea Management | The idea was declined; no further action defined | Reject decision at `PA` | — none | — (terminal) | Terminal |
| `RET` | Retained | Idea Management | The idea is parked/dormant, not actively rejected or approved | Retain decision at `PA` | CEO/MM (administrative Reopen, Permission #1) | Reopen → back to `PA` | Dormant |
| `PL` | Planning | Planning | Planning parameters, schedule, outputs, publication scope, and initial Cameraperson assignment are being filled in | Idea Approval; or Planning Review rework; or admin Reopen | CEO/MM or delegated Permission #2 Employee | Complete parameters → Submit for Planning Review | Active |
| `PLRV` | Planning Review | Planning | The completed Planning package is awaiting review | Submit for Planning Review at `PL` | CEO/MM or delegated Permission #3 Employee | Approve → `SA` (via `PLAP`) / Request Rework → back to `PL` | Active |
| `PLAP` | Planning Approved | Planning | Momentary status the instant Planning Review is approved | Planning Review approval | (system) | auto-advances to Shoot Assigned | Active |
| `SA` | Shoot Assigned | Shooting | The assigned Cameraperson(s) are ready to begin filming | Planning Review approval | Assigned Cameraperson, or CEO/MM | Start Shooting | Active |
| `SIP` | Shoot In Progress | Shooting | Filming is underway | Start Shooting at `SA` | Cameraperson, or CEO/MM | Submit for Shoot Review (also: Hold is possible here) | Active |
| `SRV` | Shoot Review | Shooting | Shoot output is awaiting review | Submit for Shoot Review at `SIP` | CEO/MM or delegated Permission #5 Employee | Approve → `SAP` / Request Rework → back to `SIP` | Active |
| `SAP` | Shoot Approved | Shooting | Shoot is approved; qualifying Cameraperson(s) receive their Mark here | Shoot Review approval | CEO/MM or delegated Permission #6 Employee | Assign Editor → `EA` | Active |
| `EA` | Edit Assigned | Editing | The assigned Editor(s) are ready to begin editing | Editor Assignment at `SAP` | Assigned Editor, or CEO/MM | Start Editing | Active |
| `ED` | Editing | Editing | Editing is underway | Start Editing at `EA` | Editor, or CEO/MM | Submit for Edit Review (also: Hold is possible here) | Active |
| `ERV` | Edit Review | Editing | Edit output is awaiting review | Submit for Edit Review at `ED` | CEO/MM or delegated Permission #7 Employee | Approve → `EAP` / Request Rework → back to `ED` | Active |
| `EAP` | Edit Approved | Editing | Momentary status the instant Edit Review is approved; qualifying Editor(s) receive their Mark here | Edit Review approval | (system) | auto-advances to Ready for Publishing | Active |
| `RFP` | Ready for Publishing | Publishing | Content is fully produced and cleared to publish | Edit Review approval | CEO/MM or delegated Permission #8 Employee | Start Publishing | Active |
| `PUBG` | Publishing | Publishing | Publishing is underway; not every planned output/target has gone live yet | Start Publishing at `RFP` | Publisher (Permission #8) | Record Actual Publication event(s) / designate Target N/A, until scope is fully resolved | Active |
| `PP` | Performance Pending | Performance | Publication scope is fully resolved (every planned target is live or N/A); waiting for the Performance Due Date | Scope resolved at `PUBG` | (system, then Permission #9 holder) | Wait for Due Date (Actual Publication date + 2 days, non-reschedulable), then metric entry begins | Active |
| `PFUP` | Performance Update | Performance | Performance metrics are being entered/finalized | First eligible scorecard draft/entry on-or-after the due date | CEO/MM or delegated Permission #9 Employee | Draft → Submit scorecard, for every obligation | Active |
| `COMP` | Completed | Closed | The full lifecycle is finished for this Content ID | Every performance obligation submitted | — (lifecycle finished) | CEO/MM may Reopen for Publishing (Permission #8, → `PUBG`) or Reopen for Metric Correction (Permission #9, → `PFUP`) | Closed (reopenable) |
| `DLY` | Delayed | (flag, not a stage) | A supplementary flag shown alongside an *active* status when the deliverable has passed its planned date for its current stage | Computed automatically, never a status of its own | — | — (informational only) | Supplementary Flag |
| `CAN` | Cancelled | (any) | The deliverable was administratively cancelled and will not proceed | Cancel action (Permission #12), at any non-closed status | — none | — (terminal; cancellation is permanently blocked once the deliverable has ever reached `COMP`) | Terminal |

## 2. Reading the table

- **Active** statuses are the normal, moving parts of the lifecycle.
- **Dormant** (`RET`) means parked, not moving, but recoverable via an explicit administrative action.
- **Terminal** (`RJ`, `CAN`) means the lifecycle stops there — no further workflow action is defined.
- **Closed (reopenable)** (`COMP`) means the lifecycle finished normally, but two specific, permission-gated Reopen paths exist for legitimate corrections after the fact (an additional platform/repost, or a metric correction) — this is not the same as being able to freely re-edit a completed item.
- **Supplementary Flag** (`DLY`) is never a status by itself — you will never see a deliverable "in" the Delayed status; you'll see it Delayed **while** in some other active status (e.g. "Shoot In Progress — Delayed").

## 3. Conformance finding closed during UAT preparation

**Prior finding (now fixed):** earlier builds displayed the raw internal code (e.g. `PLRV`, `RFP`, `SAP`) directly on the Content Pipeline, My Work, Idea Queue, Idea Detail, and Deliverable Detail screens — a genuine **UI CONFORMANCE FINDING** against the UI/UX specification's expectation of human-readable presentation. This was identified and fixed in the working session immediately prior to this UAT pass: every status-display location now shows the human-readable name from the table above (`WorkflowStatus.getStatusName()`), while all internal workflow-logic comparisons continue to use the code, unaffected. **Testers should confirm this holds during Golden Flow execution** (Step 04 onward in `02_QUICK_GOLDEN_FLOW_UAT.md`) — if any screen is found still showing a raw code, log it as a regression in `09_DEFECT_LOG.md`.
