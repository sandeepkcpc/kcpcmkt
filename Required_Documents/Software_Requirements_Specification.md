<!-- =========================================================================== -->
<!-- KCPC BANDHANI — CONTENT PRODUCTION LIFECYCLE MVP                          -->
<!-- SOFTWARE REQUIREMENTS SPECIFICATION (SRS)                                  -->
<!-- =========================================================================== -->

<div align="center">

# Software Requirements Specification (SRS)
## KCPC Bandhani — Content Production Lifecycle MVP

---

### Document Control

| Attribute              | Detail                                                                                                                                                                                              |
| :--------------------- | :-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document ID**        | `KCPC-MKT-SRS-001`                                                                                                                                                                                  |
| **Document Title**     | Software Requirements Specification (SRS)                                                                                                                                                           |
| **Project Name**       | Content Production Lifecycle MVP                                                                                                                                                                    |
| **Client**             | KCPC Bandhani                                                                                                                                                                                       |
| **Version**            | `0.3`                                                                                                                                                                                               |
| **Status**             | `Draft — Initial Software Requirements Baseline`                                                                                                                                                    |
| **Classification**     | `Confidential — Internal Use Only`                                                                                                                                                                  |
| **Author(s)**          | Enterprise Solution Architecture & Software Engineering Team                                                                                                                                        |
| **Reviewed By**        | Pending Technical Review                                                                                                                                                                            |
| **Approved By**        | Pending Stakeholder Sign-Off                                                                                                                                                                        |
| **Created Date**       | August 10, 2026                                                                                                                                                                                     |
| **Last Modified Date** | August 12, 2026                                                                                                                                                                                     |
| **Source Baselines**   | `KCPC-MKT-BFD-001` v1.5.0 (*Draft — Pending CEO Review; R3.4 candidate*)<br/>`KCPC-MKT-BRS-001` v1.1.0 (*Draft — Pending Stakeholder Review; R3.4 candidate*)<br/>`KCPC-MKT-RTM-001` v0.3 (*Draft — Initial Traceability Baseline; R3.4 candidate*) |

### Revision History

| Version | Date            | Author                      | Change Description                                                                                                                                                                                     | Reviewed By |
| :------ | :-------------- | :-------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :---------- |
| `0.1`   | August 10, 2026 | Enterprise Engineering Team | Initial Software Requirements Specification derived from BRS v1.0.3, translating the 83 BRS requirements and 200 Acceptance Criteria into a controlled baseline of 90 traceable software requirements. | Pending     |
| `0.2`   | August 11, 2026 | Enterprise Engineering Team | Controlled business change pass: (1) Derived from authoritative BFD v1.4.4, BRS v1.0.4, and RTM v0.2 baselines; (2) Synchronized Category governance to optional manual free-text attribute (SRS-REQ-021); (3) Added SRS-REQ-091 for In-Progress Work Hold & Resume Governance (derived from BRS-REQ-084 / AC-084.1 through AC-084.4); (4) Preserved SRS-REQ-084 as Dedicated Idea Form Fields & Planning Field Exclusion Guard (derived from BRS-REQ-014 / AC-014.2, AC-014.3); and (5) Maintained exactly 91 unique software requirements across 84 BRS requirements and 204 Acceptance Criteria. Current-baseline references were subsequently re-synchronized to BFD v1.4.5 following the recorded SC-REQ-001/002 stakeholder scorecard decisions, which resolved the two previously-open scorecard clarifications — zero-denominator rates are recorded as N/A (excluded from averages/KPIs) and partial metrics use a DRAFT-then-submit lifecycle — thereby changing the affected scorecard capture/derivation semantics; the core role, permission, and workflow business model is unchanged. | Pending     |
| `0.3` | August 12, 2026 | Enterprise Engineering Team | Controlled Business Change Package **R3.4** (candidate): added **SRS-REQ-092** (Business Role Catalogue & Administration) and **SRS-REQ-093** (Planning Mode & Urgent Scheduling); revised SRS-REQ-004/024/027 (Business Role assignment; `Short Clip`→`Video` rename with reel-conditional zero-coercion validation; Standard-default vs Urgent). **CANDIDATE — PENDING INDEPENDENT REVIEW; not frozen; R3.3 remains the current frozen baseline.** _Closure pass (Aug 12, 2026): applied independent-review corrections (findings 1–7) and the approved same-day-Urgent decision (new AC-086.6 / ERD-CON-066); independent review returned FAIL→corrected; pending final independent re-audit._ _Final re-audit surgical closure pass (Aug 13, 2026): closed findings 1–5 and 7 (current source-baseline metadata, current normative counts, as-built register, Business Role vs internal access class terminology, self-version footers, mechanical-audit methodology). Historical revision rows and freeze records untouched. Finding 6 (same-day Urgent approval provenance) remains OPEN — PENDING OWNER / STAKEHOLDER CONFIRMATION; the rule and its recorded provenance are unchanged. Status: R3.4 CANDIDATE — PENDING FINAL INDEPENDENT RE-AUDIT._ _Targeted traceability/governance closure pass (Aug 13, 2026) after the second independent re-audit returned FAIL: closed residual findings 1–8 and the two terminology residues (CN-009 role constraint, RTM-086/ERD §25/API-OP-018 trace edges, as-built +9→+10 ACs, 'forced −5/−2' wording, UIUX v0.2.1 R3.2→R3.3 companion governance, SAD §9.1 heading, BFD glossary). Sweep D expected-edge validation added to the audit method. Finding 6 (same-day Urgent approval provenance) remains OPEN — PENDING OWNER / STAKEHOLDER CONFIRMATION; rule and provenance untouched. Status: R3.4 CANDIDATE — PENDING RE-SUBMISSION FOR FINAL INDEPENDENT RE-AUDIT._ _Finding 6 governance closure (Aug 13, 2026): the third independent re-audit returned TECHNICAL & TRACEABILITY PASS with a governance hold; that hold is closed by CEO / Owner business decision record `KCPC-MKT-DR-R3.4-001` (same-day Urgent Shoot/Edit permitted). Provenance wording only — no requirement, AC, design element, constraint, operation, count or identifier changed. Status: R3.4 CANDIDATE — ALL FINDINGS CLOSED; PENDING FINAL FREEZE-READINESS CONFIRMATION._ | Pending |

### Distribution List

| Role / Stakeholder             | Organization / Practice   | Purpose                                            |
| :----------------------------- | :------------------------ | :------------------------------------------------- |
| CEO / Owner                    | KCPC Bandhani             | Executive Sign-Off & Governance Alignment          |
| Marketing Manager              | KCPC Bandhani             | Operational Workflow & Functional Validation       |
| Solution Architect & Tech Lead | Engineering Delivery Team | Technical Architecture, Data Modeling & API Design |
| UI/UX Lead Designer            | Experience Design Team    | Screen Composition & Interactive Specification     |
| QA Engineering Lead            | Quality Engineering Team  | Test Strategy, Test Cases & Verification Planning  |

---

</div>

## Table of Contents
1. [Document Control](#document-control)
2. [Purpose & System Context](#1-purpose--system-context)
3. [Architectural & Implementation Boundaries](#2-architectural--implementation-boundaries)
4. [User Classes, Roles & Access Control Model](#3-user-classes-roles--access-control-model)
5. [Governing System Invariants](#4-governing-system-invariants)
6. [End-to-End Workflow & State Machine Specification](#5-end-to-end-workflow--state-machine-specification)
7. [Detailed Functional Software Requirements](#6-detailed-functional-software-requirements)
8. [Logical Information Model & Data Requirements](#7-logical-information-model--data-requirements)
9. [Access-Control & Security Behavior](#8-access-control--security-behavior)
10. [Auditability & Historical Integrity](#9-auditability--historical-integrity)
11. [Non-Functional Software Requirements](#10-non-functional-software-requirements)
12. [Constraints & Explicit Exclusions](#11-constraints--explicit-exclusions)
13. [System Validation & Error Handling Specification](#12-system-validation--error-handling-specification)
14. [Source Clarifications & Deferred Design Decisions Register](#13-source-clarifications--deferred-design-decisions-register)
15. [Appendix A: BRS -> SRS Traceability Matrix](#appendix-a-brs--srs-traceability-matrix)
16. [Appendix B: BRS Acceptance Criteria (AC) -> SRS Coverage Matrix](#appendix-b-brs-acceptance-criteria-ac--srs-coverage-matrix)
17. [Appendix C: SRS Requirement Inventory & Verification Metrics](#appendix-c-srs-requirement-inventory--verification-metrics)
18. [Appendix D: Glossary & Controlled Terminology](#appendix-d-glossary--controlled-terminology)
19. [Change Control & Maintenance Lifecycle](#14-change-control--maintenance-lifecycle)

---

## 1. Purpose & System Context

### 1.1 Document Purpose
This **Software Requirements Specification (SRS)** defines the complete, precise, and testable functional and non-functional software behaviors required for the **KCPC Bandhani — Content Production Lifecycle MVP**. It translates the authoritative business rules, operating constraints, workflow state machine, and governance boundaries established in the **Business Foundation Document (BFD) v1.5.0** (`KCPC-MKT-BFD-001`) and the translated requirements catalogue in the **Business Requirements Specification (BRS) v1.1.0** (`KCPC-MKT-BRS-001`), as verified in the **Requirements Traceability Matrix (RTM) v0.3** (`KCPC-MKT-RTM-001`), into actionable software specifications for technical design, data modeling, API engineering, UI/UX specification, and QA verification.

### 1.2 System Boundary & Product Scope
The system specified herein is a single, centralized web-accessed software application supporting the internal marketing content production lifecycle for KCPC Bandhani across **seven operational stages**:
1. **Stage 1 (Idea Submission):** Structured capture of content concepts via a dedicated Idea Form.
2. **Stage 2 (Idea Review):** Formal evaluation gate (Approve, Reject, Retain) with predefined role Mark assignment under Permission #1.
3. **Stage 3 (Planning):** Single Content ID (`C-MMYY-NNNN`) creation, optional Category free-text entry, scheduling (-5d shoot, -2d edit defaults), Content Priority (Low/Med/High), Planned Outputs, Reel Types, Publication Scope, initial Cameraperson assignment under Permission #4, and parent Content Asset Folder Link capture under Permission #13.
4. **Stage 4 (Shooting Execution & Review):** Shooting task queues, Shoot Review gate under Permission #5, folder link validation, and qualifying Cameraperson Mark attribution.
5. **Stage 5 (Editing Execution & Review):** Post-Shoot Approval initial Editor assignment under Permission #6, editing task queues, Edit Review gate under Permission #7, and qualifying Editor Mark attribution.
6. **Stage 6 (Multi-Channel Publishing):** Multi-platform publication event recording (`Original`/`Repost`) under Permission #8, Target N/A exception handling, and transition to performance tracking.
7. **Stage 7 (Performance Tracking & Completion):** Event-specific Performance Due Date (`Actual + 2d`), Creative Performance Scorecard metric entry under Permission #9, platform N/A handling, and final completion.

### 1.3 Business Enablement vs Software Outcome Boundary
The system provides **Direct Operational Enablement** for workflow discipline, role isolation, permission enforcement, review gate recording, audit logging, and metric tracking. Business outcomes such as sales revenue, brand growth, or lead generation depend on external commercial, audience, and market factors; the system enables evaluation of lead-generation effectiveness via structured scorecard capture (Link Clicks / CTR) without guaranteeing commercial conversion rates.

---

## 2. Architectural & Implementation Boundaries

### 2.1 What the SRS Defines
This document specifies **WHAT THE SOFTWARE MUST DO**, including:
- Logical system responsibilities and normative requirements (The system shall...);
- Role-based landing experiences, permission validation logic, and access boundaries;
- Workflow state machine rules, transition prerequisites, and state guards;
- Predefined Mark assignment, qualifying contributor attribution, and Mark correction governance under Permission #1;
- Publication event recording, Target N/A rules, and Performance Due Date formulas;
- Scorecard formulas (Hook Rate, Hold Rate, CTR) and platform N/A rules;
- System audit logging triggers, append-only immutability, and linked correction records;
- KPI calculation and reporting according to the governed 30-KPI catalogue, including real-time operational KPI behavior where explicitly required;
- Employee self-service workspace boundaries and strict peer privacy rules.

### 2.2 What the SRS Defers to Downstream Design
This document explicitly **DOES NOT DEFINE physical implementation details**, which belong to downstream artifacts:
- Physical SQL database tables, column data types, foreign key constraints, or indexes (deferred to *ERD & Data Dictionary*);
- HTTP API endpoints, REST/GraphQL payload schemas, status codes, or middleware implementations (deferred to *API Specification*);
- User interface pixel layouts, visual styles, colors, CSS, responsive breakpoints, or component libraries (deferred to *UI/UX Specification*);
- Specific frontend/backend frameworks, programming languages, ORM tools, or container topologies (deferred to *Solution Architecture*);
- Test case scripts, automated test frameworks, or QA execution plans (deferred to *Test Strategy & QA Documentation*).

---

## 3. User Classes, Roles & Access Control Model

### 3.1 Internal Access Classes & the Business Role Catalogue (R3.4)
The system enforces **exactly three internal access classes** (security/authorization classifications). These are **not** the user-facing role names; every user carries **one Business Role** (organizational designation from the expandable Business Role catalogue, `SRS-REQ-092`) that **resolves to** exactly one of these three access classes. Authorization evaluates the resolved access class + active operational permissions — never the Business Role name.

The three internal access classes:
1. **`CEO_OWNER` (CEO / Owner):** Executive class with full operational visibility, complete operational management authority, exclusive user access administration authority, and exclusive operational permission administration authority.
2. **`MARKETING_MANAGER` (Marketing Manager):** Operational-management class with full department-wide operational access, review decision authority, task assignment authority, and performance monitoring. Has **ZERO** user access administration authority and **ZERO** permission administration authority.
3. **`EMPLOYEE`:** Operational-execution class with a default self-service workspace. Receives additional operational capabilities **exclusively** via active CEO-Granted Operational Permissions. Has zero administration authority and cannot delegate permissions onward.

The **Business Role catalogue** (organizational designations such as Camera Person, Video Editor, Publisher, HR Manager, …) is expandable; the initial 17 seeded roles resolve as `CEO` → `CEO_OWNER`, `Marketing Manager` → `MARKETING_MANAGER`, and all others → `EMPLOYEE`. A Business Role name never grants an Operational Permission, and no fourth access class exists (`SRS-REQ-092`). Every `EMPLOYEE`-class Business Role inherits all Employee-class governance (privacy, self-review prohibition, no onward delegation, scope behaviour) without per-designation duplication.

### 3.2 17 CEO-Granted Operational Permissions Catalogue
The system provides **exactly 17 governed operational permissions** configurable exclusively by the CEO:
- `Permission #1 (Idea Review):` Evaluate submitted ideas (Approve, Reject, Retain), capture predefined Role Marks, and govern predefined Mark corrections.
- `Permission #2 (Planning Execution):` Prepare Stage 3 Planning parameters, set Category, dates, priority, models, SKU, Planned Outputs, Reel Types, and publication scope.
- `Permission #3 (Planning Review):` Evaluate Stage 3 Planning parameters (Approve -> `Planning Approved` -> auto `Shoot Assigned`, Request Rework -> `Planning`).
- `Permission #4 (Shooting Assignment Management):` Perform initial Cameraperson assignment during Stage 3 Planning.
- `Permission #5 (Shoot Review):` Evaluate shoot deliverables (Approve -> `Shoot Approved`, Request Rework -> `Shoot In Progress`) and confirm qualifying final Cameraperson Marks.
- `Permission #6 (Editing Assignment Management):` Perform initial Editor assignment strictly during Stage 5 post-Shoot Approval.
- `Permission #7 (Edit Review):` Evaluate edit deliverables (Approve -> `Edit Approved` -> auto `Ready for Publishing`, Request Rework -> `Editing`) and confirm qualifying final Editor Marks.
- `Permission #8 (Publishing Execution):` Record Stage 6 Actual Publication events, evidence links, Repost publishing, and Publication Target N/A exception selections.
- `Permission #9 (Performance Update):` Enter and update Stage 7 Creative Performance Scorecard metrics on or after Performance Due Date.
- `Permission #10 (Reschedule):` Modify planned target dates across workflow stages with audit logging.
- `Permission #11 (Reassign):` Replace existing task assignees (Cameraperson or Editor) after initial assignment exists.
- `Permission #12 (Cancel):` Terminate active pre-completion deliverables with mandatory reason logging.
- `Permission #13 (Content Asset Folder Link Management):` Record or replace parent Content Asset Folder Links pointing to cloud folder storage.
- `Permission #14 (Team Workload Visibility):` View department-wide active task assignment distribution and workload summaries.
- `Permission #15 (Team KPI Visibility):` View department-wide aggregated operational KPIs and performance reports.
- `Permission #16 (Relevant Audit-History Visibility):` View relevant audit history records within authorized operational scope.
- `Permission #17 (Platform & Channel Catalogue Management):` Maintain controlled Platform and Company Channel / Account master seed catalogues.

---

## 4. Governing System Invariants

The software shall strictly enforce the following **ten governing system invariants**:
1. **Single Content ID Invariant:** One approved Idea transitions to exactly one Content ID (`C-MMYY-NNNN` with monthly reset). All Planned Outputs remain governed under this single shared Content ID workflow.
2. **Planned Output & Reel Type Invariant:** Planned Outputs are classified strictly as `Photography`, `Reel`, or `Video`. Applicable Reel outputs receive a single Reel Type (`Very Short`, `Short`, `Long`) with no numeric duration bands.
3. **Stage 3 Category Invariant:** Category is an optional manual free-text planning attribute captured in Stage 3 Planning (allowed to remain blank or contain multiple category values within the single Category field), applies to the Content ID, has no reference list/catalogue, and is conceptually distinct from SKU ID.
4. **Assignment Timing Invariant:** Initial Cameraperson assignment occurs in Stage 3 Planning under Permission #4. Initial Editor assignment is **prohibited** until Shoot Approval is granted (Stage 4 → Stage 5 boundary), occurring under Permission #6. Reassignments occur strictly under Permission #11.
5. **Marks Model Invariant:** Predefined role Marks are selected at Idea Approval under Permission #1 from controlled values `[0, 0.5, 1.0, 2.0, 3.0]` (where numeric 0 is a legitimate selectable predefined Mark satisfying mandatory entry). Shoot Approval under Permission #5 attributes the full predefined Cameraperson Mark to each qualifying final Cameraperson. Edit Approval under Permission #7 attributes the full predefined Editor Mark to each qualifying final Editor. NO splitting or averaging. Replaced contributors or contributors to earlier reworked versions receive no personal Mark attribution records. Request Rework creates no personal Mark attribution records and leaves predefined role Marks unchanged. Publishing creates no personal Mark attribution records. Repost creates no additional personal Mark attribution records. Predefined Mark corrections occur exclusively under Permission #1 via linked immutable audit records.
6. **Review Gate Invariant:** Formal review gates are `Idea Review`, `Planning Review`, `Shoot Review`, and `Edit Review`. An Employee exercising delegated review authority is strictly prohibited from reviewing work they personally submitted, executed, or prepared.
7. **Publishing Event Invariant:** Supports multiple Actual Publication events (`Original`/`Repost`) per Content ID under Permission #8. Target N/A requires mandatory reason and is auditable/reversible. An all-N/A publishing outcome is prohibited.
8. **Performance Due Date & Scorecard Invariant:** Performance Due Date = `Actual Publication Date + 2 calendar days` (system-derived, non-reschedulable). Scorecard metric formulas are strictly: **Hook Rate** = `(3-second views / Plays) * 100`, **Hold Rate** = `(Average watch time / Video length) * 100`, **CTR** = `(Link clicks / Impressions) * 100`.
9. **Employee Privacy Invariant:** Employees view own tasks, deadlines, feedback, own Marks, and own 5-measure performance indicators. Exposure of peer Marks, peer performance, rankings, leaderboards, compensation, or payroll is strictly prohibited.
10. **State Machine & Audit Invariant:** 22 formal workflow concepts (17 active statuses, 1 dormant status, 2 terminal statuses, 1 closed/reopenable status, 1 supplementary flag). All state transitions are 100% system-generated. Administrative actions (Hold, Resume, Reschedule, Reassign, Cancel, Reopen Retained Idea, Reopen Completed Deliverable) generate immutable audit records.

---

## 5. End-to-End Workflow & State Machine Specification

### 5.1 22 Workflow Concepts Catalogue
- **17 Active Statuses:** `Idea Submitted`, `Pending Approval`, `Planning`, `Planning Review`, `Planning Approved`, `Shoot Assigned`, `Shoot In Progress`, `Shoot Review`, `Shoot Approved`, `Edit Assigned`, `Editing`, `Edit Review`, `Edit Approved`, `Ready for Publishing`, `Publishing`, `Performance Pending`, `Performance Update`.
- **1 Dormant Status:** `Retained`.
- **2 Terminal Statuses:** `Rejected`, `Cancelled`.
- **1 Closed / Reopenable Status:** `Completed`.
- **1 Supplementary Flag:** `Delayed` (evaluated in real time when `Current Date > Current Approved Planned Date` for active stages).

### 5.2 Transition Guards & Review Gates
- **Idea Review:** `Idea Submitted` -> `Pending Approval` -> Approve (-> `Planning`), Reject (-> `Rejected`), Retain (-> `Retained`).
- **Planning Review:** `Planning` -> submit -> `Planning Review` -> Approve (-> `Planning Approved` -> auto `Shoot Assigned`), Request Rework (-> `Planning`).
- **Shoot Review:** `Shoot Assigned` -> execution starts -> `Shoot In Progress` -> submit -> `Shoot Review` -> Approve (-> `Shoot Approved`), Request Rework (-> `Shoot In Progress`).
- **Post-Shoot Transition:** `Shoot Approved` -> initial Editor assignment -> `Edit Assigned` -> execution starts -> `Editing`.
- **Edit Review:** `Editing` -> submit -> `Edit Review` -> Approve (-> `Edit Approved` -> auto `Ready for Publishing`), Request Rework (-> `Editing`).
- **Publishing Resolution:** `Ready for Publishing` -> `Publishing` -> All required targets published or marked N/A -> `Performance Pending`.
- **Performance Resolution:** `Performance Pending` -> metric entry -> `Performance Update` -> all obligations resolved -> `Completed`.

### 5.3 Administrative Actions vs Workflow Statuses
Administrative actions (Hold and Resume by CEO/MM, Reschedule under Permission #10, Reassign under Permission #11, Cancel under Permission #12, Reopen Retained under Permission #1, Reopen Completed under Permission #8 or Permission #9) modify parameters or execute transition workflows. They are administrative actions, NOT workflow statuses.

### 5.4 Reopen Completed Governance
- **Publishing-Related Reopen:** (Additional publication, Repost, evidence correction, N/A adjustment) Returns deliverable to `Publishing` under Permission #8. If new publication/performance obligations exist -> `Performance Pending` -> `Performance Update` -> `Completed`. If no new obligations exist -> returns directly to `Completed` after resolution.
- **Metric-Only Reopen:** Returns deliverable to `Performance Update` under Permission #9 for scorecard correction without production-stage or publishing re-execution -> returns directly to `Completed`.

---

## 6. Detailed Functional Software Requirements

This section specifies the 93 atomic functional software requirements (`SRS-REQ-001` through `SRS-REQ-093`) structured across 16 functional domains, fully covering all 86 BRS requirements (`BRS-REQ-001`..`086`) and 214 Acceptance Criteria (`AC-001.1`..`AC-086.6`). Complex multi-behavior business requirements have been rigorously decomposed into atomic software specifications. (Candidate successor **R3.4** added `SRS-REQ-092` (Business Role Catalogue & Administration → `BRS-REQ-085`) and `SRS-REQ-093` (Planning Mode & Urgent Scheduling → `BRS-REQ-086`); these are R3.4 candidate content; the independent technical and traceability review passed on August 13, 2026 and the package awaits final freeze-readiness confirmation.)

#### SRS-REQ-001 — Shared Application Authentication & Role-Appropriate Landing Experience
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-001`
- **Source Acceptance Criteria:** `AC-001.1`, `AC-001.2`, `AC-001.3`
- **Source Priority:** `Critical`
- **Actors / Authorization:** Authenticated User (All Access Classes: CEO / Owner, Marketing Manager, Employee)
- **Preconditions:** User possesses valid login credentials and active account status.
- **Requirement Statement:** The system shall provide a single shared application entry point that authenticates users and delivers role- and permission-appropriate landing experiences, navigation options, data views, and actionable controls based on the user's resolved internal access class (from the assigned Business Role — CEO / Owner, Marketing Manager, Employee) and active CEO-Granted Operational Permissions.
- **Validation / Exception Behavior:** Deny unauthenticated requests; redirect unauthenticated users to login; block navigation to unauthorized views.
- **State / Data / Audit Effect:** None — Role-appropriate landing view rendered upon successful authentication.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-002 — System Access Boundary Enforcement & Screen/Data Scoping
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-002`
- **Source Acceptance Criteria:** `AC-002.1`, `AC-002.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Authenticated user initiates any screen navigation, data query, or operational action.
- **Requirement Statement:** The system shall strictly restrict access to screens, records, data fields, and operational actions according to the authenticated user's **resolved internal access class** (derived from the user's assigned Business Role — `SRS-REQ-092`) and active CEO-Granted Operational Permissions, ensuring that unauthorized resources remain fully inaccessible regardless of access mechanism.
- **Validation / Exception Behavior:** Evaluate active role context and granted permissions; block unauthorized URL direct access or API requests with explicit access-denied response and record unauthorized access attempts in the audit log.
- **State / Data / Audit Effect:** None — Access boundary enforced; unauthorized requests blocked; audit log entry generated upon violation.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-003 — Exclusive CEO User Account Management
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-003`
- **Source Acceptance Criteria:** `AC-003.1`, `AC-003.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner
- **Preconditions:** CEO / Owner session is active; user management interface accessed.
- **Requirement Statement:** The system shall restrict user account creation, activation, and deactivation exclusively to authenticated users holding the CEO / Owner internal access class.
- **Validation / Exception Behavior:** Block account creation, activation, and deactivation attempts by any non-CEO role (including Marketing Manager); validate unique user identifier.
- **State / Data / Audit Effect:** User account created, activated, or deactivated; user administration audit log entry generated.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-004 — Exclusive CEO Business Role Assignment & Modification
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-004`
- **Source Acceptance Criteria:** `AC-004.1`, `AC-004.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner
- **Preconditions:** CEO / Owner session is active; user account is being created or edited.
- **Requirement Statement:** The system shall restrict **Business Role assignment** (the user's organizational designation, which resolves to exactly one internal access class — `CEO_OWNER`, `MARKETING_MANAGER`, or `EMPLOYEE`) during user account creation or modification exclusively to authenticated users holding the CEO / Owner access class. Assigning an ordinary Business Role never grants CEO/MM authority; the resolved access class governs authorization (`SRS-REQ-092`).
- **Validation / Exception Behavior:** Block Business Role assignment or modification attempts by non-CEO users; validate the assigned Business Role exists in the active catalogue (`business_roles`, `ERD-TBL-044`) and resolves to exactly one internal access class (`CEO_OWNER`, `MARKETING_MANAGER`, or `EMPLOYEE`); assigning an ordinary Business Role never confers CEO/MM authority; enforce mandatory reason entry.
- **State / Data / Audit Effect:** User's Business Role assigned or updated (with the resolved internal access class recorded); immutable audit record generated capturing previous Business Role, new Business Role, resolved access class, actor (CEO), timestamp, and mandatory reason.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-005 — Account Status Transitions & User Management Audit Logging
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-005`
- **Source Acceptance Criteria:** `AC-005.1`, `AC-005.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** User account creation, activation, deactivation, or role change transaction initiated by CEO.
- **Requirement Statement:** The system shall record an immutable audit log entry for every user account creation, activation, deactivation, and Business Role change, capturing affected user, action, previous status/Business Role, new status/Business Role (and resolved access class), actor, timestamp, mandatory reason, and outcome.
- **Validation / Exception Behavior:** Enforce mandatory non-empty reason entry before saving; block transaction if reason is missing; permanently preserve records in immutable audit log.
- **State / Data / Audit Effect:** Immutable audit record generated and linked to user account history.
- **Verification Basis:** Functional Test / Audit Verification

#### SRS-REQ-006 — Exclusive CEO Operational Permission Administration & 17-Permission Catalogue
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-006`
- **Source Acceptance Criteria:** `AC-006.1`, `AC-006.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner
- **Preconditions:** CEO / Owner session is active; permission administration console accessed.
- **Requirement Statement:** The system shall enforce that only the CEO / Owner can grant, modify, expire, or revoke permissions from the predefined 17-permission catalogue to named Employees, without altering their underlying Employee internal access class.
- **Validation / Exception Behavior:** Block permission administration attempts by non-CEO users; validate permission belongs strictly to the approved 17-item catalogue (Permissions #1 through #17).
- **State / Data / Audit Effect:** Operational permission grant created, modified, expired, or revoked; permission administration audit record generated.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-007 — Real-Time Runtime Permission Validation & Audit Logging
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-007`
- **Source Acceptance Criteria:** `AC-007.1`, `AC-007.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** User initiates an action governed by an operational permission.
- **Requirement Statement:** The system shall perform real-time authorization checks prior to allowing execution of any permitted action, verifying active status, scope, effective time, and absence of revocation, while logging every permission lifecycle event.
- **Validation / Exception Behavior:** Verify permission validity at the exact moment of execution; reject unauthorized or expired actions; require mandatory reason for permission lifecycle edits.
- **State / Data / Audit Effect:** Action executed if valid; immutable audit record created capturing actor, timestamp, scope, reason, and outcome.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-008 — Operational Permission Granular Scope Configuration
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-008`
- **Source Acceptance Criteria:** `AC-008.1`, `AC-008.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner
- **Preconditions:** CEO / Owner is granting or modifying an operational permission.
- **Requirement Statement:** The system shall support configuring granular permission scopes (Global, Stage-Restricted, or Specific Idea ID / Content ID Restricted) for every granted operational permission.
- **Validation / Exception Behavior:** Validate configured scope matches supported scope types; restrict permitted operations strictly to designated scope boundary.
- **State / Data / Audit Effect:** Permission grant scope persisted on user permission record; audit record generated.
- **Verification Basis:** Functional Test / Configuration Verification

#### SRS-REQ-009 — Permission Scope, Active Validity, and System Enforcement
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-009`
- **Source Acceptance Criteria:** `AC-009.1`, `AC-009.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** User attempts action under a scoped operational permission grant.
- **Requirement Statement:** The system shall validate permission validity in real time prior to executing any permitted action, verifying that the permission is active, current timestamp is between effective and expiry times, permission has not been revoked, and requested action falls within recorded scope.
- **Validation / Exception Behavior:** Reject actions attempted outside effective time boundaries or recorded scope instantly; evaluate active status prior to rendering actionable controls.
- **State / Data / Audit Effect:** None — Permission scope enforced; unauthorized out-of-scope actions blocked.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-010 — Employee Interface Boundary Control for Permission Grants
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-010`
- **Source Acceptance Criteria:** `AC-010.1`, `AC-010.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Authenticated Employee accesses system with active operational permission grants.
- **Requirement Statement:** The system shall ensure that granting one or more operational permissions to an Employee exposes only the specific screens, fields, and action controls corresponding to those granted permissions, without exposing the complete Marketing Manager interface or unrelated management features.
- **Validation / Exception Behavior:** Render only permitted queues and controls within assigned scope; keep unrelated management dashboards, catalogues, and administrative settings hidden and inaccessible.
- **State / Data / Audit Effect:** None — Dynamic interface scoping and action boundary enforced.
- **Verification Basis:** Functional Test / UI Verification

#### SRS-REQ-011 — Prohibition of Onward Permission Delegation
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-011`
- **Source Acceptance Criteria:** `AC-011.1`, `AC-011.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Non-CEO user (Marketing Manager or Employee) attempts permission or access administration.
- **Requirement Statement:** The system shall prevent Marketing Managers and Employees from granting, modifying, revoking, or delegating operational permissions or user access to any other user.
- **Validation / Exception Behavior:** Hide all delegation and user management controls from non-CEO users; reject any permission modification request originating from a non-CEO user with an authorization error.
- **State / Data / Audit Effect:** None — Delegation blocked; unauthorized delegation attempt logged in audit trail.
- **Verification Basis:** Functional Test / Authorization Guard Verification

#### SRS-REQ-012 — Employee Self-Approval Prohibition for Delegated Review Permissions
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-012`
- **Source Acceptance Criteria:** `AC-012.1`, `AC-012.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** An Employee exercising a CEO-granted review permission (Idea Review, Planning Review, Shoot Review, Edit Review) attempts to evaluate a deliverable.
- **Requirement Statement:** The system shall prevent an Employee exercising a CEO-granted review permission (Idea Review, Planning Review, Shoot Review, Edit Review) from making review decisions on ideas or work that they personally submitted, executed, prepared, or submitted for review.
- **Validation / Exception Behavior:** Disable and hide review decision controls for that deliverable when the item was submitted, executed, or prepared by the permitted Employee; reject attempted self-approval with a validation error and log an unauthorized review attempt.
- **State / Data / Audit Effect:** None — Self-approval blocked; unauthorized attempt recorded in audit log.
- **Verification Basis:** Functional Test / Self-Approval Guard Verification

#### SRS-REQ-013 — Permission Administration & Exercise Audit Logging
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-013`
- **Source Acceptance Criteria:** `AC-013.1`, `AC-013.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Operational permission grant, modification, revocation, or permitted action occurs.
- **Requirement Statement:** The system shall record immutable audit logs for all operational permission grants, scope modifications, revocations, expirations, successful permission exercises, and denied permission attempts, capturing actor identity, target Employee, permission type, scope, timestamps, decision reasons, and outcomes.
- **Validation / Exception Behavior:** Enforce mandatory non-empty reason entry before saving permission grants, modifications, or revocations; capture both successful uses and rejected unauthorized attempts.
- **State / Data / Audit Effect:** Immutable audit record generated and linked to permission governance history.
- **Verification Basis:** Functional Test / Audit Verification

#### SRS-REQ-014 — Multi-Role Idea Submission Access via Dedicated Form
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-014`
- **Source Acceptance Criteria:** `AC-014.1`
- **Source Priority:** `High`
- **Actors / Authorization:** Authenticated User (All Access Classes: CEO / Owner, Marketing Manager, Employee)
- **Preconditions:** User is authenticated with active account status.
- **Requirement Statement:** The system shall provide a dedicated Idea Submission Form allowing authenticated CEO / Owner, Marketing Manager, and Employee users to submit new marketing ideas.
- **Validation / Exception Behavior:** Allow all three internal access classes (users of any Business Role) to access and complete the Idea Submission Form without administrative permissions.
- **State / Data / Audit Effect:** None — Dedicated Idea Submission Form rendered for authenticated user.
- **Verification Basis:** Functional Test / Access Verification

#### SRS-REQ-084 — Dedicated Idea Form Fields & Planning Field Exclusion Guard
- **Classification:** Idea Management (`IDEA`)
- **Source BRS Requirement:** `BRS-REQ-014`
- **Source Acceptance Criteria:** `AC-014.2`, `AC-014.3`
- **Source Priority:** `High`
- **Actors / Authorization:** Authenticated User (All Access Classes: CEO / Owner, Marketing Manager, Employee)
- **Preconditions:** User accesses Idea Submission form to submit a content concept.
- **Requirement Statement:** The system shall capture user-entered fields on the Idea Submission Form: (1) **Idea Title** (Mandatory, non-empty text), (2) **Reference Link / Note** (Optional URL or reference string), and (3) **Remarks** (Optional, long-form free text with no business-defined word limit, supporting extended script notes, dialogue drafts, or creative guidance), while automatically populating system-derived attributes (Idea ID, Submitted By, Submitted Date/Time, Status *Idea Submitted → Pending Approval*), and strictly excluding planning fields (Category, Shoot Date, Edit Date, Content Priority, Cameraperson, Models, Planned Outputs, Publication Targets, SKU ID, Folder Link) from the Idea Submission Form.
- **Validation / Exception Behavior:** Validate Idea Title is non-empty; allow optional Reference Link / Note; allow optional Remarks with no word limit; strictly block display or input of Planning fields during Idea Submission.
- **State / Data / Audit Effect:** New Idea entity created in 'Idea Submitted' / 'Pending Approval' status; submission timestamp logged.
- **Verification Basis:** Functional Test / Form Validation

#### SRS-REQ-015 — Automated System-Generated Idea ID Assignment
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-015`
- **Source Acceptance Criteria:** `AC-015.1`, `AC-015.2`
- **Source Priority:** `High`
- **Actors / Authorization:** System
- **Preconditions:** Valid Idea submission submitted by authenticated user.
- **Requirement Statement:** The system shall automatically generate and assign a unique system-generated Idea ID to every submitted Idea upon creation.
- **Validation / Exception Behavior:** Generate exactly one unique Idea ID per submitted Idea automatically; associate Idea record with generated ID.
- **State / Data / Audit Effect:** Idea persisted with unique system-generated Idea ID; initial status set to 'Idea Submitted' / 'Pending Approval'.
- **Verification Basis:** Functional Test / ID Generation Verification

#### SRS-REQ-016 — Idea Review Evaluation Gate & Decision Enforcement
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-016`
- **Source Acceptance Criteria:** `AC-016.1`, `AC-016.2`, `AC-016.5`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #1 (Idea Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Idea exists in 'Pending Approval' status.
- **Requirement Statement:** The system shall queue newly submitted and reopened ideas in *Pending Approval* status for evaluation by authorized reviewers (CEO, Marketing Manager, or Employee with valid CEO-Granted Idea Review permission), requiring an explicit evaluation decision of **Approve**, **Reject**, or **Retain**, and enforcing that selecting Reject or Retain does not require, create, or assign predefined Cameraperson Mark or Editor Mark values.
- **Validation / Exception Behavior:** Block progression to Planning or terminal states without an explicit evaluation decision; block evaluation by unauthorized users; enforce that Reject and Retain decisions do not require, create, or assign predefined role Marks.
- **State / Data / Audit Effect:** Idea Review decision recorded; status transitioned (Approve -> 'Planning', Reject -> 'Rejected', Retain -> 'Retained'); review audit entry generated.
- **Verification Basis:** Functional Test / Review Workflow Verification

#### SRS-REQ-085 — Predefined Role Marks Assignment at Idea Approval
- **Classification:** Idea Management (`IDEA`)
- **Source BRS Requirement:** `BRS-REQ-016`
- **Source Acceptance Criteria:** `AC-016.3`, `AC-016.6`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #1 (Idea Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Reviewer selects Approve decision on Idea in 'Pending Approval'.
- **Requirement Statement:** The system shall require the reviewer selecting Approve to capture two separate predefined role Mark values from the controlled list `[0, 0.5, 1.0, 2.0, 3.0]`: (1) **Cameraperson Mark** and (2) **Editor Mark**, which attach to the approved Idea / Content ID without requiring assignees to exist at Idea Review, enforcing that numeric 0 is a legitimate selectable predefined Mark value that satisfies the mandatory Mark requirement and does not represent absence, N/A, rework, or repost, while strictly rejecting blank values or values outside the controlled list.
- **Validation / Exception Behavior:** Validate predefined Cameraperson Mark and predefined Editor Mark are selected from controlled values `[0, 0.5, 1.0, 2.0, 3.0]`; block approval if either Mark is missing or outside the list.
- **State / Data / Audit Effect:** Predefined role Marks attached to approved Idea and propagated to Content ID; audit record generated.
- **Verification Basis:** Functional Test / Marks Capture Verification

#### SRS-REQ-090 — Predefined Role Mark Correction Governance under Permission #1
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-016`
- **Source Acceptance Criteria:** `AC-016.4`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #1 (Idea Review), subject to granted scope/validity
- **Preconditions:** Predefined role Mark on approved Idea or Content ID requires correction.
- **Requirement Statement:** The system shall restrict predefined role Mark value corrections strictly to authorized users under Idea Review authority (Permission #1), executing corrections via immutable linked correction records preserving original value, corrected value, affected role (Cameraperson or Editor), Idea ID / Content ID, original Idea Review decision reference, correcting actor, correction timestamp, mandatory correction reason, and applicable permission reference without overwriting history.
- **Validation / Exception Behavior:** Authorize predefined Mark correction when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #1 (Idea Review); validate new Mark value belongs to `[0, 0.5, 1.0, 2.0, 3.0]`; require mandatory non-empty correction reason; block direct silent overwrite.
- **State / Data / Audit Effect:** Immutable linked correction record generated and linked to Idea/Content ID history.
- **Verification Basis:** Functional Test / Mark Correction Verification

#### SRS-REQ-091 — In-Progress Work Hold & Resume Governance
- **Classification:** Workflow & Control (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-084`
- **Source Acceptance Criteria:** `AC-084.1`, `AC-084.2`, `AC-084.3`, `AC-084.4`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager (Employee Hold/Resume rejected regardless of permissions)
- **Preconditions:** Work is currently in active *Shoot In Progress* (`SIP`) or *Editing* (`ED`) status.
- **Requirement Statement:** The system shall provide Hold and Resume administrative action capabilities allowing authorized users (CEO / Owner or Marketing Manager) to temporarily pause (`Hold`) and unpause (`Resume`) active work currently in *Shoot In Progress* (`SIP`) or *Editing* (`ED`) states without creating a new workflow status, without changing primary workflow status, assigned employee(s), or Content ID identity, and without granting Hold/Resume authority to Employees via CEO-granted operational permissions. Placing work on Hold requires a mandatory non-empty Hold reason and records actor, timestamp, Content/workflow identity, and underlying state. Work currently held is visibly distinguished as On Hold and cannot proceed with normal execution continuation or submit to Shoot Review / Edit Review until Resumed by an authorized actor. Hold does not automatically change approved execution dates; any revised date relies on existing Reschedule governance (SRS-REQ-056). Performance Due Date remains non-reschedulable.
- **Validation / Exception Behavior:** Restrict Hold and Resume actions strictly to CEO / Owner and Marketing Manager; reject Employee Hold/Resume attempts regardless of permission grants; require mandatory non-empty reason to place on Hold; block submission to Shoot Review / Edit Review while task is On Hold; maintain primary workflow status (`SIP` or `ED`) and assignees unchanged; restore normal execution and review submission upon Resume; preserve multi-cycle Hold history; require Reschedule under Permission #10 if approved dates change.
- **State / Data / Audit Effect:** Open Hold record created in an auditable Hold lifecycle record and administrative audit entry logged; task visual status updated to reflect On Hold state; submission controls blocked; Resume updates open Hold record with resume actor and timestamp and logs resume audit entry; primary workflow status remains unchanged throughout.
- **Verification Basis:** Functional Test / Hold and Resume Governance Verification

#### SRS-REQ-092 — Business Role Catalogue & Administration (R3.4)
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-085`
- **Source Acceptance Criteria:** `AC-085.1`, `AC-085.2`, `AC-085.3`, `AC-085.4`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner (exclusive Business Role catalogue administration and user Business-Role assignment); all users carry one Business Role.
- **Preconditions:** Authenticated context; internal access classes (`CEO_OWNER`, `MARKETING_MANAGER`, `EMPLOYEE`) remain the three security classifications.
- **Requirement Statement:** The system shall provide an **expandable Business Role (organizational designation) catalogue** in which each user holds exactly **one** current Business Role, and each Business Role **resolves to exactly one** internal access class. The initial catalogue seeds exactly 17 Business Roles (`CEO` → `CEO_OWNER`; `Marketing Manager` → `MARKETING_MANAGER`; all others → `EMPLOYEE`: HR Manager, Camera Person, Video Editor, Marketing Coordinator, CEO's Executive Assistant, Publisher, Model, Senior Manager, SEO Executive, SEO Intern, Marketing Intern, Sales Manager, CRM Manager, Customer Support Executive, Marketing Data Operator). New ordinary Business Roles shall be creatable without code or schema change and shall default to the `EMPLOYEE` access class. A Business Role name shall **never** grant an Operational Permission, and no fourth access class shall be created. Authorization shall continue to evaluate **internal access class + active operational permissions**, resolved as user → Business Role → access class — not the Business Role name and not per-designation logic. Every Business Role mapped to `EMPLOYEE` shall automatically inherit all existing Employee-class governance (self-service boundaries, own-Marks visibility, peer-Marks/peer-performance prohibition, delegated-review self-review prohibition, no onward delegation, operational-permission scope behaviour, management-screen restrictions, audit-view scope, personal-performance rules).
- **Validation / Exception Behavior:** Restrict Business Role catalogue create/deactivate and user Business-Role assignment/change to CEO / Owner; validate every Business Role resolves to exactly one of the three access classes; default ordinary/new Roles to `EMPLOYEE`; reject any attempt to create a fourth access class or to grant CEO/MM authority through ordinary Role creation; prevent destructive deletion of a Business Role that is historically referenced (deactivate instead); support active/inactive filtering.
- **State / Data / Audit Effect:** Business Role master (`business_roles` / `ERD-TBL-044`) with `access_class_code` (`ERD-CON-063`); `users.business_role_id` FK; immutable audit entries on Role create/change/deactivate and on user Business-Role assignment/change (`system_audit_log`).
- **Verification Basis:** Functional Test / Role-Model & Access-Class Resolution Verification

#### SRS-REQ-093 — Planning Mode & Urgent Scheduling (R3.4)
- **Classification:** Content Planning (`PLANNING`)
- **Source BRS Requirement:** `BRS-REQ-086`
- **Source Acceptance Criteria:** `AC-086.1`, `AC-086.2`, `AC-086.3`, `AC-086.4`, `AC-086.5`, `AC-086.6`
- **Source Priority:** `High`
- **Actors / Authorization:** Planner (CEO / Owner, Marketing Manager, or Employee with Planning Execution permission), within the existing Stage-3 Planning authority.
- **Preconditions:** Stage-3 Planning; a mandatory Planned Live Date; existing Planning Review gate.
- **Requirement Statement:** The system shall provide a Stage-3 **Planning Mode** with exactly two values — **`STANDARD`** and **`URGENT`** — as a planning characteristic of the single Content Plan entity and workflow (it is **not** a workflow status, a Priority level, an Operational Permission, a review gate, a Content ID type, or a separate Planning workflow/entity). When `STANDARD`, the system shall auto-calculate default Shoot Date = Planned Live Date − 5 calendar days and Edit Date = Planned Live Date − 2 calendar days (a default formula, overridable under existing manual-override governance). When `URGENT`, the system shall **not** force the −5/−2 calculation; the planner shall manually specify Shoot Date and Edit Date and shall provide a **mandatory Urgency Reason**; the manually specified dates and reason are approved through the **same** Planning Review gate, after which the approved dates become the normal governed planned dates and later changes use existing Reschedule governance (`SRS-REQ-056`). If Planned Live Date − Current Business Date **< 5 calendar days**, Standard planning is not valid and the system shall **require** Urgent Planning; if the Planned Live Date is exactly 5 calendar days away, Standard remains possible (Shoot Date may equal the current business date); a Planned Live Date in the past is invalid. An authorized planner **may** also select Urgent intentionally where the Planned Live Date is 5+ days away. Planning Mode is independent of Content Priority (`LOW`/`MEDIUM`/`HIGH`); no `Urgent` Priority is introduced. An Urgent plan shall **not** automatically be flagged Delayed; the existing Delayed calculation evaluates the approved dates. Planning dates must satisfy `Planned Shoot Date ≤ Planned Edit Date ≤ Planned Live Date` (Edit not before Shoot, Live not before Edit); under **Urgent**, `Planned Shoot Date = Planned Edit Date` (same-day Shoot and Edit) **is permitted** — approved by the CEO / Owner (`CEO_OWNER`), 13 August 2026, `KCPC-MKT-DR-R3.4-001` — while Standard's −5/−2 formula naturally yields distinct ordered dates (`ERD-CON-066`).
- **Validation / Exception Behavior:** Enforce `planning_mode ∈ {STANDARD, URGENT}`; require a non-empty Urgency Reason before Planning Review submission when `URGENT` (block otherwise); require Urgent when Live Date is < 5 calendar days from the current business date; permit Standard at exactly 5 days; reject a past Planned Live Date; enforce `Planned Shoot Date ≤ Planned Edit Date ≤ Planned Live Date`, **accepting same-day Shoot/Edit (equal dates) under Urgent** while rejecting Edit-before-Shoot or Live-before-Edit (`ERD-CON-066`); do not force −5/−2 in Urgent; do not invent an urgent −2/−1 formula; keep Priority and Planning Mode independent; keep the same single Planning Review gate.
- **State / Data / Audit Effect:** `content_plans.planning_mode` and `content_plans.urgency_reason` (`ERD-CON-064`, `ERD-CON-065`); planned-date chronology enforced by `ERD-CON-066` (same-day permitted under Urgent); Urgency Reason persisted and auditable; approved dates stored as the governed planned dates; audit entry on planning-parameter save.
- **Verification Basis:** Functional Test / Standard-vs-Urgent Planning Governance Verification

#### SRS-REQ-017 — Terminal Idea Rejection Handling
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-017`
- **Source Acceptance Criteria:** `AC-017.1`, `AC-017.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #1 (Idea Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Idea in 'Pending Approval' is evaluated for Rejection.
- **Requirement Statement:** The system shall transition an idea selected for Rejection during Idea Review to the terminal *Rejected* status, capture a mandatory rejection reason, log reviewer identity and timestamp, and permanently prevent further workflow progression.
- **Validation / Exception Behavior:** Require mandatory rejection reason before saving; block further editing or reopening once transitioned to Rejected.
- **State / Data / Audit Effect:** Idea transitioned to terminal 'Rejected' status; mandatory reason text persisted; review history logged.
- **Verification Basis:** Functional Test / Terminal State Verification

#### SRS-REQ-018 — Dormant Retained Idea Preservation
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-018`
- **Source Acceptance Criteria:** `AC-018.1`, `AC-018.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #1 (Idea Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Idea in 'Pending Approval' is evaluated for Retention.
- **Requirement Statement:** The system shall transition an idea selected for Retention during Idea Review to the dormant *Retained* status under its existing Idea ID, without assigning a Content ID, establishing a future review date, or requiring a mandatory reason (allowing an optional comment), preserving the idea for potential future reconsideration.
- **Validation / Exception Behavior:** Move idea to Retained under original Idea ID; ensure no Content ID is generated and idea does not enter Planning queues.
- **State / Data / Audit Effect:** Idea transitioned to dormant 'Retained' status; submission details preserved.
- **Verification Basis:** Functional Test / Dormant State Verification

#### SRS-REQ-019 — Administrative Reopen of Retained Ideas
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-019`
- **Source Acceptance Criteria:** `AC-019.1`, `AC-019.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #1 (Idea Review), subject to granted scope/validity
- **Preconditions:** Idea is currently in dormant 'Retained' status.
- **Requirement Statement:** The system shall allow authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Idea Review permission) to execute an administrative **Reopen** action on a dormant *Retained* idea, returning it to *Pending Approval* status for fresh evaluation while logging actor, timestamp, and permission references.
- **Validation / Exception Behavior:** Authorize the action when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #1 (Idea Review); transition status from Retained to Pending Approval; log reopen action.
- **State / Data / Audit Effect:** Idea reopened and transitioned to 'Pending Approval'; reopen audit record generated.
- **Verification Basis:** Functional Test / Reopen Verification

#### SRS-REQ-020 — Content ID Generation & Single Content Identity Rule
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-020`
- **Source Acceptance Criteria:** `AC-020.1`, `AC-020.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Idea is Approved and enters Stage 3 Planning.
- **Requirement Statement:** The system shall automatically generate exactly ONE unique, non-editable Content ID formatted as `C-MMYY-NNNN` when an approved Idea transitions to *Planning*, grouping all associated Planned Outputs under that single Content ID and workflow lifecycle.
- **Validation / Exception Behavior:** Enforce format `C-MMYY-NNNN` (C = Content, MM = month 01–12, YY = last two digits of year, NNNN = 0001–9999 resetting monthly); ensure ID is non-editable and does not encode platform/employee identifiers.
- **State / Data / Audit Effect:** Single Content ID generated; monthly sequence counter updated; single workflow identity established.
- **Verification Basis:** Functional Test / ID Generation Verification

#### SRS-REQ-021 — Non-Assignment Planning Parameter Definition
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-021`
- **Source Acceptance Criteria:** `AC-021.1`, `AC-021.2`, `AC-021.3`, `AC-021.4`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #2 (Planning Execution), subject to granted scope/validity
- **Preconditions:** Content deliverable is in Stage 3 'Planning' status.
- **Requirement Statement:** The system shall capture non-assignment Planning parameters including Category (optional, manually entered free-text attribute; allowed to remain blank or contain multiple category values within the single Category field), Content Priority using the controlled list (**Low**, **Medium**, **High**), Models / talent (one or more selected, or empty), SKU ID (or explicit **N/A** for generic content), and selected Publication Targets, prepared by authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Planning Execution permission), without requiring Category selection from a reference table or catalogue.
- **Validation / Exception Behavior:** Support free-text Category entry; allow Category field to remain blank or hold multiple category values as typed by the user; do not enforce Category reference data validation or single-select constraints; allow Content Priority only from [Low, Medium, High]; allow multi-talent selection; support alphanumeric SKU ID or explicit N/A; reject automated priority algorithms.
- **State / Data / Audit Effect:** Planning parameters persisted under Content ID; audit record updated.
- **Verification Basis:** Functional Test / Planning Parameters Validation

#### SRS-REQ-022 — Initial Shooting Assignment during Planning
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-022`
- **Source Acceptance Criteria:** `AC-022.1`, `AC-022.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #4 (Shooting Assignment Management), subject to granted scope/validity
- **Preconditions:** Content Plan is in Stage 3 'Planning' status.
- **Requirement Statement:** The system shall capture initial shooting team and cameraperson assignments (supporting one or more Camerapersons) during Planning, performed by authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Shooting Assignment Management permission), while prohibiting initial Editor assignment during Stage 3.
- **Validation / Exception Behavior:** Authorize the assignment when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #4 (Shooting Assignment Management); allow selecting one or more Camerapersons; disable and hide Editor assignment controls during Stage 3 Planning.
- **State / Data / Audit Effect:** Initial Cameraperson assignment(s) recorded on Content Plan; assignment audit entry generated.
- **Verification Basis:** Functional Test / Assignment Timing Verification

#### SRS-REQ-023 — Planned Output Taxonomy Classification & Multi-Asset Grouping
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-023`
- **Source Acceptance Criteria:** `AC-023.1`, `AC-023.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #2 (Planning Execution), subject to granted scope/validity
- **Preconditions:** Content Plan is in Stage 3 'Planning' status.
- **Requirement Statement:** The system shall allow selecting one or multiple Planned Outputs under a Content ID during Planning using the controlled Planned Output Types (**Photography**, **Reel**, **Video**), maintaining all outputs under the shared Content ID workflow without creating deliverable-level sub-statuses.
- **Validation / Exception Behavior:** Restrict Planned Output selections strictly to [Photography, Reel, Video]; ensure outputs do not create child workflow states or separate Content IDs.
- **State / Data / Audit Effect:** Planned Outputs persisted under Content ID; planning records updated.
- **Verification Basis:** Functional Test / Taxonomy Classification Verification

#### SRS-REQ-024 — Reel Type Duration Attribution per Reel-Type Planned Output
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-024`
- **Source Acceptance Criteria:** `AC-024.1`, `AC-024.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #2 (Planning Execution), subject to granted scope/validity
- **Preconditions:** Reel-type Planned Output is configured under Content Plan in Stage 3.
- **Requirement Statement:** The system shall capture Reel Type duration classification (Very Short, Short, Long) individually for each applicable Reel-type Planned Output under a Content ID, rather than as a single property of the overall Content ID. Reel Type applies **only** when Output Type = `REEL`: for `REEL` it is shown/enabled and mandatory (exactly one of Very Short/Short/Long); for `PHOTOGRAPHY` and `VIDEO` no interactive Reel Type control is shown and the persisted `reel_type` is `NULL`. If Output Type changes from `REEL` to `PHOTOGRAPHY`/`VIDEO`, any Reel Type is cleared. Under the zero-silent-coercion philosophy, the server independently rejects `VIDEO + reelType`, `PHOTOGRAPHY + reelType`, and `REEL + null reelType`; database integrity protects the same invariant (`ERD-CON-008`).
- **Validation / Exception Behavior:** Require selecting exactly one Reel Type (Very Short, Short, or Long) for each Reel Planned Output; do not require Reel Type for non-Reel outputs; allow distinct Reel Types across multiple Reels under same Content ID.
- **State / Data / Audit Effect:** Reel Type persisted per Reel Planned Output record.
- **Verification Basis:** Functional Test / Reel Type Validation

#### SRS-REQ-025 — Intended Publication Scope Mapping
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-025`
- **Source Acceptance Criteria:** `AC-025.1`, `AC-025.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #2 (Planning Execution), subject to granted scope/validity
- **Preconditions:** Content Plan is in Stage 3 'Planning' status.
- **Requirement Statement:** The system shall capture during Planning an intended publication scope mapping that associates each Planned Output intended for publication with one or more selected Publication Targets, serving as supporting data beneath the Content ID without creating child workflows.
- **Validation / Exception Behavior:** Allow mapping specific Planned Outputs to specific selected Publication Targets from the controlled catalogue; maintain scope mappings as planning attributes under Content ID without sub-status tracking.
- **State / Data / Audit Effect:** Planned Output to Publication Target mappings persisted under Content Plan.
- **Verification Basis:** Functional Test / Publication Scope Mapping Verification

#### SRS-REQ-026 — Shared Approved Planned Live Date Model
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-026`
- **Source Acceptance Criteria:** `AC-026.1`, `AC-026.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #2 (Planning Execution), subject to granted scope/validity
- **Preconditions:** Content Plan is in Stage 3 'Planning' status.
- **Requirement Statement:** The system shall enforce a single approved Planned Live Date per Content ID, which shall govern all Planned Outputs within the active publication scope.
- **Validation / Exception Behavior:** Apply Planned Live Date uniformly across all Planned Outputs under the Content ID; block creation of asset-specific planned live dates within a single publication scope.
- **State / Data / Audit Effect:** Shared Planned Live Date persisted on Content Plan.
- **Verification Basis:** Functional Test / Live Date Model Verification

#### SRS-REQ-027 — Default Execution Date Calculation & Manual Override Governance
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-027`
- **Source Acceptance Criteria:** `AC-027.1`, `AC-027.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #2 (Planning Execution), subject to granted scope/validity
- **Preconditions:** Content Plan is in Stage 3 'Planning' status.
- **Requirement Statement:** Under **Standard** Planning Mode (`SRS-REQ-093`), the system shall automatically calculate default Shoot Date (Planned Live Date minus 5 calendar days) and Edit Date (Planned Live Date minus 2 calendar days) during Planning, while permitting authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Planning Execution permission) to manually override default calculated dates. This −5/−2 calculation is a default planning formula, not an immutable workflow rule; under **Urgent** Planning Mode (`SRS-REQ-093`) the −5/−2 default calculation is suppressed and Shoot/Edit dates are manually specified.
- **Validation / Exception Behavior:** Automatically populate Shoot Date (-5d) and Edit Date (-2d) when Planned Live Date is entered; permit authorized planning users to override calculated dates prior to review submission.
- **State / Data / Audit Effect:** Planned Shoot Date and Planned Edit Date persisted on Content Plan.
- **Verification Basis:** Functional Test / Date Defaults Verification

#### SRS-REQ-028 — Content Asset Folder Link Establishment & Maintenance
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-028`
- **Source Acceptance Criteria:** `AC-028.1`, `AC-028.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #13 (Content Asset Folder Link Management), subject to granted scope/validity
- **Preconditions:** Content Plan is in 'Planning' or active production status.
- **Requirement Statement:** The system shall store one current authoritative Content Asset Folder Link pointing to the parent company-controlled cloud storage folder for each Content ID (e.g., Google Drive folder link), allowing the link to be added or replaced by authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Content Asset Folder Link Management permission) while preserving previous link values, actor, timestamp, and mandatory reason in audit history.
- **Validation / Exception Behavior:** Validate URL string; require mandatory non-empty reason for link replacement; log previous and new URLs in audit history; block direct file uploads.
- **State / Data / Audit Effect:** Current folder link updated; immutable link history audit record generated.
- **Verification Basis:** Functional Test / Folder Link Maintenance Verification

#### SRS-REQ-029 — Planning Review Gate & Rework Handling
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-029`
- **Source Acceptance Criteria:** `AC-029.1`, `AC-029.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #3 (Planning Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Content Plan is completed in 'Planning' and submitted to 'Planning Review'.
- **Requirement Statement:** The system shall require completed planning details to enter *Planning Review* for review by authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Planning Review permission), enforcing explicit decisions of **Approve** or **Request Rework** (which returns the deliverable to *Planning* for correction without changing assignees).
- **Validation / Exception Behavior:** Block progression without explicit review decision; on Request Rework, return status to Planning and enforce mandatory reviewer feedback comments.
- **State / Data / Audit Effect:** Review decision recorded; on Rework: status reverts to 'Planning' with feedback logged.
- **Verification Basis:** Functional Test / Planning Review Gate Verification

#### SRS-REQ-030 — Planning Approval & Task Activation
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-030`
- **Source Acceptance Criteria:** `AC-030.1`, `AC-030.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Content Plan is in 'Planning Review' status and reviewer selects Approve.
- **Requirement Statement:** The system shall transition a deliverable approved at Planning Review to *Planning Approved* and automatically activate the assigned shooting task in *Shoot Assigned* status.
- **Validation / Exception Behavior:** Verify Approve decision; transition status to Planning Approved; automatically activate assigned shooting task into Shoot Assigned; make task visible in assigned team's own-work view.
- **State / Data / Audit Effect:** Status transitioned to 'Planning Approved' and then 'Shoot Assigned'; task activated.
- **Verification Basis:** Functional Test / Task Activation Verification

#### SRS-REQ-031 — Shoot Execution & Shoot In Progress State Transition
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-031`
- **Source Acceptance Criteria:** `AC-031.1`, `AC-031.2`
- **Source Priority:** `High`
- **Actors / Authorization:** Assigned Cameraperson(s)
- **Preconditions:** Shooting task is in 'Shoot Assigned' status.
- **Requirement Statement:** The system shall allow the assigned shooting team to initiate shooting execution, transitioning deliverable status from *Shoot Assigned* to *Shoot In Progress*.
- **Validation / Exception Behavior:** Verify user is assigned Cameraperson; permit initiating shooting execution; reflect active shooting state on task indicators.
- **State / Data / Audit Effect:** Task status transitioned from 'Shoot Assigned' to 'Shoot In Progress'.
- **Verification Basis:** Functional Test / Shoot Execution Verification

#### SRS-REQ-032 — Folder Link Prerequisite for Shoot Review Submission
- **Classification:** Exception / Control (`EXCEPTION`)
- **Source BRS Requirement:** `BRS-REQ-032`
- **Source Acceptance Criteria:** `AC-032.1`, `AC-032.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** Assigned Cameraperson(s)
- **Preconditions:** Task is in 'Shoot In Progress' and Cameraperson attempts submission to Shoot Review.
- **Requirement Statement:** The system shall enforce that completed shoot output cannot be submitted for Shoot Review unless a valid Content Asset Folder Link has been recorded for the Content ID.
- **Validation / Exception Behavior:** Check for presence of valid folder link; display blocking validation error if link is absent; enable review submission control only when valid link exists.
- **State / Data / Audit Effect:** Submission permitted if link exists; status transitioned to 'Shoot Review'.
- **Verification Basis:** Functional Test / Prerequisite Guard Verification

#### SRS-REQ-033 — Shoot Review Gate, Approval, and Rework Handling
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-033`
- **Source Acceptance Criteria:** `AC-033.1`, `AC-033.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #5 (Shoot Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Deliverable is in 'Shoot Review' status.
- **Requirement Statement:** The system shall queue submitted shoot output in *Shoot Review* for evaluation by authorized reviewers (CEO, Marketing Manager, or Employee with CEO-Granted Shoot Review permission), requiring explicit decisions of **Approve** (transitions to *Shoot Approved*) or **Request Rework** (returns task to *Shoot In Progress* with reviewer remarks identifying specific affected outputs, creating no personal Mark attribution record, and leaving predefined role Marks unchanged).
- **Validation / Exception Behavior:** Block editing progression without explicit review approval; on Request Rework, return status to Shoot In Progress, retain assigned shooting team, require source-defined reviewer remarks, create no personal Mark attribution record, and leave predefined Cameraperson and Editor role Marks unchanged.
- **State / Data / Audit Effect:** Review decision recorded. On Request Rework: status returns to Shoot In Progress; review feedback is preserved; no personal Mark attribution record is created; predefined role Marks remain unchanged.
- **Verification Basis:** Functional Test / Shoot Review Workflow Verification

#### SRS-REQ-086 — Shoot Approval Qualifying Cameraperson Mark Attribution & Replaced Contributor Exclusion
- **Classification:** Shooting (`SHOOT`)
- **Source BRS Requirement:** `BRS-REQ-033`
- **Source Acceptance Criteria:** `AC-033.3`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #5 (Shoot Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Shoot Reviewer grants Approve decision under Permission #5.
- **Requirement Statement:** The system shall require the reviewer upon Shoot Approval to confirm all qualifying final Camerapersons who contributed to the Final Approved Shoot Work, attributing the full predefined Cameraperson Mark (`[0, 0.5, 1.0, 2.0, 3.0]` established at Idea Approval) to each confirmed qualifying final Cameraperson without splitting, averaging, or manual per-person Mark selection, recorded in immutable audit logs without self-approval, and ensuring that earlier contributors who were replaced or contributed only to earlier reworked versions receive no Marks.
- **Validation / Exception Behavior:** Confirm qualifying final Camerapersons; attribute full predefined Mark to each confirmed contributor; block splitting/averaging; exclude replaced contributors.
- **State / Data / Audit Effect:** Personal Cameraperson Mark attribution records generated for qualifying final Camerapersons; audit record logged.
- **Verification Basis:** Functional Test / Marks Attribution Verification

#### SRS-REQ-034 — Shoot Approval & Post-Shoot Eligibility for Editor Assignment
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-034`
- **Source Acceptance Criteria:** `AC-034.1`, `AC-034.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Shoot Review approval is granted under Permission #5.
- **Requirement Statement:** The system shall transition deliverables passing Shoot Review to *Shoot Approved*, rendering the Content ID eligible for initial Editor assignment.
- **Validation / Exception Behavior:** Update status to Shoot Approved upon Shoot Review approval; display Content ID in Editing Assignment Management queue.
- **State / Data / Audit Effect:** Status transitioned to 'Shoot Approved'; editing assignment unlocked.
- **Verification Basis:** Functional Test / Post-Shoot Eligibility Verification

#### SRS-REQ-035 — Initial Post-Shoot Approval Editor Assignment
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-035`
- **Source Acceptance Criteria:** `AC-035.1`, `AC-035.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #6 (Editing Assignment Management), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Shoot Approved' status.
- **Requirement Statement:** The system shall allow authorized assigners (CEO, Marketing Manager, or Employee with CEO-Granted Editing Assignment Management permission) to perform initial Editor assignment (supporting one or more Editors where collaborative editing occurs) on *Shoot Approved* deliverables prior to editing execution.
- **Validation / Exception Behavior:** Authorize the assignment when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #6 (Editing Assignment Management); block editor assignment prior to Shoot Approval; allow selecting one or more active Editors; transition status to Edit Assigned upon assignment.
- **State / Data / Audit Effect:** Initial Editor assignment(s) recorded; deliverable status transitioned to 'Edit Assigned'.
- **Verification Basis:** Functional Test / Editor Assignment Verification

#### SRS-REQ-036 — Edit Assigned Task Activation & Editing Execution
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-036`
- **Source Acceptance Criteria:** `AC-036.1`, `AC-036.2`
- **Source Priority:** `High`
- **Actors / Authorization:** Assigned Editor(s)
- **Preconditions:** Editing task is in 'Edit Assigned' status.
- **Requirement Statement:** The system shall queue assigned editing tasks in *Edit Assigned* status and allow the assigned Editor(s) to initiate post-production work, transitioning status to *Editing*.
- **Validation / Exception Behavior:** Present tasks in Edit Assigned in assigned Editor workspace; allow Editor to initiate work; transition status to Editing.
- **State / Data / Audit Effect:** Task status transitioned from 'Edit Assigned' to 'Editing'.
- **Verification Basis:** Functional Test / Editing Execution Verification

#### SRS-REQ-037 — Edit Review Gate, Approval, and Rework Handling
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-037`
- **Source Acceptance Criteria:** `AC-037.1`, `AC-037.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #7 (Edit Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Deliverable is in 'Edit Review' status.
- **Requirement Statement:** The system shall queue submitted edit output in *Edit Review* for evaluation by authorized reviewers (CEO, Marketing Manager, or Employee with CEO-Granted Edit Review permission), requiring explicit decisions of **Approve** (transitions to *Edit Approved*) or **Request Rework** (returns task to *Editing* with reviewer remarks identifying specific affected outputs without changing the assigned Editor, creating no personal Mark attribution record, and leaving predefined role Marks unchanged).
- **Validation / Exception Behavior:** Block publishing progression without explicit Edit Review approval; on Request Rework, return status to Editing, retain assigned Editor(s), require source-defined reviewer remarks, create no personal Mark attribution record, and leave predefined Cameraperson and Editor role Marks unchanged.
- **State / Data / Audit Effect:** Review decision recorded. On Request Rework: status returns to Editing; review feedback is preserved; no personal Mark attribution record is created; predefined role Marks remain unchanged.
- **Verification Basis:** Functional Test / Edit Review Workflow Verification

#### SRS-REQ-087 — Edit Approval Qualifying Editor Mark Attribution & Replaced Contributor Exclusion
- **Classification:** Editing (`EDIT`)
- **Source BRS Requirement:** `BRS-REQ-037`
- **Source Acceptance Criteria:** `AC-037.3`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #7 (Edit Review), subject to granted scope/validity and self-approval prohibition
- **Preconditions:** Edit Reviewer grants Approve decision under Permission #7.
- **Requirement Statement:** The system shall require the reviewer upon Edit Approval to confirm all qualifying final Editors who contributed to the Final Approved Edit Work, attributing the full predefined Editor Mark (`[0, 0.5, 1.0, 2.0, 3.0]` established at Idea Approval) to each confirmed qualifying final Editor without splitting, averaging, or manual per-person Mark selection, recorded in immutable audit logs without self-approval, and ensuring that earlier contributors who were replaced or contributed only to earlier reworked versions receive no Marks.
- **Validation / Exception Behavior:** Confirm qualifying final Editors; attribute full predefined Mark to each confirmed contributor; block splitting/averaging; exclude replaced contributors.
- **State / Data / Audit Effect:** Personal Editor Mark attribution records generated for qualifying final Editors; audit record logged.
- **Verification Basis:** Functional Test / Marks Attribution Verification

#### SRS-REQ-038 — Edit Approval & Transition to Ready for Publishing
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-038`
- **Source Acceptance Criteria:** `AC-038.1`, `AC-038.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Edit Approval is granted under Permission #7.
- **Requirement Statement:** The system shall transition deliverables passing Edit Review to *Edit Approved* and automatically queue them in *Ready for Publishing* status.
- **Validation / Exception Behavior:** Automatically update status to Edit Approved and then Ready for Publishing upon Edit Review approval; place deliverable into publishing execution queue.
- **State / Data / Audit Effect:** Status transitioned from 'Edit Review' -> 'Edit Approved' -> 'Ready for Publishing'.
- **Verification Basis:** Functional Test / Stage Transition Verification

#### SRS-REQ-039 — Contextual Workload Display during Shooting and Editing Assignments
- **Classification:** Privacy & Visibility (`PRIVACY`)
- **Source BRS Requirement:** `BRS-REQ-039`
- **Source Acceptance Criteria:** `AC-039.1`, `AC-039.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #4 (Shooting Assignment Management) or Permission #6 (Editing Assignment Management)
- **Preconditions:** Authorized assigner is selecting a Cameraperson (Stage 3) or Editor (Stage 5).
- **Requirement Statement:** The system shall display minimal contextual workload summaries—limited to operational active assigned task counts and delay indicators—to authorized assigners performing shooting or editing assignments, supporting human load balancing without exposing peer-private performance data, peer Marks, employee rankings, compensation, or incentives.
- **Validation / Exception Behavior:** Display candidate active task counts and delay badges; strictly withhold peer performance ratings, peer Marks, rework rates, or personal metrics.
- **State / Data / Audit Effect:** None — Contextual workload summary rendered to assist assignment decision.
- **Verification Basis:** Functional Test / Workload Display Verification

#### SRS-REQ-040 — Human Assignment Control & Automated Algorithm Prohibition
- **Classification:** Business Constraint (`CONSTRAIN`)
- **Source BRS Requirement:** `BRS-REQ-040`
- **Source Acceptance Criteria:** `AC-040.1`, `AC-040.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Assignment Permissions (#4, #6, #11)
- **Preconditions:** Task assignment or reassignment action initiated.
- **Requirement Statement:** The system shall ensure all shooting team and Editor assignments remain fully human-controlled decisions, and shall not automatically allocate tasks, calculate candidate suitability rankings, or execute automated assignment algorithms.
- **Validation / Exception Behavior:** Require manual assignee selection from authorized user lists; contain zero automated task auto-assignment or candidate ranking routines.
- **State / Data / Audit Effect:** Human-selected assignment persisted; assignment audit record generated.
- **Verification Basis:** Functional Test / Human Control Verification

#### SRS-REQ-041 — Publishing Stage Triggering & Initiation Governance
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-041`
- **Source Acceptance Criteria:** `AC-041.1`, `AC-041.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity (or System upon Planned Live Date)
- **Preconditions:** Deliverable is in 'Ready for Publishing' status.
- **Requirement Statement:** The system shall allow transitioning a deliverable from *Ready for Publishing* to *Publishing* either automatically when the Current Planned Live Date arrives or manually when initiated by an authorized user (CEO, Marketing Manager, or Employee with CEO-Granted Publishing Execution permission).
- **Validation / Exception Behavior:** Enable transition when Planned Live Date arrives or upon authorized manual initiation prior to the date by CEO / Owner, Marketing Manager, or an Employee holding an active, valid, in-scope CEO-Granted Permission #8 (Publishing Execution).
- **State / Data / Audit Effect:** Deliverable status transitioned from 'Ready for Publishing' to 'Publishing'.
- **Verification Basis:** Functional Test / Publishing Trigger Verification

#### SRS-REQ-042 — Execution of Manual Publishing, Event Type Classification, & Event Recording
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-042`
- **Source Acceptance Criteria:** `AC-042.1`, `AC-042.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Publishing' status.
- **Requirement Statement:** The system shall enable authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Publishing Execution permission) to execute publishing according to the approved publication scope and record a separate Actual Publication event and live URL publishing link for each separate live publication, capturing **Publication Event Type** as either **Original** or **Repost**.
- **Validation / Exception Behavior:** Authorize the action when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #8 (Publishing Execution); allow recording separate Actual Publication event per live post (supporting multiple events per output/target combination); require valid URL, actor, timestamp, and Event Type selection (Original | Repost).
- **State / Data / Audit Effect:** Actual Publication event persisted under Content ID; audit record generated.
- **Verification Basis:** Functional Test / Event Recording Verification

#### SRS-REQ-043 — Actual Publication Event Traceability & Attribute Capture
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-043`
- **Source Acceptance Criteria:** `AC-043.1`, `AC-043.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity
- **Preconditions:** Actual Publication event is recorded in Stage 6.
- **Requirement Statement:** The system shall capture for every Actual Publication event the Content ID, applicable Planned Output, Platform, Company Channel / Account, Publication Target, Publication Event Type (`Original` or `Repost`), Actual Publication Date and Time, publishing link URL, responsible actor identity, and system recording timestamp.
- **Validation / Exception Behavior:** Validate all mandatory metadata attributes; associate recorded events directly with parent Content ID; maintain event immutability.
- **State / Data / Audit Effect:** Actual Publication event record stored under Content ID; publication audit entry generated.
- **Verification Basis:** Functional Test / Attribute Capture Verification

#### SRS-REQ-044 — Late Actual Publication Recording & Operating Schedule Interpretation
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-044`
- **Source Acceptance Criteria:** `AC-044.1`, `AC-044.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity
- **Preconditions:** Live publication occurs after the Current Planned Live Date.
- **Requirement Statement:** The system shall record the true Actual Publication Date and Time for every publication event, preserving the Current Planned Live Date, allowing late publication events to be recorded and evaluated for delay reporting without hard-blocking or auto-cancelling the deliverable.
- **Validation / Exception Behavior:** Record truthful publication timestamp even if later than Planned Live Date; evaluate late events for delay reporting without blocking execution.
- **State / Data / Audit Effect:** Late Actual Publication event recorded; delay evaluation metrics updated.
- **Verification Basis:** Functional Test / Late Publication Verification

#### SRS-REQ-045 — Publication Target N/A Exception Recording & Reversal
- **Classification:** Exception / Control (`EXCEPTION`)
- **Source BRS Requirement:** `BRS-REQ-045`
- **Source Acceptance Criteria:** `AC-045.1`, `AC-045.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Publishing' status and a planned target will not be published, or an N/A target requires reactivation.
- **Requirement Statement:** The system shall allow authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Publishing Execution permission) to mark a selected Publication Target as N/A by entering a mandatory non-empty reason, excluding the target from performance metrics and compliance denominators, while permitting subsequent reversal via linked auditable supersession records to enable later publication.
- **Validation / Exception Behavior:** Require mandatory non-empty reason for N/A; remove performance metric obligations for N/A target; log supersession audit record upon reversal and restore tracking; block all-N/A completed publication outcome.
- **State / Data / Audit Effect:** Publication Target N/A record or reversal supersession record persisted; audit log updated.
- **Verification Basis:** Functional Test / N/A Exception Verification

#### SRS-REQ-046 — Linked Publication Evidence & Link Correction
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-046`
- **Source Acceptance Criteria:** `AC-046.1`, `AC-046.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity
- **Preconditions:** Actual Publication event exists with previously recorded publishing link.
- **Requirement Statement:** The system shall prevent overwriting or deleting recorded Actual Publication events or links, requiring corrections to be submitted by authorized users as linked correction records preserving original values, corrected values, actor, timestamp, mandatory reason, and original record reference.
- **Validation / Exception Behavior:** Prohibit direct overwrite; require mandatory non-empty reason; create linked correction audit record preserving original entry.
- **State / Data / Audit Effect:** Immutable linked correction record generated and attached to publication event history.
- **Verification Basis:** Functional Test / Link Correction Verification

#### SRS-REQ-047 — Initial Publishing Scope Completion Rule & Minimum Publication Requirement
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-047`
- **Source Acceptance Criteria:** `AC-047.1`, `AC-047.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Publishing activities conclude for all targets under active publication scope.
- **Requirement Statement:** The system shall transition a Content ID from *Publishing* to *Performance Pending* during initial publication only when at least one valid Actual Publication event exists and all publication obligations in the approved publication scope are resolved (as Actual Publication events or authorized Publication Target N/A records), while prohibiting all selected targets from remaining N/A simultaneously as a completed publication outcome.
- **Validation / Exception Behavior:** Verify >=1 Actual Publication event exists AND 100% of scope targets are resolved; return blocking validation error if all targets are N/A.
- **State / Data / Audit Effect:** Deliverable status transitioned from 'Publishing' to 'Performance Pending'.
- **Verification Basis:** Functional Test / Scope Completion Verification

#### SRS-REQ-048 — Performance Pending State Transition & Event Obligation Tracking
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-048`
- **Source Acceptance Criteria:** `AC-048.1`, `AC-048.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Deliverable transitions from 'Publishing' with outstanding performance obligations.
- **Requirement Statement:** The system shall maintain deliverables with outstanding publication performance obligations in *Performance Pending* status as a single Content ID-level workflow state, tracking event-level performance due dates underneath the Content ID.
- **Validation / Exception Behavior:** Transition Content ID to Performance Pending upon scope completion; track individual event due dates and metric obligations underneath the shared Content ID status.
- **State / Data / Audit Effect:** Status maintained as 'Performance Pending'; event obligations tracked.
- **Verification Basis:** Functional Test / Performance Tracking Verification

#### SRS-REQ-049 — System-Derived Performance Due Date Calculation
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-049`
- **Source Acceptance Criteria:** `AC-049.1`, `AC-049.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Actual Publication event is recorded with an Actual Publication Date.
- **Requirement Statement:** The system shall automatically calculate the Performance Due Date for each Actual Publication event as exactly the second calendar date after the event's Actual Publication Date ($\text{Actual Publication Date} + 2\text{ calendar days}$), enforcing that Performance Due Dates are system-derived and non-reschedulable.
- **Validation / Exception Behavior:** Calculate Performance Due Date as Actual Publication Date + 2 calendar days automatically; block manual edits or rescheduling of derived date.
- **State / Data / Audit Effect:** Performance Due Date persisted on Actual Publication event record.
- **Verification Basis:** Functional Test / Date Calculation Verification

#### SRS-REQ-050 — Performance Update Eligibility & Stage Activation
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-050`
- **Source Acceptance Criteria:** `AC-050.1`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #9 (Performance Update), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Performance Pending' and current date >= Performance Due Date.
- **Requirement Statement:** The system shall transition a deliverable from *Performance Pending* to *Performance Update* when at least one eligible Actual Publication event reaches its calculated Performance Due Date and an authorized user (CEO, Marketing Manager, or Employee with CEO-Granted Performance Update permission) begins entering or correcting metrics for that event using the authoritative Creative Performance Scorecard.
- **Validation / Exception Behavior:** Enable metric entry controls only when current date >= Performance Due Date for that event; transition status to Performance Update upon metric entry initiation.
- **State / Data / Audit Effect:** Status transitioned from 'Performance Pending' to 'Performance Update'.
- **Verification Basis:** Functional Test / Stage Activation Verification

#### SRS-REQ-088 — Creative Performance Scorecard Raw Metric Capture & Late Entry Compliance
- **Classification:** Performance Update (`PERF-UPD`)
- **Source BRS Requirement:** `BRS-REQ-050`
- **Source Acceptance Criteria:** `AC-050.2`, `AC-050.4`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #9 (Performance Update), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Performance Update' status.
- **Requirement Statement:** The system shall capture manual scorecard inputs per event: (1) `Views / Plays` (always available), (2) `3-second views` (IG/YT; missing on Moj), (3) `Average watch time` in seconds (IG/YT; missing on Moj), (4) `Video length` in seconds (video assets), (5) `Link clicks` (posts with link), and (6) `Impressions` (always available), and flag metric submissions completed after the Performance Due Date as late in compliance reporting. The system shall support an editable **DRAFT lifecycle** (`SC-REQ-002`, resolved): a partial/incomplete scorecard may be saved and revised repeatedly (every metric field optional while draft) before an explicit **Submit** that validates the applicable metrics, seals the record immutable, completes the performance obligation, and progresses the workflow; genuinely unavailable metrics are marked `N/A` at submit.
- **Validation / Exception Behavior:** Capture raw scorecard metrics per event; permit partial DRAFT saves (all fields optional while `submitted_at IS NULL`); validate applicable metrics only at Submit; support platform N/A designation; record compliance flag if entered after Due Date.
- **State / Data / Audit Effect:** Raw scorecard metrics and entry timestamp persisted per publication event; audit log generated.
- **Verification Basis:** Functional Test / Metric Capture Verification

#### SRS-REQ-089 — Creative Performance Scorecard Metric Derivation Formulas & Platform N/A Suppression
- **Classification:** Performance Update (`PERF-UPD`)
- **Source BRS Requirement:** `BRS-REQ-050`
- **Source Acceptance Criteria:** `AC-050.3`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Raw scorecard metrics entered for publication event.
- **Requirement Statement:** The system shall calculate controlled derived rates with frozen denominators: `Views` = Raw count, `Hook Rate` = $(\text{3-second views} \div \text{Plays}) \times 100$, `Hold Rate` = $(\text{Average watch time} \div \text{Video length}) \times 100$, and `CTR` = $(\text{Link clicks} \div \text{Impressions}) \times 100$, suppressing formula calculation and recording `N/A` (never numeric 0) where a platform does not report a metric, and likewise recording the derived rate as `N/A` where a denominator is 0 (`Plays`, `Video length`, or `Impressions` = 0) — never dividing by zero and never substituting 0 (`SC-REQ-001`, resolved).
- **Validation / Exception Behavior:** Calculate exact rate formulas; suppress calculation and record `N/A` when source metrics are platform-N/A OR when a denominator is 0; excluded from averages/KPI aggregations.
- **State / Data / Audit Effect:** Derived scorecard metrics persisted on publication event record.
- **Verification Basis:** Functional Test / Formula Verification

#### SRS-REQ-051 — Linked Performance Metric Correction Governance
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-051`
- **Source Acceptance Criteria:** `AC-051.1`, `AC-051.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #9 (Performance Update), subject to granted scope/validity
- **Preconditions:** Scorecard metrics exist for publication event in 'Performance Update' or reopened state.
- **Requirement Statement:** The system shall prevent overwriting or deleting submitted performance metrics, requiring metric corrections to be performed by authorized users via linked correction records capturing original values, corrected values, actor, timestamp, mandatory reason, and original record reference.
- **Validation / Exception Behavior:** Prohibit direct metric overwrite; require mandatory non-empty reason; create linked correction audit record.
- **State / Data / Audit Effect:** Immutable linked correction record generated storing prior metrics, corrected metrics, actor, timestamp, and reason.
- **Verification Basis:** Functional Test / Metric Correction Verification

#### SRS-REQ-052 — Workflow Completion Rule & Closed / Reopenable Classification
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-052`
- **Source Acceptance Criteria:** `AC-052.1`, `AC-052.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Deliverable is in 'Performance Update' and all required metric obligations are satisfied.
- **Requirement Statement:** The system shall transition a Content ID from *Performance Update* to *Completed* as soon as mandatory performance metrics have been entered for all Actual Publication events requiring performance tracking and all non-published targets have valid N/A records, classifying *Completed* as Closed / Reopenable.
- **Validation / Exception Behavior:** Verify 100% of event metric obligations and N/A targets are resolved; transition status to Completed; close active queues while preserving reopening capability.
- **State / Data / Audit Effect:** Deliverable status transitioned from 'Performance Update' to 'Completed'.
- **Verification Basis:** Functional Test / Completion Verification

#### SRS-REQ-053 — Reopen Completed Deliverable for Additional Publication, Repost, Evidence Correction, or N/A Adjustment
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-053`
- **Source Acceptance Criteria:** `AC-053.1`, `AC-053.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #8 (Publishing Execution), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Completed' status and publishing modification or repost is required.
- **Requirement Statement:** The system shall allow authorized users with applicable Publishing Execution authority (CEO, Marketing Manager, or Employee with CEO-Granted Publishing Execution permission) to reopen a Completed Content ID to Publishing for additional publication, Reposts, publishing-evidence correction, or applicable Publication Target N/A reversal/correction, while retaining the original Content ID, creating no additional personal Mark attribution records on Reposts, and preventing production-stage re-execution.
- **Validation / Exception Behavior:** Authorize reopening when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #8 (Publishing Execution); require mandatory non-empty reopen reason; retain original Content ID; block re-execution of production stages; create no additional personal Mark attribution records on Reposts; establish fresh Actual Publication event and Performance Due Date (+2d) for new live posts.
- **State / Data / Audit Effect:** Deliverable status transitioned from 'Completed' to 'Publishing'; reopen audit record generated.
- **Verification Basis:** Functional Test / Reopen Publishing Verification

#### SRS-REQ-054 — Reopen Completed Deliverable Exclusively for Metric Correction
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-054`
- **Source Acceptance Criteria:** `AC-054.1`, `AC-054.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #9 (Performance Update), subject to granted scope/validity
- **Preconditions:** Deliverable is in 'Completed' status and metric correction is required.
- **Requirement Statement:** The system shall allow authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Performance Update permission) to execute an administrative Reopen Completed action on a Completed deliverable exclusively to perform metric corrections, returning the deliverable to Performance Update without re-executing production or publishing stages.
- **Validation / Exception Behavior:** Authorize reopening when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #9 (Performance Update); require mandatory non-empty reopen reason; transition status directly to Performance Update; keep production and publishing stages locked.
- **State / Data / Audit Effect:** Deliverable status transitioned from 'Completed' to 'Performance Update'; reopen audit record generated.
- **Verification Basis:** Functional Test / Reopen Metric Verification

#### SRS-REQ-055 — Exit Condition for Reopened Publishing Activities
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-055`
- **Source Acceptance Criteria:** `AC-055.1`, `AC-055.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Reopened deliverable completes its resolution activities.
- **Requirement Statement:** The system shall enforce that a reopened *Publishing* deliverable producing new Actual Publication events progresses through *Performance Pending* $\rightarrow$ *Performance Update* $\rightarrow$ *Completed*, whereas a reopened activity producing no new publication or performance obligation returns directly to *Completed* upon resolution.
- **Validation / Exception Behavior:** If new live posts created: route Publishing -> Performance Pending -> Performance Update -> Completed; if evidence/NA fix only: return directly to Completed upon resolution.
- **State / Data / Audit Effect:** Deliverable transitioned through appropriate exit path; status updated.
- **Verification Basis:** Functional Test / Reopen Exit Verification

#### SRS-REQ-056 — Cross-Stage Reschedule Governance
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-056`
- **Source Acceptance Criteria:** `AC-056.1`, `AC-056.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #10 (Reschedule), subject to granted scope/validity
- **Preconditions:** Active deliverable in pre-completion stage requires date adjustment.
- **Requirement Statement:** The system shall allow authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Reschedule permission) to execute an administrative **Reschedule** action on active approved execution dates (Shoot Date, Edit Date, Current Planned Live Date, or any other approved business-defined execution date formally supported by the workflow) while an activity is active, retaining or returning the task to its active execution state, recording previous date, new date, actor, timestamp, reason, and permission reference, while prohibiting the rescheduling of Performance Due Dates.
- **Validation / Exception Behavior:** Authorize rescheduling when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #10 (Reschedule); update approved execution dates; log complete date history; prohibit rescheduling or manual editing of Performance Due Dates.
- **State / Data / Audit Effect:** Planned dates updated; reschedule audit entry generated.
- **Verification Basis:** Functional Test / Reschedule Verification

#### SRS-REQ-057 — Reassignment Governance & Task State Reset
- **Classification:** Business Functional (`BUS-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-057`
- **Source Acceptance Criteria:** `AC-057.1`, `AC-057.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #11 (Reassign), subject to granted scope/validity
- **Preconditions:** Active task in shooting or editing has an existing assignee requiring replacement.
- **Requirement Statement:** The system shall allow authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Reassign permission) to replace an existing assigned employee, team member, cameraperson(s), or editor(s) after an initial assignment has been made, returning the task to the applicable assigned state (*Shoot Assigned*, *Edit Assigned*), recording previous assignee(s), new assignee(s), actor, timestamp, mandatory reason, and permission reference.
- **Validation / Exception Behavior:** Authorize reassignment when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #11 (Reassign); require mandatory non-empty reason; update assigned resources; reset status to Shoot Assigned or Edit Assigned; exclude replaced contributors from final Mark award.
- **State / Data / Audit Effect:** Task assignee updated; reassignment audit record generated.
- **Verification Basis:** Functional Test / Reassignment Verification

#### SRS-REQ-058 — Pre-First-Completion Cancellation Governance & Terminal State Transition
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-058`
- **Source Acceptance Criteria:** `AC-058.1`, `AC-058.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #12 (Cancel), subject to granted scope/validity
- **Preconditions:** Deliverable is in an active or dormant workflow stage prior to first completion.
- **Requirement Statement:** The system shall allow authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Cancel permission) to cancel an eligible active or dormant workflow record prior to its first completion, capturing mandatory cancellation reason, actor, timestamp, and permission reference, and transitioning the record to the terminal *Cancelled* status.
- **Validation / Exception Behavior:** Authorize cancellation when the actor is CEO / Owner or Marketing Manager, or when the actor is an Employee holding an active, valid, in-scope CEO-Granted Permission #12 (Cancel); require mandatory non-empty cancellation reason; transition record to terminal Cancelled status; terminate active task obligations.
- **State / Data / Audit Effect:** Deliverable transitioned to terminal 'Cancelled' status; cancellation audit record generated.
- **Verification Basis:** Functional Test / Cancellation Verification

#### SRS-REQ-059 — Permanent Post-Completion Cancellation Prohibition
- **Classification:** Exception / Control (`EXCEPTION`)
- **Source BRS Requirement:** `BRS-REQ-059`
- **Source Acceptance Criteria:** `AC-059.1`, `AC-059.2`
- **Source Priority:** `High`
- **Actors / Authorization:** System
- **Preconditions:** User attempts Cancel action on deliverable.
- **Requirement Statement:** The system shall permanently prevent executing a Cancel action on any Content ID that has reached *Completed* status at least once.
- **Validation / Exception Behavior:** Permanently disable Cancel controls for any Content ID with historical Completed status; validate completion history and reject cancel attempts with domain error.
- **State / Data / Audit Effect:** None — Cancellation attempt blocked; error returned.
- **Verification Basis:** Functional Test / Prohibition Verification

#### SRS-REQ-060 — Master Publishing Catalogue Maintenance
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-060`
- **Source Acceptance Criteria:** `AC-060.1`, `AC-060.2`
- **Source Priority:** `Medium`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #17 (Platform & Channel Catalogue Management), subject to granted scope/validity
- **Preconditions:** Authorized user accesses Platform & Channel Catalogue management.
- **Requirement Statement:** The system shall maintain a master publishing catalogue structured hierarchically as **Platform $\rightarrow$ Company Channel / Account $\rightarrow$ Publication Target**, seeded with initial business channels (`kcpcsikar`, `kcpcbandhani`, `kcpc_sikar`, `kcpcbandhani.01`, `kcpc.english`, `piyushxbusiness`, `kcpclegacy`, `kcpc_legacy`) across platforms (Instagram, Threads, YouTube, Facebook, Moj, TikTok), allowing authorized users (CEO, Marketing Manager, or Employee with CEO-Granted Platform & Channel Catalogue Management permission) to create, modify, activate, or deactivate platforms and company-controlled channels. Platform-to-Channel associations shall be configured through controlled master data and are not fixed by this requirement.
- **Validation / Exception Behavior:** Allow authorized catalogue managers to add/update Platforms and Channels; hide deactivated channels from new Planning selections while preserving historical data.
- **State / Data / Audit Effect:** Master seed catalogue updated; catalogue audit entry logged.
- **Verification Basis:** Functional Test / Catalogue Verification

#### SRS-REQ-061 — Master Catalogue Audit Logging
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-061`
- **Source Acceptance Criteria:** `AC-061.1`, `AC-061.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Any Platform or Company Channel / Account creation, modification, status change, or deactivation occurs.
- **Requirement Statement:** The system shall automatically record an immutable audit log entry for every Platform and Company Channel / Account creation, modification, status change, or deactivation, capturing object type, platform, channel/account name, previous status, new status, actor, timestamp, mandatory reason, and permission reference.
- **Validation / Exception Behavior:** Enforce mandatory non-empty reason entry before saving catalogue changes; preserve catalogue audit records as immutable historical logs without overwrite.
- **State / Data / Audit Effect:** Immutable catalogue audit record generated and linked to master data history.
- **Verification Basis:** Functional Test / Catalogue Audit Verification

#### SRS-REQ-062 — Controlled Content Taxonomy Governance
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-062`
- **Source Acceptance Criteria:** `AC-062.1`, `AC-062.2`
- **Source Priority:** `Medium`
- **Actors / Authorization:** System
- **Preconditions:** Content planning or deliverable classification is executed.
- **Requirement Statement:** The system shall enforce a controlled business taxonomy for Planned Output Types (**Photography**, **Reel**, **Video**) and Reel Type classifications (**Very Short**, **Short**, **Long**), restricting planned output and reel type selections strictly to authorized taxonomy values (with combined labels such as *Reel — Short* permitted for derived display and reporting).
- **Validation / Exception Behavior:** Restrict content selections strictly to controlled taxonomy list; reject unrecognized or free-text classifications.
- **State / Data / Audit Effect:** None — Taxonomy validation enforced.
- **Verification Basis:** Functional Test / Taxonomy Governance Verification

#### SRS-REQ-063 — System-Wide Immutable Audit Logging
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-063`
- **Source Acceptance Criteria:** `AC-063.1`, `AC-063.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Any workflow transition, review decision, administrative action, access change, or data mutation occurs.
- **Requirement Statement:** The system shall automatically generate immutable audit log records for all idea evaluation decisions, predefined Marks capture at Idea Approval, planning approvals, shoot reviews, edit reviews, qualifying contributor Marks attributions, predefined Mark corrections, rework requests, initial assignments, reassignments, holds, resumes, reschedules, cancellations, retained idea reopenings, completed deliverable reopenings, workflow status transitions, publication events, link corrections, N/A exceptions, N/A reversals, metric entries, metric corrections, user access administration actions, permission grants/modifications/revocations, and permission exercises/attempts, capturing all BFD-defined mandatory audit attributes.
- **Validation / Exception Behavior:** Capture actor and timestamp together with event-specific previous/new values, reasons, assignees, dates, permission references, scope, outcomes, and Mark records; cover 100% of defined system transitions.
- **State / Data / Audit Effect:** Append-only audit record created and linked to target entity history.
- **Verification Basis:** Functional Test / Audit Logging Verification

#### SRS-REQ-064 — Audit Trail Immutability & Deletion Prohibition
- **Classification:** Audit & Traceability (`AUDIT`)
- **Source BRS Requirement:** `BRS-REQ-064`
- **Source Acceptance Criteria:** `AC-064.1`, `AC-064.2`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** User or administrative role attempts to edit, modify, overwrite, truncate, or delete audit records.
- **Requirement Statement:** The system shall enforce strict immutability on all audit log records, preventing modification, overwriting, or deletion of audit logs by any user, including management and administrative roles.
- **Validation / Exception Behavior:** Ensure user interfaces contain zero audit record edit or delete capabilities; permanently prohibit modification, overwrite, or deletion by any user.
- **State / Data / Audit Effect:** None — Direct audit mutation blocked; historical integrity enforced.
- **Verification Basis:** Functional Test / Immutability Verification

#### SRS-REQ-065 — Relevant Audit-History Visibility Permission
- **Classification:** Access & Authorization (`ACCESS`)
- **Source BRS Requirement:** `BRS-REQ-065`
- **Source Acceptance Criteria:** `AC-065.1`, `AC-065.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #16 (Relevant Audit-History Visibility), subject to granted scope/validity
- **Preconditions:** User queries audit history records.
- **Requirement Statement:** The system shall restrict viewing system audit logs to authenticated CEO / Owner and Marketing Manager users, and Employees holding a valid CEO-Granted Relevant Audit-History Visibility permission within their granted scope.
- **Validation / Exception Behavior:** Make audit log viewing accessible only to CEO, Marketing Manager, and permitted Employees; restrict permitted Employees strictly to their granted operational boundary.
- **State / Data / Audit Effect:** None — Scoped audit history rendered to authorized user.
- **Verification Basis:** Functional Test / Audit Visibility Verification

#### SRS-REQ-066 — Employee Self-Service Own-Work Operational Visibility
- **Classification:** Privacy & Visibility (`PRIVACY`)
- **Source BRS Requirement:** `BRS-REQ-066`
- **Source Acceptance Criteria:** `AC-066.1`, `AC-066.2`
- **Source Priority:** `High`
- **Actors / Authorization:** Role Context: Employee (own assigned work and recorded actual participation)
- **Preconditions:** Authenticated Employee accesses personal workspace.
- **Requirement Statement:** The system shall provide Employees with a self-service workspace displaying record-level operational information exclusively for work assigned to them or where their actual participation is recorded—including assigned tasks, current workflow status, planned execution dates, delay indicators, required next actions, review feedback, rework comments, folder links, personal workload, own Marks (for qualifying Camerapersons/Editors), and submitted ideas.
- **Validation / Exception Behavior:** Display task lists, folder links, progress indicators, review feedback, rework requests, and own personal Mark attributions exclusively for assigned work; block rendering of peer tasks.
- **State / Data / Audit Effect:** None — Employee's own operational data displayed.
- **Verification Basis:** Functional Test / Self-Service Visibility Verification

#### SRS-REQ-067 — Peer Privacy Protection & Compensation/Ranking/Marks Boundary
- **Classification:** Privacy & Visibility (`PRIVACY`)
- **Source BRS Requirement:** `BRS-REQ-067`
- **Source Acceptance Criteria:** `AC-067.1`, `AC-067.2`
- **Source Priority:** `High`
- **Actors / Authorization:** System
- **Preconditions:** Employee role session active.
- **Requirement Statement:** The system shall strictly prohibit Employees from viewing another Employee's private performance information, personal performance indicators, peer Marks, identifiable peer comparisons, employee rankings, leaderboards, compensation details, payroll data, or incentive calculations.
- **Validation / Exception Behavior:** Ensure employee dashboards contain zero peer performance lookup, peer Marks viewing, peer ranking, or peer comparison features; restrict peer performance data and peer Marks strictly to authorized management views (CEO and Marketing Manager).
- **State / Data / Audit Effect:** None — Peer privacy boundaries enforced.
- **Verification Basis:** Functional Test / Peer Privacy Verification

#### SRS-REQ-068 — Employee Personal Performance Attribution & Approved Indicators
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-068`
- **Source Acceptance Criteria:** `AC-068.1`, `AC-068.2`
- **Source Priority:** `High`
- **Actors / Authorization:** Role Context: Employee (own personal performance attribution)
- **Preconditions:** Authenticated Employee views personal performance tab.
- **Requirement Statement:** The system shall derive Employee personal performance views exclusively from their own assigned work and recorded actual participation, limited strictly to five approved measures: (1) Delayed Work, (2) Approved Work / Task Outputs, (3) Review Submissions, (4) Request Rework Before Approval, and (5) Personal Marks (qualifying Camerapersons and Editors only), crediting all recorded participants in shared work without multiplying company-level content counts or formal KPI totals.
- **Validation / Exception Behavior:** Display only the five approved measures derived from actual user participation; credit shared work participants individually without multiplying Content ID totals.
- **State / Data / Audit Effect:** None — Personal performance metrics rendered.
- **Verification Basis:** Functional Test / Performance Attribution Verification

#### SRS-REQ-069 — Team Workload Visibility Permission & Aggregate Boundaries
- **Classification:** Privacy & Visibility (`PRIVACY`)
- **Source BRS Requirement:** `BRS-REQ-069`
- **Source Acceptance Criteria:** `AC-069.1`, `AC-069.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #14 (Team Workload Visibility), subject to granted scope/validity
- **Preconditions:** Authorized user queries team workload dashboard.
- **Requirement Statement:** The system shall allow Employees holding a valid CEO-Granted Team Workload Visibility permission to view authorized aggregate or operational workload information (task distribution, active task counts, unassigned work, stage capacity) within their granted scope, while strictly withholding peer-private performance metrics, peer Marks, rankings, and compensation data.
- **Validation / Exception Behavior:** Verify user holds Permission #14 or management role; display team task distribution and active queue counts; omit individual employee performance scores, peer Marks, and comparisons.
- **State / Data / Audit Effect:** None — Aggregate workload distribution rendered.
- **Verification Basis:** Functional Test / Workload Visibility Verification

#### SRS-REQ-070 — Team KPI Visibility Permission & Aggregate Boundaries
- **Classification:** Privacy & Visibility (`PRIVACY`)
- **Source BRS Requirement:** `BRS-REQ-070`
- **Source Acceptance Criteria:** `AC-070.1`, `AC-070.2`
- **Source Priority:** `High`
- **Actors / Authorization:** CEO / Owner; Marketing Manager; Employee with active CEO-Granted Permission #15 (Team KPI Visibility), subject to granted scope/validity
- **Preconditions:** Authorized user queries department performance reports.
- **Requirement Statement:** The system shall allow Employees holding a valid CEO-Granted Team KPI Visibility permission to view authorized aggregate department-level KPI reports (workflow totals, completion rates, delay totals, channel distribution), while strictly withholding identifiable peer performance, individual peer Marks, individual rankings, and financial data.
- **Validation / Exception Behavior:** Verify user holds Permission #15 or management role; display high-level department KPI dashboards; omit individual performance breakdowns and peer Marks.
- **State / Data / Audit Effect:** None — Department KPI dashboard rendered.
- **Verification Basis:** Functional Test / KPI Visibility Verification

#### SRS-REQ-071 — Operational KPIs Capture & Reporting (KPI-001 through KPI-007)
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-071`
- **Source Acceptance Criteria:** `AC-071.1`, `AC-071.2`, `AC-071.3`, `AC-071.4`, `AC-071.5`, `AC-071.6`, `AC-071.7`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Operational pipeline data recorded; operational dashboard requested.
- **Requirement Statement:** The system shall calculate and report real-time operational metrics covering KPI-001 through KPI-007: (1) **KPI-001 Pending Work** (total active content deliverables in progress across all workflow stages in real time), (2) **KPI-002 Delayed Work** (total active tasks currently exceeding their Current Approved Planned Date in real time), (3) **KPI-003 Upcoming Shoots** (lists and counts all shoots scheduled within the next 7 calendar days), (4) **KPI-004 Upcoming Publishing** (lists and counts all deliverables scheduled for publication within the next 7 calendar days), (5) **KPI-005 Pending Approvals** (total pending review decisions across Idea Review, Planning Review, Shoot Review, and Edit Review), (6) **KPI-006 Editor Workload** (active editing tasks distributed per editor), and (7) **KPI-007 Performance Pending Work** (total Actual Publication events with outstanding performance obligations whose Performance Due Date has not arrived, whose performance entry has not begun, or whose mandatory metrics remain incomplete).
- **Validation / Exception Behavior:** Compute operational metrics dynamically from active system state.
- **State / Data / Audit Effect:** None — Operational KPIs presented.
- **Verification Basis:** Functional Test / Calculation Verification

#### SRS-REQ-072 — Productivity KPIs Capture & Reporting (KPI-008 through KPI-011)
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-072`
- **Source Acceptance Criteria:** `AC-072.1`, `AC-072.2`, `AC-072.3`, `AC-072.4`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Productivity reporting period closed or report generated over date range.
- **Requirement Statement:** The system shall track and report periodic productivity metrics covering KPI-008 through KPI-011: (1) **KPI-008 Employee Productivity** (aggregates total production tasks completed per employee across stages over monthly periods), (2) **KPI-009 Manager Productivity** (aggregates total review decisions processed by the Marketing Manager over monthly periods), (3) **KPI-010 Tasks Completed** (counts each Content ID exactly once upon initial transition to Completed status, excluding reopened deliverables), and (4) **KPI-011 Tasks Cancelled** (aggregates total content deliverables cancelled prior to completion over monthly periods).
- **Validation / Exception Behavior:** Aggregate productivity metrics over specified date range; count Completed deliverables exactly once upon initial completion.
- **State / Data / Audit Effect:** None — Productivity KPIs computed and rendered.
- **Verification Basis:** Functional Test / Calculation Verification

#### SRS-REQ-073 — Content & Published Unit KPIs Capture & Reporting (KPI-012 through KPI-020)
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-073`
- **Source Acceptance Criteria:** `AC-073.1`, `AC-073.2`, `AC-073.3`, `AC-073.4`, `AC-073.5`, `AC-073.6`, `AC-073.7`, `AC-073.8`, `AC-073.9`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Content and publication records recorded.
- **Requirement Statement:** The system shall compute content volume metrics covering KPI-012 through KPI-020: (1) **KPI-012 Published Content** (counts distinct Content IDs having $\ge 1$ live Actual Publication event, counting multiple posts or Reposts exactly once), (2) **KPI-013 Reels Produced** (total Reel-type Planned Outputs produced and approved across deliverables, excluding reposts), (3) **KPI-014 Photography Produced** (total Photography Planned Outputs produced and approved across deliverables, excluding reposts), (4) **KPI-015 Publication Distribution** (distribution of live Actual Publication events by Platform and/or Company Channel / Account), (5) **KPI-016 Content by Type** (distribution of produced Planned Outputs across controlled content taxonomy classifications), (6) **KPI-017 Ideas Submitted** (total ideas submitted through Idea Submission Form), (7) **KPI-018 Ideas Approved** (total ideas approved at Idea Review and progressed to Planning), (8) **KPI-019 Ideas Rejected** (total ideas rejected at Idea Review), and (9) **KPI-020 Idea Approval Rate** (computes approval rate as $\left(\frac{\text{Ideas Approved}}{\text{Ideas Approved} + \text{Ideas Rejected}}\right) \times 100$, explicitly excluding dormant Retained ideas from the denominator).
- **Validation / Exception Behavior:** Compute volume metrics; exclude Reposts from production totals; exclude Retained ideas from approval rate denominator.
- **State / Data / Audit Effect:** None — Content KPIs computed and presented.
- **Verification Basis:** Functional Test / Calculation Verification

#### SRS-REQ-074 — Approval & Review KPIs Capture & Reporting (KPI-021 through KPI-024)
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-074`
- **Source Acceptance Criteria:** `AC-074.1`, `AC-074.2`, `AC-074.3`, `AC-074.4`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Review decisions recorded across lifecycle gates.
- **Requirement Statement:** The system shall calculate quality and review efficiency metrics covering KPI-021 through KPI-024: (1) **KPI-021 Approval Turnaround Time** (measures average duration from review submission to decision logging across all review gates), (2) **KPI-022 Approvals by Manager** (computes total review decisions processed by Marketing Manager over monthly periods), (3) **KPI-023 Approvals by CEO** (computes total review decisions processed by CEO over monthly periods), and (4) **KPI-024 Rework Rate** (computes percentage of Planning, Shoot, and Edit reviews resulting in Request Rework, explicitly excluding Retain and Reject decisions).
- **Validation / Exception Behavior:** Calculate review efficiency metrics; exclude Retain and Reject from rework rate calculation.
- **State / Data / Audit Effect:** None — Review efficiency KPIs computed.
- **Verification Basis:** Functional Test / Calculation Verification

#### SRS-REQ-075 — Delay, SLA, & On-Time Performance KPIs Capture & Reporting (KPI-025 through KPI-030 & SC-002)
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-075`
- **Source Acceptance Criteria:** `AC-075.1`, `AC-075.2`, `AC-075.3`, `AC-075.4`, `AC-075.5`, `AC-075.6`, `AC-075.7`
- **Source Priority:** `High`
- **Actors / Authorization:** System
- **Preconditions:** Milestone schedules and completion timestamps recorded.
- **Requirement Statement:** The system shall measure SLA and timeliness performance covering KPI-025 through KPI-030 and support the phased 90-day baseline comparison for deadline reduction: (1) **KPI-025 Average Delay** (measures the mean number of calendar days active tasks exceed their Current Approved Planned Date (or Current Planned Live Date)), (2) **KPI-026 Content Completion Rate** (percentage of planned deliverables currently in Completed status), (3) **KPI-027 Content Turnaround Time** (average duration from Planning initiation to initial Completed status), (4) **KPI-028 Shoot-to-Publish Cycle Time** (average duration from Shoot Approved to latest applicable Actual Publication event under Content ID, excluding N/A targets), (5) **KPI-029 Delay Distribution** (breakdown of delays categorized by workflow stage against Current Approved Planned Dates), (6) **KPI-030 On-Time Delivery Rate** (percentage of applicable Actual Publication events whose Actual Publication Date is on or before Current Planned Live Date, excluding N/A targets), and (7) **SC-002 Phased Baseline Comparison** (measures delay reduction across Days 1–30 Baseline, Days 31–60 Stabilization, and Days 61–90 Comparison, evaluating whether Days 61–90 achieves $\ge 50\%$ reduction in average delay / missed deadlines compared to baseline).
- **Validation / Exception Behavior:** Calculate SLA metrics; exclude N/A targets from denominator; compute phased baseline comparison.
- **State / Data / Audit Effect:** None — SLA and delay KPIs rendered.
- **Verification Basis:** Functional Test / Calculation Verification

#### SRS-REQ-076 — Administrative Action & Permission Usage Reporting
- **Classification:** Reporting & KPI (`REPORTING`)
- **Source BRS Requirement:** `BRS-REQ-076`
- **Source Acceptance Criteria:** `AC-076.1`, `AC-076.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** Role Context: CEO / Owner, Marketing Manager
- **Preconditions:** Management reporting console accessed.
- **Requirement Statement:** The system shall generate management reports derived from audit logs summarizing administrative actions (holds, resumes, reschedule counts/reasons, reassignment counts/reasons, cancellations, ideas retained/reopened, deliverables reopened, publication N/A/reversals, master catalogue changes, user administration events, permission grants/revocations, permission exercise attempt outcomes, and predefined Mark capture, personal Mark attribution, and predefined Mark correction logs).
- **Validation / Exception Behavior:** Surface total counts and categorical reason breakdowns for all administrative actions and Mark-related events; highlight successful permission exercises and unauthorized attempt frequencies.
- **State / Data / Audit Effect:** None — Administrative activity reports rendered.
- **Verification Basis:** Functional Test / Report Verification

#### SRS-REQ-077 — Web Browser Availability & Operating Environment
- **Classification:** Non-Functional Business (`NON-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-077`
- **Source Acceptance Criteria:** `AC-077.1`, `AC-077.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Client browser connects to centralized application.
- **Requirement Statement:** The MVP shall be accessible through supported modern web browsers using active internet connectivity, rendering full application functionality without requiring native desktop software installation.
- **Validation / Exception Behavior:** Support access via supported modern web browsers over active internet connectivity.
- **State / Data / Audit Effect:** None — Browser accessibility delivered.
- **Verification Basis:** Inspection / Environment Verification

#### SRS-REQ-078 — System Availability & 24×7 Uptime Standard
- **Classification:** Non-Functional Business (`NON-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-078`
- **Source Acceptance Criteria:** `AC-078.1`, `AC-078.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Continuous system operation.
- **Requirement Statement:** The system shall maintain continuous operational availability of at least 99% on a 24×7 basis, excluding approved planned maintenance windows.
- **Validation / Exception Behavior:** Measure uptime continuously across 24x7 operation; exclude approved maintenance windows from availability penalty calculations.
- **State / Data / Audit Effect:** None — High availability operational standard maintained.
- **Verification Basis:** Monitoring / Availability Verification

#### SRS-REQ-079 — User Concurrency Sizing & Capacity Boundary
- **Classification:** Non-Functional Business (`NON-FUNC`)
- **Source BRS Requirement:** `BRS-REQ-079`
- **Source Acceptance Criteria:** `AC-079.1`, `AC-079.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** Concurrent user sessions active during MVP operation.
- **Requirement Statement:** The MVP shall be designed under the business assumption that concurrent users remain fewer than 15 during the MVP period, supporting peak marketing department operational access within this capacity boundary.
- **Validation / Exception Behavior:** Maintain operational responsiveness under concurrent workloads of fewer than 15 active users.
- **State / Data / Audit Effect:** None — Concurrency sizing boundary maintained.
- **Verification Basis:** Load Test / Concurrency Verification

#### SRS-REQ-080 — Temporary MVP Lifespan & Business OS Transition Readiness
- **Classification:** Business Constraint (`CONSTRAIN`)
- **Source BRS Requirement:** `BRS-REQ-080`
- **Source Acceptance Criteria:** `AC-080.1`, `AC-080.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** System operational lifecycle (6-8 months temporary MVP lifespan).
- **Requirement Statement:** The MVP shall operate as a streamlined temporary business solution intended for approximately 6–8 months and shall support structured business data export for transition to the future KCPC Business OS.
- **Validation / Exception Behavior:** Adhere strictly to approved MVP scope boundaries; support structured data export for downstream migration activities.
- **State / Data / Audit Effect:** None — Transition readiness constraint maintained.
- **Verification Basis:** Inspection / Architecture Verification

#### SRS-REQ-081 — System Data Export Capability
- **Classification:** Business Data / Information (`DATA-INFO`)
- **Source BRS Requirement:** `BRS-REQ-081`
- **Source Acceptance Criteria:** `AC-081.1`, `AC-081.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** Role Context: CEO / Owner, Marketing Manager
- **Preconditions:** Authorized management user requests data export.
- **Requirement Statement:** The system shall provide structured data export capabilities allowing authorized management users (CEO, Marketing Manager) to export core operational records, workflow histories, performance metrics, and audit logs, capturing full relational attributes, Marks, and historical timestamps.
- **Validation / Exception Behavior:** Verify management authorization; extract ideas, deliverables, metrics, Marks, and audit logs into structured export format.
- **State / Data / Audit Effect:** None — Structured data export package generated.
- **Verification Basis:** Functional Test / Export Verification

#### SRS-REQ-082 — External API Integration & Automation Exclusion
- **Classification:** Business Constraint (`CONSTRAIN`)
- **Source BRS Requirement:** `BRS-REQ-082`
- **Source Acceptance Criteria:** `AC-082.1`, `AC-082.2`
- **Source Priority:** `Derived — No Explicit Priority`
- **Actors / Authorization:** System
- **Preconditions:** System operation and publishing workflows.
- **Requirement Statement:** The system shall explicitly exclude external API integrations (social media posting APIs, analytics APIs, Google Drive API, CRM/ERP interfaces) and automated social publishing, relying exclusively on manual execution proof and manual metric entry during the MVP period.
- **Validation / Exception Behavior:** Ensure publishing execution and metric entry rely on manual user input screens; exclude automated third-party API posting and analytics scraping.
- **State / Data / Audit Effect:** None — Scope boundaries strictly protected.
- **Verification Basis:** Inspection / Architecture Verification

#### SRS-REQ-083 — System-Generated Workflow Status & Manual Status Edit Prohibition
- **Classification:** Workflow (`WORKFLOW`)
- **Source BRS Requirement:** `BRS-REQ-083`
- **Source Acceptance Criteria:** `AC-083.1`, `AC-083.2`, `AC-083.3`
- **Source Priority:** `Critical`
- **Actors / Authorization:** System
- **Preconditions:** Workflow state transition evaluation context.
- **Requirement Statement:** The system shall derive workflow status exclusively through authorized workflow transitions, review decisions, administrative actions, and defined system events. Users shall not manually edit, override, skip, or directly set workflow statuses.
- **Validation / Exception Behavior:** Prohibit direct editing or overriding of workflow statuses; ensure status changes occur only through defined authorized actions, decisions, or system events; prevent skipping workflow statuses through manual manipulation.
- **State / Data / Audit Effect:** None — 22-concept workflow state machine integrity enforced.
- **Verification Basis:** Functional Test / State Machine Guard Verification

---

## 7. Logical Information Model & Data Requirements

### 7.1 Core Logical Entities & Attributes
The system defines 45 core logical entities and concepts (pure logical model, not physical SQL schema):
1. **User:** Identity representing a human user (CEO / Owner, Marketing Manager, Employee).
2. **Internal Access Class:** Primary authorization classification (CEO / Owner, Marketing Manager, Employee), resolved from the user’s Business Role.
3. **User Account Status:** Operational state of user identity (Active, Inactive).
4. **Operational Permission:** CEO-granted permissions (Permissions #1 through #17).
5. **Permission Grant:** Active assignment of an operational permission to a user.
6. **Permission Scope:** Operational boundaries and constraints attached to a permission grant (Global, Stage-Restricted, Specific-Item).
7. **Idea:** Stage 1 submitted content concept.
8. **Idea Submission:** Form record capturing Title, optional Reference Link / Note, and optional Remarks.
9. **Idea Review Decision:** Decision record (Approve, Reject, Retain) with timestamp and feedback under Permission #1.
10. **Predefined Cameraperson Mark:** Selected mark value `[0, 0.5, 1.0, 2.0, 3.0]` at Idea Approval.
11. **Predefined Editor Mark:** Selected mark value `[0, 0.5, 1.0, 2.0, 3.0]` at Idea Approval.
12. **Content ID:** Single unique identifier (`C-MMYY-NNNN` format, monthly reset) assigned at Stage 3.
13. **Category:** Optional manually entered free-text marketing classification captured in Stage 3 Planning (blank permitted, multi-value free-text permitted, no master catalogue table, no delimiter semantics, distinct from SKU reference).
14. **Content Priority:** Priority classification (`Low`, `Medium`, `High`) set in Stage 3.
15. **Model / Talent Reference:** Optional reference data captured in Stage 3.
16. **SKU ID / Explicit N/A:** SKU identifier or explicit N/A designation captured in Stage 3.
17. **Planned Output:** Deliverable type (`Photography`, `Reel`, `Video`).
18. **Reel Type:** Specific length classification (`Very Short`, `Short`, `Long`) for Reel outputs.
19. **Publication Scope Mapping:** Intended mapping of specific Planned Outputs to one or more Publication Targets.
20. **Publication Target:** Specific platform channel / account endpoint.
21. **Platform:** Reference platform (Instagram, Threads, YouTube, Facebook, Moj, TikTok).
22. **Company Channel / Account:** Owned account entity under a platform (`kcpcsikar`, `kcpcbandhani`, `kcpc_sikar`, `kcpcbandhani.01`, `kcpc.english`, `piyushxbusiness`, `kcpclegacy`, `kcpc_legacy`).
23. **Cameraperson Assignment:** Initial Stage 3 assignment under Permission #4.
24. **Editor Assignment:** Initial Stage 5 assignment post-Shoot Approval under Permission #6.
25. **Content Asset Folder Link:** Parent cloud folder URL captured under Permission #13.
26. **Planning Review Decision:** Decision record (Approve -> Planning Approved, Request Rework) under Permission #3.
27. **Shoot Review Decision:** Decision record (Approve -> Shoot Approved, Request Rework) under Permission #5.
28. **Edit Review Decision:** Decision record (Approve -> Edit Approved, Request Rework) under Permission #7.
29. **Qualifying Final Contributor:** Confirmed Cameraperson(s) or Editor(s) receiving full predefined Mark.
30. **Personal Mark Attribution:** Awarded mark record assigned to qualifying final contributor.
31. **Predefined Mark Correction:** Immutable linked correction record under Permission #1.
32. **Actual Publication Event:** Recorded publication event (`Original` or `Repost`) under Permission #8.
33. **Publication Event Type:** Event classification (`Original` vs `Repost`).
34. **Publication Evidence Correction:** Audit record for updated publication URL evidence.
35. **Publication Target N/A Record:** Authorized N/A exception record with mandatory reason under Permission #8.
36. **Publication Target N/A Reversal / Supersession:** Reversal record restoring metric obligation.
37. **Performance Due Date:** System-derived date = `Actual Publication Date + 2 calendar days`.
38. **Creative Performance Scorecard:** Scorecard record (Hook Rate, Hold Rate, CTR) entered under Permission #9.
39. **Performance Metric Entry:** Individual metric values (3s views, plays, watch time, video length, link clicks, impressions).
40. **Performance Metric Correction:** Immutable correction entry for scorecard metrics.
41. **Reschedule Record:** Audit log entry for planned date changes under Permission #10.
42. **Reassignment Record:** Audit log entry for replacing assignees under Permission #11.
43. **Cancellation Record:** Audit log entry for terminating deliverables under Permission #12.
44. **Reopen Record:** Audit log entry for reopening Retained or Completed content.
45. **System Audit Record:** Immutable system log entry capturing user, timestamp, state, action, and event-specific attributes.

### 7.2 Predefined Mark vs Personal Mark Attribution vs Predefined Mark Correction
The system explicitly distinguishes these three Mark-related logical concepts:
1. **Predefined Role Mark:** Selected during Stage 2 Idea Review under Permission #1 (Idea Review) from controlled values `[0, 0.5, 1.0, 2.0, 3.0]` for Cameraperson and Editor roles. It represents the potential value awarded to qualifying contributors upon deliverable approval. Numeric 0 is a valid selectable predefined Mark satisfying mandatory entry.
2. **Personal Mark Attribution:** Created upon Shoot Approval under Permission #5 (Shoot Review) for qualifying final Camerapersons and Edit Approval under Permission #7 (Edit Review) for qualifying final Editors. Each qualifying final contributor receives the full predefined Mark. No splitting or averaging. Request Rework creates no personal Mark attribution record.
3. **Predefined Mark Correction:** Created exclusively under Permission #1 (Idea Review) when correcting a predefined Mark setting. Generates an immutable linked record storing prior value, new value, actor, timestamp, and mandatory reason.

---

## 8. Access-Control & Security Behavior

Access control is governed strictly by user role context and active CEO-granted permissions (Permissions #1 through #17). Unauthenticated users are blocked. Employees have default self-service visibility restricted to their own tasks, deadlines, feedback, own Marks, and approved own 5-measure performance indicators. An Employee exercising delegated review authority is strictly prohibited from reviewing work they personally submitted, executed, or prepared. Department-wide operational data, peer Marks, peer performance, rankings, leaderboards, compensation, and payroll are hidden from non-authorized roles.

---

## 9. Auditability & Historical Integrity

All status transitions, reschedules under Permission #10, reassignments under Permission #11, cancellations under Permission #12, reopens, folder link updates under Permission #13, predefined Mark corrections under Permission #1, master catalogue changes under Permission #17, and Target N/A selections/reversals under Permission #8 generate append-only, immutable audit log records. Audit records shall capture actor and timestamp together with the event-specific mandatory attributes defined by the applicable requirement/business rule, including previous/new values, mandatory reasons, assignees, dates, permission references, Marks, or original-record references where applicable.

---

## 10. Non-Functional Software Requirements

The system shall maintain ≥99% continuous 24×7 operational availability (excluding approved maintenance windows), support access via supported modern web browsers over active internet connectivity, and support the concurrent operating workload of fewer than 15 active users during the MVP period.

---

## 11. Constraints & Explicit Exclusions

The system strictly enforces the following **11 authoritative out-of-scope boundaries** (noting `OOS-001` through `OOS-011` are RTM/SRS-local convenience identifiers):
- `OOS-001 (Enterprise Business OS Modules):` No CRM, Inventory, HRMS, Finance, or BI modules.
- `OOS-002 (External System Integrations):` No external social media APIs, CRM, ERP, or Google Drive API integrations.
- `OOS-003 (Automatic Social Publishing):` No automatic API posting, automated scheduling, or direct platform publishing engine.
- `OOS-004 (Digital Asset Management & Storage):` No file upload, media storage hosting, automatic folder creation, or Drive sync.
- `OOS-005 (Customer-Facing Features):` No customer-facing portals, public websites, or e-commerce features.
- `OOS-006 (Payroll & Compensation Calculations):` No automated payroll, financial compensation, or bonus calculation engine.
- `OOS-007 (Multi-Department Workflows):` No workflow management outside the internal KCPC Bandhani marketing team.
- `OOS-008 (Historical Data Migration):` No automated migration of legacy spreadsheet data.
- `OOS-009 (Native Mobile Applications):` No native iOS or Android mobile application development.
- `OOS-010 (Advanced Analytics & AI/ML):` No AI/ML algorithms, employee ranking engines, or predictive scoring.
- `OOS-011 (Automated Workload Allocation):` No automated task assignment algorithms.

---

## 12. System Validation & Error Handling Specification

Inputs are validated against controlled reference data and domain models. The system shall block invalid operations and return explicit domain-specific error messages for blocking conditions including:
- Unauthorized action or missing permission grant;
- Self-approval attempts by an Employee exercising delegated review authority on own work;
- Missing parent Content Asset Folder Link prior to Shoot Review submission;
- All-N/A publication scope outcome;
- Invalid Mark values outside `[0, 0.5, 1.0, 2.0, 3.0]`;
- Manual status edit attempts;
- Post-first-completion deliverable cancellation attempts;
- Missing mandatory rejection, cancellation, hold, reassignment, reschedule, or catalogue modification reasons.

*Note on Resolved Handling (Aug 11, 2026):* Partial / incomplete Performance Metric Entry handling is RESOLVED under `SC-REQ-002` via an editable draft-then-submit lifecycle (see `SRS-REQ-088`); a partial scorecard is a valid saved DRAFT and only a final Submit enforces applicable-metric completeness.

---

## 13. Source Clarifications & Deferred Design Decisions Register

### 13.1 Source Clarifications Required

| ID           | Source Reference                 | Issue                                                   | Why Business Clarification Is Needed                                                      | Impacted SRS Requirement(s) | Status                    |
| :----------- | :------------------------------- | :------------------------------------------------------ | :---------------------------------------------------------------------------------------- | :-------------------------- | :------------------------ |
| `SC-REQ-001` | BRS-REQ-050 / AC-050.3           | Scorecard Metric Division-by-Zero Handling              | RESOLVED: derived rate recorded as `N/A` when denominator (Plays / Video length / Impressions) = 0; excluded from averages/KPIs; never 0, never a division error. | SRS-REQ-089                 | **RESOLVED (Aug 11, 2026)** |
| `SC-REQ-002` | BRS-REQ-050 / AC-050.2, AC-050.4 | Partial / Incomplete Performance Metric Entry Treatment | RESOLVED: editable DRAFT lifecycle — partial scorecard saved/revised (`submitted_at IS NULL`), then validated & sealed on Submit. | SRS-REQ-088                 | **RESOLVED (Aug 11, 2026)** |

### 13.2 Downstream Design Decisions

| ID        | Design Topic                           | Source Behavior Already Clear                         | Deferred Decision                                                  | Future Artifact Owner          | Status   |
| :-------- | :------------------------------------- | :---------------------------------------------------- | :----------------------------------------------------------------- | :----------------------------- | :------- |
| `DDD-001` | User Authentication Mechanism          | Role context & access blocking defined.               | Token / session implementation (JWT, Session Cookie, OAuth).       | Solution Architecture          | Deferred |
| `DDD-002` | Physical Database Engine & Schema      | Logical information model defined.                    | Database technology (PostgreSQL, MySQL) & SQL tables/indexes.      | ERD & Data Dictionary          | Deferred |
| `DDD-003` | Timezone & Timestamp Precision         | Performance Due Date +2d & timestamp logging defined. | Timezone storage representation & datetime precision.              | Technical Architecture         | Deferred |
| `DDD-004` | Folder Link & Evidence URL Validation  | Valid parent folder URL & evidence URL required.      | Regular expression URL pattern validation algorithm.               | API & UI Specification         | Deferred |
| `DDD-005` | REST / GraphQL API Endpoint Topography | Endpoint functional behaviors defined.                | HTTP route paths, request payloads & status codes.                 | API Specification              | Deferred |
| `DDD-006` | UI Component Layout & Styling          | Screen composition & forms defined.                   | Pixel layouts, colors, CSS design tokens & responsive breakpoints. | UI/UX Specification            | Deferred |
| `DDD-007` | Audit Log Storage Engine               | Append-only immutability defined.                     | Physical log storage engine & long-term archiving topography.      | System Architecture            | Deferred |
| `DDD-008` | Operational Data Export File Format    | Structured export capability required by BRS-REQ-081. | Export file format selection (CSV, XLSX, JSON).                    | Architecture / Data / API Spec | Deferred |

---

## Appendix A: BRS -> SRS Traceability Matrix

| BRS Requirement ID | BRS Title                                                                                               | BRS Acceptance Criteria                                                                                    | Mapped SRS Requirement ID(s)                | Coverage Status | Traceability Notes                                       |
| :----------------- | :------------------------------------------------------------------------------------------------------ | :--------------------------------------------------------------------------------------------------------- | :------------------------------------------ | :-------------- | :------------------------------------------------------- |
| `BRS-REQ-001`      | Shared Application Authentication & Role-Appropriate Landing Experience                                 | `AC-001.1`, `AC-001.2`, `AC-001.3`                                                                         | `SRS-REQ-001`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-002`      | System Access Boundary Enforcement & Screen/Data Scoping                                                | `AC-002.1`, `AC-002.2`                                                                                     | `SRS-REQ-002`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-003`      | Exclusive CEO User Account Management                                                                   | `AC-003.1`, `AC-003.2`                                                                                     | `SRS-REQ-003`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-004`      | Exclusive CEO Business Role Assignment & Modification                                                       | `AC-004.1`, `AC-004.2`                                                                                     | `SRS-REQ-004`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-005`      | Account Status Transitions & User Management Audit Logging                                              | `AC-005.1`, `AC-005.2`                                                                                     | `SRS-REQ-005`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-006`      | Exclusive CEO Operational Permission Administration & 17-Permission Catalogue                           | `AC-006.1`, `AC-006.2`                                                                                     | `SRS-REQ-006`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-007`      | Real-Time Runtime Permission Validation & Audit Logging                                                 | `AC-007.1`, `AC-007.2`                                                                                     | `SRS-REQ-007`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-008`      | Operational Permission Granular Scope Configuration                                                     | `AC-008.1`, `AC-008.2`                                                                                     | `SRS-REQ-008`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-009`      | Permission Scope, Active Validity, and System Enforcement                                               | `AC-009.1`, `AC-009.2`                                                                                     | `SRS-REQ-009`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-010`      | Employee Interface Boundary Control for Permission Grants                                               | `AC-010.1`, `AC-010.2`                                                                                     | `SRS-REQ-010`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-011`      | Prohibition of Onward Permission Delegation                                                             | `AC-011.1`, `AC-011.2`                                                                                     | `SRS-REQ-011`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-012`      | Employee Self-Approval Prohibition for Delegated Review Permissions                                     | `AC-012.1`, `AC-012.2`                                                                                     | `SRS-REQ-012`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-013`      | Permission Administration & Exercise Audit Logging                                                      | `AC-013.1`, `AC-013.2`                                                                                     | `SRS-REQ-013`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-014`      | Multi-Role Idea Submission Access via Dedicated Form                                                    | `AC-014.1`, `AC-014.2`, `AC-014.3`                                                                         | `SRS-REQ-014`, `SRS-REQ-084`                | Fully Covered   | Decomposed into Access and Dedicated Fields              |
| `BRS-REQ-015`      | Automated System-Generated Idea ID Assignment                                                           | `AC-015.1`, `AC-015.2`                                                                                     | `SRS-REQ-015`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-016`      | Idea Review Evaluation Gate, Decision Enforcement, and Predefined Marks Capture                         | `AC-016.1`, `AC-016.2`, `AC-016.3`, `AC-016.4`, `AC-016.5`, `AC-016.6`                                     | `SRS-REQ-016`, `SRS-REQ-085`, `SRS-REQ-090` | Fully Covered   | Decomposed into Decisions, Marks Capture, and Correction |
| `BRS-REQ-017`      | Terminal Idea Rejection Handling                                                                        | `AC-017.1`, `AC-017.2`                                                                                     | `SRS-REQ-017`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-018`      | Dormant Retained Idea Preservation                                                                      | `AC-018.1`, `AC-018.2`                                                                                     | `SRS-REQ-018`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-019`      | Administrative Reopen of Retained Ideas                                                                 | `AC-019.1`, `AC-019.2`                                                                                     | `SRS-REQ-019`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-020`      | Content ID Generation & Single Content Identity Rule                                                    | `AC-020.1`, `AC-020.2`                                                                                     | `SRS-REQ-020`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-021`      | Non-Assignment Planning Parameter Definition                                                            | `AC-021.1`, `AC-021.2`, `AC-021.3`, `AC-021.4`                                                             | `SRS-REQ-021`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-022`      | Initial Shooting Assignment during Planning                                                             | `AC-022.1`, `AC-022.2`                                                                                     | `SRS-REQ-022`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-023`      | Planned Output Taxonomy Classification & Multi-Asset Grouping                                           | `AC-023.1`, `AC-023.2`                                                                                     | `SRS-REQ-023`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-024`      | Reel Type Duration Attribution per Reel-Type Planned Output                                             | `AC-024.1`, `AC-024.2`                                                                                     | `SRS-REQ-024`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-025`      | Intended Publication Scope Mapping                                                                      | `AC-025.1`, `AC-025.2`                                                                                     | `SRS-REQ-025`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-026`      | Shared Approved Planned Live Date Model                                                                 | `AC-026.1`, `AC-026.2`                                                                                     | `SRS-REQ-026`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-027`      | Default Execution Date Calculation & Manual Override Governance                                         | `AC-027.1`, `AC-027.2`                                                                                     | `SRS-REQ-027`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-028`      | Content Asset Folder Link Establishment & Maintenance                                                   | `AC-028.1`, `AC-028.2`                                                                                     | `SRS-REQ-028`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-029`      | Planning Review Gate & Rework Handling                                                                  | `AC-029.1`, `AC-029.2`                                                                                     | `SRS-REQ-029`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-030`      | Planning Approval & Task Activation                                                                     | `AC-030.1`, `AC-030.2`                                                                                     | `SRS-REQ-030`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-031`      | Shoot Execution & Shoot In Progress State Transition                                                    | `AC-031.1`, `AC-031.2`                                                                                     | `SRS-REQ-031`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-032`      | Folder Link Prerequisite for Shoot Review Submission                                                    | `AC-032.1`, `AC-032.2`                                                                                     | `SRS-REQ-032`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-033`      | Shoot Review Gate, Approval, Rework, and Cameraperson Marks Attribution Governance                      | `AC-033.1`, `AC-033.2`, `AC-033.3`                                                                         | `SRS-REQ-033`, `SRS-REQ-086`                | Fully Covered   | Decomposed into Review Workflow and Marks Attribution    |
| `BRS-REQ-034`      | Shoot Approval & Post-Shoot Eligibility for Editor Assignment                                           | `AC-034.1`, `AC-034.2`                                                                                     | `SRS-REQ-034`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-035`      | Initial Post-Shoot Approval Editor Assignment                                                           | `AC-035.1`, `AC-035.2`                                                                                     | `SRS-REQ-035`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-036`      | Edit Assigned Task Activation & Editing Execution                                                       | `AC-036.1`, `AC-036.2`                                                                                     | `SRS-REQ-036`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-037`      | Edit Review Gate, Approval, Rework, and Editor Marks Attribution Governance                             | `AC-037.1`, `AC-037.2`, `AC-037.3`                                                                         | `SRS-REQ-037`, `SRS-REQ-087`                | Fully Covered   | Decomposed into Review Workflow and Marks Attribution    |
| `BRS-REQ-038`      | Edit Approval & Transition to Ready for Publishing                                                      | `AC-038.1`, `AC-038.2`                                                                                     | `SRS-REQ-038`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-039`      | Contextual Workload Display during Shooting and Editing Assignments                                     | `AC-039.1`, `AC-039.2`                                                                                     | `SRS-REQ-039`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-040`      | Human Assignment Control & Automated Algorithm Prohibition                                              | `AC-040.1`, `AC-040.2`                                                                                     | `SRS-REQ-040`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-041`      | Publishing Stage Triggering & Initiation Governance                                                     | `AC-041.1`, `AC-041.2`                                                                                     | `SRS-REQ-041`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-042`      | Execution of Manual Publishing, Event Type Classification, & Event Recording                            | `AC-042.1`, `AC-042.2`                                                                                     | `SRS-REQ-042`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-043`      | Actual Publication Event Traceability & Attribute Capture                                               | `AC-043.1`, `AC-043.2`                                                                                     | `SRS-REQ-043`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-044`      | Late Actual Publication Recording & Operating Schedule Interpretation                                   | `AC-044.1`, `AC-044.2`                                                                                     | `SRS-REQ-044`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-045`      | Publication Target N/A Exception Recording & Reversal                                                   | `AC-045.1`, `AC-045.2`                                                                                     | `SRS-REQ-045`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-046`      | Linked Publication Evidence & Link Correction                                                           | `AC-046.1`, `AC-046.2`                                                                                     | `SRS-REQ-046`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-047`      | Initial Publishing Scope Completion Rule & Minimum Publication Requirement                              | `AC-047.1`, `AC-047.2`                                                                                     | `SRS-REQ-047`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-048`      | Performance Pending State Transition & Event Obligation Tracking                                        | `AC-048.1`, `AC-048.2`                                                                                     | `SRS-REQ-048`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-049`      | System-Derived Performance Due Date Calculation                                                         | `AC-049.1`, `AC-049.2`                                                                                     | `SRS-REQ-049`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-050`      | Performance Update Eligibility, Scorecard Capture, & Manual Metric Entry                                | `AC-050.1`, `AC-050.2`, `AC-050.3`, `AC-050.4`                                                             | `SRS-REQ-050`, `SRS-REQ-088`, `SRS-REQ-089` | Fully Covered   | Decomposed into Eligibility, Raw Entry, and Derivations  |
| `BRS-REQ-051`      | Linked Performance Metric Correction Governance                                                         | `AC-051.1`, `AC-051.2`                                                                                     | `SRS-REQ-051`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-052`      | Workflow Completion Rule & Closed / Reopenable Classification                                           | `AC-052.1`, `AC-052.2`                                                                                     | `SRS-REQ-052`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-053`      | Reopen Completed Deliverable for Additional Publication, Repost, Evidence Correction, or N/A Adjustment | `AC-053.1`, `AC-053.2`                                                                                     | `SRS-REQ-053`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-054`      | Reopen Completed Deliverable Exclusively for Metric Correction                                          | `AC-054.1`, `AC-054.2`                                                                                     | `SRS-REQ-054`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-055`      | Exit Condition for Reopened Publishing Activities                                                       | `AC-055.1`, `AC-055.2`                                                                                     | `SRS-REQ-055`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-056`      | Cross-Stage Reschedule Governance                                                                       | `AC-056.1`, `AC-056.2`                                                                                     | `SRS-REQ-056`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-057`      | Reassignment Governance & Task State Reset                                                              | `AC-057.1`, `AC-057.2`                                                                                     | `SRS-REQ-057`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-058`      | Pre-First-Completion Cancellation Governance & Terminal State Transition                                | `AC-058.1`, `AC-058.2`                                                                                     | `SRS-REQ-058`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-059`      | Permanent Post-Completion Cancellation Prohibition                                                      | `AC-059.1`, `AC-059.2`                                                                                     | `SRS-REQ-059`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-060`      | Master Publishing Catalogue Maintenance                                                                 | `AC-060.1`, `AC-060.2`                                                                                     | `SRS-REQ-060`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-061`      | Master Catalogue Audit Logging                                                                          | `AC-061.1`, `AC-061.2`                                                                                     | `SRS-REQ-061`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-062`      | Controlled Content Taxonomy Governance                                                                  | `AC-062.1`, `AC-062.2`                                                                                     | `SRS-REQ-062`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-063`      | System-Wide Immutable Audit Logging                                                                     | `AC-063.1`, `AC-063.2`                                                                                     | `SRS-REQ-063`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-064`      | Audit Trail Immutability & Deletion Prohibition                                                         | `AC-064.1`, `AC-064.2`                                                                                     | `SRS-REQ-064`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-065`      | Relevant Audit-History Visibility Permission                                                            | `AC-065.1`, `AC-065.2`                                                                                     | `SRS-REQ-065`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-066`      | Employee Self-Service Own-Work Operational Visibility                                                   | `AC-066.1`, `AC-066.2`                                                                                     | `SRS-REQ-066`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-067`      | Peer Privacy Protection & Compensation/Ranking/Marks Boundary                                           | `AC-067.1`, `AC-067.2`                                                                                     | `SRS-REQ-067`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-068`      | Employee Personal Performance Attribution & Approved Indicators                                         | `AC-068.1`, `AC-068.2`                                                                                     | `SRS-REQ-068`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-069`      | Team Workload Visibility Permission & Aggregate Boundaries                                              | `AC-069.1`, `AC-069.2`                                                                                     | `SRS-REQ-069`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-070`      | Team KPI Visibility Permission & Aggregate Boundaries                                                   | `AC-070.1`, `AC-070.2`                                                                                     | `SRS-REQ-070`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-071`      | Operational KPIs Capture & Reporting                                                                    | `AC-071.1`, `AC-071.2`, `AC-071.3`, `AC-071.4`, `AC-071.5`, `AC-071.6`, `AC-071.7`                         | `SRS-REQ-071`                               | Fully Covered   | Covers KPI-001 through KPI-007                           |
| `BRS-REQ-072`      | Productivity KPIs Capture & Reporting                                                                   | `AC-072.1`, `AC-072.2`, `AC-072.3`, `AC-072.4`                                                             | `SRS-REQ-072`                               | Fully Covered   | Covers KPI-008 through KPI-011                           |
| `BRS-REQ-073`      | Content & Published Unit KPIs Capture & Reporting                                                       | `AC-073.1`, `AC-073.2`, `AC-073.3`, `AC-073.4`, `AC-073.5`, `AC-073.6`, `AC-073.7`, `AC-073.8`, `AC-073.9` | `SRS-REQ-073`                               | Fully Covered   | Covers KPI-012 through KPI-020                           |
| `BRS-REQ-074`      | Approval & Review KPIs Capture & Reporting                                                              | `AC-074.1`, `AC-074.2`, `AC-074.3`, `AC-074.4`                                                             | `SRS-REQ-074`                               | Fully Covered   | Covers KPI-021 through KPI-024                           |
| `BRS-REQ-075`      | Delay, SLA, & On-Time Performance KPIs Capture & Reporting                                              | `AC-075.1`, `AC-075.2`, `AC-075.3`, `AC-075.4`, `AC-075.5`, `AC-075.6`, `AC-075.7`                         | `SRS-REQ-075`                               | Fully Covered   | Covers KPI-025 through KPI-030 and SC-002                |
| `BRS-REQ-076`      | Administrative Action & Permission Usage Reporting                                                      | `AC-076.1`, `AC-076.2`                                                                                     | `SRS-REQ-076`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-077`      | Web Browser Availability & Operating Environment                                                        | `AC-077.1`, `AC-077.2`                                                                                     | `SRS-REQ-077`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-078`      | System Availability & 24×7 Uptime Standard                                                              | `AC-078.1`, `AC-078.2`                                                                                     | `SRS-REQ-078`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-079`      | User Concurrency Sizing & Capacity Boundary                                                             | `AC-079.1`, `AC-079.2`                                                                                     | `SRS-REQ-079`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-080`      | Temporary MVP Lifespan & Business OS Transition Readiness                                               | `AC-080.1`, `AC-080.2`                                                                                     | `SRS-REQ-080`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-081`      | System Data Export Capability                                                                           | `AC-081.1`, `AC-081.2`                                                                                     | `SRS-REQ-081`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-082`      | External API Integration & Automation Exclusion                                                         | `AC-082.1`, `AC-082.2`                                                                                     | `SRS-REQ-082`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-083`      | System-Generated Workflow Status & Manual Status Edit Prohibition                                       | `AC-083.1`, `AC-083.2`, `AC-083.3`                                                                         | `SRS-REQ-083`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-084`      | In-Progress Work Hold & Resume Governance                                                               | `AC-084.1`, `AC-084.2`, `AC-084.3`, `AC-084.4`                                                             | `SRS-REQ-091`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-085`      | Business Role Catalogue & Administration (R3.4)                                                         | `AC-085.1`, `AC-085.2`, `AC-085.3`, `AC-085.4`                                                             | `SRS-REQ-092`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |
| `BRS-REQ-086`      | Planning Mode & Urgent Scheduling (R3.4)                                                                | `AC-086.1`, `AC-086.2`, `AC-086.3`, `AC-086.4`, `AC-086.5`, `AC-086.6`                                     | `SRS-REQ-093`                               | Fully Covered   | 100% Traceable to BRS v1.1.0                             |

---

## Appendix B: BRS Acceptance Criteria (AC) -> SRS Coverage Matrix

| AC ID      | Parent BRS REQ | Concise Faithful AC Meaning / Intent                                                                                                        | Mapped SRS Requirement ID(s) | Coverage Status | Notes                       |
| :--------- | :------------- | :------------------------------------------------------------------------------------------------------------------------------------------ | :--------------------------- | :-------------- | :-------------------------- |
| `AC-001.1` | `BRS-REQ-001`  | Upon authentication, CEO receives executive governance views and access-administration controls.                                            | `SRS-REQ-001`                | Covered         | Verified Normative Coverage |
| `AC-001.2` | `BRS-REQ-001`  | Upon authentication, Marketing Manager receives operational management dashboards and execution controls.                                   | `SRS-REQ-001`                | Covered         | Verified Normative Coverage |
| `AC-001.3` | `BRS-REQ-001`  | Upon authentication, Employee receives self-service workspace with assigned work, deadlines, and metrics.                                   | `SRS-REQ-001`                | Covered         | Verified Normative Coverage |
| `AC-002.1` | `BRS-REQ-002`  | Employees without relevant permissions cannot access management dashboards, review queues, or catalogues.                                   | `SRS-REQ-002`                | Covered         | Verified Normative Coverage |
| `AC-002.2` | `BRS-REQ-002`  | Attempted unauthorized navigation or action execution is denied and logged in the audit trail.                                              | `SRS-REQ-002`                | Covered         | Verified Normative Coverage |
| `AC-003.1` | `BRS-REQ-003`  | User account creation, activation, and deactivation is restricted exclusively to CEO users.                                                 | `SRS-REQ-003`                | Covered         | Verified Normative Coverage |
| `AC-003.2` | `BRS-REQ-003`  | Marketing Managers and Employees cannot view account creation controls or modify account statuses.                                          | `SRS-REQ-003`                | Covered         | Verified Normative Coverage |
| `AC-004.1` | `BRS-REQ-004`  | Business Role assignment (the designation resolving to one internal access class — CEO / Owner, Marketing Manager, Employee) is restricted exclusively to CEO users.                                             | `SRS-REQ-004`                | Covered         | Verified Normative Coverage |
| `AC-004.2` | `BRS-REQ-004`  | Modifying a user’s Business Role requires a mandatory reason and is logged in the audit log with previous/new Business Role (and resolved access class).                                    | `SRS-REQ-004`                | Covered         | Verified Normative Coverage |
| `AC-005.1` | `BRS-REQ-005`  | Account administration actions require entering a mandatory reason before saving.                                                           | `SRS-REQ-005`                | Covered         | Verified Normative Coverage |
| `AC-005.2` | `BRS-REQ-005`  | User access administration records are permanently preserved in the immutable audit log.                                                    | `SRS-REQ-005`                | Covered         | Verified Normative Coverage |
| `AC-006.1` | `BRS-REQ-006`  | Permission grant, modification, and revocation controls are accessible exclusively to CEO users.                                            | `SRS-REQ-006`                | Covered         | Verified Normative Coverage |
| `AC-006.2` | `BRS-REQ-006`  | CEO can grant only permissions from the approved 17-item catalogue (Permissions #1 through #17).                                            | `SRS-REQ-006`                | Covered         | Verified Normative Coverage |
| `AC-007.1` | `BRS-REQ-007`  | Permission validity is checked in real time at the exact moment an action is triggered.                                                     | `SRS-REQ-007`                | Covered         | Verified Normative Coverage |
| `AC-007.2` | `BRS-REQ-007`  | Every permission grant, edit, or revocation creates an audit record with actor, timestamp, scope, and reason.                               | `SRS-REQ-007`                | Covered         | Verified Normative Coverage |
| `AC-008.1` | `BRS-REQ-008`  | Permission scope configuration supports Global, Stage-Restricted, or Specific-Item scope.                                                   | `SRS-REQ-008`                | Covered         | Verified Normative Coverage |
| `AC-008.2` | `BRS-REQ-008`  | The system restricts permitted operations strictly to the designated scope boundary.                                                        | `SRS-REQ-008`                | Covered         | Verified Normative Coverage |
| `AC-009.1` | `BRS-REQ-009`  | Actions attempted outside effective time boundaries or recorded scope are rejected instantly.                                               | `SRS-REQ-009`                | Covered         | Verified Normative Coverage |
| `AC-009.2` | `BRS-REQ-009`  | Real-time checks evaluate active validity prior to rendering actionable UI controls.                                                        | `SRS-REQ-009`                | Covered         | Verified Normative Coverage |
| `AC-010.1` | `BRS-REQ-010`  | An Employee granted specific permission sees only the screens and controls for that permission.                                             | `SRS-REQ-010`                | Covered         | Verified Normative Coverage |
| `AC-010.2` | `BRS-REQ-010`  | Unrelated management dashboards, catalogues, and administrative tools remain hidden from permitted Employees.                               | `SRS-REQ-010`                | Covered         | Verified Normative Coverage |
| `AC-011.1` | `BRS-REQ-011`  | Non-CEO users have no interface options or functional capability to delegate permissions or manage access.                                  | `SRS-REQ-011`                | Covered         | Verified Normative Coverage |
| `AC-011.2` | `BRS-REQ-011`  | System authorization rejects any permission modification request originated by a non-CEO user.                                              | `SRS-REQ-011`                | Covered         | Verified Normative Coverage |
| `AC-012.1` | `BRS-REQ-012`  | Review controls are disabled for permitted Employees on items they personally submitted, executed, or prepared.                             | `SRS-REQ-012`                | Covered         | Verified Normative Coverage |
| `AC-012.2` | `BRS-REQ-012`  | Attempting self-approval returns a validation error and logs an unauthorized review attempt.                                                | `SRS-REQ-012`                | Covered         | Verified Normative Coverage |
| `AC-013.1` | `BRS-REQ-013`  | Permission grant, modification, and revocation actions require entering a mandatory reason before saving.                                   | `SRS-REQ-013`                | Covered         | Verified Normative Coverage |
| `AC-013.2` | `BRS-REQ-013`  | Both successful permission uses and rejected unauthorized attempts generate immutable audit records.                                        | `SRS-REQ-013`                | Covered         | Verified Normative Coverage |
| `AC-014.1` | `BRS-REQ-014`  | CEO, Marketing Manager, and Employee users can access and complete the Idea Submission Form.                                                | `SRS-REQ-014`                | Covered         | Verified Normative Coverage |
| `AC-014.2` | `BRS-REQ-014`  | Idea Form captures Title (mandatory), Reference Link/Note (optional), and Remarks (optional free text).                                     | `SRS-REQ-084`                | Covered         | Verified Normative Coverage |
| `AC-014.3` | `BRS-REQ-014`  | Planning fields are omitted from Idea Submission Form and captured exclusively in Stage 3 Planning.                                         | `SRS-REQ-084`                | Covered         | Verified Normative Coverage |
| `AC-015.1` | `BRS-REQ-015`  | A unique Idea ID is generated automatically upon valid Idea submission.                                                                     | `SRS-REQ-015`                | Covered         | Verified Normative Coverage |
| `AC-015.2` | `BRS-REQ-015`  | Each submitted Idea is associated with exactly one system-generated Idea ID.                                                                | `SRS-REQ-015`                | Covered         | Verified Normative Coverage |
| `AC-016.1` | `BRS-REQ-016`  | Pending ideas cannot progress to Planning or terminal states without an explicit evaluation decision.                                       | `SRS-REQ-016`                | Covered         | Verified Normative Coverage |
| `AC-016.2` | `BRS-REQ-016`  | Authorized reviewers can select Approve, Reject, or Retain for queued ideas.                                                                | `SRS-REQ-016`                | Covered         | Verified Normative Coverage |
| `AC-016.3` | `BRS-REQ-016`  | Selecting Approve enforces mandatory selection of predefined Cameraperson and Editor Marks from `[0, 0.5, 1.0, 2.0, 3.0]`.                  | `SRS-REQ-085`                | Covered         | Verified Normative Coverage |
| `AC-016.4` | `BRS-REQ-016`  | Predefined Mark corrections occur only under Permission #1 with linked immutable correction history.                                        | `SRS-REQ-090`                | Covered         | Verified Normative Coverage |
| `AC-016.5` | `BRS-REQ-016`  | Reject and Retain decisions do not require, create, or assign predefined role Marks.                                                        | `SRS-REQ-016`                | Covered         | Verified Normative Coverage |
| `AC-016.6` | `BRS-REQ-016`  | Numeric 0 is a valid selectable predefined Mark satisfying mandatory entry and does not mean absence/rework.                                | `SRS-REQ-085`                | Covered         | Verified Normative Coverage |
| `AC-017.1` | `BRS-REQ-017`  | Selecting Reject requires entering a mandatory rejection reason.                                                                            | `SRS-REQ-017`                | Covered         | Verified Normative Coverage |
| `AC-017.2` | `BRS-REQ-017`  | Rejected ideas transition to status Rejected and cannot be reopened or edited.                                                              | `SRS-REQ-017`                | Covered         | Verified Normative Coverage |
| `AC-018.1` | `BRS-REQ-018`  | Selecting Retain moves the idea to dormant status Retained under its original Idea ID.                                                      | `SRS-REQ-018`                | Covered         | Verified Normative Coverage |
| `AC-018.2` | `BRS-REQ-018`  | Retained ideas receive no Content ID and do not enter Planning queues.                                                                      | `SRS-REQ-018`                | Covered         | Verified Normative Coverage |
| `AC-019.1` | `BRS-REQ-019`  | Executing Reopen on a Retained idea transitions its status back to Pending Approval.                                                        | `SRS-REQ-019`                | Covered         | Verified Normative Coverage |
| `AC-019.2` | `BRS-REQ-019`  | Reopening generates an audit record capturing actor, timestamp, and status transition.                                                      | `SRS-REQ-019`                | Covered         | Verified Normative Coverage |
| `AC-020.1` | `BRS-REQ-020`  | Approving an idea generates exactly one Content ID formatted as `C-MMYY-NNNN` upon entry to Planning.                                       | `SRS-REQ-020`                | Covered         | Verified Normative Coverage |
| `AC-020.2` | `BRS-REQ-020`  | Content ID does not encode channel/employee IDs, is non-editable, and groups all associated Planned Outputs.                                | `SRS-REQ-020`                | Covered         | Verified Normative Coverage |
| `AC-021.1` | `BRS-REQ-021`  | Content Priority is selected from controlled list: Low, Medium, High.                                                                       | `SRS-REQ-021`                | Covered         | Verified Normative Coverage |
| `AC-021.2` | `BRS-REQ-021`  | Category is an optional free-text field (single field, supporting one or multiple values or blank), conceptually distinct from SKU ID.      | `SRS-REQ-021`                | Covered         | Verified Normative Coverage |
| `AC-021.3` | `BRS-REQ-021`  | Models/talent supports multi-selection or empty; SKU supports alphanumeric or explicit N/A; targets supported.                              | `SRS-REQ-021`                | Covered         | Verified Normative Coverage |
| `AC-021.4` | `BRS-REQ-021`  | System rejects Critical/Urgent priorities, permits free-text Category without reference validation, and contains no auto-priority algorithms.| `SRS-REQ-021`                | Covered         | Verified Normative Coverage |
| `AC-022.1` | `BRS-REQ-022`  | Shooting team assignment allows selecting one or more Camerapersons during Planning under Permission #4.                                    | `SRS-REQ-022`                | Covered         | Verified Normative Coverage |
| `AC-022.2` | `BRS-REQ-022`  | Editor assignment controls are disabled and hidden during Stage 3 Planning.                                                                 | `SRS-REQ-022`                | Covered         | Verified Normative Coverage |
| `AC-023.1` | `BRS-REQ-023`  | Planning allows selecting multiple Planned Outputs from [Photography, Reel, Video] for a single Content ID.                            | `SRS-REQ-023`                | Covered         | Verified Normative Coverage |
| `AC-023.2` | `BRS-REQ-023`  | Selected Planned Outputs do not generate child workflow states or separate Content IDs.                                                     | `SRS-REQ-023`                | Covered         | Verified Normative Coverage |
| `AC-024.1` | `BRS-REQ-024`  | Each Reel Planned Output requires specifying exactly one Reel Type (Very Short, Short, Long); non-Reel outputs do not.                      | `SRS-REQ-024`                | Covered         | Verified Normative Coverage |
| `AC-024.2` | `BRS-REQ-024`  | Multiple Reel Planned Outputs under same Content ID can possess distinct Reel Type classifications.                                         | `SRS-REQ-024`                | Covered         | Verified Normative Coverage |
| `AC-025.1` | `BRS-REQ-025`  | Planning allows mapping specific Planned Outputs to specific selected Publication Targets under Content ID.                                 | `SRS-REQ-025`                | Covered         | Verified Normative Coverage |
| `AC-025.2` | `BRS-REQ-025`  | Scope mappings exist as planning attributes under Content ID without deliverable sub-status tracking.                                       | `SRS-REQ-025`                | Covered         | Verified Normative Coverage |
| `AC-026.1` | `BRS-REQ-026`  | Setting or updating Planned Live Date applies uniformly to all Planned Outputs under the Content ID.                                        | `SRS-REQ-026`                | Covered         | Verified Normative Coverage |
| `AC-026.2` | `BRS-REQ-026`  | Asset-specific planned live dates cannot be created within a single publication scope.                                                      | `SRS-REQ-026`                | Covered         | Verified Normative Coverage |
| `AC-027.1` | `BRS-REQ-027`  | Entering Planned Live Date populates default Shoot Date (-5d) and Edit Date (-2d) automatically.                                            | `SRS-REQ-027`                | Covered         | Verified Normative Coverage |
| `AC-027.2` | `BRS-REQ-027`  | Authorized planning users can override default dates prior to submitting Planning output for review.                                        | `SRS-REQ-027`                | Covered         | Verified Normative Coverage |
| `AC-028.1` | `BRS-REQ-028`  | Authorized users can add or update the Content Asset Folder Link URL for a Content ID under Permission #13.                                 | `SRS-REQ-028`                | Covered         | Verified Normative Coverage |
| `AC-028.2` | `BRS-REQ-028`  | Link replacements require entering a mandatory reason and log previous/new URLs in audit history.                                           | `SRS-REQ-028`                | Covered         | Verified Normative Coverage |
| `AC-029.1` | `BRS-REQ-029`  | Planning output cannot progress without explicit Planning Review approval.                                                                  | `SRS-REQ-029`                | Covered         | Verified Normative Coverage |
| `AC-029.2` | `BRS-REQ-029`  | Request Rework returns status to Planning and requires mandatory reviewer feedback comments.                                                | `SRS-REQ-029`                | Covered         | Verified Normative Coverage |
| `AC-030.1` | `BRS-REQ-030`  | Planning approval updates status to Planning Approved and queues shooting task as Shoot Assigned.                                           | `SRS-REQ-030`                | Covered         | Verified Normative Coverage |
| `AC-030.2` | `BRS-REQ-030`  | Active Shoot Assigned task is visible to assigned shooting team in their authorized own-work view.                                          | `SRS-REQ-030`                | Covered         | Verified Normative Coverage |
| `AC-031.1` | `BRS-REQ-031`  | Assigned team members can transition task from Shoot Assigned to Shoot In Progress.                                                         | `SRS-REQ-031`                | Covered         | Verified Normative Coverage |
| `AC-031.2` | `BRS-REQ-031`  | Task progress indicators reflect active shooting state in real time.                                                                        | `SRS-REQ-031`                | Covered         | Verified Normative Coverage |
| `AC-032.1` | `BRS-REQ-032`  | Attempting to submit shoot output without recorded folder link displays blocking validation error.                                          | `SRS-REQ-032`                | Covered         | Verified Normative Coverage |
| `AC-032.2` | `BRS-REQ-032`  | Recording a valid folder link enables the Shoot Review submission control.                                                                  | `SRS-REQ-032`                | Covered         | Verified Normative Coverage |
| `AC-033.1` | `BRS-REQ-033`  | Shoot output cannot proceed to editing without explicit Shoot Review approval.                                                              | `SRS-REQ-033`                | Covered         | Verified Normative Coverage |
| `AC-033.2` | `BRS-REQ-033`  | Request Rework returns status to Shoot In Progress with comments and creates no personal Mark attribution record.                           | `SRS-REQ-033`                | Covered         | Verified Normative Coverage |
| `AC-033.3` | `BRS-REQ-033`  | Shoot Approval attributes full predefined Cameraperson Mark to confirmed qualifying final Camerapersons without splitting/averaging.        | `SRS-REQ-086`                | Covered         | Verified Normative Coverage |
| `AC-034.1` | `BRS-REQ-034`  | Passing Shoot Review sets deliverable status to Shoot Approved.                                                                             | `SRS-REQ-034`                | Covered         | Verified Normative Coverage |
| `AC-034.2` | `BRS-REQ-034`  | The Content ID becomes visible in the Editing Assignment Management queue.                                                                  | `SRS-REQ-034`                | Covered         | Verified Normative Coverage |
| `AC-035.1` | `BRS-REQ-035`  | Users holding Editing Assignment Management permission can assign Editors to Shoot Approved deliverables.                                   | `SRS-REQ-035`                | Covered         | Verified Normative Coverage |
| `AC-035.2` | `BRS-REQ-035`  | Initial Editor assignment updates task status to Edit Assigned.                                                                             | `SRS-REQ-035`                | Covered         | Verified Normative Coverage |
| `AC-036.1` | `BRS-REQ-036`  | Assigned Editors see tasks in Edit Assigned and can initiate editing execution.                                                             | `SRS-REQ-036`                | Covered         | Verified Normative Coverage |
| `AC-036.2` | `BRS-REQ-036`  | Starting editing work updates deliverable status to Editing.                                                                                | `SRS-REQ-036`                | Covered         | Verified Normative Coverage |
| `AC-037.1` | `BRS-REQ-037`  | Editing output cannot proceed to publishing without explicit Edit Review approval.                                                          | `SRS-REQ-037`                | Covered         | Verified Normative Coverage |
| `AC-037.2` | `BRS-REQ-037`  | Request Rework returns status to Editing with remarks and creates no personal Mark attribution record.                                      | `SRS-REQ-037`                | Covered         | Verified Normative Coverage |
| `AC-037.3` | `BRS-REQ-037`  | Edit Approval attributes full predefined Editor Mark to confirmed qualifying final Editors without splitting/averaging.                     | `SRS-REQ-087`                | Covered         | Verified Normative Coverage |
| `AC-038.1` | `BRS-REQ-038`  | Edit Review approval updates status to Edit Approved and then Ready for Publishing.                                                         | `SRS-REQ-038`                | Covered         | Verified Normative Coverage |
| `AC-038.2` | `BRS-REQ-038`  | The deliverable enters the publishing execution queue.                                                                                      | `SRS-REQ-038`                | Covered         | Verified Normative Coverage |
| `AC-039.1` | `BRS-REQ-039`  | Assignment interfaces display candidate active task counts and delay badges.                                                                | `SRS-REQ-039`                | Covered         | Verified Normative Coverage |
| `AC-039.2` | `BRS-REQ-039`  | Contextual views do not display peer performance ratings, peer Marks, rework rates, or personal metrics.                                    | `SRS-REQ-039`                | Covered         | Verified Normative Coverage |
| `AC-040.1` | `BRS-REQ-040`  | Assigners select task assignees manually from authorized user lists.                                                                        | `SRS-REQ-040`                | Covered         | Verified Normative Coverage |
| `AC-040.2` | `BRS-REQ-040`  | System contains zero automated task auto-assignment or candidate ranking routines.                                                          | `SRS-REQ-040`                | Covered         | Verified Normative Coverage |
| `AC-041.1` | `BRS-REQ-041`  | When Planned Live Date arrives, Content ID becomes eligible to enter Publishing.                                                            | `SRS-REQ-041`                | Covered         | Verified Normative Coverage |
| `AC-041.2` | `BRS-REQ-041`  | Authorized publishing users can manually initiate Publishing prior to the planned date.                                                     | `SRS-REQ-041`                | Covered         | Verified Normative Coverage |
| `AC-042.1` | `BRS-REQ-042`  | Authorized users record separate Actual Publication events per live post, setting Event Type as Original or Repost.                         | `SRS-REQ-042`                | Covered         | Verified Normative Coverage |
| `AC-042.2` | `BRS-REQ-042`  | Each recorded publication event requires capturing a valid publishing link URL, actor, and timestamp.                                       | `SRS-REQ-042`                | Covered         | Verified Normative Coverage |
| `AC-043.1` | `BRS-REQ-043`  | Actual Publication event records capture all mandatory metadata attributes including Event Type.                                            | `SRS-REQ-043`                | Covered         | Verified Normative Coverage |
| `AC-043.2` | `BRS-REQ-043`  | Recorded publication events are associated directly with parent Content ID and are immutable.                                               | `SRS-REQ-043`                | Covered         | Verified Normative Coverage |
| `AC-044.1` | `BRS-REQ-044`  | Actual Publication Date and Time are recorded truthfully even if later than Planned Live Date.                                              | `SRS-REQ-044`                | Covered         | Verified Normative Coverage |
| `AC-044.2` | `BRS-REQ-044`  | Late Actual Publication events remain recordable and are evaluated for delay / on-time reporting.                                           | `SRS-REQ-044`                | Covered         | Verified Normative Coverage |
| `AC-045.1` | `BRS-REQ-045`  | Marking a target N/A requires entering a non-empty reason and removes its metric entry obligation.                                          | `SRS-REQ-045`                | Covered         | Verified Normative Coverage |
| `AC-045.2` | `BRS-REQ-045`  | Reversing an N/A target logs a supersession audit record and restores publication tracking.                                                 | `SRS-REQ-045`                | Covered         | Verified Normative Coverage |
| `AC-046.1` | `BRS-REQ-046`  | Correcting a publishing link creates a linked correction audit record without altering original entry.                                      | `SRS-REQ-046`                | Covered         | Verified Normative Coverage |
| `AC-046.2` | `BRS-REQ-046`  | Mandatory correction reasons are required prior to saving link updates.                                                                     | `SRS-REQ-046`                | Covered         | Verified Normative Coverage |
| `AC-047.1` | `BRS-REQ-047`  | Transition to Performance Pending requires >=1 Actual Publication event AND 100% scope resolution.                                          | `SRS-REQ-047`                | Covered         | Verified Normative Coverage |
| `AC-047.2` | `BRS-REQ-047`  | Attempting completion when all selected targets are marked N/A returns a blocking validation error.                                         | `SRS-REQ-047`                | Covered         | Verified Normative Coverage |
| `AC-048.1` | `BRS-REQ-048`  | Content ID transitions to Performance Pending when scope completion leaves open performance obligations.                                    | `SRS-REQ-048`                | Covered         | Verified Normative Coverage |
| `AC-048.2` | `BRS-REQ-048`  | Outstanding event obligations are tracked underneath the Content ID status.                                                                 | `SRS-REQ-048`                | Covered         | Verified Normative Coverage |
| `AC-049.1` | `BRS-REQ-049`  | Generating an Actual Publication event sets its Performance Due Date to Actual Publication Date + 2 calendar days.                          | `SRS-REQ-049`                | Covered         | Verified Normative Coverage |
| `AC-049.2` | `BRS-REQ-049`  | Performance Due Dates cannot be manually edited or reschedulable.                                                                           | `SRS-REQ-049`                | Covered         | Verified Normative Coverage |
| `AC-050.1` | `BRS-REQ-050`  | Metric entry controls become active only when current date >= Performance Due Date for that event.                                          | `SRS-REQ-050`                | Covered         | Verified Normative Coverage |
| `AC-050.2` | `BRS-REQ-050`  | System captures manual scorecard inputs per event: Views, 3s views, Avg watch time, Video length, Link clicks, Impressions.                 | `SRS-REQ-088`                | Covered         | Verified Normative Coverage |
| `AC-050.3` | `BRS-REQ-050`  | System calculates Hook Rate, Hold Rate, CTR with frozen formulas; suppresses N/A platform metrics.                                          | `SRS-REQ-089`                | Covered         | Verified Normative Coverage |
| `AC-050.4` | `BRS-REQ-050`  | Metric submissions after the Performance Due Date are flagged as late in compliance reporting.                                              | `SRS-REQ-088`                | Covered         | Verified Normative Coverage |
| `AC-051.1` | `BRS-REQ-051`  | Metric updates create linked audit records preserving original metric entries.                                                              | `SRS-REQ-051`                | Covered         | Verified Normative Coverage |
| `AC-051.2` | `BRS-REQ-051`  | Mandatory correction reasons are enforced for all metric edits.                                                                             | `SRS-REQ-051`                | Covered         | Verified Normative Coverage |
| `AC-052.1` | `BRS-REQ-052`  | Status transitions to Completed when 100% of event metric obligations and N/A targets are resolved.                                         | `SRS-REQ-052`                | Covered         | Verified Normative Coverage |
| `AC-052.2` | `BRS-REQ-052`  | Completed status closes active operational queues while preserving reopening capability.                                                    | `SRS-REQ-052`                | Covered         | Verified Normative Coverage |
| `AC-053.1` | `BRS-REQ-053`  | Reopening Completed for additional publication or Repost retains Content ID and creates fresh Actual event without re-executing production. | `SRS-REQ-053`                | Covered         | Verified Normative Coverage |
| `AC-053.2` | `BRS-REQ-053`  | Reopening Completed for evidence correction or N/A reversal retains Content ID with mandatory reason and zero production re-execution.      | `SRS-REQ-053`                | Covered         | Verified Normative Coverage |
| `AC-054.1` | `BRS-REQ-054`  | Reopening for metric correction transitions status to Performance Update.                                                                   | `SRS-REQ-054`                | Covered         | Verified Normative Coverage |
| `AC-054.2` | `BRS-REQ-054`  | Prior production and publishing stages remain locked against re-execution during metric reopening.                                          | `SRS-REQ-054`                | Covered         | Verified Normative Coverage |
| `AC-055.1` | `BRS-REQ-055`  | Reopened publishing creating new live posts progresses to Performance Pending upon scope completion.                                        | `SRS-REQ-055`                | Covered         | Verified Normative Coverage |
| `AC-055.2` | `BRS-REQ-055`  | Reopened publishing resolving evidence/NA without new posts returns directly to Completed.                                                  | `SRS-REQ-055`                | Covered         | Verified Normative Coverage |
| `AC-056.1` | `BRS-REQ-056`  | Rescheduling updates approved execution dates and logs complete date history.                                                               | `SRS-REQ-056`                | Covered         | Verified Normative Coverage |
| `AC-056.2` | `BRS-REQ-056`  | Performance Due Dates remain non-editable and non-reschedulable.                                                                            | `SRS-REQ-056`                | Covered         | Verified Normative Coverage |
| `AC-057.1` | `BRS-REQ-057`  | Reassigning an active task updates assigned resource(s) and sets status to Shoot Assigned or Edit Assigned.                                 | `SRS-REQ-057`                | Covered         | Verified Normative Coverage |
| `AC-057.2` | `BRS-REQ-057`  | Reassignment requires entering a mandatory reason and logs previous/new assignees.                                                          | `SRS-REQ-057`                | Covered         | Verified Normative Coverage |
| `AC-058.1` | `BRS-REQ-058`  | Executing Cancel on an eligible pre-completion record moves status to terminal Cancelled.                                                   | `SRS-REQ-058`                | Covered         | Verified Normative Coverage |
| `AC-058.2` | `BRS-REQ-058`  | Mandatory cancellation reasons are captured in the immutable audit log.                                                                     | `SRS-REQ-058`                | Covered         | Verified Normative Coverage |
| `AC-059.1` | `BRS-REQ-059`  | Cancel controls are permanently disabled for any Content ID with historical Completed status.                                               | `SRS-REQ-059`                | Covered         | Verified Normative Coverage |
| `AC-059.2` | `BRS-REQ-059`  | System controls validate historical completion and reject cancel requests for post-completion records.                                      | `SRS-REQ-059`                | Covered         | Verified Normative Coverage |
| `AC-060.1` | `BRS-REQ-060`  | Authorized catalogue managers can add/update Platforms and Company Channels / Accounts.                                                     | `SRS-REQ-060`                | Covered         | Verified Normative Coverage |
| `AC-060.2` | `BRS-REQ-060`  | Deactivated channels are hidden from new Planning selections while preserving historical data.                                              | `SRS-REQ-060`                | Covered         | Verified Normative Coverage |
| `AC-061.1` | `BRS-REQ-061`  | Master catalogue modifications enforce mandatory reason entry before saving.                                                                | `SRS-REQ-061`                | Covered         | Verified Normative Coverage |
| `AC-061.2` | `BRS-REQ-061`  | Master catalogue audit records remain preserved as immutable historical records.                                                            | `SRS-REQ-061`                | Covered         | Verified Normative Coverage |
| `AC-062.1` | `BRS-REQ-062`  | Content selections during Planning are restricted to the controlled taxonomy list.                                                          | `SRS-REQ-062`                | Covered         | Verified Normative Coverage |
| `AC-062.2` | `BRS-REQ-062`  | Unrecognized or free-text content classifications are rejected by the system.                                                               | `SRS-REQ-062`                | Covered         | Verified Normative Coverage |
| `AC-063.1` | `BRS-REQ-063`  | Each auditable event captures actor, timestamp, and event-specific mandatory attributes.                                                    | `SRS-REQ-063`                | Covered         | Verified Normative Coverage |
| `AC-063.2` | `BRS-REQ-063`  | Audit logs cover 100% of defined system business transitions and administrative actions.                                                    | `SRS-REQ-063`                | Covered         | Verified Normative Coverage |
| `AC-064.1` | `BRS-REQ-064`  | User interfaces contain zero audit record edit or delete capabilities.                                                                      | `SRS-REQ-064`                | Covered         | Verified Normative Coverage |
| `AC-064.2` | `BRS-REQ-064`  | Audit records cannot be modified, overwritten, or deleted by any user.                                                                      | `SRS-REQ-064`                | Covered         | Verified Normative Coverage |
| `AC-065.1` | `BRS-REQ-065`  | Audit log viewing controls are accessible only to CEO, Marketing Manager, and permitted Employees.                                          | `SRS-REQ-065`                | Covered         | Verified Normative Coverage |
| `AC-065.2` | `BRS-REQ-065`  | Permitted Employees view audit logs scoped strictly to their granted operational boundary.                                                  | `SRS-REQ-065`                | Covered         | Verified Normative Coverage |
| `AC-066.1` | `BRS-REQ-066`  | Employees view task lists, folder links, and progress indicators for their own assigned work.                                               | `SRS-REQ-066`                | Covered         | Verified Normative Coverage |
| `AC-066.2` | `BRS-REQ-066`  | Review feedback, rework requests, and own personal Mark attributions are clearly displayed for assigned employees.                          | `SRS-REQ-066`                | Covered         | Verified Normative Coverage |
| `AC-067.1` | `BRS-REQ-067`  | Employee dashboards contain zero peer performance lookup, peer Marks viewing, or peer comparisons.                                          | `SRS-REQ-067`                | Covered         | Verified Normative Coverage |
| `AC-067.2` | `BRS-REQ-067`  | Peer performance data and peer Marks are restricted strictly to authorized management views.                                                | `SRS-REQ-067`                | Covered         | Verified Normative Coverage |
| `AC-068.1` | `BRS-REQ-068`  | Personal performance metrics display only the five approved measures derived from actual participation.                                     | `SRS-REQ-068`                | Covered         | Verified Normative Coverage |
| `AC-068.2` | `BRS-REQ-068`  | Shared task contribution credits all participants individually without multiplying Content ID totals.                                       | `SRS-REQ-068`                | Covered         | Verified Normative Coverage |
| `AC-069.1` | `BRS-REQ-069`  | Permitted Employees view team task distribution and active queue counts under Permission #14.                                               | `SRS-REQ-069`                | Covered         | Verified Normative Coverage |
| `AC-069.2` | `BRS-REQ-069`  | Aggregate workload screens omit individual employee performance scores, peer Marks, and comparisons.                                        | `SRS-REQ-069`                | Covered         | Verified Normative Coverage |
| `AC-070.1` | `BRS-REQ-070`  | Permitted Employees view high-level department KPI dashboards under Permission #15.                                                         | `SRS-REQ-070`                | Covered         | Verified Normative Coverage |
| `AC-070.2` | `BRS-REQ-070`  | Department KPI screens contain no individual employee performance breakdowns or peer Marks.                                                 | `SRS-REQ-070`                | Covered         | Verified Normative Coverage |
| `AC-071.1` | `BRS-REQ-071`  | KPI-001 Pending Work computes total active content deliverables in progress across stages in real time.                                     | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.2` | `BRS-REQ-071`  | KPI-002 Delayed Work computes total active tasks exceeding Current Approved Planned Date in real time.                                      | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.3` | `BRS-REQ-071`  | KPI-003 Upcoming Shoots lists and counts all shoots scheduled within the next 7 calendar days.                                              | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.4` | `BRS-REQ-071`  | KPI-004 Upcoming Publishing lists and counts deliverables scheduled for publication within 7 calendar days.                                 | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.5` | `BRS-REQ-071`  | KPI-005 Pending Approvals computes pending review decisions across all four review gates.                                                   | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.6` | `BRS-REQ-071`  | KPI-006 Editor Workload computes active editing tasks distributed per editor.                                                               | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.7` | `BRS-REQ-071`  | KPI-007 Performance Pending Work counts Actual Publication events with outstanding metric obligations.                                      | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-072.1` | `BRS-REQ-072`  | KPI-008 Employee Productivity aggregates production tasks completed per employee over monthly periods.                                      | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-072.2` | `BRS-REQ-072`  | KPI-009 Manager Productivity aggregates review decisions processed by Marketing Manager over monthly periods.                               | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-072.3` | `BRS-REQ-072`  | KPI-010 Tasks Completed counts each Content ID once upon initial transition to Completed status.                                            | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-072.4` | `BRS-REQ-072`  | KPI-011 Tasks Cancelled aggregates content deliverables cancelled prior to completion over monthly periods.                                 | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-073.1` | `BRS-REQ-073`  | KPI-012 Published Content counts distinct Content IDs with >=1 live Actual Publication event.                                               | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.2` | `BRS-REQ-073`  | KPI-013 Reels Produced computes Reel Planned Outputs produced and approved, excluding reposts.                                              | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.3` | `BRS-REQ-073`  | KPI-014 Photography Produced computes Photography Planned Outputs produced and approved, excluding reposts.                                 | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.4` | `BRS-REQ-073`  | KPI-015 Publication Distribution reports distribution of live Actual Publication events by Platform/Channel.                                | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-066.1` | `BRS-REQ-066`  | Employee self-service workspace displays record-level data exclusively for work assigned to or participated in by that employee.            | `SRS-REQ-066`                | Covered         | Verified Normative Coverage |
| `AC-066.2` | `BRS-REQ-066`  | Workspace includes assigned tasks, status, deadlines, delay indicators, feedback, links, personal workload, and own Marks.                  | `SRS-REQ-066`                | Covered         | Verified Normative Coverage |
| `AC-067.1` | `BRS-REQ-067`  | Employee workspace strictly conceals peer operational queues, unassigned work, peer Marks, peer performance, rankings, and leaderboards.  | `SRS-REQ-067`                | Covered         | Verified Normative Coverage |
| `AC-067.2` | `BRS-REQ-067`  | System contains zero compensation, incentive payout, or payroll processing functionality.                                                   | `SRS-REQ-067`                | Covered         | Verified Normative Coverage |
| `AC-068.1` | `BRS-REQ-068`  | System reports Employee personal performance strictly across five approved operational measures.                                            | `SRS-REQ-068`                | Covered         | Verified Normative Coverage |
| `AC-068.2` | `BRS-REQ-068`  | Personal performance measures are computed strictly from verified deliverable records and own Marks.                                       | `SRS-REQ-068`                | Covered         | Verified Normative Coverage |
| `AC-069.1` | `BRS-REQ-069`  | Viewing team-wide task assignments and workload summaries requires CEO, Marketing Manager, or Permission #14.                              | `SRS-REQ-069`                | Covered         | Verified Normative Coverage |
| `AC-069.2` | `BRS-REQ-069`  | Team workload views display aggregated operational assignment counts without exposing compensation or ranking data.                         | `SRS-REQ-069`                | Covered         | Verified Normative Coverage |
| `AC-070.1` | `BRS-REQ-070`  | Viewing team-wide aggregated operational KPIs requires CEO, Marketing Manager, or Permission #15.                                           | `SRS-REQ-070`                | Covered         | Verified Normative Coverage |
| `AC-070.2` | `BRS-REQ-070`  | Team KPI views display aggregated metrics without individual ranking or performance comparison features.                                    | `SRS-REQ-070`                | Covered         | Verified Normative Coverage |
| `AC-071.1` | `BRS-REQ-071`  | System captures and calculates Pending Work KPI (count of active pre-completion deliverables).                                              | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.2` | `BRS-REQ-071`  | System captures and calculates Delayed Work KPI (count of active deliverables where Current Date > Target Date).                            | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.3` | `BRS-REQ-071`  | System captures and calculates Upcoming Shoots KPI (count of deliverables scheduled for shoot in rolling window).                          | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.4` | `BRS-REQ-071`  | System captures and calculates Upcoming Publishing KPI (count of deliverables scheduled for publishing in rolling window).                  | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.5` | `BRS-REQ-071`  | System captures and calculates Pending Approvals KPI (count of deliverables awaiting review across all 4 gates).                           | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.6` | `BRS-REQ-071`  | System captures and calculates Completed Deliverables KPI (count of deliverables reaching Completed status in period).                      | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-071.7` | `BRS-REQ-071`  | System captures and calculates Cancelled Deliverables KPI (count of deliverables reaching Cancelled status in period).                      | `SRS-REQ-071`                | Covered         | Verified Normative Coverage |
| `AC-072.1` | `BRS-REQ-072`  | System calculates Shoots Completed per Cameraperson productivity metric.                                                                    | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-072.2` | `BRS-REQ-072`  | System calculates Edits Completed per Editor productivity metric.                                                                           | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-072.3` | `BRS-REQ-072`  | System calculates Output Assets Produced productivity metric by output type.                                                                | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-072.4` | `BRS-REQ-072`  | System calculates Cumulative Contributor Marks Earned per qualifying role.                                                                  | `SRS-REQ-072`                | Covered         | Verified Normative Coverage |
| `AC-073.1` | `BRS-REQ-073`  | System calculates Total Planned Outputs across deliverables.                                                                                | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.2` | `BRS-REQ-073`  | System calculates Planned Photography Count across deliverables.                                                                            | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.3` | `BRS-REQ-073`  | System calculates Planned Reels Count across deliverables.                                                                                  | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.4` | `BRS-REQ-073`  | System calculates Planned Videos Count across deliverables.                                                                            | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.5` | `BRS-REQ-073`  | System calculates Total Actual Publications count.                                                                                          | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.6` | `BRS-REQ-073`  | System calculates Original Publications Count.                                                                                              | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.7` | `BRS-REQ-073`  | System calculates Repost Publications Count.                                                                                                | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.8` | `BRS-REQ-073`  | System calculates Target N/A Exceptions Count.                                                                                              | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-073.9` | `BRS-REQ-073`  | System calculates Publications by Channel distribution.                                                                                     | `SRS-REQ-073`                | Covered         | Verified Normative Coverage |
| `AC-074.1` | `BRS-REQ-074`  | System calculates Idea Approval Rate (% of evaluated ideas approved).                                                                       | `SRS-REQ-074`                | Covered         | Verified Normative Coverage |
| `AC-074.2` | `BRS-REQ-074`  | System calculates Shoot First-Pass Approval Rate (% of shoots approved without rework).                                                     | `SRS-REQ-074`                | Covered         | Verified Normative Coverage |
| `AC-074.3` | `BRS-REQ-074`  | System calculates Edit First-Pass Approval Rate (% of edits approved without rework).                                                       | `SRS-REQ-074`                | Covered         | Verified Normative Coverage |
| `AC-074.4` | `BRS-REQ-074`  | System calculates Review Turnaround Time across all 4 review gates.                                                                         | `SRS-REQ-074`                | Covered         | Verified Normative Coverage |
| `AC-075.1` | `BRS-REQ-075`  | System calculates Shoot Delay Rate (% of shoots completing after approved shoot date).                                                      | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-075.2` | `BRS-REQ-075`  | System calculates Edit Delay Rate (% of edits completing after approved edit date).                                                         | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-075.3` | `BRS-REQ-075`  | System calculates Publishing Delay Rate (% of deliverables publishing after approved live date).                                            | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-075.4` | `BRS-REQ-075`  | System calculates Scorecard Entry Delay Rate (% of scorecards submitted after Performance Due Date).                                       | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-075.5` | `BRS-REQ-075`  | System calculates Average Days Delayed across delayed deliverables.                                                                         | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-075.6` | `BRS-REQ-075`  | System calculates On-Time Shoot Completion Rate (% of shoots completed on or before approved shoot date).                                    | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-075.7` | `BRS-REQ-075`  | System calculates On-Time Publishing Rate (% of deliverables published on or before approved live date).                                    | `SRS-REQ-075`                | Covered         | Verified Normative Coverage |
| `AC-076.1` | `BRS-REQ-076`  | System generates Administrative Action Reporting (Reschedules, Reassignments, Cancellations, Reopens, Holds, Resumes).                       | `SRS-REQ-076`                | Covered         | Verified Normative Coverage |
| `AC-076.2` | `BRS-REQ-076`  | System generates Permission Usage Reporting (grants, revocations, exercise frequency, failed attempts).                                     | `SRS-REQ-076`                | Covered         | Verified Normative Coverage |
| `AC-077.1` | `BRS-REQ-077`  | System is accessible via standard modern web browsers (Chrome, Edge, Firefox, Safari) without proprietary plugins.                          | `SRS-REQ-077`                | Covered         | Verified Normative Coverage |
| `AC-077.2` | `BRS-REQ-077`  | System renders responsively on desktop and laptop display resolutions.                                                                      | `SRS-REQ-077`                | Covered         | Verified Normative Coverage |
| `AC-078.1` | `BRS-REQ-078`  | System is designed for 24×7 operational availability with planned maintenance windows.                                                      | `SRS-REQ-078`                | Covered         | Verified Normative Coverage |
| `AC-078.2` | `BRS-REQ-078`  | System maintains data integrity across unplanned outages or connection losses.                                                              | `SRS-REQ-078`                | Covered         | Verified Normative Coverage |
| `AC-079.1` | `BRS-REQ-079`  | System supports 10–20 concurrent users with sub-second response times for standard operational transactions.                                | `SRS-REQ-079`                | Covered         | Verified Normative Coverage |
| `AC-079.2` | `BRS-REQ-079`  | System handles expected peak concurrency during business hours without performance degradation.                                             | `SRS-REQ-079`                | Covered         | Verified Normative Coverage |
| `AC-080.1` | `BRS-REQ-080`  | System architecture supports clean data export and migration readiness for future Business OS transition.                                  | `SRS-REQ-080`                | Covered         | Verified Normative Coverage |
| `AC-080.2` | `BRS-REQ-080`  | All core data entities are structured with standard relational models supporting full export.                                               | `SRS-REQ-080`                | Covered         | Verified Normative Coverage |
| `AC-081.1` | `BRS-REQ-081`  | Authorized users can export operational data, deliverables, metrics, and audit logs to standard formats (CSV, JSON).                        | `SRS-REQ-081`                | Covered         | Verified Normative Coverage |
| `AC-081.2` | `BRS-REQ-081`  | Data export operations enforce role-based and permission-based scoping boundaries.                                                         | `SRS-REQ-081`                | Covered         | Verified Normative Coverage |
| `AC-082.1` | `BRS-REQ-082`  | System operates standalone without mandatory real-time dependencies on third-party social media platform APIs.                              | `SRS-REQ-082`                | Covered         | Verified Normative Coverage |
| `AC-082.2` | `BRS-REQ-082`  | The MVP explicitly excludes external API integrations within the approved scope.                                                            | `SRS-REQ-082`                | Covered         | Verified Normative Coverage |
| `AC-083.1` | `BRS-REQ-083`  | Users cannot directly edit or overwrite workflow status.                                                                                    | `SRS-REQ-083`                | Covered         | Verified Normative Coverage |
| `AC-083.2` | `BRS-REQ-083`  | Status changes occur only through defined authorized actions, decisions, or system events.                                                  | `SRS-REQ-083`                | Covered         | Verified Normative Coverage |
| `AC-083.3` | `BRS-REQ-083`  | Workflow statuses cannot be skipped through manual status manipulation.                                                                     | `SRS-REQ-083`                | Covered         | Verified Normative Coverage |
| `AC-084.1` | `BRS-REQ-084`  | Given status is SIP or ED, CEO/MM may execute Hold; primary status and assignee(s) remain unchanged, work visibly flagged On Hold.          | `SRS-REQ-091`                | Covered         | Verified Normative Coverage |
| `AC-084.2` | `BRS-REQ-084`  | Hold requires mandatory non-empty reason and logs actor, timestamp, Content/workflow ID, and state; Employee Hold rejected.                  | `SRS-REQ-091`                | Covered         | Verified Normative Coverage |
| `AC-084.3` | `BRS-REQ-084`  | CEO/MM may Resume open Hold; system logs resumed by actor & timestamp; status and assignee remain unchanged, work restored.                  | `SRS-REQ-091`                | Covered         | Verified Normative Coverage |
| `AC-084.4` | `BRS-REQ-084`  | Hold does not auto-change approved execution date; changed execution date uses Reschedule governance; Performance Due Date non-reschedulable.| `SRS-REQ-091`                | Covered         | Verified Normative Coverage |
| `AC-085.1` | `BRS-REQ-085`  | Exactly 17 Business Roles seeded (`CEO`→`CEO_OWNER`, `Marketing Manager`→`MARKETING_MANAGER`, all others→`EMPLOYEE`); catalogue expandable; new ordinary Roles default to `EMPLOYEE`. | `SRS-REQ-092`                | Covered         | Verified Normative Coverage |
| `AC-085.2` | `BRS-REQ-085`  | A Business Role name grants no Operational Permission; authorization evaluates internal access class + active operational permissions.       | `SRS-REQ-092`                | Covered         | Verified Normative Coverage |
| `AC-085.3` | `BRS-REQ-085`  | Business Role create/change/deactivate and user assignment are CEO-exclusive and immutably audited; referenced Roles deactivated, not deleted.| `SRS-REQ-092`                | Covered         | Verified Normative Coverage |
| `AC-085.4` | `BRS-REQ-085`  | No fourth internal access class; every `EMPLOYEE`-class Business Role inherits Employee privacy, self-review, delegation and scope rules.    | `SRS-REQ-092`                | Covered         | Verified Normative Coverage |
| `AC-086.1` | `BRS-REQ-086`  | Planning Mode is exactly Standard or Urgent; not a Priority, status, permission or review gate; one Planning workflow/form.                  | `SRS-REQ-093`                | Covered         | Verified Normative Coverage |
| `AC-086.2` | `BRS-REQ-086`  | Standard keeps the −5/−2 default; Urgent requires manual Shoot/Edit dates and a mandatory non-empty Urgency Reason before Planning Review.   | `SRS-REQ-093`                | Covered         | Verified Normative Coverage |
| `AC-086.3` | `BRS-REQ-086`  | Planned Live Date <5 calendar days from current business date requires Urgent; exactly 5 permits Standard; past date invalid; Urgent may be chosen at 5+. | `SRS-REQ-093`                | Covered         | Verified Normative Coverage |
| `AC-086.4` | `BRS-REQ-086`  | Approved Urgent dates become the governed planned dates; later changes use Reschedule governance; Urgent is not automatically Delayed.       | `SRS-REQ-093`                | Covered         | Verified Normative Coverage |
| `AC-086.5` | `BRS-REQ-086`  | Urgency Reason is persisted and auditable; Planning Mode and Content Priority remain independent fields.                                     | `SRS-REQ-093`                | Covered         | Verified Normative Coverage |
| `AC-086.6` | `BRS-REQ-086`  | Under Urgent, same-day Shoot/Edit accepted; system enforces only `Planned Shoot Date ≤ Planned Edit Date ≤ Planned Live Date`.               | `SRS-REQ-093`                | Covered         | Verified Normative Coverage |

---

## Appendix C: SRS Requirement Inventory & Verification Metrics

| Metric                                   | Value | Verification Basis                                                                      |
| :--------------------------------------- | :---- | :-------------------------------------------------------------------------------------- |
| **Total SRS Requirements**               | 93    | Atomic Functional Software Requirements Baseline (`SRS-REQ-001`..`093`; R3.4 candidate) |
| **Total Mapped BRS Requirements**        | 86    | 100% Traceability to BRS v1.1.0 (`BRS-REQ-001`..`086`; R3.4 candidate)                  |
| **Total Mapped Acceptance Criteria**     | 214   | 100% Unique Authoritative AC Inventory (`AC-001.1`..`AC-086.6`; R3.4 candidate)         |
| **Acceptance Criteria Covered**          | 214   | 100% Normative Implementation Coverage                                                  |
| **Acceptance Criteria Review Required**  | 0     | All 214 ACs fully satisfied by normative SRS requirements (204 R3.3 + 10 R3.4: `AC-085.1`–`.4`, `AC-086.1`–`.6`) |
| **Orphan SRS Requirements**              | 0     | Every SRS requirement has an explicit upstream BRS source                               |
| **Invalid AC References**                | 0     | Nonexistent ACs (`AC-026.3`, `AC-033.4`, `AC-033.5`, `AC-037.4`, `AC-037.5`) eliminated |
| **Source Priority Mismatches**           | 0     | 100% Exact Copy of BRS Priorities across `SRS-REQ-001`..`093` and decomposed items      |
| **Semantic BRS->SRS Mismatches**         | 0     | 100% Semantic Translation Fidelity                                                      |
| **Source Clarifications (now Resolved)** | 0     | Both prior clarifications RESOLVED Aug 11, 2026 (`SC-REQ-001` → zero-denominator = N/A; `SC-REQ-002` → draft lifecycle) |
| **Downstream Design Decisions**          | 8     | Items deferred to technical architecture / UI / API design (`DDD-001`..`DDD-008`)       |
| **Governed Internal Access Classes**     | 3     | `CEO_OWNER`, `MARKETING_MANAGER`, `EMPLOYEE` (security classes — not organizational Business Roles) |
| **Seeded Business Roles (catalogue)**    | 17    | Expandable organizational designations resolving to the 3 internal access classes (`SRS-REQ-092`, R3.4) |
| **Governed CEO Operational Permissions** | 17    | Permission #1 through Permission #17                                                    |
| **Governed Workflow Statuses & Flags**   | 22    | 17 Active, 1 Dormant, 2 Terminal, 1 Closed / Reopenable, 1 Delayed Flag                 |
| **Governed System Invariants**           | 10    | Strict Business & Software Constraints                                                  |
| **Authoritative Scorecard Formulas**     | 3     | Hook Rate, Hold Rate, CTR 100% Exact                                                    |

---

## Appendix D: Glossary & Controlled Terminology

| Term                          | Definition                                                                                                                                | Source Baseline         |
| :---------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------- | :---------------------- |
| **Content ID**                | Unique monthly format `C-MMYY-NNNN` assigned to approved ideas upon entry to Stage 3 Planning.                                            | BFD v1.5.0 / BRS v1.1.0 |
| **Category**                  | Optional manually entered free-text planning attribute (single field, supporting one or multiple category values as typed by the user, or left blank when undecided; not selected from a reference list and not backed by catalogue reference data). | BFD v1.5.0 / BRS v1.1.0 |
| **Predefined Role Marks**     | Controlled values `[0, 0.5, 1.0, 2.0, 3.0]` established at Idea Approval for Cameraperson and Editor roles.                               | BFD v1.5.0 / BRS v1.1.0 |
| **Personal Mark Attribution** | Award of full predefined Mark to qualifying final Camerapersons (Shoot Approval) and Editors (Edit Approval) without splitting/averaging. | BFD v1.5.0 / BRS v1.1.0 |
| **Hold / Resume**             | Administrative operational actions allowing CEO / Owner or Marketing Manager to temporarily pause (`Hold`) or unpause (`Resume`) work currently in *Shoot In Progress* or *Editing* state without changing primary workflow status, assignee(s), or Content ID identity. | BFD v1.5.0 / BRS v1.1.0 |
| **Performance Due Date**      | System-derived date = `Actual Publication Date + 2 calendar days` (non-reschedulable).                                                    | BFD v1.5.0 / BRS v1.1.0 |
| **Hook Rate**                 | Scorecard metric = `(3-second views / Plays) * 100`.                                                                                      | BFD v1.5.0 / BRS v1.1.0 |
| **Hold Rate**                 | Scorecard metric = `(Average watch time / Video length) * 100`.                                                                           | BFD v1.5.0 / BRS v1.1.0 |
| **CTR**                       | Scorecard metric = `(Link clicks / Impressions) * 100`.                                                                                   | BFD v1.5.0 / BRS v1.1.0 |

---

## 14. Change Control & Maintenance Lifecycle

Changes to business intent, scope, workflow, roles, permissions, governed statuses, Marks, KPIs, Business Rules, source priorities, Acceptance Criteria, or other upstream business requirements must originate through the applicable BFD/BRS change-control process and then be synchronized through RTM and SRS. SRS-only technical clarifications that do not alter upstream business intent may be controlled within the SRS and applicable downstream technical artifacts, while preserving full traceability to the approved upstream baseline. The SRS shall not override the BFD, BRS, or RTM.
