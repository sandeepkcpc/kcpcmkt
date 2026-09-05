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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Team -&gt; Workload: a Model's Active Tasks count must be gated by the SHOOT stage actually being
 * active, not merely by the Content Plan being "not yet closed out." Before this fix,
 * {@code TeamWorkloadService#modelRow} used {@code isActiveStatus} (excludes only
 * COMP/CAN/RJ/RET), so a Model kept counting as having an active Shoot task even after Shoot
 * completed (folded into Edit) or was skipped straight past. The fix reuses the exact same
 * {@code AssigneeActiveWindows#SHOOT} window Cameraperson's own Shoot row already uses.
 *
 * <p>Complements {@link TeamWorkloadModelAndContentIdDrillDownTest} (date basis, stage filter,
 * drill-down mechanics for Model - none of that is re-proven here) by covering exactly the
 * completed/skipped-Shoot deactivation gap that was missing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeamWorkloadModelActiveStageTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String TEST_PASSWORD = "Passw0rd!";

    private record TestUser(String id, String email) {
    }

    // ------------------------------------------------------------------ fixture helpers

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
        String email = "tw-model-" + fullName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"team workload model active stage regression test\"}");
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
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"team workload model active stage regression test grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    /** Approves an Idea straight to Shoot Assigned (SA) with the given Cameraperson, Model(s) and a
     * mandatory Publisher, pinning Shoot/Edit/Live dates explicitly. */
    private String approveToShootAssigned(TestApiClient ceo, String title, TestUser cam, List<TestUser> models,
                                           LocalDate shootDate, LocalDate editDate, LocalDate liveDate) throws Exception {
        TestUser pub = createUser(ceo, "Auto Pub", PUBLISHER_ROLE_ID, System.nanoTime());
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");
        StringBuilder modelJson = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) modelJson.append(',');
            modelJson.append('"').append(models.get(i).id()).append('"');
        }
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/tw-model-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"talentUserIds\":[" + modelJson + "],"
                        + "\"publisherUserIds\":[\"" + pub.id() + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    /** SA -&gt; Edit Assigned (EA) via a real Shoot Review approval - Shoot genuinely "completed". */
    private void completeShoot(TestApiClient ceo, String planId, TestUser cam, TestUser editor) throws Exception {
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");
    }

    /** SA -&gt; Edit Assigned (EA) via Skip Shoot Stage (PERM_20_SKIP_STAGE) - Shoot genuinely
     * "skipped", never completed through a real Shoot Review. */
    private void skipShoot(TestApiClient ceo, String planId, TestUser editor) throws Exception {
        grant(ceo, editor.id(), "PERM_19_EDIT_EXECUTION");
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("reason", "team workload model active stage regression test - skip shoot");
        params.put("editorUserIds", editor.id());
        params.put("leadEditorUserId", editor.id());
        Map<String, String> stringParams = new LinkedHashMap<>();
        params.forEach((k, v) -> stringParams.put(k, String.valueOf(v)));
        HttpResponse<String> resp = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/skip", stringParams);
        assertThat(resp.statusCode()).isEqualTo(200);
    }

    private void setPlannedDates(String planId, LocalDate shootDate, LocalDate editDate, LocalDate liveDate) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
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
        Pattern.compile("class=\"num\">(\\d+)<").matcher(tr);
        java.util.regex.Matcher m = Pattern.compile("class=\"num\">(\\d+)<").matcher(tr);
        assertThat(m.find()).as("no numeric cells found in row for " + userId).isTrue();
        assertThat(Long.parseLong(m.group(1))).as("Active Tasks for employee " + userId).isEqualTo(expectedActive);
    }

    /** The row's second numeric cell is Delayed Tasks (Active, Delayed, On Hold, in that order). */
    private static void assertDelayedTasksCount(String body, String userId, long expectedDelayed) {
        String marker = "data-employee-id=\"" + userId + "\"";
        int markerIdx = body.indexOf(marker);
        assertThat(markerIdx).as("marker not found: " + marker).isGreaterThanOrEqualTo(0);
        int trStart = body.lastIndexOf("<tr", markerIdx);
        int trEnd = body.indexOf("</tr>", markerIdx) + "</tr>".length();
        String tr = body.substring(trStart, trEnd);
        java.util.regex.Matcher m = Pattern.compile("class=\"num\">(\\d+)<").matcher(tr);
        assertThat(m.find()).as("Active cell not found for " + userId).isTrue();
        assertThat(m.find()).as("Delayed cell not found for " + userId).isTrue();
        assertThat(Long.parseLong(m.group(1))).as("Delayed Tasks for employee " + userId).isEqualTo(expectedDelayed);
    }

    private static boolean rowExistsFor(String body, String userId) {
        return body.contains("data-employee-id=\"" + userId + "\"");
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

    private static int countOccurrences(String haystack, String needle) {
        return haystack.split(Pattern.quote(needle), -1).length - 1;
    }

    // ------------------------------------------------------------------ 1: pending Shoot -> Active = 1

    @Test
    void modelAssignedToPendingShootCountsAsOneActiveTask() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Pending Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Pending Model " + unique, MODEL_ROLE_ID, unique + 1);

        approveToShootAssigned(ceo, "TWM Pending " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Model");
        assertActiveTasksCount(resp.body(), model.id(), 1);
    }

    // ------------------------------------------------------------------ 2: Shoot completed -> Active = 0

    @Test
    void modelActiveTasksDropToZeroAfterShootCompletion() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Completed Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Completed Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Completed Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "TWM Completed " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        assertActiveTasksCount(ceo.get("/app/reports/workload?stage=Model").body(), model.id(), 1);

        completeShoot(ceo, planId, cam, editor);

        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Model");
        assertActiveTasksCount(resp.body(), model.id(), 0);
    }

    // ------------------------------------------------------------------ 3: Shoot skipped -> Active = 0

    @Test
    void modelActiveTasksDropToZeroAfterShootSkipped() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Skipped Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Skipped Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Skipped Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "TWM Skipped " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        assertActiveTasksCount(ceo.get("/app/reports/workload?stage=Model").body(), model.id(), 1);

        skipShoot(ceo, planId, editor);

        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Model");
        assertActiveTasksCount(resp.body(), model.id(), 0);
    }

    // ------------------------------------------------------------------ 4/5: multiple Models on one Shoot

    @Test
    void multipleModelsOnTheSameShootEachCountActiveWhileShootIsActiveThenAllDropToZeroOnCompletion() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Multi Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser modelA = createUser(ceo, "Multi Model A " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser modelB = createUser(ceo, "Multi Model B " + unique, MODEL_ROLE_ID, unique + 2);
        TestUser editor = createUser(ceo, "Multi Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 3);

        String planId = approveToShootAssigned(ceo, "TWM Multi " + unique, cam, List.of(modelA, modelB),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        String duringShoot = ceo.get("/app/reports/workload?stage=Model").body();
        assertActiveTasksCount(duringShoot, modelA.id(), 1);
        assertActiveTasksCount(duringShoot, modelB.id(), 1);

        completeShoot(ceo, planId, cam, editor);

        String afterCompletion = ceo.get("/app/reports/workload?stage=Model").body();
        assertActiveTasksCount(afterCompletion, modelA.id(), 0);
        assertActiveTasksCount(afterCompletion, modelB.id(), 0);
    }

    // ------------------------------------------------------------------ 6: Cameraperson workload unaffected

    @Test
    void camerapersonActiveWorkloadStillFollowsItsOwnShootWindowUnaffectedByTheModelFix() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Regression Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Regression Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Regression Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "TWM CamRegression " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        assertActiveTasksCount(ceo.get("/app/reports/workload?stage=Shoot").body(), cam.id(), 1);

        completeShoot(ceo, planId, cam, editor);

        HttpResponse<String> resp = ceo.get("/app/reports/workload?stage=Shoot");
        // Cameraperson's own Shoot-stage deactivation on completion is pre-existing behavior,
        // unchanged by this fix - still 0 Active Tasks once the plan moves past Shoot.
        assertActiveTasksCount(resp.body(), cam.id(), 0);
    }

    // ------------------------------------------------------------------ 7: Edit and Publishing workload unaffected

    @Test
    void editAndPublishingWorkloadAreUnaffectedByTheModelFix() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "EdPub Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "EdPub Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "EdPub Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "TWM EdPub " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        // Before Shoot completes, Edit has no active row for this editor yet.
        assertThat(rowExistsFor(ceo.get("/app/reports/workload?stage=Edit").body(), editor.id())).isFalse();

        completeShoot(ceo, planId, cam, editor);

        // After Shoot completes/folds into Edit, the Editor correctly gains an active Edit task -
        // this Model-only fix must not have disturbed that pre-existing Edit workload behavior.
        assertActiveTasksCount(ceo.get("/app/reports/workload?stage=Edit").body(), editor.id(), 1);
    }

    // ------------------------------------------------------------------ 8: delayed-count behavior unchanged

    @Test
    void modelDelayedCountFollowsTheSameShootWindowAsActiveCount() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Delayed Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Delayed Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Delayed Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "TWM Delayed " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        // Shoot Date pinned into the past while still SA -> genuinely delayed right now. Set via
        // direct repository reschedule (test fixture only, same pattern
        // TeamWorkloadModelAndContentIdDrillDownTest#setPlannedDates uses), since Planning approval
        // itself validates against past dates.
        setPlannedDates(planId, LocalDate.now().minusDays(3), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        String duringShoot = ceo.get("/app/reports/workload?stage=Model").body();
        assertActiveTasksCount(duringShoot, model.id(), 1);
        assertDelayedTasksCount(duringShoot, model.id(), 1);

        completeShoot(ceo, planId, cam, editor);

        HttpResponse<String> afterCompletion = ceo.get("/app/reports/workload?stage=Model");
        // Delayed must drop to 0 alongside Active once Shoot completes - no separate delayed-only
        // leftover row.
        assertActiveTasksCount(afterCompletion.body(), model.id(), 0);
        assertDelayedTasksCount(afterCompletion.body(), model.id(), 0);
    }

    // ------------------------------------------------------------------ 9: employee summary + drill-down remain consistent

    @Test
    void employeeSummaryAndContentIdDrillDownStayConsistentForModelBeforeAndAfterCompletion() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser cam = createUser(ceo, "Drill Cam " + unique, CAMERA_PERSON_ROLE_ID, unique);
        TestUser model = createUser(ceo, "Drill Model " + unique, MODEL_ROLE_ID, unique + 1);
        TestUser editor = createUser(ceo, "Drill Ed " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "TWM Drill " + unique, cam, List.of(model),
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        String contentId = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow().getContentId();

        // Employee-wise summary (All Stages, no stage filter) must equal the per-stage Model count,
        // and the drill-down panel must show exactly one Content ID link matching Active Tasks.
        String allStages = ceo.get("/app/reports/workload").body();
        assertActiveTasksCount(allStages, model.id(), 1);
        String panel = extractBreakdownPanel(allStages, model.id());
        assertThat(countOccurrences(panel, "idea-id-link")).isEqualTo(1);
        assertThat(panel).contains(contentId);

        completeShoot(ceo, planId, cam, editor);

        HttpResponse<String> afterCompletion = ceo.get("/app/reports/workload");
        // Employee-wise summary must sum back down to 0 too, by construction (it's a genuine SUM
        // over the employee's own per-stage rows - see TeamWorkloadService's own class javadoc).
        assertActiveTasksCount(afterCompletion.body(), model.id(), 0);
    }
}
