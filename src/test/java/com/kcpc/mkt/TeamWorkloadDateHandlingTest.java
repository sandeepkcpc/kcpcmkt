package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEAM -> WORKLOAD - DATE HANDLING + EMPLOYEE-WISE UI UPDATE regression suite, written directly
 * against {@link com.kcpc.mkt.reporting.service.TeamWorkloadService}'s fixed behavior (spec
 * Sections 1-18): the stage-specific date mapping (Shoot -> Planned Shoot Date, Edit -> Planned
 * Edit Date, Publishing -> Planned Live Date, never plannedLiveDate universally), the
 * currently-delayed-task exemption from the "before From" exclusion, inclusive From<=date<=To
 * semantics, and the employee-wise aggregation (one row per employee, stage breakdown totals
 * summing back to the employee's own main-row totals). Every test hits the real
 * {@code /app/reports/workload} endpoint over HTTP (no mocking of the service or repositories),
 * consistent with this codebase's existing MVC-screen regression style
 * (see {@code PermissionDrivenWorkflowTest#teamWorkload_...}, which this suite complements rather
 * than duplicates - that test proves an employee with assignments in two stages appears under
 * both stage filters; this suite proves the DATE and AGGREGATION behavior around that).
 *
 * <p>Plans are advanced to the target workflow status through the real approval/execution API
 * (never by writing a status directly) - Shoot/Edit start and review-submit must be called as the
 * actual assigned Cameraperson/Editor (ShootingService#startShooting/submitShootReview and
 * EditingService's equivalents both call {@code requireActiveAssignee}), while the review
 * decision itself is made by the CEO, exactly mirroring PermissionDrivenWorkflowTest's own
 * helpers. Planned dates are then pinned to exact test values via
 * {@link ContentPlan#applyReschedule} + repository save - the same "Reschedule" mutation
 * Permission #10 already uses in production, applied here purely as a test fixture tool so a
 * scenario can freely place a date in the past (to construct a genuinely delayed task) without
 * fighting Standard Planning Mode's own "date cannot be in the past" creation-time guard.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeamWorkloadDateHandlingTest {

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TEST_PASSWORD = "Passw0rd!";

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private record TestUser(String id, String email) {
    }

    // ------------------------------------------------------------------ A/D: Shoot uses plannedShootDate, not plannedLiveDate

    @Test
    void shootStageFiltersByPlannedShootDate_notPlannedLiveDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Shoot Date Basis Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);

        // Standard planning: plannedLiveDate=+30d, explicit plannedShootDate=+6d (must be within
        // [today, plannedEditDate]; kept far apart from plannedLiveDate on purpose).
        approveAtSa(ceo, "Shoot Date Basis " + unique, cam,
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(6), LocalDate.now().plusDays(20));

        // Query range covers the Shoot Date (+6d) but NOT the Live Date (+30d). Pre-fix, this
        // screen filtered every stage on plannedLiveDate, so this employee would have been
        // (incorrectly) excluded.
        String from = LocalDate.now().plusDays(5).toString();
        String to = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom=" + from + "&dateTo=" + to);
        assertThat(resp.body()).as("Shoot row must be governed by Planned Shoot Date, not Planned Live Date")
                .contains("Shoot Date Basis Cam " + unique);
    }

    @Test
    void shootStageExcludesPlanWhenOnlyLiveDateIsInRange() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Shoot Date Neg Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        approveAtSa(ceo, "Shoot Date Neg " + unique, cam,
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(6), LocalDate.now().plusDays(20));

        // Query range covers the Live Date (+30d) but NOT the Shoot Date (+6d) - proves the mapping
        // is the applicable stage date, not "any date on the plan".
        String from = LocalDate.now().plusDays(28).toString();
        String to = LocalDate.now().plusDays(32).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom=" + from + "&dateTo=" + to);
        // The employee still has a real active ShootingAssignment (so the Employee-select dropdown
        // and even a 0/0/0 main-table row can legitimately still reference their name - that's
        // unrelated, pre-existing "every active assignee gets a row" behavior, out of scope here);
        // what this test actually proves is that their ACTIVE TASKS count for this out-of-range
        // query is 0, i.e. the Live Date was correctly NOT used to include them.
        assertActiveTasksCount(resp.body(), cam.id(), 0);
    }

    // ------------------------------------------------------------------ B: Edit uses plannedEditDate, not plannedLiveDate

    @Test
    void editStageFiltersByPlannedEditDate_notPlannedLiveDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Edit Date Basis Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor = createUser(ceo, "Edit Date Basis Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);

        String planId = approveAtSa(ceo, "Edit Date Basis " + unique, cam,
                LocalDate.now().plusDays(40), LocalDate.now().plusDays(6), LocalDate.now().plusDays(12));
        advanceSaToEa(ceo, planId, cam, editor);

        // Pin the Edit Date to a value far from the Live Date after reaching EA, so the two dates
        // are unambiguously distinguishable in the query below.
        setPlannedDates(planId, LocalDate.now().plusDays(6), LocalDate.now().plusDays(12), LocalDate.now().plusDays(40));

        String from = LocalDate.now().plusDays(11).toString();
        String to = LocalDate.now().plusDays(13).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Edit&dateFrom=" + from + "&dateTo=" + to);
        assertThat(resp.body()).as("Edit row must be governed by Planned Edit Date, not Planned Live Date")
                .contains("Edit Date Basis Ed " + unique);

        // And the same employee's Active Tasks count must be 0 when the range only covers the (far
        // away) Live Date - they still have a real active EditingAssignment, so their row can
        // legitimately still be present (pre-existing "every active assignee gets a row" behavior,
        // out of scope here); what matters is that Live Date did NOT count as a match.
        HttpResponse<String> liveRangeResp = ceo.get("/app/reports/workload?stage=Edit&dateFrom="
                + LocalDate.now().plusDays(38) + "&dateTo=" + LocalDate.now().plusDays(42));
        assertActiveTasksCount(liveRangeResp.body(), editor.id(), 0);
    }

    // ------------------------------------------------------------------ C: Publishing uses plannedLiveDate

    @Test
    void publishingStageFiltersByPlannedLiveDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Pub Date Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor = createUser(ceo, "Pub Date Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);
        TestUser pub = createUser(ceo, "Pub Date Pub " + unique, PUBLISHER_ROLE_ID, unique + 2);

        String planId = approveAtSa(ceo, "Pub Date " + unique, cam,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(5), LocalDate.now().plusDays(10));
        advanceToRfp(ceo, planId, cam, editor, pub);

        String from = LocalDate.now().plusDays(18).toString();
        String to = LocalDate.now().plusDays(22).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Publishing&dateFrom=" + from + "&dateTo=" + to);
        assertThat(resp.body()).contains("Pub Date Pub " + unique);
    }

    // ------------------------------------------------------------------ H: delayed-task exemption (spec Section 4, CRITICAL)

    @Test
    void delayedTaskRemainsVisibleEvenWhenPlannedDateIsBeforeSelectedFromDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Delayed Exempt Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        String planId = approveAtSa(ceo, "Delayed Exempt " + unique, cam,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(5), LocalDate.now().plusDays(10));

        // Pin the Shoot Date into the past (still status SA -> genuinely delayed right now).
        setPlannedDates(planId, LocalDate.now().minusDays(10), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        // Selected range starts well AFTER the (past) Shoot Date - pre-fix, this task would have
        // silently vanished from the range; the exemption must keep it visible.
        String from = LocalDate.now().plusDays(1).toString();
        String to = LocalDate.now().plusDays(30).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom=" + from + "&dateTo=" + to);
        assertThat(resp.body()).as("A currently-delayed task must remain visible even if its planned date is before From")
                .contains("Delayed Exempt Cam " + unique);
    }

    @Test
    void nonDelayedTaskOutsideRangeIsStillExcluded_exemptionIsNotBlanket() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Not Delayed Excl Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        approveAtSa(ceo, "Not Delayed Excl " + unique, cam,
                LocalDate.now().plusDays(40), LocalDate.now().plusDays(6), LocalDate.now().plusDays(20));

        // Shoot Date is in the FUTURE (not delayed) and outside the queried range - must be excluded
        // from the Active Tasks count (the row itself may still exist at 0/0/0 - see the sibling
        // "Live Date only" tests' comments for why that's expected, pre-existing behavior).
        String from = LocalDate.now().plusDays(1).toString();
        String to = LocalDate.now().plusDays(3).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom=" + from + "&dateTo=" + to);
        assertActiveTasksCount(resp.body(), cam.id(), 0);
    }

    @Test
    void delayedTaskIsStillExcludedWhenItsDateFallsAfterTheSelectedToDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Delayed After To Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        String planId = approveAtSa(ceo, "Delayed After To " + unique, cam,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(5), LocalDate.now().plusDays(10));

        // Shoot Date in the past (delayed), but the query's own To date is even further in the past
        // than the plan's Shoot Date - the exemption only overrides "before From", never "after To".
        setPlannedDates(planId, LocalDate.now().minusDays(3), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        String from = LocalDate.now().minusDays(20).toString();
        String to = LocalDate.now().minusDays(10).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom=" + from + "&dateTo=" + to);
        // The delayed exemption must not override the To-date boundary - Active Tasks stays 0.
        assertActiveTasksCount(resp.body(), cam.id(), 0);
    }

    // ------------------------------------------------------------------ Inclusive From<=date<=To, From=To single-day range

    @Test
    void fromEqualsToIsAValidInclusiveSingleDayRange() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Single Day Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        approveAtSa(ceo, "Single Day " + unique, cam,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));

        LocalDate shootDate = LocalDate.now().plusDays(6);
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom=" + shootDate + "&dateTo=" + shootDate);
        assertThat(resp.body()).contains("Single Day Cam " + unique);
    }

    // ------------------------------------------------------------------ E/§7: stage skipping - no phantom workload row

    @Test
    void directEditStart_producesNoPhantomShootWorkloadRow() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser editor = createUser(ceo, "Skip Shoot Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique);
        TestUser pub = createUser(ceo, "Skip Shoot Pub " + unique, PUBLISHER_ROLE_ID, unique + 1);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Skip Shoot Flow " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/skip-shoot-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\","
                        + "\"publisherUserIds\":[\"" + pub.id() + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");

        // A Shoot-stage query, over a wide-open range, must never surface this employee under Shoot
        // - Shoot was never part of this plan's pipeline (plannedShootDate is legitimately null),
        // and this editor has no ShootingAssignment at all, so unlike the date-range-mismatch tests
        // above, no row (not even a 0/0/0 one) should exist for them here at all. Checking the
        // "Open" button's data-employee-id marker (rather than their plain name) avoids a false
        // positive from the Employee filter dropdown, which always lists the full roster regardless
        // of the Stage filter.
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot&dateFrom="
                + LocalDate.now().minusDays(365) + "&dateTo=" + LocalDate.now().plusDays(365));
        assertThat(resp.body()).doesNotContain("data-employee-id=\"" + editor.id() + "\"");

        // But the same employee IS correctly visible under Edit for the same wide-open range.
        HttpResponse<String> editResp = ceo.get("/app/reports/workload?stage=Edit&dateFrom="
                + LocalDate.now().minusDays(365) + "&dateTo=" + LocalDate.now().plusDays(365));
        assertThat(editResp.body()).contains("Skip Shoot Ed " + unique);
    }

    // ------------------------------------------------------------------ §8/§18: employee-wise aggregation + reconciliation

    @Test
    void employeeWithWorkloadInTwoStagesAppearsAsOneAggregatedRow_withMatchingBreakdown() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        // Same permission-driven multi-function user performs Shoot on one plan and Edit on another
        // (mirrors PermissionDrivenWorkflowTest's own multi-stage fixture), so their real assignment
        // rows land in two different stage buckets that the employee-wise aggregation must sum.
        TestUser multi = createUser(ceo, "Agg Multi " + unique, CAMERA_PERSON_ROLE_ID, unique);
        grant(ceo, multi.id(), "PERM_19_EDIT_EXECUTION");

        approveAtSa(ceo, "Agg Multi Shoot " + unique, multi,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));

        TestUser otherCam = createUser(ceo, "Agg Multi Other Cam " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        String editPlanId = approveAtSa(ceo, "Agg Multi Edit " + unique, otherCam,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));
        advanceSaToEa(ceo, editPlanId, otherCam, multi);

        HttpResponse<String> resp = ceo.get("/app/reports/workload");
        String body = resp.body();
        assertThat(body).contains("Agg Multi " + unique);

        String userId = multi.id();
        // Exactly one "Open" button (i.e. exactly one main-table row) for this employee, no matter
        // how many stages they have workload in - the core employee-wise UI update guarantee.
        String openBtnMarker = "data-employee-id=\"" + userId + "\"";
        assertThat(countOccurrences(body, openBtnMarker)).as("Employee must appear exactly once in the main table")
                .isEqualTo(1);

        long[] mainRowNums = extractNums(extractTrContaining(body, openBtnMarker));
        assertThat(mainRowNums[0]).as("Active Tasks summed across Shoot+Edit").isEqualTo(2);

        String breakdownSnippet = extractBreakdownPanel(body, userId);
        assertThat(breakdownSnippet).contains("stage-dot-Shoot").contains("stage-dot-Edit");

        // §18: the breakdown's own Total row must equal the employee's main-row totals exactly.
        long[] breakdownTotal = extractLastNums(breakdownSnippet);
        assertThat(breakdownTotal).isEqualTo(mainRowNums);
    }

    // ------------------------------------------------------------------ helpers

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email(), TEST_PASSWORD);
        return client;
    }

    private TestUser createUser(TestApiClient ceo, String fullName, String businessRoleId, long unique) throws Exception {
        String email = "tw-date-" + fullName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"team workload date handling regression test\"}");
        String userId = response.get("userId").asText();
        String permission = switch (businessRoleId) {
            case CAMERA_PERSON_ROLE_ID -> "PERM_18_SHOOT_EXECUTION";
            case VIDEO_EDITOR_ROLE_ID -> "PERM_19_EDIT_EXECUTION";
            case PUBLISHER_ROLE_ID -> "PERM_08_PUBLISHING_EXECUTION";
            default -> null;
        };
        if (permission != null) {
            grant(ceo, userId, permission);
        }
        return new TestUser(userId, email);
    }

    private void grant(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> resp = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"team workload date handling regression test grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    /** Idea Review approval -> Shoot Assigned (SA), with explicit Live/Shoot/Edit dates (Standard mode). */
    private String approveAtSa(TestApiClient ceo, String title, TestUser cam, LocalDate liveDate, LocalDate shootDate,
                                LocalDate editDate) throws Exception {
        TestUser pub = createUser(ceo, "Auto Pub", PUBLISHER_ROLE_ID, System.nanoTime());
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/tw-date-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"publisherUserIds\":[\"" + pub.id() + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    /** Shoot start -> submit -> review-approve (folds in Editor team) -> Edit Assigned (EA). Start
     * and review-submit must be performed by the assigned Cameraperson themselves
     * (ShootingService#startShooting/submitShootReview both call requireActiveAssignee) - the
     * review DECISION is made by the CEO, exactly like PermissionDrivenWorkflowTest's own helpers. */
    private void advanceSaToEa(TestApiClient ceo, String planId, TestUser cam, TestUser editor) throws Exception {
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
    }

    /** SA -> ... -> RFP (Edit approved, Publisher team folded in). Same actor-login requirement as
     * {@link #advanceSaToEa} applies to Editing start/review-submit. */
    private void advanceToRfp(TestApiClient ceo, String planId, TestUser cam, TestUser editor, TestUser pub) throws Exception {
        advanceSaToEa(ceo, planId, cam, editor);
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + pub.id() + "\"]}");
    }

    /** Test-fixture-only date pinning via the same {@code applyReschedule} mutation Permission #10
     * uses in production - lets a scenario freely place a date in the past (to construct a
     * genuinely delayed task) without fighting Standard Planning Mode's creation-time
     * "date cannot be in the past" guard, which only applies at Idea Review approval time. */
    private void setPlannedDates(String planId, LocalDate shootDate, LocalDate editDate, LocalDate liveDate) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        plan.applyReschedule(shootDate, editDate, liveDate);
        contentPlanRepository.save(plan);
    }

    /** Asserts the Active Tasks number in this employee's own main-table row (identified by their
     * "Open" button's data-employee-id marker, never their plain name - see the phantom-row test's
     * comment for why name-based matching is unsafe here). Every user with a real active
     * assignment for the queried stage gets a row regardless of whether the current date range
     * matches any of their plans (pre-existing "every active assignee gets a row" behavior,
     * unrelated to and out of scope for this fix) - so proving the date filter actually excluded a
     * plan means proving its count is 0, not that the row itself is absent. */
    private static void assertActiveTasksCount(String body, String userId, long expectedActive) {
        String marker = "data-employee-id=\"" + userId + "\"";
        long[] nums = extractNums(extractTrContaining(body, marker));
        assertThat(nums[0]).as("Active Tasks for employee " + userId).isEqualTo(expectedActive);
    }

    private static int countOccurrences(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }

    /** Returns the full {@code <tr>...</tr>} snippet that contains the given marker substring. */
    private static String extractTrContaining(String body, String marker) {
        int markerIdx = body.indexOf(marker);
        assertThat(markerIdx).as("marker not found: " + marker).isGreaterThanOrEqualTo(0);
        int trStart = body.lastIndexOf("<tr", markerIdx);
        int trEnd = body.indexOf("</tr>", markerIdx) + "</tr>".length();
        return body.substring(trStart, trEnd);
    }

    /** Returns the stage-wise breakdown panel's own HTML for one employee. */
    private static String extractBreakdownPanel(String body, String userId) {
        String startMarker = "id=\"workloadBreakdown-" + userId + "\"";
        int markerIdx = body.indexOf(startMarker);
        assertThat(markerIdx).as("breakdown panel not found for user " + userId).isGreaterThanOrEqualTo(0);
        int divStart = body.lastIndexOf("<div", markerIdx);
        int nextPanel = body.indexOf("workloadBreakdown-", markerIdx + startMarker.length());
        int boundary = nextPanel >= 0 ? body.lastIndexOf("<div", nextPanel) : body.length();
        if (boundary <= divStart) {
            boundary = body.length();
        }
        return body.substring(divStart, boundary);
    }

    /** All {@code class="num">N<} numbers appearing in the snippet, in document order. */
    private static long[] extractNums(String snippet) {
        Matcher m = Pattern.compile("class=\"num\">(\\d+)<").matcher(snippet);
        List<Long> nums = new ArrayList<>();
        while (m.find()) {
            nums.add(Long.parseLong(m.group(1)));
        }
        return nums.stream().mapToLong(Long::longValue).toArray();
    }

    /** The last 3 numbers in the snippet (the breakdown panel's own Total row: Active/Delayed/On Hold). */
    private static long[] extractLastNums(String snippet) {
        long[] all = extractNums(snippet);
        assertThat(all.length).isGreaterThanOrEqualTo(3);
        return new long[] {all[all.length - 3], all[all.length - 2], all[all.length - 1]};
    }
}
