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
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

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

        String pubId = createPublisherUser(ceo, "MyWork Visibility Pub " + unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Visibility " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Workflow redesign: Idea Review approval carries every former Planning field (including the
        // initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA).
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        assertThat(beforeApproval).doesNotContain(plan.getContentId());
        assertThat(approved.get("status").asText()).isEqualTo("SA");

        String afterApproval = cam.get("/app/my-work").body();
        assertThat(afterApproval).contains(plan.getContentId());
    }

    /**
     * Explicit user request: once a stage's own review has decided and the plan moved on, that
     * assignment must drop out of Active Assignments. My Work no longer has any History section at
     * all (see MyWorkRoleBasedNavigationTest) - the completed record now surfaces only on My
     * Performance's own Task Performance table, reusing the exact same underlying completion data
     * (own stage summary + Approved result), never duplicated on My Work.
     */
    @Test
    void camerapersonTaskMovesFromActiveToMyPerformanceOnceShootIsApproved() throws Exception {
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
        String pubId = createPublisherUser(ceo, "MyWork History Pub " + unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork History " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-history-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        // Still shooting (SA) - active, not yet completed, not yet on My Performance.
        String duringShoot = cam.get("/app/my-work").body();
        assertThat(duringShoot).contains(plan.getContentId());
        assertThat(cam.get("/app/my-performance").body()).doesNotContain(plan.getContentId());

        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String edId = createEditorUser(ceo, "MyWork History Ed " + unique);
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");

        // Shoot Approved - moved out of the Active Shoot Tasks table on My Work (which has no
        // History section to fall into any more), and now appears on My Performance instead.
        String bodyAfterApproval = cam.get("/app/my-work").body();
        assertThat(activeShootTasksTableRegion(bodyAfterApproval)).doesNotContain(plan.getContentId());
        assertThat(bodyAfterApproval).doesNotContain("Completed Shoot Work");
        assertThat(cam.get("/app/my-performance").body()).contains(plan.getContentId());
    }

    private String activeShootTasksTableRegion(String body) {
        int start = body.indexOf("Active Shoot Tasks");
        // My Work's Active Shoot Tasks table is followed directly by its note-box paragraph now
        // that the History sub-tab/panel has been removed entirely (see
        // MyWorkRoleBasedNavigationTest) - a stable, stage-agnostic end marker.
        int end = body.indexOf("Need help or have questions?");
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
        String pubId = createPublisherUser(ceo, "MyWork Rework Pub " + unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Rework " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-rework-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
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

    /** Throwaway Publisher now unconditionally required by Idea Review approval
     * (IdeaService#approve), unrelated to these tests' own Cameraperson-focused assertions. */
    private String createPublisherUser(TestApiClient ceo, String fullName) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + fullName.toLowerCase().replace(" ", "-")
                        + "@kcpcbandhani.local\",\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + PUBLISHER_ROLE_ID
                        + "\",\"creationReason\":\"e2e test fixture\"}");
        String publisherId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisherId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        return publisherId;
    }
}
