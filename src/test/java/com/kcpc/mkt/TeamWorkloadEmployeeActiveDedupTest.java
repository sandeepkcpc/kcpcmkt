package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Team -&gt; Workload: an employee's Active Tasks must be counted as the number of DISTINCT
 * active Content IDs, never the number of underlying role/stage rows. Before this fix,
 * {@code TeamWorkloadService}'s employee-level aggregation summed every stage row's own
 * {@code activeTasks} ({@code stageRows.stream().mapToLong(AssigneeLoadRow::getActiveTasks).sum()}),
 * so an employee holding more than one role (e.g. Model + Cameraperson) on the SAME Content ID
 * while that content's relevant stage was active got counted twice for what is really one piece
 * of work. The fix counts distinct {@code WorkloadContentItem#getContentPlanId()} values across
 * all of the employee's own stage rows' {@code items} instead - reusing the exact data already
 * computed by the stage-lifecycle window filters, never a new query.
 *
 * <p>Delayed/On Hold are deliberately NOT deduplicated (unchanged, still a plain sum) - this file
 * explicitly proves that distinction (see {@link #delayedTasksRemainASumNotDeduplicatedByContentId}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TeamWorkloadEmployeeActiveDedupTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PerformanceObligationRepository obligationRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";
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
        String email = "tw-dedup-" + fullName.toLowerCase().replaceAll("[^a-z0-9]+", "-") + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"team workload employee active dedup regression test\"}");
        String userId = response.get("userId").asText();
        return new TestUser(userId, email);
    }

    private void grant(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> resp = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"team workload employee active dedup regression test grant\"}");
        if (resp.statusCode() != 201) {
            throw new IllegalStateException("Grant failed: " + resp.statusCode() + " " + resp.body());
        }
    }

    /** Approves an Idea straight to Shoot Assigned (SA). {@code camerapersons}/{@code models} may
     * hold the same person (or several); the given Planning-time Publisher grant is mandatory but
     * otherwise unrelated to this test's own assertions. */
    private String approveToShootAssigned(TestApiClient ceo, String title, List<TestUser> camerapersons,
                                           List<TestUser> models, TestUser publisher, LocalDate shootDate,
                                           LocalDate editDate, LocalDate liveDate) throws Exception {
        StringBuilder camJson = new StringBuilder();
        for (int i = 0; i < camerapersons.size(); i++) {
            if (i > 0) camJson.append(',');
            camJson.append('"').append(camerapersons.get(i).id()).append('"');
        }
        StringBuilder modelJson = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) modelJson.append(',');
            modelJson.append('"').append(models.get(i).id()).append('"');
        }
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/tw-dedup-" + Instant.now().toEpochMilli() + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\",\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}],"
                        + "\"camerapersonUserIds\":[" + camJson + "],\"talentUserIds\":[" + modelJson + "],"
                        + "\"publisherUserIds\":[\"" + publisher.id() + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        return plan.getId().toString();
    }

    /** SA -&gt; Edit Assigned (EA) via a real Shoot Review approval, folding in the given Editor(s). */
    private void completeShoot(TestApiClient ceo, String planId, TestUser shootActor, List<TestUser> qualifying,
                                List<TestUser> editors, TestUser leadEditor) throws Exception {
        TestApiClient actor = loginAs(shootActor);
        actor.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        actor.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        StringBuilder qualJson = new StringBuilder();
        for (int i = 0; i < qualifying.size(); i++) {
            if (i > 0) qualJson.append(',');
            qualJson.append('"').append(qualifying.get(i).id()).append('"');
        }
        StringBuilder edJson = new StringBuilder();
        for (int i = 0; i < editors.size(); i++) {
            if (i > 0) edJson.append(',');
            edJson.append('"').append(editors.get(i).id()).append('"');
        }
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[" + qualJson + "],"
                        + "\"editorUserIds\":[" + edJson + "],\"leadEditorUserId\":\"" + leadEditor.id() + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");
    }

    /** EA -&gt; Ready for Publishing (RFP) via a real Edit Review approval, folding in the given
     * Publisher(s). */
    private void completeEdit(TestApiClient ceo, String planId, TestUser editActor, List<TestUser> qualifying,
                               List<TestUser> publishers) throws Exception {
        TestApiClient actor = loginAs(editActor);
        actor.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        actor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        StringBuilder qualJson = new StringBuilder();
        for (int i = 0; i < qualifying.size(); i++) {
            if (i > 0) qualJson.append(',');
            qualJson.append('"').append(qualifying.get(i).id()).append('"');
        }
        StringBuilder pubJson = new StringBuilder();
        for (int i = 0; i < publishers.size(); i++) {
            if (i > 0) pubJson.append(',');
            pubJson.append('"').append(publishers.get(i).id()).append('"');
        }
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[" + qualJson + "],"
                        + "\"publisherUserIds\":[" + pubJson + "]}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");
    }

    /** RFP -&gt; Completed (COMP) via a real publication event + performance scorecard, the same
     * golden path {@code GoldenEndToEndFlowTest} uses. */
    private void completePublishing(TestApiClient ceo, String planId, TestUser publishActor) throws Exception {
        TestApiClient actor = loginAs(publishActor);
        actor.post("/api/v1/content-plans/" + planId + "/publishing/start", "");
        String outputId = plannedOutputRepository.findByContentPlan(
                        contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow())
                .stream().findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        actor.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/tw-dedup-" + Instant.now().toEpochMilli() + "\"}");
        String obligationId = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId)).stream()
                .findFirst().map(o -> o.getId().toString()).orElseThrow();
        ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"hookRatePercent\":80.00,\"hookRateIsNa\":false,\"holdRateIsNa\":true,"
                        + "\"views\":5000,\"avgViewDurationIsNa\":true}");
        JsonNode submitted = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
        assertThat(submitted.get("submitted").asBoolean()).isTrue();
        JsonNode finalPlan = ceo.getJson("/api/v1/content-plans/" + planId);
        assertThat(finalPlan.get("status").asText()).isEqualTo("COMP");
    }

    /** SA/SIP/SRV -&gt; Edit Assigned (EA) via Skip Shoot Stage (PERM_20_SKIP_STAGE), never a real
     * Shoot Review. */
    private void skipShoot(TestApiClient ceo, String planId, TestUser editor) throws Exception {
        grant(ceo, editor.id(), "PERM_19_EDIT_EXECUTION");
        LinkedHashMap<String, Object> params = new LinkedHashMap<>();
        params.put("reason", "team workload employee active dedup regression test - skip shoot");
        params.put("editorUserIds", editor.id());
        params.put("leadEditorUserId", editor.id());
        Map<String, String> stringParams = new LinkedHashMap<>();
        params.forEach((k, v) -> stringParams.put(k, String.valueOf(v)));
        HttpResponse<String> resp = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/skip", stringParams);
        assertThat(resp.statusCode()).isEqualTo(200);
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

    /** The row's numeric cells are Active, Delayed, On Hold, in that order. */
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

    private static void assertOnHoldTasksCount(String body, String userId, long expectedOnHold) {
        String marker = "data-employee-id=\"" + userId + "\"";
        int markerIdx = body.indexOf(marker);
        assertThat(markerIdx).as("marker not found: " + marker).isGreaterThanOrEqualTo(0);
        int trStart = body.lastIndexOf("<tr", markerIdx);
        int trEnd = body.indexOf("</tr>", markerIdx) + "</tr>".length();
        String tr = body.substring(trStart, trEnd);
        java.util.regex.Matcher m = Pattern.compile("class=\"num\">(\\d+)<").matcher(tr);
        assertThat(m.find()).as("Active cell not found for " + userId).isTrue();
        assertThat(m.find()).as("Delayed cell not found for " + userId).isTrue();
        assertThat(m.find()).as("On Hold cell not found for " + userId).isTrue();
        assertThat(Long.parseLong(m.group(1))).as("On Hold Tasks for employee " + userId).isEqualTo(expectedOnHold);
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

    private void setPlannedDates(String planId, LocalDate shootDate, LocalDate editDate, LocalDate liveDate) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        plan.applyReschedule(shootDate, editDate, liveDate);
        contentPlanRepository.save(plan);
    }

    // ------------------------------------------------------------------ TEST 1: Model + Cameraperson, same content -> 1

    @Test
    void modelPlusCamerapersonOnSameContentIdCountsAsOneActiveTask() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul", MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser pub = createUser(ceo, "Pub One", PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");

        approveToShootAssigned(ceo, "Dedup T1 " + unique, List.of(rahul), List.of(rahul), pub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        String body = ceo.get("/app/reports/workload").body();
        assertActiveTasksCount(body, rahul.id(), 1);
    }

    // ------------------------------------------------------------------ TEST 2 + 4: full lifecycle, Rahul holds all 4 roles on C-001

    @Test
    void sameContentIdStaysAtOneActiveTaskThroughShootEditPublishingThenDropsToZeroOnCompletion() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Full " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        grant(ceo, rahul.id(), "PERM_19_EDIT_EXECUTION");
        grant(ceo, rahul.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestUser planningPub = createUser(ceo, "Planning Pub " + unique, PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, planningPub.id(), "PERM_08_PUBLISHING_EXECUTION");

        String planId = approveToShootAssigned(ceo, "Dedup T4 " + unique, List.of(rahul), List.of(rahul), planningPub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        // Shoot Active: Rahul is both Cameraperson and Model on the SAME Content ID -> 1, not 2.
        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 1);

        // Shoot completed, Edit becomes active (Rahul folded in as his own Editor) -> still 1,
        // now representing the active Edit stage for the same Content ID (TEST 2: Model +
        // Cameraperson + Editor all held by Rahul, only the currently-active stage contributes).
        completeShoot(ceo, planId, rahul, List.of(rahul), List.of(rahul), rahul);
        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 1);

        // Edit completed, Publishing becomes active (Rahul folded in as his own Publisher too) -> still 1.
        completeEdit(ceo, planId, rahul, List.of(rahul), List.of(rahul));
        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 1);

        // Publishing completed -> the Content ID is closed out entirely -> 0.
        completePublishing(ceo, planId, rahul);
        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 0);
    }

    // ------------------------------------------------------------------ TEST 3 + 7: two Content IDs, one multi-role -> 2, not 3

    @Test
    void multipleContentIdsWithMultipleRolesCountUniqueContentIdsNotRoleOccurrences() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Multi " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        grant(ceo, rahul.id(), "PERM_19_EDIT_EXECUTION");
        TestUser pub1 = createUser(ceo, "Pub Multi 1 " + unique, PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, pub1.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestUser pub2 = createUser(ceo, "Pub Multi 2 " + unique, PUBLISHER_ROLE_ID, unique + 2);
        grant(ceo, pub2.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestUser otherCam = createUser(ceo, "Other Cam " + unique, CAMERA_PERSON_ROLE_ID, unique + 3);
        grant(ceo, otherCam.id(), "PERM_18_SHOOT_EXECUTION");

        // C-001: Rahul is Model + Cameraperson (Shoot active).
        approveToShootAssigned(ceo, "Dedup T3 C1 " + unique, List.of(rahul), List.of(rahul), pub1,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        // C-002: someone else shoots it, then Rahul is folded in as its Editor (Edit active).
        String plan2 = approveToShootAssigned(ceo, "Dedup T3 C2 " + unique, List.of(otherCam), List.of(), pub2,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        completeShoot(ceo, plan2, otherCam, List.of(otherCam), List.of(rahul), rahul);

        // 2 unique active Content IDs (C-001 via Model+Cameraperson, C-002 via Editor) - not 3
        // (which is what summing 3 role/stage rows would incorrectly give).
        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 2);
    }

    // ------------------------------------------------------------------ TEST 5: Shoot skipped removes the Content ID

    @Test
    void shootSkippedRemovesTheContentIdFromActiveTasks() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Skip " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser pub = createUser(ceo, "Pub Skip " + unique, PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestUser editor = createUser(ceo, "Editor Skip " + unique, VIDEO_EDITOR_ROLE_ID, unique + 2);

        String planId = approveToShootAssigned(ceo, "Dedup T5 " + unique, List.of(rahul), List.of(rahul), pub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 1);

        skipShoot(ceo, planId, editor);

        assertActiveTasksCount(ceo.get("/app/reports/workload").body(), rahul.id(), 0);
    }

    // ------------------------------------------------------------------ TEST 6: two employees, same Content ID, independent counts

    @Test
    void multipleEmployeesOnTheSameContentEachIndependentlyGetOneActiveTask() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Shared " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser amit = createUser(ceo, "Amit Shared " + unique, CAMERA_PERSON_ROLE_ID, unique + 1);
        grant(ceo, amit.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser pub = createUser(ceo, "Pub Shared " + unique, PUBLISHER_ROLE_ID, unique + 2);
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");

        approveToShootAssigned(ceo, "Dedup T6 " + unique, List.of(rahul, amit), List.of(rahul), pub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        String body = ceo.get("/app/reports/workload").body();
        assertActiveTasksCount(body, rahul.id(), 1);
        assertActiveTasksCount(body, amit.id(), 1);
    }

    // ------------------------------------------------------------------ TEST 8: Delayed stays a sum, not deduplicated

    @Test
    void delayedTasksRemainASumNotDeduplicatedByContentId() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Delayed " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser pub = createUser(ceo, "Pub Delayed " + unique, PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");

        String planId = approveToShootAssigned(ceo, "Dedup T8 " + unique, List.of(rahul), List.of(rahul), pub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        setPlannedDates(planId, LocalDate.now().minusDays(3), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));

        String body = ceo.get("/app/reports/workload").body();
        // Active Tasks: deduplicated to 1 (same Content ID via Model + Cameraperson).
        assertActiveTasksCount(body, rahul.id(), 1);
        // Delayed Tasks: intentionally still a plain sum across the Shoot row (delayed=1) and the
        // Model row (delayed=1) = 2 - this metric was explicitly NOT changed by this fix.
        assertDelayedTasksCount(body, rahul.id(), 2);
    }

    // ------------------------------------------------------------------ TEST 9: On Hold unchanged

    @Test
    void onHoldTasksRemainUnaffectedByTheActiveTasksDedupFix() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Hold " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser pub = createUser(ceo, "Pub Hold " + unique, PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");

        String planId = approveToShootAssigned(ceo, "Dedup T9 " + unique, List.of(rahul), List.of(rahul), pub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        TestApiClient rahulClient = loginAs(rahul);
        assertThat(rahulClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(ceo.post("/api/v1/content-plans/" + planId + "/hold",
                "{\"reason\":\"team workload employee active dedup regression test hold\"}").statusCode()).isEqualTo(200);

        String body = ceo.get("/app/reports/workload").body();
        // Active Tasks still deduplicates to 1 while on hold (hold doesn't change workflow status).
        assertActiveTasksCount(body, rahul.id(), 1);
        // On Hold: unchanged plain sum across the Shoot row (onHold=1) and the Model row (onHold=1) = 2.
        assertOnHoldTasksCount(body, rahul.id(), 2);
    }

    // ------------------------------------------------------------------ TEST 10: Content ID drill-down remains available and correct

    @Test
    void contentIdDrillDownStillShowsTheCorrectContentIdPerStageDespiteTheDeduplicatedSummary() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        TestUser rahul = createUser(ceo, "Rahul Drill " + unique, MODEL_ROLE_ID, unique);
        grant(ceo, rahul.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser pub = createUser(ceo, "Pub Drill " + unique, PUBLISHER_ROLE_ID, unique + 1);
        grant(ceo, pub.id(), "PERM_08_PUBLISHING_EXECUTION");

        String planId = approveToShootAssigned(ceo, "Dedup T10 " + unique, List.of(rahul), List.of(rahul), pub,
                LocalDate.now().plusDays(6), LocalDate.now().plusDays(10), LocalDate.now().plusDays(20));
        String contentId = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow().getContentId();

        String body = ceo.get("/app/reports/workload").body();
        assertActiveTasksCount(body, rahul.id(), 1);

        String panel = extractBreakdownPanel(body, rahul.id());
        // The stage breakdown still shows BOTH underlying stage rows (Shoot and Model), each
        // correctly referencing the same real Content ID - the employee-level summary is
        // deduplicated, but the drill-down itself was never touched by this fix.
        assertThat(countOccurrences(panel, contentId)).isEqualTo(2);
        assertThat(panel).contains("idea-id-link");
    }
}
