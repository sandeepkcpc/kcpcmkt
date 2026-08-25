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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manager review-consistency fix: the same Shoot/Edit Review workflow state must expose the same
 * review-critical information and decision requirements regardless of entry point -
 * Content Detail -> Shoot/Edit and Reviews -> Shoot/Edit now share the exact same
 * StageCommentService-backed comment thread and call the exact same
 * ShootingService#decideShootReview / EditingService#decideEditReview for a decision (never a
 * separate, reduced implementation). Also verifies Content Detail -> Overview no longer exposes
 * the old incomplete Approve Shoot/Approve Edit actions (they always 400'd without a qualifying
 * Cameraperson/Editor selection that only exists in the Shoot/Edit tabs). Covers both CEO_OWNER
 * and MARKETING_MANAGER, since both hold native review authority and the fix must apply to both
 * without creating role-specific review implementations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShootEditReviewParityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "e2e-parity-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Parity " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"review parity test fixture\"}");
        return user.get("userId").asText() + "|" + email;
    }

    /** Candidate eligibility/execution is now permission-driven (OperationalEligibilityService). */
    private void grantExecutionPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"review parity test fixture execution grant\"}");
    }

    /** Fresh Marketing Manager reviewer client, logged in and ready to act as a reviewer. */
    private TestApiClient marketingManagerReviewer(TestApiClient ceo, long unique) throws Exception {
        String[] idEmail = createUser(ceo, "mm-reviewer", MARKETING_MANAGER_ROLE_ID, unique).split("\\|");
        TestApiClient mm = new TestApiClient(port);
        mm.login(idEmail[1], "Passw0rd!");
        return mm;
    }

    /** Idea -> approved -> cameraperson assigned -> planning approved -> shoot started, up to (not
     *  including) Shoot Review submission, so the caller can post a comment as the cameraperson
     *  before submitting. */
    private String[] buildToShootStarted(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Parity Shoot " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");

        String[] camIdEmail = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique).split("\\|");
        String camId = camIdEmail[0];
        String camEmail = camIdEmail[1];
        grantExecutionPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");

        String planId = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/parity-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");

        return new String[] {planId, camId, camEmail};
    }

    @Test
    void overviewNoLongerExposesIncompleteShootOrEditApprovalActions() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToShootStarted(ceo, unique);
        String planId = fx[0];
        String camEmail = fx[2];

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");

        String overviewAtShootReview = ceo.get("/app/deliverables/" + planId).body();
        assertThat(overviewAtShootReview).contains("Shoot Review");
        assertThat(overviewAtShootReview).doesNotContain("data-action-key=\"APPROVE_SHOOT_REVIEW\"")
                .doesNotContain("data-action-key=\"REQUEST_REWORK_SHOOT_REVIEW\"");
        // Governed administrative actions remain available - only the incomplete review action was removed.
        assertThat(overviewAtShootReview).contains("data-action-key=\"RESCHEDULE\"")
                .contains("data-action-key=\"REASSIGN\"").contains("data-action-key=\"CANCEL\"");

        String camId = fx[1];
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        String[] editorIdEmail = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique).split("\\|");
        grantExecutionPermission(ceo, editorIdEmail[0], "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + editorIdEmail[0] + "\"}");
        TestApiClient editor = new TestApiClient(port);
        editor.login(editorIdEmail[1], "Passw0rd!");
        editor.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");

        String overviewAtEditReview = ceo.get("/app/deliverables/" + planId).body();
        assertThat(overviewAtEditReview).contains("Edit Review");
        assertThat(overviewAtEditReview).doesNotContain("data-action-key=\"APPROVE_EDIT_REVIEW\"")
                .doesNotContain("data-action-key=\"REQUEST_REWORK_EDIT_REVIEW\"");
    }

    private void verifyShootReviewParity(TestApiClient reviewer, String planId, String camFullNameFragment,
                                          String commentText, String driveLinkFragment, String camId) throws Exception {
        String contentDetail = reviewer.get("/app/deliverables/" + planId).body();
        String reviewsShoot = reviewer.get("/app/reviews?tab=shoot&selectedId=" + planId).body();

        // Same review-critical info visible from both entry points.
        for (String body : new String[] {contentDetail, reviewsShoot}) {
            assertThat(body).contains(camFullNameFragment);
            assertThat(body).contains(commentText);
            assertThat(body).contains(driveLinkFragment);
        }
        assertThat(reviewsShoot).contains("reviews-qualifying-checkbox");

        // Same backend decision contract: Approve without a qualifying Cameraperson fails
        // identically to how Content Detail's own Shoot tab form would fail.
        HttpResponse<String> rejectedApprove = reviewer.postFormAjax("/app/reviews/shoot/" + planId + "/decision",
                Map.of("approve", "true"));
        assertThat(rejectedApprove.statusCode()).isEqualTo(400);
        assertThat(rejectedApprove.body()).contains("qualifying");

        // Same valid decision succeeds from Reviews, exercising the exact same
        // ShootingService#decideShootReview the Content Detail -> Shoot form itself posts to.
        HttpResponse<String> approved = reviewer.postFormAjax("/app/reviews/shoot/" + planId + "/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", camId));
        assertThat(approved.statusCode()).isEqualTo(200);
    }

    @Test
    void shootReviewParityForCeo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToShootStarted(ceo, unique);
        String planId = fx[0];
        String camId = fx[1];
        String camEmail = fx[2];
        String commentText = "Ye wala check karo " + unique;

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        assertThat(cam.postForm("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", commentText)).statusCode()).isEqualTo(302);
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");

        verifyShootReviewParity(ceo, planId, "Parity cam", commentText, "parity-" + unique, camId);
    }

    @Test
    void shootReviewParityForMarketingManager() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToShootStarted(ceo, unique);
        String planId = fx[0];
        String camId = fx[1];
        String camEmail = fx[2];
        String commentText = "MM check karo " + unique;

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        assertThat(cam.postForm("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", commentText)).statusCode()).isEqualTo(302);
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");

        TestApiClient mm = marketingManagerReviewer(ceo, unique);
        verifyShootReviewParity(mm, planId, "Parity cam", commentText, "parity-" + unique, camId);
    }

    private void verifyEditReviewParity(TestApiClient reviewer, String planId, String editorFullNameFragment,
                                         String commentText, String driveLinkFragment, String editorId) throws Exception {
        String contentDetail = reviewer.get("/app/deliverables/" + planId).body();
        String reviewsEdit = reviewer.get("/app/reviews?tab=edit&selectedId=" + planId).body();

        for (String body : new String[] {contentDetail, reviewsEdit}) {
            assertThat(body).contains(editorFullNameFragment);
            assertThat(body).contains(commentText);
            assertThat(body).contains(driveLinkFragment);
        }
        assertThat(reviewsEdit).contains("reviews-qualifying-checkbox");

        HttpResponse<String> rejectedApprove = reviewer.postFormAjax("/app/reviews/edit/" + planId + "/decision",
                Map.of("approve", "true"));
        assertThat(rejectedApprove.statusCode()).isEqualTo(400);
        assertThat(rejectedApprove.body()).contains("qualifying");

        HttpResponse<String> approved = reviewer.postFormAjax("/app/reviews/edit/" + planId + "/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", editorId));
        assertThat(approved.statusCode()).isEqualTo(200);
    }

    private String[] buildToEditStarted(TestApiClient ceo, long unique) throws Exception {
        String[] fx = buildToShootStarted(ceo, unique);
        String planId = fx[0];
        String camId = fx[1];
        String camEmail = fx[2];

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");

        String[] editorIdEmail = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique).split("\\|");
        grantExecutionPermission(ceo, editorIdEmail[0], "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + editorIdEmail[0] + "\"}");
        TestApiClient editor = new TestApiClient(port);
        editor.login(editorIdEmail[1], "Passw0rd!");
        editor.post("/api/v1/content-plans/" + planId + "/editing/start", "");

        return new String[] {planId, editorIdEmail[0], editorIdEmail[1]};
    }

    @Test
    void editReviewParityForCeo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToEditStarted(ceo, unique);
        String planId = fx[0];
        String editorId = fx[1];
        String editorEmail = fx[2];
        String commentText = "Edit review check karo " + unique;

        TestApiClient editor = new TestApiClient(port);
        editor.login(editorEmail, "Passw0rd!");
        assertThat(editor.postForm("/app/deliverables/" + planId + "/editing/comments",
                Map.of("commentText", commentText)).statusCode()).isEqualTo(302);
        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");

        verifyEditReviewParity(ceo, planId, "Parity editor", commentText, "parity-" + unique, editorId);
    }

    @Test
    void editReviewParityForMarketingManager() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToEditStarted(ceo, unique);
        String planId = fx[0];
        String editorId = fx[1];
        String editorEmail = fx[2];
        String commentText = "MM edit check karo " + unique;

        TestApiClient editor = new TestApiClient(port);
        editor.login(editorEmail, "Passw0rd!");
        assertThat(editor.postForm("/app/deliverables/" + planId + "/editing/comments",
                Map.of("commentText", commentText)).statusCode()).isEqualTo(302);
        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");

        TestApiClient mm = marketingManagerReviewer(ceo, unique);
        verifyEditReviewParity(mm, planId, "Parity editor", commentText, "parity-" + unique, editorId);
    }
}
