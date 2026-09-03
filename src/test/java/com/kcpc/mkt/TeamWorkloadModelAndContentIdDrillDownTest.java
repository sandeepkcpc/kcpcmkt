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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TEAM -&gt; WORKLOAD - MODEL DATE BASIS + EMPLOYEE -&gt; STAGE -&gt; CONTENT ID DRILL-DOWN
 * regression suite. Complements {@link TeamWorkloadDateHandlingTest} (which already covers
 * Shoot/Edit/Publishing's own stage-date mapping, the delayed-task exemption in both directions,
 * inclusive range semantics, stage-skipping producing no phantom row, and employee-wise
 * aggregation reconciliation - none of that is re-proven here) by covering exactly what changed in
 * this task:
 * <ul>
 *   <li>Model's applicable date is {@code plannedShootDate}, never {@code plannedLiveDate}, and
 *   "Model" is now itself a selectable Stage filter value (previously only reachable under
 *   "All Stages").</li>
 *   <li>"All Stages" applies each stage's own date simultaneously, in one query.</li>
 *   <li>The new per-stage Content ID drill-down ({@code AssigneeLoadRow#items}, rendered by
 *   {@code team-workload-content.jspf}) always reconciles with the Active Tasks count next to it,
 *   shows the real Content ID and links to the existing Content Detail route
 *   ({@code DeliverableMvcController}'s {@code /app/deliverables/{id}}), and continues to respect
 *   Delayed Only and every other composed filter - because it is built from the exact same
 *   surviving records the counts themselves come from (see {@code TeamWorkloadService}'s own
 *   javadoc on {@code rowFromAssignments}/{@code modelRow}).</li>
 * </ul>
 * Same fixture style as {@link TeamWorkloadDateHandlingTest}: plans are advanced through the real
 * approval/execution API, dates pinned via {@link ContentPlan#applyReschedule} purely as a test
 * fixture tool.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeamWorkloadModelAndContentIdDrillDownTest {

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String TEST_PASSWORD = "Passw0rd!";

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private record TestUser(String id, String email) {
    }

    // ------------------------------------------------------------------ #1: Model uses plannedShootDate, not plannedLiveDate

    @Test
    void modelUsesPlannedShootDate_notPlannedLiveDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Model Date Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Model Date Talent " + unique, MODEL_ROLE_ID, unique + 1);

        approveAtSaWithModel(ceo, "Model Date Basis " + unique, cam, model,
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(6), LocalDate.now().plusDays(20));

        // Range covers the Shoot Date (+6d) but not the Live Date (+30d) - Model must be governed
        // by the Shoot Date, exactly like the Shoot stage itself.
        String from = LocalDate.now().plusDays(5).toString();
        String to = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Model&dateFrom=" + from + "&dateTo=" + to);
        assertThat(resp.body()).as("Model row must be governed by Planned Shoot Date")
                .contains("Model Date Talent " + unique);
        assertActiveTasksCount(resp.body(), model.id(), 1);

        // Range covers only the (far away) Live Date - must NOT count the Model as active; proves
        // plannedLiveDate is not (even incidentally) used for Model.
        HttpResponse<String> liveOnly = ceo.get("/app/reports/workload?stage=Model&dateFrom="
                + LocalDate.now().plusDays(28) + "&dateTo=" + LocalDate.now().plusDays(32));
        assertActiveTasksCount(liveOnly.body(), model.id(), 0);
    }

    // ------------------------------------------------------------------ #1b: Model is a selectable Stage filter value

    @Test
    void modelStageFilterIsSelectable_isolatesModelFromOtherStages() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Model Filter Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Model Filter Talent " + unique, MODEL_ROLE_ID, unique + 1);
        approveAtSaWithModel(ceo, "Model Filter " + unique, cam, model,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));

        HttpResponse<String> modelOnly = ceo.get("/app/reports/workload?stage=Model");
        assertThat(modelOnly.body()).contains("Model Filter Talent " + unique);
        assertThat(modelOnly.body()).as("Stage=Model must not also surface the Cameraperson row")
                .doesNotContain("data-employee-id=\"" + cam.id() + "\"");

        HttpResponse<String> shootOnly = ceo.get("/app/reports/workload?stage=Shoot");
        assertThat(shootOnly.body()).contains("Model Filter Cam " + unique);
        assertThat(shootOnly.body()).as("Stage=Shoot must not also surface the Model row")
                .doesNotContain("data-employee-id=\"" + model.id() + "\"");
    }

    // ------------------------------------------------------------------ #5: All Stages applies each stage's own date simultaneously

    @Test
    void allStagesAppliesEachStagesOwnDateInOneQuery() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "All Stages Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "All Stages Model " + unique, MODEL_ROLE_ID, unique + 1);
        // Kept at SA (Shoot still active) so cam/model's own ShootingAssignment/TalentEntry window
        // stays "active" - advancing this same plan past Shoot would correctly deactivate their
        // Shoot-stage row (a different, already-covered concern; see
        // TeamWorkloadDateHandlingTest#directEditStart_producesNoPhantomShootWorkloadRow), which
        // would defeat the point of this test.
        approveAtSaWithModel(ceo, "All Stages Shoot " + unique, cam, model,
                LocalDate.now().plusDays(40), LocalDate.now().plusDays(6), LocalDate.now().plusDays(20));

        // A separate plan, advanced to EA, gives the Edit stage its own real active row governed
        // by a deliberately different (out-of-window) Edit Date.
        TestUser cam2 = createUser(ceo, "All Stages Cam Two " + unique, CAMERA_PERSON_ROLE_ID, unique + 2);
        TestUser editor = createUser(ceo, "All Stages Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 3);
        String editPlanId = approveAtSa(ceo, "All Stages Edit " + unique, cam2,
                LocalDate.now().plusDays(40), LocalDate.now().plusDays(6), LocalDate.now().plusDays(20));
        advanceSaToEa(ceo, editPlanId, cam2, editor);

        // A narrow window covering ONLY the Shoot Date - under All Stages, Shoot and Model (both
        // shoot-date-governed) must be included; Edit (edit-date-governed, +20d) must be excluded.
        String from = LocalDate.now().plusDays(5).toString();
        String to = LocalDate.now().plusDays(7).toString();
        HttpResponse<String> resp = ceo.get("/app/reports/workload?dateFrom=" + from + "&dateTo=" + to);
        assertActiveTasksCount(resp.body(), cam.id(), 1);
        assertActiveTasksCount(resp.body(), model.id(), 1);
        assertActiveTasksCount(resp.body(), editor.id(), 0);
    }

    // ------------------------------------------------------------------ #9/#10/#11: Content ID drill-down reconciles, shows real IDs, links to existing route

    @Test
    void contentIdDrillDownReconcilesAndLinksToExistingDetailRoute() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam1 = createUser(ceo, "Drill Cam One " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser cam2 = createUser(ceo, "Drill Cam Two " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Drill Editor " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String plan1 = approveAtSa(ceo, "Drill Plan One " + unique, cam1,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));
        advanceSaToEa(ceo, plan1, cam1, editor);
        String plan2 = approveAtSa(ceo, "Drill Plan Two " + unique, cam2,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(7), LocalDate.now().plusDays(11));
        advanceSaToEa(ceo, plan2, cam2, editor);

        String contentId1 = contentPlanRepository.findById(java.util.UUID.fromString(plan1)).orElseThrow().getContentId();
        String contentId2 = contentPlanRepository.findById(java.util.UUID.fromString(plan2)).orElseThrow().getContentId();

        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Edit");
        String panel = extractBreakdownPanel(resp.body(), editor.id());

        // #9: displayed item count must equal the Active Tasks number for that employee.
        assertActiveTasksCount(resp.body(), editor.id(), 2);
        assertThat(countOccurrences(panel, "idea-id-link")).as("Content ID link count must equal Active Tasks")
                .isEqualTo(2);

        // #10: the actual business Content IDs must appear, verbatim.
        assertThat(panel).contains(contentId1).contains(contentId2);

        // #11: each Content ID must link to the EXISTING Content Detail route
        // (DeliverableMvcController's /app/deliverables/{id}) - never a new detail page.
        assertThat(panel).contains("/app/deliverables/" + plan1).contains("/app/deliverables/" + plan2);
    }

    // ------------------------------------------------------------------ #13: Delayed Only scopes the Content ID drill-down too

    @Test
    void delayedOnlyLimitsContentIdDrillDownToDelayedItemsOnly() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam1 = createUser(ceo, "Delayed Drill Cam One " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser cam2 = createUser(ceo, "Delayed Drill Cam Two " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Delayed Drill Editor " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String delayedPlan = approveAtSa(ceo, "Delayed Drill Plan " + unique, cam1,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));
        advanceSaToEa(ceo, delayedPlan, cam1, editor);
        // Edit Date pinned into the past while still mid-edit -> genuinely delayed right now.
        // Shoot Date must stay <= Edit Date (chronology invariant, ERD-CON-066), so it moves into
        // the past too - harmless here since only the Edit stage/date is under test.
        setPlannedDates(delayedPlan, LocalDate.now().minusDays(10), LocalDate.now().minusDays(3), LocalDate.now().plusDays(20));
        String delayedContentId = contentPlanRepository.findById(java.util.UUID.fromString(delayedPlan))
                .orElseThrow().getContentId();

        String onTimePlan = approveAtSa(ceo, "On Time Drill Plan " + unique, cam2,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(15));
        advanceSaToEa(ceo, onTimePlan, cam2, editor);
        String onTimeContentId = contentPlanRepository.findById(java.util.UUID.fromString(onTimePlan))
                .orElseThrow().getContentId();

        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Edit&delayedOnly=true");
        String panel = extractBreakdownPanel(resp.body(), editor.id());
        assertThat(panel).as("Delayed Only must keep the delayed item's Content ID visible")
                .contains(delayedContentId);
        assertThat(panel).as("Delayed Only must exclude the on-time item's Content ID from the drill-down")
                .doesNotContain(onTimeContentId);
        assertThat(panel).contains("Delayed");
    }

    // ------------------------------------------------------------------ #12: existing filters continue to compose (Employee + Stage + Date Range)

    @Test
    void employeeAndStageAndDateRangeFiltersComposeCorrectlyWithDrillDown() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Compose Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser editor1 = createUser(ceo, "Compose Ed One " + unique, VIDEO_EDITOR_ROLE_ID, unique + 1);
        TestUser editor2 = createUser(ceo, "Compose Ed Two " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String plan1 = approveAtSa(ceo, "Compose Plan One " + unique, cam,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));
        advanceSaToEa(ceo, plan1, cam, editor1);
        TestUser cam2 = createUser(ceo, "Compose Cam Two " + unique, CAMERA_PERSON_ROLE_ID, unique + 3);
        String plan2 = approveAtSa(ceo, "Compose Plan Two " + unique, cam2,
                LocalDate.now().plusDays(20), LocalDate.now().plusDays(6), LocalDate.now().plusDays(10));
        advanceSaToEa(ceo, plan2, cam2, editor2);

        String contentId1 = contentPlanRepository.findById(java.util.UUID.fromString(plan1)).orElseThrow().getContentId();

        // Employee filter narrows the whole result (and its drill-down) to editor1 only.
        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Edit&employeeId=" + editor1.id()
                + "&dateFrom=" + LocalDate.now().plusDays(9) + "&dateTo=" + LocalDate.now().plusDays(11));
        assertThat(resp.body()).contains("Compose Ed One " + unique);
        assertThat(resp.body()).as("Employee filter must exclude the other editor's row entirely")
                .doesNotContain("data-employee-id=\"" + editor2.id() + "\"");
        String panel = extractBreakdownPanel(resp.body(), editor1.id());
        assertThat(panel).contains(contentId1);
    }

    // ------------------------------------------------------------------ helpers (same style as TeamWorkloadDateHandlingTest)

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
        String email = "tw-drill-" + fullName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"team workload content id drill-down regression test\"}");
        String userId = response.get("userId").asText();
        String permission = switch (businessRoleId) {
            case CAMERA_PERSON_ROLE_ID -> "PERM_18_SHOOT_EXECUTION";
            case VIDEO_EDITOR_ROLE_ID -> "PERM_19_EDIT_EXECUTION";
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
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"team workload content id drill-down regression test grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    private String approveAtSa(TestApiClient ceo, String title, TestUser cam, LocalDate liveDate, LocalDate shootDate,
                                LocalDate editDate) throws Exception {
        TestUser pub = createUser(ceo, "Auto Pub", PUBLISHER_ROLE_ID, System.nanoTime());
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/tw-drill-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"publisherUserIds\":[\"" + pub.id() + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    /** Same as {@link #approveAtSa}, plus a {@code talentUserIds} entry linking the given Model. */
    private String approveAtSaWithModel(TestApiClient ceo, String title, TestUser cam, TestUser model,
                                         LocalDate liveDate, LocalDate shootDate, LocalDate editDate) throws Exception {
        TestUser pub = createUser(ceo, "Auto Pub", PUBLISHER_ROLE_ID, System.nanoTime());
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/tw-drill-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"talentUserIds\":[\"" + model.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + pub.id() + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    private void advanceSaToEa(TestApiClient ceo, String planId, TestUser cam, TestUser editor) throws Exception {
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
    }

    private void setPlannedDates(String planId, LocalDate shootDate, LocalDate editDate, LocalDate liveDate) {
        ContentPlan plan = contentPlanRepository.findById(java.util.UUID.fromString(planId)).orElseThrow();
        plan.applyReschedule(shootDate, editDate, liveDate);
        contentPlanRepository.save(plan);
    }

    private static void assertActiveTasksCount(String body, String userId, long expectedActive) {
        String marker = "data-employee-id=\"" + userId + "\"";
        int markerIdx = body.indexOf(marker);
        assertThat(markerIdx).as("marker not found: " + marker).isGreaterThanOrEqualTo(0);
        int trStart = body.lastIndexOf("<tr", markerIdx);
        int trEnd = body.indexOf("</tr>", markerIdx) + "</tr>".length();
        String tr = body.substring(trStart, trEnd);
        java.util.regex.Matcher m = Pattern.compile("class=\"num\">(\\d+)<").matcher(tr);
        assertThat(m.find()).as("no numeric cells found in row for " + userId).isTrue();
        assertThat(Long.parseLong(m.group(1))).as("Active Tasks for employee " + userId).isEqualTo(expectedActive);
    }

    private static int countOccurrences(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }

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
}
