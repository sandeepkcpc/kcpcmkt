# Google Drive Implementation Report

**Scope:** inspection-only. No code, configuration, database, migration, or deployment files were
modified to produce this report. Every claim below is backed by a specific file/line reference
found in the repository at the time of inspection.

**Status legend** used throughout: `IMPLEMENTED`, `PARTIALLY IMPLEMENTED`, `NOT IMPLEMENTED`,
`CONFIGURATION REQUIRED`, `UNKNOWN / NOT VERIFIABLE`.

---

## 1. Executive Summary

Automatic per-Content-ID Google Drive folder provisioning is **fully implemented in code**
(`IMPLEMENTED`) — service layer, domain model, DB tracking table, idempotent retry, admin
relink/repair, and a substantial automated test suite all exist. However, the feature is
**inert everywhere this repository actually runs today** (`CONFIGURATION REQUIRED`), because it
is gated by a single flag (`app.drive.enabled`, sourced from the `DRIVE_ENABLED` environment
variable) that defaults to `false`, and no `.env`/deployment file found in this repository has
real Google service-account credentials filled in. Manual entry of "Asset Folder / Drive Link" is
the fallback and is what actually happens today unless that flag and 2–3 companion values are
supplied at deploy/runtime.

---

## 2. What Is Implemented

### 2.1 Master enable/disable switch — `IMPLEMENTED`
- **Where:** `DriveProperties.java` (`src/main/java/com/kcpc/mkt/drive/config/DriveProperties.java:11-39`),
  bound from `application.yml`'s `app.drive` block (`src/main/resources/application.yml:73-82`).
- **What:** `app.drive.enabled` (boolean, default `false`) is the single master switch.
  `DriveProvisioningService.isDriveIntegrationEnabled()` (`DriveProvisioningService.java:109-111`)
  just returns this flag; every other Drive code path checks it (directly or indirectly) before
  doing anything.
- **Data used:** environment variable `DRIVE_ENABLED` (see §7).

### 2.2 Google credentials handling — `IMPLEMENTED`
- **Where:** `DriveClientConfig.java` (`src/main/java/com/kcpc/mkt/drive/config/DriveClientConfig.java`),
  the `@Bean driveFolderClient(DriveProperties)` factory method (lines 31-113).
- **What it does, in order:**
  1. If `app.drive.enabled=false` → returns `DisabledDriveFolderClient` immediately (lines 34-36).
  2. If enabled but `app.drive.service-account-key` is blank → logs an `ERROR` and returns
     `DisabledDriveFolderClient` anyway (lines 38-46) — **enabling the flag alone, without a key,
     does not activate real Drive calls; it silently falls back to disabled.**
  3. Otherwise, accepts the service-account key value in **either** of two forms (lines 50-72):
     - a **file path** on the container filesystem (checked via `new File(...).exists()`) —
       documented as the production convention.
     - **raw JSON content** as a single string — documented as the local/dev convention (matches
       `docker-compose.yml`'s comment, §6).
  4. Builds `GoogleCredentials` scoped to `DriveScopes.DRIVE` (full Drive scope) via
     `GoogleCredentials.fromStream(...).createScoped(...)` (lines 75-77).
  5. If `app.drive.impersonate-user` is set, applies domain-wide-delegation impersonation via
     `credentials.createDelegated(...)` (lines 80-86).
  6. Builds a `com.google.api.services.drive.Drive` client and wraps it in `GoogleDriveFolderClient`
     (lines 89-101).
  7. **Any exception anywhere in this process** (bad JSON, network error building the transport,
     etc.) is caught, logged as an `ERROR`, and falls back to `DisabledDriveFolderClient` (lines
     104-112) — the application never fails to start over a Drive credential problem.
- **Library used:** `com.google.api.services.drive` (Drive API v3) + `com.google.auth.oauth2`
  (`GoogleCredentials`) — confirmed via imports at the top of `DriveClientConfig.java`.

### 2.3 Folder provisioning orchestration (idempotent) — `IMPLEMENTED`
- **Where:** `DriveProvisioningService.java` (`src/main/java/com/kcpc/mkt/drive/service/DriveProvisioningService.java`).
- **Key methods:**
  - `initiateProvisioning(ContentPlan plan)` (lines 119-125) — called from **within** the same
    transaction that creates the `ContentPlan`. Inserts a `NOT_STARTED` tracking row (cheap,
    no network call) and publishes a `ContentPlanCreatedEvent`.
  - `onContentPlanCreated(...)` (lines 127-130) — a `@TransactionalEventListener(phase =
    TransactionPhase.AFTER_COMMIT)` that fires **only after** the creating transaction commits,
    and calls `provision(...)` through a `@Lazy` self-reference (so the `@Transactional` proxy is
    actually honored — a documented, deliberate workaround, see the class's own comment at
    lines 62-67).
  - `provision(UUID contentPlanId)` (lines 223-274) — the actual work, `REQUIRES_NEW` propagation
    (its own transaction, independent of whatever triggered it). This is the method that:
    1. No-ops if `app.drive.enabled=false` (line 225-228).
    2. Row-locks the tracking record (`findByContentPlanIdForUpdate`, pessimistic write lock —
       `ContentDriveProvisioningRepository.java:19-25`) so two concurrent attempts (e.g. the
       automatic event listener racing an admin-triggered retry) can't both proceed.
    3. No-ops if the row isn't in a retryable state (`canRetry()` — only `NOT_STARTED`/`FAILED`).
    4. Marks the row `IN_PROGRESS`, then creates/confirms the root folder, then each of the 3
       subfolders, **flushing the DB after every single folder** (lines 245-259) — so a failure
       partway through leaves an accurate record of exactly what was actually created.
    5. On success: marks `SUCCEEDED` and syncs `ContentPlan.folderLink` to the root folder's URL
       (lines 260-268).
    6. On `DriveProvisioningException` (any Drive API failure): marks `FAILED` with the error
       message stored, logs a warning, and returns — **never throws further** (lines 269-273).
  - `ensureFolder(alreadyStoredId, name, parentId)` (lines 279-288) — the idempotency primitive:
    trusts an already-stored folder ID first (no network call); otherwise looks the folder up by
    exact name+parent on Drive itself before creating, so a retry can never duplicate a folder
    that already exists even if the local DB record doesn't yet know its ID.
- **Trigger:** exclusively `IdeaService.approve(...)`, via `driveProvisioningService.initiateProvisioning(contentPlan)`
  at `src/main/java/com/kcpc/mkt/idea/service/IdeaService.java:429` — see §5.

### 2.4 Folder naming / hierarchy — `IMPLEMENTED`
- **Where:** `DriveProvisioningService.java:94-104` (package-private static builders, the single
  source of truth used both for creation and for idempotent name-based lookup):
  ```java
  static String rawShootFolderName(String contentId)     { return "01 - Raw Shoot-" + contentId; }
  static String editFolderName(String contentId)          { return "02 - Edit-" + contentId; }
  static String finalContentFolderName(String contentId)  { return "03 - Final Content-" + contentId; }
  ```
- **Root folder name:** the Content ID itself, exactly (`plan.getContentId()`, see `provision()`
  line 244) — never the idea's title. Confirmed by test
  `folderNameIsTheImmutableContentIdNeverTheTitle` (`DriveProvisioningServiceTest.java:156-166`).
- **Hierarchy:** root folder created directly under `app.drive.root-folder-id` (the configured
  KCPC company root); all 3 subfolders created directly under **that content's own root** — never
  under the company root directly, never under each other. See §4 for the exact tree.
- **URL derivation:** `DriveProvisioningService.folderUrl(String folderId)` (lines 87-89) builds
  `"https://drive.google.com/drive/folders/" + folderId` **on demand** — a share URL is never
  itself the persisted canonical value; the folder ID is (see §2.6).

### 2.5 Google Drive API client (real) — `IMPLEMENTED`
- **Where:** `GoogleDriveFolderClient.java` (`src/main/java/com/kcpc/mkt/drive/client/GoogleDriveFolderClient.java`).
- **`createFolder`** (lines 34-50): creates a `File` with `mimeType = application/vnd.google-apps.folder`,
  a single parent, `supportsAllDrives(true)` set on the create call (required for Shared Drive
  writes) — returns the new folder's ID. Wraps any `IOException` into `DriveProvisioningException`.
- **`findFolder`** (lines 52-77): queries `name = '<escaped name>' and '<parentId>' in parents and
  mimeType = '<folder mime>' and trashed = false`, with `supportsAllDrives(true)`,
  `includeItemsFromAllDrives(true)`, and — only if `sharedDriveId` is configured — `corpora("drive")`
  + `driveId(sharedDriveId)` scoping the search to that Shared Drive. Returns the first match's ID,
  or empty.
- **No sharing/permissions operations exist anywhere in `DriveFolderClient`** (interface has
  exactly `createFolder`/`findFolder` — enforced by a structural test, see §9) — a created folder
  only ever inherits sharing from its Shared Drive/parent; this client cannot make anything public.

### 2.6 Disabled-state stand-in client — `IMPLEMENTED`
- **Where:** `DisabledDriveFolderClient.java`. Both methods **throw** `DriveProvisioningException`
  ("Google Drive integration is disabled...") rather than silently no-op — by design (see the
  class's own comment), so a wiring mistake that somehow calls this bean directly fails loudly.
  In normal operation, `DriveProvisioningService.provision()` already checks the enabled flag
  itself and returns before ever reaching this client (§2.3 step 1), so these throw paths are a
  defense-in-depth, not the normal disabled-flow (the normal disabled flow is a silent early
  return inside `provision()`, not an exception).

### 2.7 Database tracking (idempotency ledger) — `IMPLEMENTED`
- **Table:** `content_drive_provisioning`, created by migration
  `src/main/resources/db/migration/V25__content_drive_provisioning.sql`. One row per
  `ContentPlan` (`content_plan_id UUID NOT NULL UNIQUE`).
- **Columns:** `provisioning_id` (PK), `content_plan_id` (FK, unique), `status` (VARCHAR(20),
  CHECK'd to `NOT_STARTED|IN_PROGRESS|SUCCEEDED|FAILED`), `root_folder_id`, `raw_shoot_folder_id`,
  `edit_folder_id`, `final_content_folder_id` (all `VARCHAR(128)`, nullable — populated one at a
  time as each folder is confirmed), `last_error` (TEXT), `created_at`, `updated_at`.
- **Entity:** `ContentDriveProvisioning.java` (`src/main/java/com/kcpc/mkt/drive/domain/ContentDriveProvisioning.java`) —
  status-transition helper methods `markInProgress()`/`markSucceeded()`/`markFailed(error)`/
  `markNeedsProvisioning()` (lines 135-156), plus `isFullyProvisioned()` (line 124-127) and
  `canRetry()` (line 131-133).
- **Status enum:** `DriveProvisioningStatus.java` — `NOT_STARTED → IN_PROGRESS → SUCCEEDED | FAILED`.
- **Repository:** `ContentDriveProvisioningRepository.java` — `findByContentPlan`,
  `findByContentPlan_Id`, and the pessimistic-lock `findByContentPlanIdForUpdate` used by
  `provision()`'s critical section.

### 2.8 `content_plans.folder_link` — display/compatibility field — `IMPLEMENTED`
- **Where:** column added by `V7__idea_and_content_plan_core.sql:39` (`folder_link TEXT`), long
  before the structured provisioning table existed.
- **Role today:** the single field every existing UI screen already displays as "Drive Link"
  (Content Detail, Reviews, Pipeline, My Work — unchanged by this feature). Once structured
  provisioning succeeds, `DriveProvisioningService.provision()` syncs this field to the
  provisioned root folder's URL (§2.4). It is **never** the source of truth for the actual folder
  IDs — `content_drive_provisioning` is.
- **Manual-edit guard:** `PlanningService.updateParameters(...)`
  (`src/main/java/com/kcpc/mkt/planning/service/PlanningService.java:125-167`) only lets an
  ordinary Planning edit change `folder_link` **if no structured root is known yet**
  (`structuredRootKnown`, lines 144-148) — once a real root folder ID exists, an ordinary edit can
  never silently diverge the displayed link from the structured record. This is enforced only in
  application code, not a DB constraint.

### 2.9 Manual admin retry — `IMPLEMENTED`
- **Where:** `DriveProvisioningService.retry(User actor, UUID contentPlanId)` (lines 137-148),
  exposed via `POST /app/deliverables/{id}/drive/retry`
  (`DeliverableMvcController.java:1198-1222`).
- **Authorization:** `PERM_13_FOLDER_LINK_MANAGE` (`LifecycleStage.ADMINISTRATIVE`) — checked in
  the controller before calling the service (line 1202-1203).
- **Behavior:** if Drive integration is disabled, returns an honest `"Drive integration is not
  enabled on this server (app.drive.enabled=false) - nothing was attempted."` info message
  **without calling the service at all** (lines 1204-1208) — this exact behavior is locked in by
  a regression test describing a real prior production incident (§9, §10). Otherwise calls the
  exact same idempotent `provision()` logic automatic provisioning uses (via `self.provision(...)`)
  — never a second, different retry-specific code path — and reflects the row's actual resulting
  status in the flash message (`SUCCEEDED`/`FAILED`/other).

### 2.10 Manual admin relink/repair — `IMPLEMENTED`
- **Where:** `DriveProvisioningService.relinkRootFolder(User actor, UUID contentPlanId, String newRootFolderIdOrUrl)`
  (lines 163-188), exposed via `POST /app/deliverables/{id}/drive/relink`
  (`DeliverableMvcController.java:1224-1240`), same `PERM_13_FOLDER_LINK_MANAGE` gate.
- **Behavior:** accepts either a raw Drive folder ID or a full share URL — `extractFolderId(...)`
  (lines 190-202) extracts the ID from a `/folders/{id}` URL pattern via regex, or falls back to
  treating the whole trimmed input as a raw ID.
- Updates the structured record's `root_folder_id` **first**; if all 3 subfolder IDs are already
  known under it, marks the row `SUCCEEDED`, otherwise `markNeedsProvisioning()` (eligible for a
  subsequent Retry to create/confirm the subfolders under the corrected root). Then resyncs
  `content_plans.folder_link` from the new root — always structured-record-first,
  display-field-second, never the reverse.
- Works even for content with **no existing provisioning row at all** (legacy content predating
  this feature) — creates one (`orElseGet(() -> provisioningRepository.save(new
  ContentDriveProvisioning(plan)))`, line 172), bringing it under structured tracking for the
  first time.

### 2.11 Content Detail UI — `IMPLEMENTED`
- **Where:** `deliverable-detail.jsp:158-235`.
- Always shows "Drive Link" as an "Open ↗" hyperlink when `plan.folderLink` is non-empty, else a
  muted em-dash (lines 158-164).
- If a structured `driveProvisioning` record exists **and** its status isn't `SUCCEEDED`, shows an
  explicit status line (`NOT_STARTED`/`IN_PROGRESS`/`FAILED`, plus the stored error text for
  `FAILED`) and — if the viewer holds `PERM_13_FOLDER_LINK_MANAGE` and status isn't
  `IN_PROGRESS` — a "Retry Provisioning" button (lines 169-188). The normal case (auto-provisioned
  and `SUCCEEDED`) renders nothing extra here.
- If the viewer holds `PERM_13_FOLDER_LINK_MANAGE`, an always-available collapsed "Folder Link
  Management: relink Drive root folder" `<details>` panel is shown (lines 225-235), independent of
  current status.
- **Model wiring:** `DeliverableMvcController.view(...)` adds `driveProvisioning`
  (`driveProvisioningRepository.findByContentPlan(plan).orElse(null)`) and `canManageDriveFolders`
  (`PERM_13` check) to the model (lines 240-242).

---

## 3. Idea Approval Integration

- **Where:** `IdeaService.approve(...)` (`src/main/java/com/kcpc/mkt/idea/service/IdeaService.java`),
  the single combined "Idea Review + Planning Details" approval command.
- **Manual-link validation gate** (lines 283-285):
  ```java
  boolean driveWillAutoProvision = driveProvisioningService.isDriveIntegrationEnabled();
  if (!driveWillAutoProvision && (planning.folderLink() == null || planning.folderLink().isBlank())) {
      throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "Drive Folder Link is mandatory");
  }
  ```
  This is the **exact** validation error reported in the screenshot that prompted this report. It
  fires whenever `app.drive.enabled=false` **and** no manual link was typed. It does **not** fire
  when Drive integration is enabled — auto-provisioning is expected to fill the link in shortly
  after approval, so a manual link is optional in that case.
- **Provisioning trigger** (line 429): `driveProvisioningService.initiateProvisioning(contentPlan)`
  — called **unconditionally**, after the `ContentPlan` row is saved, regardless of which pipeline
  stage combination was selected (see §5). This call is inside the same `@Transactional` method
  as `ContentPlan` creation, but `initiateProvisioning` itself only inserts the cheap tracking row
  and publishes an event — the real Drive network calls happen only after the transaction commits
  (§2.3).
- **Nothing else in `IdeaService`** references Drive provisioning — it is a one-shot trigger at
  approval time, not something re-invoked on every field edit.

---

## 4. Current Folder Structure

Confirmed exactly matches the structure your message described as the desired behavior — this
**is** what the current code produces when Drive integration is enabled and a folder is
successfully provisioned:

```text
{DRIVE_ROOT_FOLDER_ID}/                         <- configured company root (app.drive.root-folder-id)
└── {CONTENT_ID}/                               <- root folder, name = ContentPlan.getContentId(), exactly
    ├── 01 - Raw Shoot-{CONTENT_ID}
    ├── 02 - Edit-{CONTENT_ID}
    └── 03 - Final Content-{CONTENT_ID}
```

- Folder name builders: `DriveProvisioningService.rawShootFolderName/editFolderName/finalContentFolderName`
  (`DriveProvisioningService.java:94-104`).
- Root folder name is the Content ID, confirmed never the idea title (`DriveProvisioningServiceTest.java:156-166`).
- All 3 subfolders are always siblings directly under the content's own root — never nested under
  each other, never directly under the company root.
- This is **not an assumption** — it is asserted directly by
  `newContentIdCreatesAllFourFoldersWithCorrectNamesAndHierarchy`
  (`DriveProvisioningServiceTest.java:128-154`), which checks the exact parent/child relationship
  and exact names via the fake Drive client.

---

## 5. Idea Review → Assign & Approve → Drive Provisioning, Per Starting Stage

The Stages feature (ENG-091) lets an approved idea start the pipeline at Shoot, Edit, or
Publishing. Drive provisioning's trigger point (`IdeaService.approve()` line 429) sits **after**
all stage-specific branching and is called identically in every case — it is not inside any
`if (shootStarts)`/`if (editStarts)`/`if (publishingStarts)` block. Verified by direct code
inspection of `IdeaService.java:283-431` (see §3).

| Starting stage | Manual Drive Link required (when `app.drive.enabled=false`)? | `initiateProvisioning` called? | Folders created (when enabled) |
|---|---|---|---|
| **A. Shoot + Edit + Publishing** (Standard) | Yes, if disabled | Yes | Root + all 3 subfolders |
| **B. Edit + Publishing** (Direct Edit) | Yes, if disabled | Yes | Root + all 3 subfolders |
| **C. Publishing** (Direct Publishing) | Yes, if disabled | Yes | Root + all 3 subfolders |

- **Direct Edit works:** `IMPLEMENTED` — nothing about skipping Shoot changes the Drive trigger or
  folder set; it runs exactly as in the Standard case.
- **Direct Publishing works:** `IMPLEMENTED` — same conclusion; even though the plan never visits
  a Shoot or Edit workflow status, all 3 subfolders (including "01 - Raw Shoot-…" and
  "02 - Edit-…") are still created, because folder creation is driven purely by `initiateProvisioning`
  being called at approval time, not by which workflow stage is reached later.
- **All three folders are created regardless of selected stage:** confirmed `TRUE` by code
  inspection — there is no branch anywhere in `DriveProvisioningService.provision()`
  (`DriveProvisioningService.java:223-274`) that reads `ContentPlan` stage-selection data or skips
  creating the "Raw Shoot" or "Edit" subfolders based on which stages were chosen at approval.
  This was not separately asserted by a dedicated Stages+Drive test (see §10, "Gaps").

---

## 6. Manual vs. Automatic Drive Link — Current Behavior

- **`app.drive.enabled=false` (the default, and the current state everywhere this repo has been
  run in this session — see §7):** the "Asset Folder / Drive Link" field on Idea Review approval
  is **mandatory** — approval is rejected with `400 VALIDATION_FAILED "Drive Folder Link is
  mandatory"` if left blank (`IdeaService.java:283-285`). This is exactly the behavior in the
  screenshot. `PARTIALLY IMPLEMENTED` from the user's stated goal's perspective: the code to make
  it automatic exists and is complete, but is not switched on.
- **`app.drive.enabled=true` with valid credentials:** the field becomes optional at approval time
  (the mandatory check is skipped entirely); the folder link is filled in automatically, shortly
  after approval, once the post-commit provisioning event completes (§2.3, §3). `IMPLEMENTED`.
- **`app.drive.enabled=true` but credentials missing/invalid:** `DriveClientConfig` silently falls
  back to `DisabledDriveFolderClient` (§2.2 step 2/7) — but `isDriveIntegrationEnabled()` still
  returns `true` (it only reads the raw flag, not whether the client actually initialized — see
  §10, "Gaps"), so the mandatory-link validation is **skipped** even though Drive calls will then
  fail. The `content_drive_provisioning` row would end up `FAILED` after the post-commit attempt,
  and `content_plans.folder_link` would stay blank unless a manual link was also typed.

---

## 7. Configuration Required

All values are sourced from environment variables (never hardcoded) via `application.yml:73-82`.
No real value for any of these was found in this repository at inspection time — every location
listed below contains only placeholders or blank defaults.

| Property | Env var | Purpose | Found configured in this repo? |
|---|---|---|---|
| `app.drive.enabled` | `DRIVE_ENABLED` | Master on/off switch | No — `false` everywhere (`.env`, `.env.example`, `deploy/dev/.env.example`, `deploy/prod/.env.example`, `application.yml` default) |
| `app.drive.service-account-key` | `DRIVE_SERVICE_ACCOUNT_KEY` | Google service-account credentials — a file path (prod convention) or raw single-line JSON (dev convention, per `docker-compose.yml:46-47`) | No — blank everywhere. **Never print this value; mask as `<SECRET>`/`<REDACTED>` if ever displayed.** |
| `app.drive.shared-drive-id` | `DRIVE_SHARED_DRIVE_ID` | The Google Shared Drive ID folders are created on | No — blank everywhere |
| `app.drive.root-folder-id` | `DRIVE_ROOT_FOLDER_ID` | The KCPC company root folder ID inside that Shared Drive | No — blank everywhere |
| `app.drive.impersonate-user` | `DRIVE_IMPERSONATE_USER` | Optional: Workspace user to impersonate via domain-wide delegation | No — blank everywhere; optional even when enabling |

**Where these need to be set, depending on environment:**
- Local `mvn spring-boot:run`: as process environment variables (this repo's `./.env` is **not**
  read by plain `mvn spring-boot:run` — only by `docker-compose`, which auto-loads `./.env` by
  convention).
- Local Docker Compose dev stack: `./.env` (root) — see `.env.example` for the exact keys/format.
- `deploy/dev` stack: `deploy/dev/.env` (copied from `deploy/dev/.env.example`; not present in
  this repo — only the `.example` template exists).
- `deploy/prod` stack: `deploy/prod/.env` (copied from `deploy/prod/.env.example`; not present in
  this repo — only the `.example` template exists). `deploy/README.md:260-267` explicitly requires
  DEV and PROD to use **different** `DRIVE_SHARED_DRIVE_ID`/`DRIVE_ROOT_FOLDER_ID` values — the
  application itself does not enforce this isolation; it is a deployment-discipline requirement
  only.
- Real value for `DRIVE_SERVICE_ACCOUNT_KEY` in this report: `<REDACTED>` (not present anywhere
  inspected; would be `<SECRET>` if it were).

---

## 8. Current Drive Flow

```text
1. Idea Review — Approve & Assign
   (IdeaMvcController#decide / ReviewsMvcController#decideIdea / IdeaRestController#review
    -> IdeaService.decide -> IdeaService.approve)
        │
        ▼
2. approve(): validate Planning fields
        │
        ├─ app.drive.enabled == false AND Drive Folder Link blank?
        │      └─ YES → 400 "Drive Folder Link is mandatory"  (STOP - this is the screenshot)
        │      └─ NO  → continue
        ▼
3. Create ContentPlan, ShootingAssignment/EditingAssignment/PublishingAssignment
   (per selected Stages), PredefinedRoleMarks, PlannedOutputs, etc.
        │
        ▼
4. driveProvisioningService.initiateProvisioning(contentPlan)
     - INSERT content_drive_provisioning row, status = NOT_STARTED   (cheap, no network call)
     - publish ContentPlanCreatedEvent                                (still inside this transaction)
        │
        ▼
5. Idea-approval transaction COMMITS
        │
        ▼
6. @TransactionalEventListener(AFTER_COMMIT) onContentPlanCreated fires
        │
        ▼
7. provision(contentPlanId)                        [new, separate transaction - REQUIRES_NEW]
        │
        ├─ app.drive.enabled == false?  → log + return (no-op, row stays NOT_STARTED forever
        │                                   unless a later manual Retry is attempted)
        ▼
        row-lock the content_drive_provisioning row; mark IN_PROGRESS
        │
        ▼
        ensureFolder(root)         -> create/find "{CONTENT_ID}" under company root  -> save id, flush
        ensureFolder(raw shoot)    -> create/find "01 - Raw Shoot-{CONTENT_ID}"       -> save id, flush
        ensureFolder(edit)         -> create/find "02 - Edit-{CONTENT_ID}"            -> save id, flush
        ensureFolder(final)        -> create/find "03 - Final Content-{CONTENT_ID}"   -> (about to flush)
        │
        ├─ any step throws DriveProvisioningException?
        │      └─ YES → mark FAILED, store last_error, STOP (whatever succeeded so far stays saved)
        │      └─ NO  → continue
        ▼
        mark SUCCEEDED; content_plans.folder_link = "https://drive.google.com/drive/folders/{rootId}"
        │
        ▼
8. Content Detail / Reviews / Pipeline / My Work all display content_plans.folder_link unchanged
   (no other screen was modified by this feature)

── Manual recovery path (any time after step 7, PERM_13_FOLDER_LINK_MANAGE only) ──
   POST /app/deliverables/{id}/drive/retry   -> re-runs step 7's provision() logic (idempotent)
   POST /app/deliverables/{id}/drive/relink  -> admin supplies a folder ID/URL directly,
                                                updates the structured record + folder_link
```

---

## 9. Tests

Two test classes are dedicated specifically to Drive provisioning; several others incidentally
reference Drive fields as unrelated fixture data (not listed here as Drive tests).

### `DriveProvisioningServiceTest.java` (`src/test/java/com/kcpc/mkt/DriveProvisioningServiceTest.java`)
Runs with `@TestPropertySource(properties = {"app.drive.enabled=true", "app.drive.root-folder-id=kcpc-company-root"})`
and a `@Primary` **fake** Drive client (`FakeDriveFolderClient`,
`src/test/java/com/kcpc/mkt/drive/FakeDriveFolderClient.java`) — an in-memory stand-in; no real
Google credentials or network access are used, in this test or anywhere else in the suite. 12
test methods:

| Test | What it verifies |
|---|---|
| `newContentIdCreatesAllFourFoldersWithCorrectNamesAndHierarchy` | Root + 3 subfolders created with exact names, exact parent/child hierarchy, and `folder_link` synced to the root's URL |
| `folderNameIsTheImmutableContentIdNeverTheTitle` | Root folder name is the Content ID, never the idea title |
| `retryAfterPartialFailureCompletesWithoutDuplicatingAlreadyCreatedFolders` | A retry after a partial failure (root+raw-shoot succeeded, edit failed) completes the rest without recreating what already exists |
| `retryOnRecordWithFoldersAlreadyStoredUnderThePreviousNamingConventionNeverTouchesOrDuplicatesThem` | An already-stored folder ID (even under an older naming convention) is trusted as-is on retry, never renamed or re-created |
| `retryAfterCompleteSuccessIsIdempotentAndMakesNoNewDriveCalls` | Retrying an already-`SUCCEEDED` row makes **zero** new `createFolder` calls |
| `apiFailureIsSurfacedClearlyOnTheProvisioningRecord` | A Drive API failure is stored verbatim in `last_error`, status becomes `FAILED` |
| `driveFolderClientInterfaceHasNoWayToGrantPublicSharing` | Structural check: `DriveFolderClient` exposes exactly `createFolder`/`findFolder`, no sharing/permissions method exists at all |
| `existingContentCreationFlowStillProducesAWorkflowInstanceAtPlanning` | Approval still transitions to `SA` as normal when Drive is enabled |
| `perm13HolderCanRetryAndRelinkViaTheAdminHttpEndpoints` | A user with `PERM_13_FOLDER_LINK_MANAGE` can successfully call both `/drive/retry` and `/drive/relink` |
| `employeeWithoutPerm13CannotRetryOrRelink` | A user without `PERM_13` cannot trigger a retry (status stays `FAILED`, not silently retried) |
| `legacyContentWithNoProvisioningRecordKeepsFolderLinkFreelyEditable` | Content with no `content_drive_provisioning` row at all still allows free manual editing of `folder_link` |
| `ordinaryPlanningEditCannotDivergeFolderLinkOnceStructuredRootIsKnown` | Once a structured root folder ID is known, an ordinary Planning edit's `folderLink` param is ignored — the canonical link is preserved |
| `provisioningIsANoOpWhenDriveIntegrationIsDisabled` | Direct check that `DisabledDriveFolderClient` throws when Drive is disabled |

### `DriveProvisioningDisabledFeedbackTest.java` (`src/test/java/com/kcpc/mkt/DriveProvisioningDisabledFeedbackTest.java`)
Runs against the **real default** (`app.drive.enabled` left unset → `false`) — deliberately not
overridden, matching the actual state of every environment inspected in this repo. Its own class
comment (lines 23-35) documents that it is a **regression test for a real prior production
incident**: Content Detail showed "Drive folders not yet provisioned" + a Retry button; clicking
Retry produced a misleading green "success" banner while nothing actually happened, because
`DRIVE_ENABLED` was not set in the shell that launched the server — an environmental issue, not a
code bug, but the misleading message itself was a real bug that this test locks the fix for. Its
one test, `retryingWithDriveIntegrationDisabledShowsAnHonestInfoMessageNeverAMisleadingSuccess`,
verifies: (a) the tracking row is always created regardless of the enabled flag, (b) retrying with
Drive disabled leaves the row's status unchanged (`NOT_STARTED`), and (c) the page shows an honest
`alert-info` "Drive integration is not enabled on this server" message, never a misleading
`alert-success`.

---

## 10. Gaps / Potential Issues

Only issues directly supported by the code inspected above.

1. **`isDriveIntegrationEnabled()` reflects the raw config flag, not whether the client actually
   initialized successfully.** If `app.drive.enabled=true` but the service-account key is
   blank/invalid (or any other `GoogleCredentials`/transport error occurs),
   `DriveClientConfig` silently falls back to `DisabledDriveFolderClient` (§2.2), but
   `DriveProperties.isEnabled()` — and therefore `isDriveIntegrationEnabled()` — still returns
   `true`. Practical effect: the mandatory-manual-link check in `IdeaService.approve()` (§3) would
   be **skipped** in this state, and the resulting Drive attempt would fail and leave
   `content_plans.folder_link` blank with no manual entry required, unless the failure is noticed
   via the `content_drive_provisioning` row's `FAILED` status on Content Detail.
2. **No dedicated automated test asserts "all 3 folders are created for Direct Edit/Direct
   Publishing specifically."** §5's conclusion (all three subfolders are always created regardless
   of starting stage) is derived from direct code inspection of `IdeaService.approve()` and
   `DriveProvisioningService.provision()` (neither reads stage-selection data), not from a test
   that explicitly approves a Direct Edit/Direct Publishing idea and asserts the folder set. This
   is a documented gap in test coverage, not a known defect.
3. **This exact class of failure has already happened once in production**, per
   `DriveProvisioningDisabledFeedbackTest`'s own class comment (§9): `DRIVE_ENABLED` not being set
   in the process environment that launches the server. The application code has no way to detect
   "was this feature intentionally left off, or did the environment just lose its configuration"
   — both look identical (`app.drive.enabled=false`) from inside the running application.
4. **No file in this repository (`.env`, any `deploy/*/.env.example`, `application.yml`) contains
   real Drive credentials.** `deploy/dev/.env` and `deploy/prod/.env` (the real, non-template
   files each stack actually reads) do not exist in this checkout at all — only their `.example`
   templates. Whatever credentials/values were previously in use (if any) exist only outside this
   repository (e.g. directly on a deployment host, or only ever held in a now-terminated process's
   environment) and were not discoverable by inspection.
5. **`GoogleDriveFolderClient.createFolder` does not pass `driveId`/`corpora` Shared-Drive-scoping
   parameters** the way `findFolder` does (§2.5) — only `supportsAllDrives(true)`. This is
   consistent with the Google Drive API's own documented requirements for a `create` call (the
   parent folder ID alone determines placement), so this is noted as a factual difference between
   the two methods, not asserted here as a defect.

---

## 11. Files Inspected

**Drive-specific implementation:**
- `src/main/java/com/kcpc/mkt/drive/client/DriveFolderClient.java`
- `src/main/java/com/kcpc/mkt/drive/client/DisabledDriveFolderClient.java`
- `src/main/java/com/kcpc/mkt/drive/client/GoogleDriveFolderClient.java`
- `src/main/java/com/kcpc/mkt/drive/client/DriveProvisioningException.java`
- `src/main/java/com/kcpc/mkt/drive/config/DriveClientConfig.java`
- `src/main/java/com/kcpc/mkt/drive/config/DriveProperties.java`
- `src/main/java/com/kcpc/mkt/drive/domain/ContentDriveProvisioning.java`
- `src/main/java/com/kcpc/mkt/drive/domain/DriveProvisioningStatus.java`
- `src/main/java/com/kcpc/mkt/drive/event/ContentPlanCreatedEvent.java`
- `src/main/java/com/kcpc/mkt/drive/repository/ContentDriveProvisioningRepository.java`
- `src/main/java/com/kcpc/mkt/drive/service/DriveProvisioningService.java`

**Integration points:**
- `src/main/java/com/kcpc/mkt/idea/service/IdeaService.java` (approval trigger + mandatory-link
  validation; lines 283-285 and 429 specifically)
- `src/main/java/com/kcpc/mkt/planning/service/PlanningService.java` (manual `folder_link` edit
  guard, lines 125-167)
- `src/main/java/com/kcpc/mkt/web/mvc/DeliverableMvcController.java` (view wiring lines 225-244;
  retry endpoint lines 1195-1222; relink endpoint lines 1224-1240)
- `src/main/webapp/WEB-INF/views/deliverable-detail.jsp` (Drive Link display, provisioning status,
  Retry button, relink panel; lines 158-235)

**Database:**
- `src/main/resources/db/migration/V25__content_drive_provisioning.sql`
- `src/main/resources/db/migration/V7__idea_and_content_plan_core.sql` (line 39, `folder_link` column)

**Configuration:**
- `src/main/resources/application.yml` (lines 73-82)
- `.env` (repo root — inspected for presence/absence of real values only; no secret content
  reproduced here)
- `.env.example` (repo root)
- `deploy/dev/.env.example`
- `deploy/prod/.env.example`
- `docker-compose.yml` (repo root, lines 44-53)
- `deploy/dev/docker-compose.yml` (lines 57-66)
- `deploy/prod/docker-compose.yml` (lines 54-64)
- `deploy/README.md` (lines 251-267, "Google Drive isolation" section)

**Tests:**
- `src/test/java/com/kcpc/mkt/DriveProvisioningServiceTest.java`
- `src/test/java/com/kcpc/mkt/DriveProvisioningDisabledFeedbackTest.java`
- `src/test/java/com/kcpc/mkt/drive/FakeDriveFolderClient.java`

---

## 12. Summary Status Table

| Capability | Status |
|---|---|
| Master enable/disable flag | `IMPLEMENTED` |
| Google credential loading (file path or raw JSON) | `IMPLEMENTED` |
| Real Google Drive API client (create/find folder) | `IMPLEMENTED` |
| Folder hierarchy: root = Content ID, 3 named subfolders | `IMPLEMENTED` |
| Idempotency (id-first, then name+parent lookup, never duplicate) | `IMPLEMENTED` |
| Partial-failure recovery / per-folder progress tracking | `IMPLEMENTED` |
| Manual admin Retry (`PERM_13`) | `IMPLEMENTED` |
| Manual admin Relink/repair (`PERM_13`) | `IMPLEMENTED` |
| Drive URL persistence (ID-canonical, URL derived on demand) | `IMPLEMENTED` |
| Idea Approval integration (trigger point) | `IMPLEMENTED` |
| Direct Edit stage integration | `IMPLEMENTED` (same trigger, unconditional) |
| Direct Publishing stage integration | `IMPLEMENTED` (same trigger, unconditional) |
| All 3 folders regardless of starting stage | `IMPLEMENTED` (by inspection; not separately test-covered — see Gap #2) |
| Shoot/Edit/Publishing *workflow-stage* re-triggering of Drive calls | `NOT IMPLEMENTED` (provisioning is one-shot at approval only, by design) |
| Automatic Drive Link generation **in this repository's current running configuration** | `CONFIGURATION REQUIRED` (`app.drive.enabled=false`, no credentials present anywhere inspected) |
| Detection of "enabled but misconfigured" vs. "enabled and working" | `NOT IMPLEMENTED` (Gap #1) |
| Real credentials present anywhere in this repo/checkout | `NOT IMPLEMENTED` / not found (Gap #4) |
