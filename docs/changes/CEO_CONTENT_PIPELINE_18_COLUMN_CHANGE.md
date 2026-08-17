# CEO Content Pipeline — 18-Column Dashboard Change

**Stakeholder:** CEO / Owner
**Requirement:** post-R3.5 stakeholder-requested enhancement (this document, not the frozen R3.5
specification set, is the change record — no R3.5 spec file was modified).
**Requirement statement:** The CEO Content Pipeline must display exactly these 18 business
columns, one row per Content ID, regardless of how many child records (Planned Outputs,
Camerapersons, Editors, Models, Channels, Platforms, publication events) a Content ID has:
Content ID, SKU, Idea, Reference Link / Note, Category, Channels, Actor, Camera Person, Models,
Video Editor, Drive Link, Planned Live Date, Shoot Date, Edit Date, Live Date, Platforms,
Performance, Status.

## Previous UI

`pipeline.jsp` showed 6 columns for every authenticated user regardless of role (no server-side
role check existed on `/app/pipeline` at all — any Employee could load it): Content ID, Title
(Idea), Status, Priority, Flags (Delayed/Hold chips), Live Date (actually the *planned* live
date, mislabeled), plus a trailing "Open" action link. Multi-valued relations (Camerapersons,
Editors, Models, Channels, Platforms) were not shown at all.

## New 18-column definition and business semantics

| # | Column | Source | Cardinality | Display rule |
|---|--------|--------|-------------|---------------|
| 1 | Content ID | `ContentPlan.contentId` | 1 | clickable, opens `/app/deliverables/{id}` |
| 2 | SKU | `ContentPlan.skuReference` / `skuNotApplicable` | 1 | `N/A` if flagged, else value or `—` |
| 3 | Idea | `Idea.title` | 1 | direct |
| 4 | Reference Link / Note | `Idea.referenceLink` | 1 | clickable `<a>` only if it looks like a URL (free-form field, not strictly validated per UI/UX spec); else plain text; `—` if empty |
| 5 | Category | `ContentPlan.categoryText` | 1 | direct, `—` if blank |
| 6 | Channels | `PlannedOutput` → `PlannedOutputPublicationTargetMapping` → `PublicationTarget.channel.channelHandle` | multi | all distinct, comma-joined |
| 7 | Actor | `ContentPlan.preparedBy` | 1 | see "Actor implementation" below |
| 8 | Camera Person | `ShootingAssignment` (active=true) → `.cameraperson` | multi | all distinct, comma-joined |
| 9 | Models | `ContentPlanTalentEntry.talentName` | multi | all, comma-joined (free-text, not User-linked) |
| 10 | Video Editor | `EditingAssignment` (active=true) → `.editor` | multi | all distinct, comma-joined |
| 11 | Drive Link | `ContentPlan.folderLink` | 1 | compact chain-icon link, not the raw URL |
| 12 | Planned Live Date | `ContentPlan.plannedLiveDate` | 1 | direct |
| 13 | Shoot Date | `ContentPlan.plannedShootDate` | 1 | direct (planned, never actual) |
| 14 | Edit Date | `ContentPlan.plannedEditDate` | 1 | direct (planned, never actual) |
| 15 | Live Date | `ActualPublicationEvent` count per plan | multi | see "Live Date implementation" below |
| 16 | Platforms | same mapping chain as Channels → `PublicationTarget.platform.platformName` | multi | all distinct, comma-joined |
| 17 | Performance | derived from `WorkflowStatus` | 1 | see "Performance implementation" below |
| 18 | Status | `WorkflowStatus.statusName` | 1 | human-readable, never the raw code |

## Actor implementation

**Not ambiguous — no data-model gap.** `ContentPlan.preparedBy` (nullable `User` FK, last-writer
wins) is set by `PlanningService.updateParameters()` every time a Planning-Execution-authorized
user saves the Planning parameters form. This is distinct from `PlanningPreparer`
(`planning_preparers`), which is a separate, multi-row provenance list used only for the
self-review-conflict guard and must not be confused with Actor. Displayed as the User's full
name, or `—` before the first parameter save.

## Live Date implementation

**Genuinely ambiguous — reported per the requirement's own instruction rather than invented.**
`ActualPublicationEvent` has a direct `contentPlan` FK and a Content ID can have many rows (one
per publication-target × event). No canonical "the" Content-ID-level actual live date exists
anywhere in the current implementation (checked `KpiService`, `AdminReportingService`,
`PerformanceService` — no such aggregate exists to reuse). Rather than arbitrarily picking
earliest/latest/first-Original, the column shows a neutral non-date placeholder: `—` when zero
publication events exist, `Published` once one or more exist. Full per-event history remains on
Deliverable Detail. **Open product decision needed:** should this column show the earliest
Original-type event's date, the latest event across all targets, or something else — that
decision should come from the CEO/product owner, not be invented here.

## Performance implementation

Derived directly from `ContentPlan.workflowInstance.currentStatusCode` (already loaded, no extra
query): `PP` → "Pending", `PFUP` → "Updated", `COMP` → "Completed", anything else → "Not Yet
Applicable". The cell is clickable (styled underline, links to
`/app/deliverables/{id}#performance`) exactly when status is `PP`, `PFUP`, or `COMP` — the same
three statuses that already gate the Performance panel's visibility on Deliverable Detail
(`deliverable-detail.jsp`), so the anchor always resolves to a rendered section. No new "canonical
performance state" concept was introduced; `PerformanceObligation`/`CreativePerformanceScorecard`
per-event detail is unchanged and remains on Deliverable Detail only.

## Multi-value handling

Channels, Camera Person, Models, Video Editor and Platforms are batch-loaded across the whole
plan list (see Query changes) and joined into one comma-separated string per Content ID — no
first-record selection, no row duplication, no collaborator discarded.

## Drive Link icon implementation

First inline SVG/icon introduced into this project (no prior icon convention existed anywhere in
`src/main/webapp` or `app.css`). A minimal two-path "link/chain" icon, `currentColor` stroke, no
external icon font or CDN. Wrapped in an `<a>` with `target="_blank" rel="noopener noreferrer"`,
`aria-label="Open Drive Link"` and a `title` tooltip; renders a muted `—` with a
`title="No Drive Link set"` tooltip when no Drive Link exists — never a broken/empty link.

## Query / read-model changes

New `PipelineDashboardService.buildRows(List<ContentPlan>)` (reporting module) batch-loads every
multi-valued relation across **all** plans in a handful of queries (grouped in memory by Content
Plan id), instead of querying per row — avoiding N+1 across a table that can have many rows.
`preparedBy` (LAZY) is additionally join-fetched at the repository level
(`ContentPlanRepository.findAllWithPreparedByOrderByCreatedAtDesc()`) so the MVC view layer never
touches a lazy association outside its transaction (`open-in-view` is disabled project-wide).

## Files changed

- `LandingMvcController.java` — role gate (EMPLOYEE redirected to `/app/home`); wires the new read
  model into `/app/pipeline`.
- `ContentPlanRepository.java` — new join-fetch query.
- `ShootingAssignmentRepository.java`, `EditingAssignmentRepository.java`,
  `ContentPlanTalentEntryRepository.java`, `PlannedOutputRepository.java`,
  `PlannedOutputPublicationTargetMappingRepository.java`, `ActualPublicationEventRepository.java`
  — new batch (`_IdIn`) query methods.
- `PipelineDashboardService.java` (new) — the read-model/projection builder.
- `PipelineRow.java` (new, `reporting.dto`) — the one-row-per-Content-ID JSP-EL DTO.
- `pipeline.jsp` — full rewrite: 18-column wide table, horizontal scroll, sticky header, sticky
  Content ID column, chain-icon Drive Link, clickable Performance cell.
- `deliverable-detail.jsp` — added `id="performance"` anchor to the existing Performance `<h2>`.
- `app.css` — new `.pipeline-*` classes (scroll container, sticky header/column, chain icon,
  clickable performance cell).
- `CeoPipelineDashboardTest.java` (new) — automated coverage (see Tests below).

## DB impact

None. No migration, no new tables/columns — every column maps to existing governed data.

## API impact

None. No REST endpoints changed; this is an MVC-only (`/app/pipeline`) view/read-model change.

## Security impact

**Closes a pre-existing gap, does not introduce one.** `/app/pipeline` previously had zero
role-based access control — any authenticated user, including plain Employees, could load it
(confirmed via `SecurityConfig` — `/app/**` only required authentication, no role check; and
`LandingMvcController.pipeline()` did no `accessClass` branching). Employees are now redirected
to `/app/home` before any pipeline data is queried or rendered. CEO_OWNER and MARKETING_MANAGER
continue to share the identical view, matching the existing role-appropriate-landing design
(`LandingMvcController.home()` already routes both roles here).

## Automated tests

`CeoPipelineDashboardTest.java`:

1. `eighteenColumnPipelineRendersOneRowPerContentIdWithMultiValueData` — drives one Content ID
   through Planning (2 Camerapersons, 3 Models, 2 Editors, 2 Channels, 2 Platforms, custom SKU/
   Category/Reference-Link/Drive-Link), Shooting, Editing and Publishing (all 3 mapped targets
   resolved with Actual Publication events) up to Performance Pending, then asserts against the
   live `/app/pipeline` response: all 18 headers present in order; exactly one row for the
   Content ID; every multi-value field shows every collaborator/channel/platform (no first-record
   selection); SKU/Category/Reference-Link/Actor/Drive-Link/Planned-dates/Live-Date-placeholder/
   Performance-state-and-link/human-readable-Status all render per the semantics above; raw codes
   (`PP`/`COMP`/`CAN`) never appear as the primary status text; an Employee is redirected away
   (302 to `/app/home`) without ever seeing `pipeline-table` markup; a Marketing Manager gets the
   identical 200 response with the same row, proving no regression.
2. `pipelineRendersSkuNaAndHandlesAMinimalNotYetPlannedContentIdSafely` — a Content ID with
   `skuNotApplicable=true` and otherwise nothing set yet (no schedule, no assignees, no outputs,
   no publication events) renders `N/A` for SKU and produces no 500/`LazyInitializationException`/
   `NullPointerException` — proves the dashboard never breaks on a Content ID that has only just
   entered Planning.

Existing `MvcScreenSmokeTest` and `AuthenticationFlowTest` (both hit `/app/pipeline` as part of
their golden-path flows) continue to pass unmodified.

## Test results

Full suite (`mvn test`): **all tests pass**, including both new tests above, run against
`kcpc_test` with real PostgreSQL (no mocks). No existing test needed modification.

## Manual verification

Started the app locally (`dev` profile, real Postgres), logged in as the seeded CEO, MM, and
Camera Person (Employee) demo accounts and hit `/app/pipeline` directly:

- CEO and MM: `200`, full 18-column table renders, including a real demo-data row with mixed
  populated/blank (`—`) cells, no server error, no lazy-init exception.
- Camera Person (Employee): `302` to `/app/home`, pipeline data never queried or rendered.

## Outstanding ambiguity / blocker

**Live Date (actual)** — see "Live Date implementation" above. This is the one open item: the
data model has no canonical Content-ID-level actual live date when a Content ID has multiple
publication events across multiple targets. The dashboard currently shows a safe non-date
placeholder (`Published` / `—`) rather than guessing. A product decision is needed on the desired
aggregation (earliest Original event? latest event overall? per-target breakdown instead of a
single value?) before a real date can be shown in this column.

## Screenshot / review recommendation

No screenshot capability available in this environment; recommend the CEO review the live
`/app/pipeline` page directly (horizontal scroll, sticky Content ID column, and the Drive Link
chain icon are all easiest to judge visually in a real browser) before sign-off.
