# 02 — Quick Golden Flow UAT

**This is the single most important document in this package.** It takes one real piece of content from Login through Completed, using real screens and real test accounts. If this passes end to end, the core promise of the software is proven. Everything else in this UAT package tests details and edge cases around this spine.

All URLs below assume the application is running at **`http://localhost:8080`** (see `06_TEST_DATA_AND_USER_ACCOUNTS.md` for how to start it). All test accounts and their passwords are listed in that same document — passwords are never repeated inline here.

---

## Section A — Smoke Test (10–15 minutes)

Run this first. **If any of these fail, STOP — do not proceed to the Golden Flow.** Fix the environment first.

| # | Check | Action | Expected Result | PASS/FAIL |
|---|---|---|---|---|
| A1 | Application starts | Run the startup command (see doc 06) | Console shows `Started KcpcMktApplication` with no `ERROR` lines | [ ] |
| A2 | Database connectivity | (part of A1) | Startup log shows Flyway `Schema "public" is up to date` or successful migration — no connection errors | [ ] |
| A3 | Login page loads | Go to `http://localhost:8080/login` | A sign-in form appears (Email, Password, Sign In) | [ ] |
| A4 | Login works | Sign in as the CEO account (doc 06) | Redirected away from `/login`, no error banner | [ ] |
| A5 | Role landing | (continues from A4) | CEO lands on **Content Pipeline** (`/app/pipeline`) — a table of content plans | [ ] |
| A6 | Navigation | Click each top-nav link once (Content Pipeline, Idea Queue, Team Workload, etc.) | Every link loads a page with no blank screen and no error page | [ ] |
| A7 | Idea Queue | Go to `/app/ideas` | A table of ideas loads (may be empty on a fresh database — that's fine) | [ ] |
| A8 | Open a deliverable | From the Pipeline table, click any existing Content ID (if the list is empty, skip and re-check after Golden Flow Step 3) | The Deliverable Detail screen loads, showing a status badge and panels | [ ] |
| A9 | Basic authorization | Log out, then try going directly to `http://localhost:8080/app/pipeline` in the same browser | Redirected to `/login` — the page does not load without being signed in | [ ] |
| A10 | Logout | Click "Sign out" (top-right, while logged in) | Returned to the login page | [ ] |

**Smoke test result:** [ ] PASS — proceed to Golden Flow  [ ] FAIL — stop, log the defect (doc 09), fix before continuing

---

## Section B — Golden Flow: Idea → Completed

### Test Data for this run

| Field | Value |
|---|---|
| Idea Title | `Diwali Bandhani Saree Reel — UAT` |
| Planning Mode | STANDARD |
| Content Priority | MEDIUM |
| Planned Live Date | today + 10 days (recompute at run time so the scenario stays valid) |
| Folder Link | `https://drive.google.com/uat-diwali-bandhani-saree` (any well-formed URL) |
| Planned Outputs | Photography; Reel (Reel Type: Short); Video |
| Publication Target | Instagram · kcpcbandhani |
| Cameraperson | Rohan Kapoor (`camera@kcpcbandhani.local`) |
| Editor | Ananya Verma (`editor@kcpcbandhani.local`) |

Use real, currently-configured accounts only — see doc 06. Do not invent users.

---

### STEP 01 — LOGIN

**User:** KCPC CEO (`ceo@kcpcbandhani.local`)
**Business Role:** CEO — Access Class CEO_OWNER
**Required Permission:** none (native authority)
**Screen / Route:** `GET /login`
**Precondition:** logged out
**Action:** enter email + password, click **Sign In**
**Expected UI Result:** redirected to `/app/pipeline` (Content Pipeline — CEO/MM landing screen)
**Expected Workflow Status:** n/a
**Expected DB/Audit Effect:** a session/token is issued (server-side token registry)
**Expected Next Actor:** same user, submitting the Idea
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-01-login.png`
**Remarks:** _______________

---

### STEP 02 — IDEA SUBMISSION

**User:** KCPC CEO
**Business Role:** CEO_OWNER
**Required Permission:** none — any of the 3 access classes may submit an idea
**Screen / Route:** `GET /app/ideas/new` (link: **Submit Idea** in the nav bar)
**Precondition:** logged in
**Action:** fill **Title** = test data above, **Reference Link** (optional, leave blank or add any URL), **Notes/Remarks** (optional), click **Submit**
**Test Data:** Idea Title above
**Expected UI Result:** redirected to the Idea Queue or Idea Detail; the new idea appears with a generated Idea Code (format `IDEA-YYYYMMDD-####`)
**Expected Workflow Status:** `IS` → `PA` (Idea Submitted, then system-derived to Pending Approval) — display shows **"Pending Approval"**, not a raw code
**Expected DB/Audit Effect:** new row in `ideas`; `workflow_instances` created; `IDEA_SUBMITTED` audit entry
**Expected Next Actor:** CEO or Marketing Manager (Idea Review, Permission #1)
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-02-idea-submitted.png`
**Remarks:** _______________

---

### STEP 03 — IDEA REVIEW → APPROVAL (Content ID + Content Plan + Marks created atomically)

**User:** KCPC CEO
**Business Role:** CEO_OWNER
**Required Permission:** Permission #1 (Idea Review) — native for CEO
**Screen / Route:** `GET /app/ideas/{ideaId}` (open the idea just submitted from the Idea Queue)
**Precondition:** idea status = Pending Approval
**Action:** in the review panel, select **Approve**, choose a Cameraperson Mark and Editor Mark from the controlled list (e.g. `1.0` each), click **Submit Decision**
**Test Data:** Decision = Approve; Cameraperson Mark = `1.0`; Editor Mark = `1.0`
**Expected UI Result:** status changes to **"Planning"**; a **Content ID** is now visible (format `C-MMDD-####`)
**Expected Workflow Status:** `PA` → `PL` (Planning)
**Expected DB/Audit Effect:** `content_plans` row created; `predefined_role_marks` row created with the two chosen Marks; `IDEA_APPROVED` audit entry with the allocated Content ID; all of this happens as one atomic operation
**Expected Next Actor:** CEO/MM or delegated Planning-Execution Employee, to complete Planning
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-03-idea-approved.png`
**Remarks:** _______________

---

### STEP 04 — CONTENT ID CREATED (verification step, no new action)

**User:** KCPC CEO
**Screen / Route:** `GET /app/pipeline` or the deliverable detail page reached from the idea
**Precondition:** Step 03 complete
**Action:** locate the new Content ID in the Pipeline table; open it
**Expected UI Result:** Deliverable Detail screen loads, status badge shows **"Planning"**, header shows the Content ID and idea title
**Expected Workflow Status:** `PL`
**Expected DB/Audit Effect:** none new — this step only verifies Step 03's effect
**Expected Next Actor:** same
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-04-content-id.png`
**Remarks:** _______________

---

### STEP 05 — PLANNING: PARAMETERS

**User:** KCPC CEO
**Required Permission:** Permission #2 (Planning Execution) — native for CEO
**Screen / Route:** Deliverable Detail → **Planning Workspace** panel
**Precondition:** status = Planning
**Action:** set **Content Priority** = MEDIUM, **Folder Link** = test data URL above, leave Category/SKU/Talent blank (all optional), click **Save**
**Expected UI Result:** panel reflects saved values, no error
**Expected Workflow Status:** unchanged (`PL`)
**Expected DB/Audit Effect:** `content_plans` row updated; `PARAMETERS_UPDATED`-style audit entry
**Expected Next Actor:** same
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 06 — PLANNING: SCHEDULE (Standard Mode)

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail → Planning Workspace → **Schedule**
**Precondition:** status = Planning
**Action:** enter **Planned Live Date** = today + 10 days, leave Shoot/Edit date overrides blank (system defaults apply), click **Set Standard Schedule**
**Expected UI Result:** Shoot Date auto-calculates to Live − 5 days, Edit Date to Live − 2 days; both now visible on the panel
**Expected Workflow Status:** unchanged (`PL`)
**Expected DB/Audit Effect:** planned dates saved on `content_plans`
**Expected Next Actor:** same
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 07 — PLANNING: PLANNED OUTPUTS

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail → Planning Workspace → **Planned Outputs** → **+ Add Output**
**Precondition:** status = Planning
**Action:** add three outputs one at a time: (1) Output Type = Photography; (2) Output Type = Reel, Reel Type = Short; (3) Output Type = Video
**Expected UI Result:** all three outputs listed under Planned Outputs
**Expected Workflow Status:** unchanged (`PL`)
**Expected DB/Audit Effect:** three `planned_outputs` rows
**Expected Next Actor:** same
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 08 — PLANNING: PUBLICATION SCOPE

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail → Planning Workspace → Planned Outputs → **Map targets** (per output)
**Precondition:** at least one Planned Output exists
**Action:** for each of the three outputs, open "Map targets" and check **Instagram · kcpcbandhani**, then save
**Expected UI Result:** the mapped target appears against each output
**Expected Workflow Status:** unchanged (`PL`)
**Expected DB/Audit Effect:** `planned_output_publication_target_mappings` rows created
**Expected Next Actor:** same
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 09 — PLANNING: INITIAL SHOOTING ASSIGNMENT

**User:** KCPC CEO
**Required Permission:** Permission #4 (Shoot Assignment) — native for CEO
**Screen / Route:** Deliverable Detail → Planning Workspace → **Cameraperson Assignment**
**Precondition:** status = Planning
**Action:** select **Rohan Kapoor** as Cameraperson, click **Assign**
**Test Data:** Cameraperson = Rohan Kapoor
**Expected UI Result:** Rohan Kapoor listed as an active Cameraperson assignment
**Expected Workflow Status:** unchanged (`PL`)
**Expected DB/Audit Effect:** `shooting_assignments` row created
**Expected Next Actor:** same (submit for review)
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 10 — PLANNING REVIEW: SUBMIT

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail → Planning Workspace → **Submit for Review**
**Precondition:** all Planning parameters, schedule, and folder link are complete (a real precondition enforced by the system — submission is blocked otherwise)
**Action:** click **Submit for Planning Review**
**Expected UI Result:** status changes to **"Planning Review"**
**Expected Workflow Status:** `PL` → `PLRV`
**Expected DB/Audit Effect:** new `review_cycles` row (gate = Planning Review)
**Expected Next Actor:** CEO/MM or delegated Planning-Review Employee
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 11 — PLANNING REVIEW: DECISION (Approve)

**User:** KCPC CEO
**Required Permission:** Permission #3 (Planning Review) — native for CEO
**Screen / Route:** Deliverable Detail → Planning Workspace → **Planning Review Decision**
**Precondition:** status = Planning Review; at least one active Cameraperson assignment exists (enforced)
**Action:** select **Approve**, click **Submit Decision**
**Expected UI Result:** status changes to **"Shoot Assigned"**
**Expected Workflow Status:** `PLRV` → `PLAP` → `SA` (both transitions happen together)
**Expected DB/Audit Effect:** `review_cycles` row decided APPROVED; `PLANNING_APPROVED` audit entry
**Expected Next Actor:** the assigned Cameraperson (or CEO/MM), to start shooting
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-11-planning-approved.png`
**Remarks:** _______________

---

### STEP 12 — START SHOOTING

**User:** KCPC CEO (or log in as Rohan Kapoor, the assigned Cameraperson, to test the assignee path)
**Screen / Route:** Deliverable Detail → **Shoot** panel → **Start Shooting**
**Precondition:** status = Shoot Assigned
**Action:** click **Start Shooting**
**Expected UI Result:** status changes to **"Shoot In Progress"**
**Expected Workflow Status:** `SA` → `SIP`
**Expected DB/Audit Effect:** `SHOOTING_STARTED` audit entry
**Expected Next Actor:** Cameraperson, to submit for review
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 13 — SHOOT REVIEW: SUBMIT

**User:** same as Step 12
**Screen / Route:** Deliverable Detail → Shoot panel → **Submit for Shoot Review**
**Precondition:** status = Shoot In Progress; no open Hold exists
**Action:** click **Submit for Shoot Review**
**Expected UI Result:** status changes to **"Shoot Review"**
**Expected Workflow Status:** `SIP` → `SRV`
**Expected DB/Audit Effect:** new `review_cycles` row (gate = Shoot Review)
**Expected Next Actor:** CEO/MM or delegated Shoot-Review Employee
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 14 — SHOOT REVIEW: APPROVAL (Cameraperson Mark attributed)

**User:** KCPC CEO
**Required Permission:** Permission #5 (Shoot Review) — native for CEO
**Screen / Route:** Deliverable Detail → Shoot panel → **Shoot Review Decision**
**Precondition:** status = Shoot Review
**Action:** select **Approve**, check **Rohan Kapoor** as a qualifying recipient, click **Submit Decision**
**Expected UI Result:** status changes to **"Shoot Approved"**; Rohan Kapoor now shows a Mark attribution
**Expected Workflow Status:** `SRV` → `SAP`
**Expected DB/Audit Effect:** `personal_mark_attributions` row created for Rohan Kapoor, value = the Cameraperson Mark set in Step 03 (full amount, not split)
**Expected Next Actor:** CEO/MM, to assign an Editor
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-14-shoot-approved-marks.png`
**Remarks:** _______________

---

### STEP 15 — EDITOR ASSIGNMENT

**User:** KCPC CEO
**Required Permission:** Permission #6 (Edit Assignment) — native for CEO
**Screen / Route:** Deliverable Detail → **Edit** panel → editor assignment form
**Precondition:** status = Shoot Approved (Editor assignment is blocked before this point)
**Action:** select **Ananya Verma**, click **Assign**
**Test Data:** Editor = Ananya Verma
**Expected UI Result:** status changes to **"Edit Assigned"**; Ananya Verma listed as active Editor
**Expected Workflow Status:** `SAP` → `EA`
**Expected DB/Audit Effect:** `editing_assignments` row created; `EDITOR_ASSIGNED` audit entry
**Expected Next Actor:** the assigned Editor, to start editing
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 16 — START EDITING

**User:** KCPC CEO (or Ananya Verma)
**Screen / Route:** Deliverable Detail → Edit panel → **Start Editing**
**Precondition:** status = Edit Assigned
**Action:** click **Start Editing**
**Expected UI Result:** status changes to **"Editing"**
**Expected Workflow Status:** `EA` → `ED`
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 17 — EDIT REVIEW: SUBMIT

**User:** same as Step 16
**Screen / Route:** Deliverable Detail → Edit panel → **Submit for Edit Review**
**Precondition:** status = Editing
**Action:** click **Submit for Edit Review**
**Expected UI Result:** status changes to **"Edit Review"**
**Expected Workflow Status:** `ED` → `ERV`
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 18 — EDIT REVIEW: APPROVAL (Editor Mark attributed)

**User:** KCPC CEO
**Required Permission:** Permission #7 (Edit Review) — native for CEO
**Screen / Route:** Deliverable Detail → Edit panel → **Edit Review Decision**
**Precondition:** status = Edit Review
**Action:** select **Approve**, check **Ananya Verma** as qualifying recipient, click **Submit Decision**
**Expected UI Result:** status changes to **"Ready for Publishing"**; Ananya Verma now shows a Mark attribution
**Expected Workflow Status:** `ERV` → `EAP` → `RFP`
**Expected DB/Audit Effect:** `personal_mark_attributions` row for Ananya Verma, full Editor Mark value from Step 03
**Expected Next Actor:** CEO/MM or delegated Publishing-Execution Employee
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 19 — START PUBLISHING

**User:** KCPC CEO
**Required Permission:** Permission #8 (Publishing Execution) — native for CEO
**Screen / Route:** Deliverable Detail → **Publishing** panel → **Start Publishing**
**Precondition:** status = Ready for Publishing
**Action:** click **Start Publishing**
**Expected UI Result:** status changes to **"Publishing"**
**Expected Workflow Status:** `RFP` → `PUBG`
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 20 — ACTUAL PUBLICATION

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail → Publishing panel → **Record Actual Publication Event**
**Precondition:** status = Publishing
**Action:** choose the Photography output, target = Instagram · kcpcbandhani, Event Type = Original, Actual Publication Timestamp = now, Evidence URL = any real-looking post link, click **Record**
**Test Data:** evidence URL e.g. `https://instagram.com/p/uat-diwali-bandhani-1`
**Expected UI Result:** event appears under **Actual Publication Events**; once every mapped output/target pair is live-or-N/A, status advances
**Expected Workflow Status:** stays `PUBG` until every planned output/target combination has a live post or a Target N/A record — with only one output mapped in this walkthrough, one Actual Publication resolves the scope → `PP`
**Expected DB/Audit Effect:** `actual_publication_events` row; `performance_obligations` row auto-created with `performance_due_date` = today + 2 days
**Expected Next Actor:** whoever records performance metrics once the due date arrives
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-20-publication-recorded.png`
**Remarks:** if you mapped all 3 outputs to the same target in Steps 07–08, you must record one Actual Publication event per output before scope resolves — adjust accordingly and note it here.

---

### STEP 21 — PERFORMANCE PENDING (verification step)

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail
**Precondition:** Step 20 complete, scope resolved
**Action:** refresh the page
**Expected UI Result:** status badge shows **"Performance Pending"**
**Expected Workflow Status:** `PP`
**Expected Next Actor:** nobody yet — the system waits for the Performance Due Date
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 22 — PERFORMANCE UPDATE / SCORECARD DRAFT

**User:** KCPC CEO
**Required Permission:** Permission #9 (Performance Update) — native for CEO
**Screen / Route:** Deliverable Detail → **Performance** panel
**Precondition:** on-or-after the Performance Due Date shown on the obligation card. **For same-day UAT testing, this step will show a validation error ("cannot be entered before the Performance Due Date") — this is correct, governed behaviour, not a defect.** To exercise this step for real, either wait 2 days or use a test scenario where the Actual Publication timestamp was recorded in the past (see `05_EDGE_CASE_AND_NEGATIVE_TESTS.md`).
**Action:** once past the due date: enter Views (3s) = 800, Plays = 1000, Average Watch Time = 12.5s, Video Length = 20s, Link Clicks = N/A (check the box), Impressions = 5000, click **Save Draft**
**Expected UI Result:** Hook Rate calculates to 80.00%; CTR shows **N/A** (not 0%, not an error) because clicks were marked N/A
**Expected Workflow Status:** `PP` → `PFUP` (fires automatically on the first eligible draft/entry on-or-after the due date)
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 23 — SCORECARD FINAL SUBMIT

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail → Performance panel → **Submit Scorecard**
**Precondition:** a draft exists; due date reached
**Action:** click **Submit Scorecard**
**Expected UI Result:** scorecard shows as submitted and becomes read-only (no further edits without a formal correction — see doc 05)
**Expected Workflow Status:** obligation marked completed; once every obligation on this deliverable is completed, the deliverable advances
**PASS/FAIL:** [ ]
**Remarks:** _______________

---

### STEP 24 — COMPLETED

**User:** KCPC CEO
**Screen / Route:** Deliverable Detail (refresh) or `/app/pipeline`
**Precondition:** Step 23 complete for every performance obligation on this deliverable
**Action:** refresh the page
**Expected UI Result:** status badge shows **"Completed"**
**Expected Workflow Status:** `PFUP` → `COMP`
**Expected DB/Audit Effect:** `workflow_instances.first_completed_at` timestamp set; `DELIVERABLE_COMPLETED` audit entry
**Expected Next Actor:** none — the lifecycle is finished. (CEO/MM retain a governed Reopen path for corrections — see doc 03, UAT-ADMIN tests.)
**PASS/FAIL:** [ ]
**Screenshot:** `UAT-GOLDEN-24-completed.png`
**Remarks:** _______________

---

## Golden Flow Result

**Total Steps:** 24 (+ 10 smoke checks)
**Steps Passed:** _____ / 24
**Steps Failed:** _____ / 24
**Overall Result:** [ ] PASS — full lifecycle completed  [ ] FAIL — see Defect Log

If every step passes, the core product promise is proven end to end. Proceed to `03_COMPLETE_FUNCTIONAL_UAT_TEST_CASES.md` for the remaining feature-by-feature coverage.
