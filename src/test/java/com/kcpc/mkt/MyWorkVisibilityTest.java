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
 * Designation/Business Role alone. Workflow redesign: the initial Shoot Team is now assigned
 * atomically as part of Idea Review approval itself (IdeaService#approve), landing directly on
 * Shoot Assigned (SA) - there is no more separate "assigned during Planning, before Planning
 * Review approves" window in which the assignment exists but the task must stay hidden; the
 * assignment and Shoot Assigned visibility are now inseparable.
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
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";

    @Test
    void camerapersonSeesTaskAssignedAtoIdeaReviewApprovalImmediatelyInMyWork() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-mywork-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MyWork Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();
        grantShootExecution(ceo, camId);

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        String beforeApproval = cam.get("/app/my-work").body();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Visibility " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Workflow redesign: Idea Review approval carries every former Planning field (including the
        // initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA).
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        assertThat(beforeApproval).doesNotContain(plan.getContentId());
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

        // Workflow redesign: Idea Review approval carries every former Planning field (including the
        // initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA).
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork History " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-history-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        // Still shooting (SA) - active, not yet in history.
        String[] halvesDuringShoot = splitOnHistoryHeader(cam.get("/app/my-work").body());
        assertThat(halvesDuringShoot[0]).contains(plan.getContentId());
        assertThat(halvesDuringShoot[1]).doesNotContain(plan.getContentId());

        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String edId = createEditorUser(ceo, "MyWork History Ed " + unique);
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");

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

        // Workflow redesign: Idea Review approval carries every former Planning field (including the
        // initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA).
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Rework " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-rework-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

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
        String edId = createEditorUser(ceo, "MyWork Rework Ed " + unique);
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");

        String myWorkAfterApproval = cam.get("/app/my-work").body();
        assertThat(myWorkAfterApproval).contains("Active Shoots</span><span class=\"kpi-card-count\">0</span>");
        assertThat(myWorkAfterApproval).contains("Rework Required</span><span class=\"kpi-card-count\">0</span>");
        assertThat(myWorkAfterApproval).contains("Completed</span><span class=\"kpi-card-count\">1</span>");

        // ENG-064: the latest decision (Approved) is preserved alongside the earlier rework reason.
        // Workflow redesign: the plan now lands on EA (Editor already assigned via the Approve
        // fold-in), outside the Cameraperson's own redesigned-page window (SA/SIP/SRV/SAP only -
        // their Shoot task is done) - viewed via CEO's standard shell instead ("Review Feedback
        // History" panel, not the task-detail page's "Latest Reviewer Feedback"), same underlying data.
        String detailAfterApproval = ceo.get("/app/deliverables/" + planId).body();
        assertThat(detailAfterApproval).contains("Review Feedback History").contains("Approved").contains(reworkReason);
    }

    /** Candidate eligibility/execution is now permission-driven (OperationalEligibilityService). */
    private void grantShootExecution(TestApiClient ceo, String userId) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
    }

    /** Throwaway Editor for the Shoot Review Approve fold-in (ShootingService#decideShootReview),
     * unrelated to these tests' own Cameraperson-focused assertions. */
    private String createEditorUser(TestApiClient ceo, String fullName) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + fullName.toLowerCase().replace(" ", "-")
                        + "@kcpcbandhani.local\",\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + VIDEO_EDITOR_ROLE_ID
                        + "\",\"creationReason\":\"e2e test fixture\"}");
        String editorId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editorId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        return editorId;
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
