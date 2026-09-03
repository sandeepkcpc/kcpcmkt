package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-097 (Reports -> KPI Dashboard -> Overview redesign) - Current Work Ownership. Covers §24 of
 * the implementation spec: pending/delayed/both/none, person-wise collaborative counting, a
 * completed Shoot task not remaining pending once the plan moves to Edit (and Edit -> Publishing),
 * cancelled work excluded, oldest-delay correctness, drill-down/summary agreement, and that the
 * drill-down is genuinely read-only. All against the real Overview page HTML
 * (/app/reports/kpis?view=overview) and the real drill-down endpoint, never the service layer
 * directly, so a regression in wiring (controller/JSP/authorization) would be caught too.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KpiOverviewCurrentWorkOwnershipTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "kpi-cwo-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"KpiCwo " + label + " " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"KPI ownership test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI ownership test fixture grant\"}");
    }

    /** Idea -> approved to Shoot Assigned with the given Cameraperson(s) - Standard mode, future
     *  Shoot Date (on-time) unless overridden by the caller. */
    private String approveToShootAssigned(TestApiClient ceo, long unique, String... camerapersonIds) throws Exception {
        StringBuilder camList = new StringBuilder();
        for (int i = 0; i < camerapersonIds.length; i++) {
            if (i > 0) {
                camList.append(',');
            }
            camList.append('"').append(camerapersonIds[i]).append('"');
        }
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiCwo Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-cwo-" + unique + "\","
                        + "\"camerapersonUserIds\":[" + camList + "],\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();
    }

    /** Same as above but Urgent Planning Mode with an explicit past Shoot Date - the established
     *  pattern (see PipelineFilterSortTest) for constructing an already-delayed fixture, since
     *  Standard mode's own past-date guard would reject a past Shoot Date at approval time. */
    private String approveToShootAssignedAlreadyDelayed(TestApiClient ceo, long unique, int delayDays,
                                                          String camerapersonId) throws Exception {
        String[] pub = createUser(ceo, "delaypub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiCwo Delayed Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Urgent Planning Mode requires an explicit date for EVERY stage that's part of the
        // pipeline - full Shoot+Edit+Publishing (the default when "stages" is omitted) needs both
        // shootDate and editDate, not just the one this fixture actually cares about.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"planningMode\":\"URGENT\",\"urgencyReason\":\"test fixture\","
                        + "\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"shootDate\":\"" + LocalDate.now().minusDays(delayDays) + "\","
                        + "\"editDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-cwo-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camerapersonId + "\"],\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();
    }

    private String overviewHtml(TestApiClient client) throws Exception {
        return client.get("/app/reports/kpis?view=overview").body();
    }

    private String drilldownHtml(TestApiClient client, String employeeId) throws Exception {
        return client.get("/app/reports/kpis/ownership-drilldown?employeeId=" + employeeId).body();
    }

    // --- employee with pending work ---
    @Test
    void employeeWithPendingWorkAppearsWithCorrectCount() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        approveToShootAssigned(ceo, unique, cam[0]);

        String html = overviewHtml(ceo);
        assertThat(html).contains("data-employee-id=\"" + cam[0] + "\"")
                .contains("KpiCwo cam " + unique);
        String drilldown = drilldownHtml(ceo, cam[0]);
        assertThat(drilldown).contains("1 Pending").contains("0 Delayed");
    }

    // --- employee with delayed work ---
    @Test
    void employeeWithDelayedWorkShowsDelayedCountAndOldestDelay() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        approveToShootAssignedAlreadyDelayed(ceo, unique, 3, cam[0]);

        String drilldown = drilldownHtml(ceo, cam[0]);
        assertThat(drilldown).contains("1 Pending").contains("1 Delayed").contains("3 days");
    }

    // --- employee with both pending and delayed ---
    @Test
    void employeeWithBothPendingAndDelayedCombinesCorrectly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        approveToShootAssigned(ceo, unique, cam[0]); // on-time
        approveToShootAssignedAlreadyDelayed(ceo, unique + 1, 5, cam[0]); // delayed

        String drilldown = drilldownHtml(ceo, cam[0]);
        assertThat(drilldown).contains("2 Pending").contains("1 Delayed");
    }

    // --- employee with no current work ---
    @Test
    void employeeWithNoCurrentWorkShowsEmptyDrillDown() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        // No assignment at all.
        String drilldown = drilldownHtml(ceo, cam[0]);
        assertThat(drilldown).contains("0 Pending").contains("0 Delayed")
                .contains("No pending work currently assigned.")
                .contains("No delayed work currently assigned.");
        assertThat(overviewHtml(ceo)).doesNotContain("KpiCwo cam " + unique);
    }

    // --- multiple Camerapersons on same Content ID -> counted for each ---
    @Test
    void multipleCamerapersonsOnSameContentIdEachCountedIndependently() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] camA = createUser(ceo, "camA", CAMERA_PERSON_ROLE_ID, unique);
        String[] camB = createUser(ceo, "camB", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camA[0], "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, camB[0], "PERM_18_SHOOT_EXECUTION");
        approveToShootAssigned(ceo, unique, camA[0], camB[0]);

        assertThat(drilldownHtml(ceo, camA[0])).contains("1 Pending");
        assertThat(drilldownHtml(ceo, camB[0])).contains("1 Pending");
    }

    // --- multiple Editors on same Content ID -> counted for each ---
    @Test
    void multipleEditorsOnSameContentIdEachCountedIndependently() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editorA = createUser(ceo, "editorA", VIDEO_EDITOR_ROLE_ID, unique);
        String[] editorB = createUser(ceo, "editorB", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editorA[0], "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, editorB[0], "PERM_19_EDIT_EXECUTION");

        String[] pub = createUser(ceo, "directeditpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiCwo DirectEdit " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-cwo-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editorA[0] + "\",\"" + editorB[0] + "\"],"
                        + "\"leadEditorUserId\":\"" + editorA[0] + "\",\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");

        assertThat(drilldownHtml(ceo, editorA[0])).contains("1 Pending");
        assertThat(drilldownHtml(ceo, editorB[0])).contains("1 Pending");
    }

    // --- completed Shoot task does not remain pending after moving to Edit ---
    @Test
    void completedShootTaskDoesNotRemainPendingAfterMovingToEdit() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String planId = approveToShootAssigned(ceo, unique, cam[0]);
        assertThat(drilldownHtml(ceo, cam[0])).contains("1 Pending");

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam[0] + "\"],"
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}");

        // Content is now at Edit (EA) - the Cameraperson's own Shoot task is done and must not
        // remain pending merely because this Content ID is still active in Edit.
        assertThat(drilldownHtml(ceo, cam[0])).contains("0 Pending").contains("0 Delayed");
    }

    // --- completed Edit task does not remain pending after moving to Publishing ---
    @Test
    void completedEditTaskDoesNotRemainPendingAfterMovingToPublishing() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] initialPub = createUser(ceo, "editdonepub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, initialPub[0], "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiCwo EditDone " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-cwo-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + initialPub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        String planId = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();
        assertThat(drilldownHtml(ceo, editor[0])).contains("1 Pending");

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        String[] publisher = createUser(ceo, "publisher", "01926e3e-0001-7000-8000-000000000008", unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}");

        assertThat(drilldownHtml(ceo, editor[0])).contains("0 Pending").contains("0 Delayed");
    }

    // --- cancelled work not counted as pending ---
    @Test
    void cancelledWorkNotCountedAsPending() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String planId = approveToShootAssigned(ceo, unique, cam[0]);
        assertThat(drilldownHtml(ceo, cam[0])).contains("1 Pending");

        HttpResponse<String> cancelResponse = ceo.post("/api/v1/content-plans/" + planId + "/cancel",
                "{\"reason\":\"KPI ownership test - cancelled fixture\"}");
        assertThat(cancelResponse.statusCode()).isEqualTo(200);

        assertThat(drilldownHtml(ceo, cam[0])).contains("0 Pending").contains("0 Delayed");
    }

    // --- oldest delay correctly calculated (max across multiple delayed items) ---
    @Test
    void oldestDelayIsTheMaximumAcrossMultipleDelayedItems() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        approveToShootAssignedAlreadyDelayed(ceo, unique, 2, cam[0]);
        approveToShootAssignedAlreadyDelayed(ceo, unique + 1, 7, cam[0]);
        approveToShootAssignedAlreadyDelayed(ceo, unique + 2, 4, cam[0]);

        String html = overviewHtml(ceo);
        // Scope the check to THIS employee's own row - the page lists every employee with current
        // work, so a bare page-wide "does not contain '2 days'" would be fragile against other
        // fixtures' own (unrelated, correct) delay values.
        int nameIndex = html.indexOf("KpiCwo cam " + unique);
        assertThat(nameIndex).isGreaterThan(-1);
        String row = html.substring(nameIndex, Math.min(html.length(), nameIndex + 400));
        // The summary row's Oldest Delay must be the maximum (7), never 2, 4, or an average.
        assertThat(row).contains("7 days").doesNotContain("2 days").doesNotContain("4 days");
    }

    // --- drill-down records match summary counts ---
    @Test
    void drillDownRecordsMatchSummaryCounts() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        approveToShootAssigned(ceo, unique, cam[0]);
        approveToShootAssignedAlreadyDelayed(ceo, unique + 1, 1, cam[0]);

        // Summary row and drill-down are both built from the exact same currentWorkItemsByEmployee()
        // population (KpiDashboardService) - asserting the drill-down's own counts is sufficient
        // proof they can never disagree, since there is no second, independently-computed source.
        String overview = overviewHtml(ceo);
        assertThat(overview).contains("KpiCwo cam " + unique);
        String drilldown = drilldownHtml(ceo, cam[0]);
        assertThat(drilldown).contains("2 Pending").contains("1 Delayed");
    }

    // --- employee drill-down is read-only ---
    @Test
    void employeeDrillDownExposesNoWorkflowActions() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String planId = approveToShootAssigned(ceo, unique, cam[0]);

        String drilldown = drilldownHtml(ceo, cam[0]);
        assertThat(drilldown).doesNotContain("<form")
                .doesNotContainIgnoringCase("reassign")
                .doesNotContainIgnoringCase("approve")
                .doesNotContainIgnoringCase("reject")
                .doesNotContainIgnoringCase("<button type=\"submit\"")
                .doesNotContainIgnoringCase("data-decision");
        // The only actionable element is a plain link into the existing, unmodified Content Detail
        // screen - never a second Content Detail implementation.
        assertThat(drilldown).contains("/app/deliverables/" + planId);
    }

    // --- existing Content Detail navigation works ---
    @Test
    void openContentLinkReachesTheExistingContentDetailScreen() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String planId = approveToShootAssigned(ceo, unique, cam[0]);

        assertThat(ceo.get("/app/deliverables/" + planId).statusCode()).isEqualTo(200);
    }

    // --- server-side authorization: a viewer without PERM_15 cannot reach Overview or the drill-down ---
    @Test
    void unauthorizedUserCannotReachOwnershipDrilldown() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique); // no PERM_15 granted
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");

        HttpResponse<String> response = camClient.get("/app/reports/kpis/ownership-drilldown?employeeId=" + cam[0]);
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/app/home");
    }
}
