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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task visibility on the Employee's "My Work" screen comes from an active assignment, never
 * Designation/Business Role alone, and (for Shoot specifically, since a Cameraperson can be
 * assigned during Planning itself, before Planning Review even starts) not until Planning has
 * actually been Approved. Explicit user request: a Cameraperson assigned mid-Planning must not
 * see the deliverable in My Work until the plan reaches Shoot Assigned (SA) or later.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyWorkVisibilityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void camerapersonSeesTaskOnlyAfterPlanningIsApprovedNotAtAssignmentTime() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-mywork-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MyWork Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();
        grantShootExecution(ceo, camId);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Visibility " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        // Assigned while the plan is still at PL (Shoot Assignment happens during Planning itself,
        // before Planning Review even starts) - matches how the real UI's Shoot Assignment picker works.
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        String beforeApproval = cam.get("/app/my-work").body();
        assertThat(beforeApproval).doesNotContain(plan.getContentId());

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/mywork-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");

        String afterApproval = cam.get("/app/my-work").body();
        assertThat(afterApproval).contains(plan.getContentId());
    }

    /**
     * Explicit user request: once a stage's own review has decided and the plan moved on, that
     * assignment must drop out of Active Assignments and appear only in the read-only "My
     * Completed Work / History" section (own stage summary + Approved result, nothing about the
     * next stage's operational detail).
     */
    @Test
    void camerapersonTaskMovesFromActiveToCompletedHistoryOnceShootIsApproved() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-mywork-history-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MyWork History Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();
        grantShootExecution(ceo, camId);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork History " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/mywork-history-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        // Still shooting (SA) - active, not yet in history.
        String[] halvesDuringShoot = splitOnHistoryHeader(cam.get("/app/my-work").body());
        assertThat(halvesDuringShoot[0]).contains(plan.getContentId());
        assertThat(halvesDuringShoot[1]).doesNotContain(plan.getContentId());

        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("SAP");

        // Shoot Approved - moved out of the Active Shoot Tasks TABLE, into Completed Work with the
        // Approve outcome, and never appears in both tables at once (ENG-057: "Completed Work", not
        // "My Completed Work / History" - the section was renamed/restyled, same underlying
        // data/rule).
        String bodyAfterApproval = cam.get("/app/my-work").body();
        assertThat(activeShootTasksTableRegion(bodyAfterApproval)).doesNotContain(plan.getContentId());
        String[] halvesAfterApproval = splitOnHistoryHeader(bodyAfterApproval);
        assertThat(halvesAfterApproval[1]).contains(plan.getContentId()).contains("Approved");
    }

    private String activeShootTasksTableRegion(String body) {
        int start = body.indexOf("Active Shoot Tasks");
        // Permission-driven My Work redesign: stage-prefixed data-tab-panel values (was
        // "history", now "shoot-history") so Shoot/Edit/Publishing's own Active/History/Marks
        // sub-tabs never collide when multiple stage panels co-exist in the DOM.
        int end = body.indexOf("data-tab-panel=\"shoot-history\"");
        assertThat(start).isPositive();
        assertThat(end).isGreaterThan(start);
        return body.substring(start, end);
    }

    /**
     * ENG-058: the KPI cards must read from the exact same data as the tables (no separate count
     * query that could drift out of sync). ENG-064: for a Camera Person, `/app/deliverables/{id}`
     * now renders the redesigned Shoot Task Detail page - its "Latest Reviewer Feedback" card must
     * show the actual decision reason during rework, then show the later Approved decision
     * prominently once resolved while still preserving the earlier rework reason (never lost)
     * inside the collapsible "View Feedback History".
     */
    @Test
    void kpiCountsMatchTablesAndReworkFeedbackShowsThenClearsAfterApproval() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-mywork-rework-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MyWork Rework Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();
        grantShootExecution(ceo, camId);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Rework " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/mywork-rework-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String reworkReason = "Lighting is too dark, please reshoot in daylight " + unique;
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":false,\"reason\":\"" + reworkReason + "\"}");

        String myWorkDuringRework = cam.get("/app/my-work").body();
        assertThat(myWorkDuringRework).contains("Active Shoots</span><span class=\"kpi-card-count\">1</span>");
        assertThat(myWorkDuringRework).contains("Rework Required</span><span class=\"kpi-card-count\">1</span>");
        assertThat(myWorkDuringRework).contains("Completed</span><span class=\"kpi-card-count\">0</span>");

        String detailDuringRework = cam.get("/app/deliverables/" + planId).body();
        assertThat(detailDuringRework).contains("Latest Reviewer Feedback").contains("REWORK REQUIRED").contains(reworkReason);

        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("SAP");

        String myWorkAfterApproval = cam.get("/app/my-work").body();
        assertThat(myWorkAfterApproval).contains("Active Shoots</span><span class=\"kpi-card-count\">0</span>");
        assertThat(myWorkAfterApproval).contains("Rework Required</span><span class=\"kpi-card-count\">0</span>");
        assertThat(myWorkAfterApproval).contains("Completed</span><span class=\"kpi-card-count\">1</span>");

        // ENG-064: the latest decision (Approved) is now shown prominently, while the earlier
        // rework reason is preserved (not lost) inside the collapsible "View Feedback History".
        String detailAfterApproval = cam.get("/app/deliverables/" + planId).body();
        assertThat(detailAfterApproval).contains("Latest Reviewer Feedback").contains("APPROVED");
        assertThat(detailAfterApproval).contains("View Feedback History").contains(reworkReason);
    }

    /** Candidate eligibility/execution is now permission-driven (OperationalEligibilityService). */
    private void grantShootExecution(TestApiClient ceo, String userId) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
    }

    private String[] splitOnHistoryHeader(String body) {
        // ENG-058: this test's fixture user is always Camera Person, which now gets the
        // Cameraperson-specific dashboard ("Completed Shoot Work", not the generic "Completed Work"
        // every other Business Role still sees).
        int splitIndex = body.indexOf("Completed Shoot Work");
        assertThat(splitIndex).isPositive();
        return new String[] {body.substring(0, splitIndex), body.substring(splitIndex)};
    }
}
