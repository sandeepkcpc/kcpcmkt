# UI/UX Design Specification — v0.2 (Deferred Screen Sets)

**Document ID:** `KCPC-MKT-UIUX-001`
**Document Title:** UI/UX Design Specification — v0.2 (Deferred Screen Sets — Administration, Reporting, Catalogue, Audit & Export)
**Project Name:** Content Production Lifecycle MVP
**Client:** KCPC Bandhani
**Version:** `0.2.2`
**Status:** Draft — Additive Companion to v0.1; frontend architecture retargeted under Candidate Successor **R3.5** to Spring MVC + JSP server-rendered pages (`KCPC-MKT-CR-R3.5-001`) — CANDIDATE, PENDING INDEPENDENT RE-REVIEW (not frozen). **Administration, reporting, catalogue, audit and export screen behaviour is unchanged from R3.4.**
**Classification:** Confidential — Internal Use Only
**Created Date:** August 12, 2026
**Last Modified Date:** August 13, 2026

---

## 1. Document Control & Governance

### 1.1 Relationship to the Frozen v0.1 Baseline

This is an **additive companion volume** to the UI/UX Design Specification. It specifies the ten screen sets that v0.1 (`KCPC-MKT-UIUX-001 v0.1`) explicitly deferred in its §14. Governance state: **R3.2 and R3.3 are superseded**; **R3.4 is the current frozen implementation baseline** (`BASELINE_FREEZE_R3.4.md`), and this companion **at v0.2.1 entered that frozen baseline as file #9** — R3.3 §6 had recorded it as an out-of-baseline companion that would join through change management, and R3.4 was that change-management event. **R3.5 is the current candidate successor** (this pass, `KCPC-MKT-CR-R3.5-001`), which retargets the frontend architecture only. Baseline membership and stakeholder acceptance remain distinct: **UX and stakeholder review of this companion is still pending.** It **does not modify, supersede, or reopen frozen v0.1**. Read together, v0.1 (MVP core production flow) and this v0.2 (administration, reporting, catalogue, audit, export) form the complete UI/UX specification for the MVP.

Where a convention is already established in frozen v0.1 — the design principles (§4), the low-fidelity component system (§6), the common interaction patterns (§7), the global states (§10), and the responsive/accessibility requirements (§11) — this volume **reuses** it by reference rather than restating it. Only additions and screen-specific deltas are specified here.

This volume introduces **no** new business rule, workflow status, permission, field, or KPI. Every screen traces to requirements and API operations governed by the current source baseline as advanced under the R3.5 candidate package (SRS v0.3 frozen in R3.4 and unchanged; API v0.4.0, ERD v0.4, RTM v0.4); the only genuinely new capability introduced downstream here is the Business Role Catalogue Administration screen (§6.4), which traces to `SRS-REQ-092` / `API-OP-071..073`.

### 1.2 Upstream Source Baselines

| Baseline ID | Document | Version | Role in this Spec |
| :--- | :--- | :---: | :--- |
| `KCPC-MKT-BFD-001` | Business Foundation Document (BFD) | `v1.5.0` | Roles, 17-permission model, KPI catalogue, privacy invariants |
| `KCPC-MKT-BRS-001` | Business Requirements Specification (BRS) | `v1.1.0` | Business requirements & acceptance criteria |
| `KCPC-MKT-SRS-001` | Software Requirements Specification (SRS) | `v0.3` | Screen behaviour, validation, role/data scoping |
| `KCPC-MKT-API-001` | API Specification (API) | `v0.4.0` | Screen ↔ endpoint bindings, DTOs, error envelope |
| `KCPC-MKT-ERD-001` | Entity Relationship Diagram & Data Dictionary (ERD) | `v0.4` | Field names, enumerations, constraints |
| `KCPC-MKT-RTM-001` | Requirements Traceability Matrix (RTM) | `v0.4` | Requirement coverage cross-reference |
| `KCPC-MKT-UIUX-001` | UI/UX Design Specification (v0.1) | `v0.1.2` | Core-flow baseline (R3.5-retargeted); conventions reused here |

### 1.3 Revision History

| Version | Date | Author / Role | Description of Changes | Reviewed By |
| :---: | :---: | :--- | :--- | :--- |
| **0.2** | August 12, 2026 | UX/Product Design (Development Review Assistant) | Additive companion to frozen v0.1: low-fidelity specification of the ten deferred screen sets — User & Base-Role Administration, Operational-Permission Administration, Master-Catalogue Management, Team Workload, Team KPI, 30-KPI Reporting, Administrative-Action Report, Delayed-Deliverables Report, Audit-History Viewer, and Multi-Format Export. No business-model change; all screens trace to existing SRS/API. | Pending UX & Stakeholder Review |
| **0.2.1** | August 12, 2026 | UX/Product Design (Development Review Assistant) | Candidate Successor **R3.4** business-change amendment (candidate, not frozen): reframed the User Administration screen from "Base-Role" assignment to **Business Role** selection (with the resolved internal access class shown read-only) per `SRS-REQ-004/092`; added the CEO-exclusive **§6.4 Business Role Catalogue Administration** screen bound to `API-OP-071/072/073`; added the Business Roles entry to navigation (§5) and the traceability matrix (§9). No change to the three internal access classes. **Closure pass (Aug 12, 2026):** reworked the User Administration list/filter to use the Business Role catalogue and resolved access class, and corrected §9 from "Platform→Channel→Target" to independent Platform & Company Channel masters joined by a Publication Target. Pending final independent re-audit. **CANDIDATE — PENDING INDEPENDENT REVIEW.** | Pending UX & Stakeholder Review |
| **0.2.2** | August 13, 2026 | UX/Product Design (Solution Architecture Team) | Candidate Successor **R3.5** technical frontend-architecture retargeting (`KCPC-MKT-CR-R3.5-001`): server-rendered Spring MVC + JSP replaces the React SPA assumption (`SAD-ADR-006`). Upstream references advanced to SAD v0.4 / ERD v0.4 / API v0.4.0 / RTM v0.4 / UIUX v0.1.2. **No administration, reporting, catalogue, audit or export screen behaviour is changed**; Business Role Catalogue administration (§6.4) and User Administration (§6.1) are behaviourally identical. **No business change. CANDIDATE — NOT FROZEN.** | Pending |

---

## 2. Purpose, Scope & Delta

### 2.1 In Scope (this v0.2 companion)

The ten deferred screen sets, grouped for delivery:

**Group A — Operational Governance (write screens):**
1. User & Business Role Administration — CEO-exclusive (`SRS-REQ-003/004/005/092`; `API-OP-004/005/006`). Business Role catalogue administration is specified in §6.4 (`API-OP-071/072/073`).
2. Operational-Permission Administration — CEO-exclusive grant / scope / revoke / modify-expire (`SRS-REQ-006/008/009/010/011/013`; `API-OP-007/008/009/065`).
3. Master-Catalogue Management — Permission #17 (`SRS-REQ-060/061`; `API-OP-036/066/067/068/069/070`).

**Group B — Analytics & Reporting (read screens):**
4. Team Workload Dashboard — Permission #14 (`SRS-REQ-069`; `API-OP-056`).
5. Team KPI Dashboard — Permission #15 (`SRS-REQ-070`; `API-OP-057`).
6. 30-KPI Reporting Console — Permission #15 (`SRS-REQ-071..075`; `API-OP-058`).
7. Administrative-Action & Permission-Usage Report — Permission #16 (`SRS-REQ-076`; `API-OP-059`).
8. Delayed-Deliverables Report — all roles, scoped (`SRS-REQ-075`; `API-OP-060`).
9. Audit-History Viewer — Permission #16 (`SRS-REQ-063/064/065`; `API-OP-061`).

**Group C — Data:**
10. Multi-Format Export — CEO / Marketing Manager (`SRS-REQ-080/081`; `API-OP-062`).

### 2.2 Out of Scope (remains in frozen v0.1)

The Stage 1–7 core production flow, authentication/landing, and Deliverable Detail + Timeline are fully specified in frozen v0.1 and are not restated here. Visual design tokens (colour, type, brand) remain deferred to the visual pass, per v0.1 §13. The Employee self-service personal-performance view (`API-OP-055`, 5 measures) is already covered by v0.1's landing (`§9.2`) and is referenced, not re-specified.

### 2.3 Audience

Frontend engineers (implementation), QA (acceptance & negative paths — especially permission gating and peer-privacy), product/stakeholders (review), backend engineers (confirming UI↔API alignment).

---

## 3. Source Hierarchy & Precedence

$$\text{BFD v1.5.0} \succ \text{BRS v1.1.0} \succ \text{RTM v0.4} \succ \text{SRS v0.3} \succ \text{SAD v0.4} \succ \text{ERD v0.4} \succ \text{API v0.4.0} \succ \text{UI/UX v0.1.2 (R3.5 candidate)} \succeq \mathbf{\text{UI/UX v0.2.2 (this companion, R3.5 candidate)}}$$

This companion sits at the same downstream tier as v0.1 and never overrides an upstream baseline. Any conflict is resolved in favour of the higher baseline, and this document is corrected. It prescribes presentation only; it introduces no governed behaviour.

---

## 4. Reused Global Conventions (by reference)

The following frozen-v0.1 sections apply unchanged to every screen in this volume and are **not** duplicated:

- **§4 UX Design Principles** — role-appropriate experience; no permission-teasers (controls absent, not disabled, when unauthorized); system-driven status (no manual status controls); no self-review decision by conflicted delegated reviewers; mandatory-reason gates; server-authoritative validation.
- **§6 Global Layout & Low-Fidelity Component System** — persistent left nav rail + top bar; table/list, form, and panel primitives; brand-neutral placeholders.
- **§7 Common Interaction Patterns** — confirm-with-reason dialogs; destructive/exception actions require a reason before the confirm button enables.
- **§10 Global States** — Loading / Empty / Error / No-permission presentation.
- **§11 Responsive & Accessibility** — keyboard operability, focus order, `aria-live` error announcement, required-field marking.

Two conventions are load-bearing for this volume and restated for emphasis:

1. **Permission-filtered rendering (`SRS-REQ-002/010`).** A screen or control the user is not authorized for is **absent** from the UI — never shown disabled as a teaser. Direct-URL access to an unauthorized route returns the standard `403` boundary screen (v0.1 §10).
2. **Peer-privacy boundary (`SRS-REQ-067`, `SRS-REQ-069`).** Where authorized, reporting screens **may** surface **operational workload information** — task distribution, active task counts, unassigned work, and stage capacity, including per-assignee active counts for load-balancing. They must **never** render **private peer performance**: peer Marks, performance ratings, identifiable performance comparisons, rankings, leaderboards, compensation, or payroll — regardless of the viewer's permissions. Only the authenticated user's **own** personal performance measures appear (via v0.1 §9.2).

---

## 5. Navigation & Route Additions

These extend v0.1 §5's reserved-but-deferred `/app/admin/*` and `/app/reports/*` nodes. All entries are **permission-filtered** (§4).

| Nav Entry | Route | Visible To | Governing Permission |
| :--- | :--- | :--- | :--- |
| Administration ▸ Users | `/app/admin/users` | CEO only | CEO-exclusive |
| Administration ▸ User Detail & Permissions | `/app/admin/users/:userId` | CEO only | CEO-exclusive |
| Administration ▸ Business Roles | `/app/admin/business-roles` | CEO only | CEO-exclusive |
| Publishing Catalogue | `/app/catalog` | CEO, MM, Employee w/ Perm #17 | `#17` |
| Reports ▸ Team Workload | `/app/reports/workload` | CEO, MM, Employee w/ Perm #14 | `#14` |
| Reports ▸ Team KPIs | `/app/reports/team-kpis` | CEO, MM, Employee w/ Perm #15 | `#15` |
| Reports ▸ KPI Console (30) | `/app/reports/kpis` | CEO, MM, Employee w/ Perm #15 | `#15` |
| Reports ▸ Administrative Actions | `/app/reports/admin-actions` | CEO, MM, Employee w/ Perm #16 | `#16` |
| Reports ▸ Delayed Deliverables | `/app/reports/delayed` | All roles (scoped) | native (scoped) |
| Audit History | `/app/audit` | CEO, MM, Employee w/ Perm #16 | `#16` |
| Export | `/app/export` | CEO, MM only | management-only |

> A nav group (e.g. "Administration", "Reports") renders only if the user is authorized for at least one entry within it.

---

## 6. Screen Specifications — Group A: Operational Governance (write)

### 6.1 User & Business Role Administration (CEO-Exclusive)

- **Route:** `/app/admin/users` (list) · `/app/admin/users/:userId` (detail) · **Primary API:** `API-OP-004` (list), `API-OP-005` (create), `API-OP-006` (update status/role) · **Access:** **Strictly `CEO_OWNER`.** MM and Employees have zero user-administration authority; the entire Administration group is absent from their navigation and any direct URL returns `403`.
```
+---------------------------------------------------------------+
| Administration ▸ Users                        [ + New User ]  |
|---------------------------------------------------------------|
| Search [_______]  Business Role [All ▾]  Access Class[All ▾] |
|---------------------------------------------------------------|
| Name       | Email         | Business Role   | Class  | Status |
| Rohit K.   | rohit@kcpc…   | CEO             | CEO_OWNER | Active|
| Aisha V.   | aisha@kcpc…   | Camera Person   | EMPLOYEE | Active[»]|
| Sunil M.   | sunil@kcpc…   | Video Editor    | EMPLOYEE | Deact. |
| Neha S.    | neha@kcpc…    | Marketing Manager | MARKETING_MANAGER | Active|
+---------------------------------------------------------------+

 Create User (CEO)                             [ + New User ]
+---------------------------------------------------------------+
| Full Name * [__________]   Email * [__________]              |
| Initial Password * [__________]                              |
| Business Role * [ Camera Person            ▾ ]  (catalogue)  |
|   ⓘ Access class (read-only): EMPLOYEE  (resolved from Role)  |
| Reason * [_________________________________]                 |
|   ⓘ Reason mandatory; Create blocked without it.             |
|                                   [ Create User ]           |
+---------------------------------------------------------------+

 User Detail — Aisha V.                        /app/admin/users/:id
+---------------------------------------------------------------+
| Full Name: Aisha V.        (read-only — not editable here)    |
| Email: aisha@kcpc…         (read-only — not editable here)    |
| Business Role [ Camera Person             ▾ ] (catalogue)     |
|   Access class (read-only): EMPLOYEE  (resolved from Role)     |
| Account Status  (•) Active   ( ) Deactivated                  |
| Reason * [_________________________________]                 |
|                                   [ Save Changes ]            |
| ⓘ Reason is mandatory; Save is blocked until entered. Every   |
|   status/role change is audit-logged with the reason.        |
|---------------------------------------------------------------|
| Granted Operational Permissions           → see §6.2          |
+---------------------------------------------------------------+
```
- **Components:** user list with **Business Role** / **access-class** / status filters (the Business Role filter is sourced from the `business_roles` catalogue; the access-class filter offers the three internal access classes) showing each user's Business Role and its resolved access class; **create-user form** (full name, email, **initial password**, **Business Role** from the catalogue, and a **mandatory creation reason** — the fields `API-OP-005` requires: `fullName`, `email`, `password`, `businessRoleId`, `creationReason`); **user-detail form** (**Business Role** selector, account-status toggle, **mandatory reason** → `statusReason`). The user's **internal access class is shown read-only**, resolved from the Business Role (`business_roles.access_class_code`) — it is not an arbitrary security-class selector (`SRS-REQ-092`). Full Name and Email are **read-only** on the detail screen — `API-OP-006` accepts only `isActive`, `businessRoleId`, and `statusReason`. A read-through to the permissions panel (§6.2) completes the screen. (R3.4 Change A: the former CEO/MM/Employee base-role radio is replaced by the expandable Business Role catalogue; see §6.4.)
- **Rules / Validation:** create/activate/deactivate and **Business Role** assignment are **exclusively CEO** (`SRS-REQ-003/004/092`). Create requires full name, a valid unique email, an initial password, and a **Business Role** selected from the catalogue (which resolves to exactly one of the three internal access classes — CEO_OWNER, MARKETING_MANAGER, EMPLOYEE — shown read-only). **A mandatory non-empty reason is required before saving any account-administration action** — creation, activation, deactivation, or Business Role change — and Save is blocked without it (`SRS-REQ-004/005`, `AC-005.1`). Deactivation is a soft status change (login blocked; historical records preserved). Changing a user's Business Role does not alter existing operational-permission grants (managed in §6.2).
- **Audit & Privacy:** every create, activation, deactivation, and role change writes an immutable audit entry capturing affected user, action, previous → new status/role, actor, timestamp (`SRS-REQ-005`; `API-OP-006` audit side-effect).
- **Trace:** `SRS-REQ-003/004/005` · `BRS-REQ-003/004/005` · `API-OP-004/005/006` · `ERD-TBL-001/003/025` · `BFD §3.1.1`.

### 6.2 Operational-Permission Administration (CEO-Exclusive)

- **Route:** `/app/admin/users/:userId` (Permissions panel) · **Primary API:** `API-OP-007` (catalogue), `API-OP-008` (grant), `API-OP-009` (revoke), `API-OP-065` (modify / expire) · **Access:** **Strictly `CEO_OWNER`.** Onward delegation is impossible — MM/Employees can never reach this screen (`SRS-REQ-011`).
```
+-----------------------------------------------------------------+
| Aisha V. — Operational Permissions             [ + Grant ]      |
|-----------------------------------------------------------------|
| # | Permission                 | Scope           | Valid | Act. |
| 2 | Planning Execution         | GLOBAL          | …-…   |  ✓   |
| 5 | Shoot Review               | STAGE (4)       | …-31/8|  ✓   |
| 9 | Performance Update         | ITEM C-0826-001 | …-…   |  ✓   |
|   [Modify/Expire]  [Revoke]                                     |
|-----------------------------------------------------------------|
| Grant Permission                                                |
|  Permission [#1..#17 ▼]   (17-item governed catalogue)         |
|  Scope  (•) Global  ( ) Stage-Restricted [stages…]             |
|         ( ) Item-Specific [Idea ID / Content ID]              |
|  Effective [YYYY-MM-DD]   Expires [YYYY-MM-DD | none]         |
|  Grant Reason * [_________________________]                    |
|                                   [ Grant ]                    |
+-----------------------------------------------------------------+
```
- **Components:** current-grants table (permission #, name, scope, validity window, active flag) with per-row **Modify/Expire** and **Revoke**; grant form (permission picker from the 17-item catalogue via `API-OP-007`, scope selector, effective/expiry dates, mandatory reason).
- **Scope model (`SRS-REQ-008`):** exactly three scope types — **Global**, **Stage-Restricted** (choose from workflow stages), **Item-Specific** (a specific Idea ID / Content ID). The selector adapts required inputs to the chosen scope.
- **Lifecycle & reasons:**
  - **Grant** (`API-OP-008`): mandatory **Grant Reason**; sets effective/expiry and scope. Confirm disabled until a non-empty reason is entered (§7 pattern).
  - **Modify / Expire** (`API-OP-065`): edits scope or effective-until, or expires the grant early; mandatory **Modification Reason**.
  - **Revoke** (`API-OP-009`): a **soft revoke** — the grant is retained with `revoked_at` set and `is_active = FALSE`; mandatory **Revoke Reason**. Revoked/expired grants remain visible (greyed, "Revoked/Expired") for provenance; they are never hard-deleted.
- **Rules / Validation:** strictly no Permission #18 — the catalogue is fixed at 17. Granting a permission never changes the user's underlying Business Role or its resolved internal access class (`SRS-REQ-006`). Real-time validity (active + within effective/expiry window + in scope) is enforced server-side at every exercise (`SRS-REQ-009`); the UI reflects, but does not substitute for, that check. Granting exposes only the specific screens/controls for those permissions (`SRS-REQ-010`).
- **Audit:** every grant, scope modification, expiry, revocation, and (server-side) denied attempt is immutably logged (`SRS-REQ-013`).
- **Trace:** `SRS-REQ-006/008/009/010/011/013` · `BRS-REQ-006/008..013` · `API-OP-007/008/009/065` · `ERD-TBL-004/005/034/035/036/025` · `BFD §6.6`.

### 6.3 Master-Catalogue Management — Platform / Channel / Target (Permission #17)

- **Route:** `/app/catalog` · **Primary API:** `API-OP-066/067` (Platform), `API-OP-036/068` (Channel), `API-OP-069/070` (Target); reads via `API-OP-034/035/037` · **Access:** CEO / MM native; Employee with **Permission #17**.
```
+-----------------------------------------------------------------+
| Publishing Catalogue (Perm #17)                                 |
| Two masters: Platforms · Company Channels                       |
| Publication Target = a Platform × Company Channel pairing       |
|-----------------------------------------------------------------|
| PLATFORMS                                 [ + Platform ]        |
|  • Instagram   • YouTube   • Moj   …            [Edit][Deact.] |
|-----------------------------------------------------------------|
| CHANNELS (Accounts)                       [ + Channel ]        |
|  • kcpcbandhani (Instagram)   • kcpc.english (YouTube)  …      |
|                                                 [Edit][Deact.] |
|-----------------------------------------------------------------|
| PUBLICATION TARGETS (Platform × Channel)  [ + Target ]        |
|  • Instagram · kcpcbandhani     • YouTube · kcpc.english  …   |
|                                                 [Config][Deact]|
|-----------------------------------------------------------------|
|  Any create / edit / deactivate → Reason * required.           |
+-----------------------------------------------------------------+
```
- **Components:** three linked lists reflecting the master-catalogue model — **Platforms** and **Company Channels / Accounts** are two *independent* master catalogues, and a **Publication Target** pairs one Platform with one Company Channel (`ERD-TBL-019` carries both `platform_id` and `channel_id`; a Channel is **not** structurally a child of a Platform). Create/edit forms per master; a target-configuration form that selects an existing active Platform and an existing active Channel; deactivate action per row (`SRS-REQ-060`).
- **Rules / Validation:** all create / update / deactivate actions require a mandatory **catalogue reason** (mirrors the API's `catalogueReason`). Deactivation is a **soft** state change (`is_active = FALSE`), never a hard delete — existing deliverables that reference a target retain their history. A Publication Target is the pairing of an existing active Platform and an existing active Channel (`API-OP-069`). Non-persistable display-only fields are not accepted (the API rejects them). Seed catalogue (initial business platforms and company channels) is present at go-live and maintained here.
- **Audit:** every Platform/Channel/Target creation, modification, status change, or deactivation is immutably logged with object type, identity, and reason (`SRS-REQ-061`).
- **Trace:** `SRS-REQ-060/061` · `BRS-REQ-060/061` · `API-OP-036/066/067/068/069/070` · `ERD-TBL-017/018/019/025` · `BFD §3.1.2, §5.9`.

---

### 6.4 Business Role Catalogue Administration (CEO-Exclusive — R3.4)

- **Route:** `/app/admin/business-roles` · **Primary API:** `API-OP-071` (list), `API-OP-072` (create), `API-OP-073` (update/deactivate); user assignment via `API-OP-005`/`API-OP-006` · **Access:** **Strictly `CEO_OWNER`** (part of the CEO-exclusive Administration group).
```
+-----------------------------------------------------------------+
| Administration ▸ Business Roles              [ + New Role ]     |
|  Filter: (•) Active  ( ) Inactive  ( ) All                     |
|---------------------------------------------------------------- |
| Business Role            | Access Class      | Active           |
| CEO                      | CEO_OWNER         | ✓ (seed)         |
| Marketing Manager        | MARKETING_MANAGER | ✓ (seed)         |
| Camera Person            | EMPLOYEE          | ✓                |
| Video Editor             | EMPLOYEE          | ✓  [Edit][Deact.]|
| …(17 seeded designations)…                                     |
|---------------------------------------------------------------- |
| Create Business Role                                            |
|  Role Name * [__________]                                      |
|  Access Class: EMPLOYEE (fixed for ordinary roles; read-only)  |
|  Reason * [________________________]                           |
|                                   [ Create ]                   |
|  ⓘ A Role name never grants a permission. Ordinary roles map   |
|    to EMPLOYEE. Deactivate (never delete) referenced roles.    |
+-----------------------------------------------------------------+
```
- **Components:** Business Role list (name, resolved access class, active flag) with active/inactive filter and per-row **Edit**/**Deactivate**; create-role form (name + mandatory reason; access class fixed to `EMPLOYEE` for ordinary roles and shown read-only).
- **Rules / Validation (`SRS-REQ-092`):** create/deactivate and user assignment are **CEO-exclusive**; role name unique; ordinary new roles resolve to `EMPLOYEE` (the access-class binding is fixed at creation and not re-assignable here — no silent privilege change; no fourth access class). Deactivation is a **soft** state change; a role referenced by users/audit cannot be deactivated until its users are reassigned, and is never destructively deleted. A Business Role name never grants an Operational Permission. Every create/change/deactivate/assignment is audit-logged.
- **Assignment:** a user's Business Role is set on the User screen (§6.1) via `businessRoleId`; the resolved access class is displayed read-only there.
- **Trace:** `SRS-REQ-092` · `BRS-REQ-085` · `API-OP-071/072/073` (+`005`/`006`) · `ERD-TBL-044/001/003/025` (`ERD-CON-063`) · `BFD §3.1, BR-064`.

---

## 7. Screen Specifications — Group B: Analytics & Reporting (read)

> **Privacy invariant for all of Group B (`SRS-REQ-067`, `SRS-REQ-069`):** authorized **operational** information (task distribution, active task counts, unassigned work, stage capacity — including per-assignee active counts) may be shown where the permission allows; **private peer performance** — peer Marks, performance ratings, identifiable performance comparisons, rankings, leaderboards, and financial/compensation data — is never rendered, regardless of permissions. An Employee viewing under a delegated visibility permission sees exactly the authorized operational/aggregate view — never a drill-through to a named peer's private performance.

### 7.1 Team Workload Dashboard (Permission #14)

- **Route:** `/app/reports/workload` · **Primary API:** `API-OP-056` · **Access:** CEO / MM native; Employee with **Permission #14**.
```
+-----------------------------------------------------------------+
| Team Workload (Perm #14)          Period [This week ▼]          |
|-----------------------------------------------------------------|
| Active tasks by stage:  Planning 6 · Shoot 4 · Edit 5 · Pub 3   |
| Assignee load (active counts, no ratings):                      |
|   Cameraperson A ▓▓▓▓ 4    Editor B ▓▓▓ 3    …                  |
| Delay badges: 2 deliverables flagged DLY                        |
|  ⓘ Counts only — no performance scores, Marks, or rankings.     |
+-----------------------------------------------------------------+
```
- **Components:** stage-distribution summary; per-assignee **active task counts** and delay badges (load-balancing view); period filter (maps to `API-OP-056` `startDate`/`endDate`, optional `stage`).
- **Rules / Privacy:** shows candidate active task counts and delay indicators to support human load-balancing; **withholds** peer performance ratings, peer Marks, rework rates, and personal metrics (`SRS-REQ-069`, `SRS-REQ-067`). No assignment automation is offered — assignment stays a human action (per frozen v0.1 and `SRS-REQ-040`).
- **Trace:** `SRS-REQ-069` (+`067`) · `BRS-REQ-069` · `API-OP-056` · `ERD-VW-001`, `ERD-TBL-013/014` · `BFD §6.6`.

### 7.2 Team KPI Dashboard (Permission #15)

- **Route:** `/app/reports/team-kpis` · **Primary API:** `API-OP-057` · **Access:** CEO / MM native; Employee with **Permission #15**.
```
+-----------------------------------------------------------------+
| Team KPIs (Perm #15)              Period [This month ▼]         |
|-----------------------------------------------------------------|
| Workflow totals · Completion rate · Delay total · On-time %     |
|   Completed 42   In-progress 18   Completion 70%   On-time 88%  |
|  ⓘ Department-level aggregates only. No individual breakdowns.   |
+-----------------------------------------------------------------+
```
- **Components:** department-level aggregate KPI tiles (workflow totals, completion rates, delay totals, on-time %); period filter (maps to `API-OP-057` `startDate`/`endDate`).
- **Rules / Privacy:** authorized **aggregate department-level** KPI reports only; omits individual performance breakdowns and peer Marks (`SRS-REQ-070`, `SRS-REQ-067`).
- **Trace:** `SRS-REQ-070` (+`067`) · `BRS-REQ-070` · `API-OP-057` · `ERD-VW-001` · `BFD §6.6`.

### 7.3 30-KPI Reporting Console (Permission #15)

- **Route:** `/app/reports/kpis` · **Primary API:** `API-OP-058` · **Access:** CEO / MM native; Employee with **Permission #15**.
```
+-----------------------------------------------------------------+
| KPI Console — KPI-001 … KPI-030      Period [__] Export [⇩]     |
|-----------------------------------------------------------------|
| Operational (KPI-001..007)   | Productivity (KPI-008..011)     |
| Content & Published (012..020)| Approval & Review (021..024)    |
| Delay/SLA/On-Time (025..030 & 90-day baseline comparison)       |
|  Each KPI: value + period; computed from operational tables.    |
|  ⓘ No persistent KPI tables — figures are derived on demand.    |
+-----------------------------------------------------------------+
```
- **Components:** the 30 governed KPIs grouped exactly as the SRS defines them — Operational `KPI-001..007` (`SRS-REQ-071`), Productivity `KPI-008..011` (`SRS-REQ-072`), Content & Published Unit `KPI-012..020` (`SRS-REQ-073`), Approval & Review `KPI-021..024` (`SRS-REQ-074`), Delay/SLA/On-Time `KPI-025..030` incl. the phased 90-day baseline comparison (`SRS-REQ-075`); period selector (maps to `API-OP-058` `startDate`/`endDate`, plus `category`/`kpiCode`); hand-off to Export (§8).
- **Rules:** KPIs are computed on demand from operational tables and `active_deliverable_delay_views` — there are no persistent KPI evaluation tables. Figures render read-only; no KPI is hand-entered. Peer-privacy (`SRS-REQ-067`) applies — the console shows governed aggregates, not per-employee private metrics.
- **Trace:** `SRS-REQ-071/072/073/074/075` (+`067`) · `BRS-REQ-071..075` · `API-OP-058` · `ERD-VW-001` · `BFD §6, KPI catalogue`.

### 7.4 Administrative-Action & Permission-Usage Report (Permission #16)

- **Route:** `/app/reports/admin-actions` · **Primary API:** `API-OP-059` · **Access:** CEO / MM native; Employee with **Permission #16**.
```
+-----------------------------------------------------------------+
| Administrative Actions Report (Perm #16)   Period [__] Type[All]|
|-----------------------------------------------------------------|
| Holds/Resumes · Reschedules · Reassignments · Cancellations     |
| Reopens · Ideas retained/reopened · Permission grants/revokes   |
|   count + reasons summarized per action type                    |
+-----------------------------------------------------------------+
```
- **Components:** management summary derived from audit logs: counts and reasons for holds, resumes, reschedules, reassignments, cancellations, reopens, retained/reopened ideas, and permission-administration events; period and action-type filters (map to `API-OP-059` `startDate`/`endDate`, `actionType`).
- **Rules:** a read-only management summary over immutable audit data (`SRS-REQ-076`); no action can be taken from this screen (reporting only).
- **Trace:** `SRS-REQ-076` · `BRS-REQ-076` · `API-OP-059` · `ERD-TBL-025/029/030/032/033/043` · `BFD §7.6`.

### 7.5 Delayed-Deliverables Report (All Roles, Scoped)

- **Route:** `/app/reports/delayed` · **Primary API:** `API-OP-060` · **Access:** all authenticated roles, **read-scoped**.
```
+-----------------------------------------------------------------+
| Delayed Deliverables              Stage[All]  Priority[All]     |
|-----------------------------------------------------------------|
| Content ID    | Stage | Days Delayed | Priority | Assignee(s)   |
| C-0826-0007   | Shoot | 3            | High     | (in scope)    |
|  ⓘ DLY is a supplementary flag; primary workflow status stands. |
+-----------------------------------------------------------------+
```
- **Components:** list of active deliverables flagged with the supplementary `DLY` indicator, with stage, days delayed, and priority; stage/priority filters (map to `API-OP-060` `stage`, `priority`).
- **Read-scope (`SRS-REQ-002/066/067`):** CEO / MM see the full department-wide projection; an **Employee** sees only delayed deliverables assigned to them or within the scope of an active operational permission — peer operational queues with no in-scope grant are concealed (mirrors the API's Employee read-scope rule on `API-OP-060`).
- **Rules:** `DLY` is a supplementary flag over the primary workflow status, not a distinct 23rd status; the primary status is unchanged. Read-only.
- **Trace:** `SRS-REQ-075` (delay KPIs) (+`002/066/067`) · `BRS-REQ-075` · `API-OP-060` · `ERD-VW-001` · `BFD §5.8`.

### 7.6 Audit-History Viewer (Permission #16)

- **Route:** `/app/audit` · **Primary API:** `API-OP-061` · **Access:** CEO / MM native; Employee with **Permission #16** (within authorized operational scope).
```
+-----------------------------------------------------------------+
| Audit History (Perm #16)     Date[__..__] Actor[__] Event[All]  |
|-----------------------------------------------------------------|
| Timestamp (IST) | Actor | Event Type        | Object    | …     |
| 2026-08-12 10:… | Rohit | PERMISSION_GRANTED | Aisha     | view  |
| 2026-08-12 09:… | Aisha | SCORECARD_SUBMITTED| C-0826-01 | view  |
|  ⓘ Read-only. Audit records are immutable — no edit/delete.     |
+-----------------------------------------------------------------+
```
- **Components:** filterable, paginated, **read-only** audit log (date range, actor, event type, object); row detail view.
- **Rules / Privacy:** viewing is restricted to CEO / MM and Employees holding **Permission #16**, within authorized operational scope (`SRS-REQ-065`). Records are strictly immutable — the UI exposes **no** edit or delete affordance for any user, including management (`SRS-REQ-063/064`). An Employee's view is limited to audit entries within their authorized scope; peer-private detail is not exposed beyond that scope.
- **Trace:** `SRS-REQ-063/064/065` · `BRS-REQ-063/064/065` · `API-OP-061` · `ERD-TBL-025` (+`ERD-CON-018/058` immutability) · `BFD §7.6`.

---

## 8. Screen Specifications — Group C: Data Export

### 8.1 Multi-Format Export (CEO / Marketing Manager)

- **Route:** `/app/export` · **Primary API:** `API-OP-062` · **Access:** **CEO / Marketing Manager only** (management export authority).
```
+-----------------------------------------------------------------+
| Data Export (Management)                                        |
|-----------------------------------------------------------------|
| Format  (•) JSON   ( ) CSV   ( ) XLSX                          |
| Scope   [x] Core operational records  [x] Workflow histories    |
|         [x] Performance metrics        [x] Audit log            |
|         (physical table union per RTM-081; retired TBL-040 excl)|
|                                   [ Generate Export ⇩ ]         |
| ⓘ Synchronous streaming download. No persistent export job.     |
+-----------------------------------------------------------------+
```
- **Components:** format selector (JSON / CSV / XLSX); scope checklist over the exportable physical table union; generate-and-download action.
- **Rules:** authorized **management users only** (CEO, MM) — the Export nav entry is absent for Employees (`SRS-REQ-081`). Export is a synchronous streaming archive over the physical table union per `RTM-081` (`ERD-TBL-007..039`, `ERD-TBL-041..043`), excluding retired `ERD-TBL-040`; there are no persistent export-job tables. Supports the temporary-MVP → future Business OS transition-readiness intent (`SRS-REQ-080`).
- **Trace:** `SRS-REQ-080/081` · `BRS-REQ-080/081` · `API-OP-062` · `ERD-TBL-007..043` · `BFD §5.10`.

---

## 9. Screen → Requirement Traceability Matrix (v0.2 screens)

| Screen | Route | Primary API Ops | Key SRS | Governed rules enforced in UI |
| :--- | :--- | :--- | :--- | :--- |
| User & Business Role Admin | `/app/admin/users` | `004`,`005`,`006` | `SRS-REQ-003/004/005/092` | CEO-exclusive; Business Role selection resolving to the 3 internal access classes; soft deactivate; all changes audited |
| Business Role Catalogue Admin | `/app/admin/business-roles` | `071`,`072`,`073` | `SRS-REQ-092` / `BRS-REQ-085` | CEO-exclusive; add/rename/activate/deactivate Business Roles; each maps to one access class; 17 seeded; role name never grants permission |
| Operational-Permission Admin | `/app/admin/users/:id` | `007`,`008`,`009`,`065` | `SRS-REQ-006/008/009/010/011/013` | CEO-exclusive; 17-catalogue; Global/Stage/Item scope; mandatory reasons; soft revoke; no onward delegation; audited |
| Master-Catalogue Management | `/app/catalog` | `036`,`066`,`067`,`068`,`069`,`070` | `SRS-REQ-060/061` | Perm #17; independent **Platform** & **Company Channel** masters joined by a **Publication Target** (Platform × Channel — not a hierarchy); mandatory reason; soft deactivate; audited |
| Team Workload | `/app/reports/workload` | `056` | `SRS-REQ-069` (+`067`) | Perm #14; active counts only; no peer perf/Marks |
| Team KPI | `/app/reports/team-kpis` | `057` | `SRS-REQ-070` (+`067`) | Perm #15; department aggregates only |
| 30-KPI Console | `/app/reports/kpis` | `058` | `SRS-REQ-071..075` (+`067`) | Perm #15; 30 KPIs by group; derived on demand; read-only |
| Admin-Action Report | `/app/reports/admin-actions` | `059` | `SRS-REQ-076` | Perm #16; audit-derived summary; read-only |
| Delayed Deliverables | `/app/reports/delayed` | `060` | `SRS-REQ-075` (+`002/066/067`) | All roles, read-scoped; DLY is a flag not a status |
| Audit-History Viewer | `/app/audit` | `061` | `SRS-REQ-063/064/065` | Perm #16; immutable; no edit/delete; scope-limited |
| Multi-Format Export | `/app/export` | `062` | `SRS-REQ-080/081` | CEO/MM only; JSON/CSV/XLSX; table union per RTM-081 |

This matrix maps the ten v0.2 screen sets; together with frozen v0.1 §12 (core-flow screens) it completes the UI→requirement coverage. These rows correspond to the 34 RTM §3.2 entries previously held as `TBD — Downstream Pending`; those RTM cells can now reference the routes above as the UI/UX artifacts are accepted.

---

## 10. Global States, Accessibility & Privacy Deltas

- **No-permission (whole screen):** unauthorized routes in this volume render the standard `403` boundary screen (v0.1 §10) — the nav entry was already absent (§4 Principle 1). Direct-URL attempts are logged as denied-permission attempts (`SRS-REQ-013`).
- **Empty:** each list/report shows a neutral empty state (e.g. "No delayed deliverables in this period") rather than a blank table.
- **Loading:** skeleton rows for tables and KPI tiles; primary actions disabled until data resolves.
- **Error:** the shared error envelope surfaces `403 PERM_DENIED` / `PERM_EXECUTIVE_ONLY`, `409` conflicts, and `422` validation inline or as a banner per v0.1 §10.
- **Accessibility (delta):** report tables carry column scopes and `aria-sort`; KPI tiles pair value with an accessible label (not colour alone), consistent with v0.1 §11 and the visual-pass colour discipline. Mandatory-reason dialogs mark the reason field `aria-required`.
- **Privacy (delta):** Group B screens must be built so peer-private fields are not present in the payload projection at all — not merely hidden in the DOM — reflecting the server-side peer-privacy enforcement (`SRS-REQ-067`).

---

## 11. Open UI/UX Items (for review — no invented resolutions)

These are presentation questions only; none blocks a governed rule, and none is resolved by invention here:

1. **KPI Console layout depth.** Whether the 30 KPIs render as five grouped tiles-with-drill or a single dense table is a visual-pass decision; both satisfy `SRS-REQ-071..075`.
2. **Catalogue deactivation confirmation copy.** The mandatory-reason dialog wording for deactivating a Platform/Channel/Target that is referenced by historical deliverables is a UX-writing item (the rule — soft deactivate, preserve history — is fixed).
3. **Export scope presets.** Whether to offer named scope presets (e.g. "Everything", "Performance only") in addition to the table-union checklist is a convenience decision; the governed scope is the full union per `RTM-081`.
4. **Audit viewer default window.** The default date range and page size for `/app/audit` is a usability default, deferred to the visual pass.

---

## 12. Change Control & Baseline Status

This companion volume builds on the current frozen source baseline **R3.3** (with R3.4 as the candidate successor). Excluding the R3.4 Change-A additions below (Business Role administration), it introduces no business rule, workflow status, permission, field, or KPI. It does not modify any frozen document; frozen UI/UX v0.1 remains the authoritative core-flow baseline. Every screen herein traces to an existing (or R3.4-added) SRS requirement and API operation. This companion is itself a **candidate** pending UX & stakeholder review; a designed screen is not an accepted/frozen screen.

This companion was held **out of baseline** at R3.2 and R3.3 — `BASELINE_FREEZE_R3.3.md` §6 records it as the out-of-baseline companion that would join *on acceptance, through change management*. **R3.4 was that event: v0.2.1 is file #9 of the frozen R3.4 manifest.** The present v0.2.2 is the **R3.5 candidate** successor. Baseline membership is **not** stakeholder acceptance: **UX and stakeholder review remains pending**, and the RTM's UI/UX cells for the ten screen sets remain `TBD — Downstream Pending` until the Test Plan stage, from which negative-path cases will be derived from the permission-gating and peer-privacy rules stated here.

Final approval remains pending formal UX and stakeholder review; no approval status is advanced by this draft.
