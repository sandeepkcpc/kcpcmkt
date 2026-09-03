package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.discussion.repository.StageCommentRepository;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-046: per-stage shared Description (Shoot/Edit/Publishing, one value per Content Plan per
 * stage, not per assignee) and Jira-style Comments threads (append-only, stage-scoped, never
 * mixing across stages).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StageDiscussionTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    StageCommentRepository stageCommentRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    SystemAuditLogRepository systemAuditLogRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shootDescriptionRequiresPerm04AndPersists() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-desc-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Desc Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Shoot Desc " + unique, cam);

        // CEO (native authority) can set the Description.
        HttpResponse<String> save = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/description",
                Map.of("description", "Front/back/close-up shots. Gota work clearly visible."));
        assertThat(save.statusCode()).isEqualTo(200);
        ContentPlan reloaded = contentPlanRepository.findById(java.util.UUID.fromString(planId)).orElseThrow();
        assertThat(reloaded.getShootDescription()).isEqualTo("Front/back/close-up shots. Gota work clearly visible.");

        // The assigned Cameraperson holds no PERM_04 grant - only CEO/MM or an authorized assigner
        // (PERM_04) may set the Description, not merely being the executing employee.
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        HttpResponse<String> forbidden = camClient.postFormAjax("/app/deliverables/" + planId + "/shooting/description",
                Map.of("description", "Trying to change it myself"));
        assertThat(forbidden.statusCode()).isEqualTo(403);

        // CEO can update it again later (not a one-time-only field).
        HttpResponse<String> update = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/description",
                Map.of("description", "Updated: also get a dupatta close-up."));
        assertThat(update.statusCode()).isEqualTo(200);
        ContentPlan afterUpdate = contentPlanRepository.findById(java.util.UUID.fromString(planId)).orElseThrow();
        assertThat(afterUpdate.getShootDescription()).isEqualTo("Updated: also get a dupatta close-up.");
    }

    // NOTE (workflow redesign): the ENG-048 "Planning Approver (PERM_03) can edit Shoot Instructions
    // during Planning Review" test formerly here is retired, not adapted - Planning Review (PLRV/
    // PLAP) no longer exists as an active-workflow gate, and PlanningService#requireShootDescriptionAuthority
    // now grants Shoot Instructions edit authority to native authority or PERM_04_SHOOT_ASSIGNMENT
    // only (the PERM_03 fallback was intentionally removed - PERM_03 has no active-workflow role any
    // more per the redesign). There is no equivalent behavior left to test.

    @Test
    void shootCommentsRestrictedToActiveAssigneeOrNativeAndPersistWithCommenterAndTimestamp() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-cmt-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Cmt Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String outsiderEmail = "e2e-cmt-outsider-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "Cmt Outsider", outsiderEmail, CAMERA_PERSON_ROLE_ID);
        String planId = approveIdeaAndGetContentPlanId(ceo, "Shoot Cmt " + unique, cam);

        // CEO comments.
        HttpResponse<String> ceoComment = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Dupatta ka close-up bhi lena."));
        assertThat(ceoComment.statusCode()).isEqualTo(200);
        JsonNode ceoBody = objectMapper.readTree(ceoComment.body());
        assertThat(ceoBody.get("commenterName").asText()).isEqualTo("KCPC CEO");
        assertThat(ceoBody.get("commentText").asText()).isEqualTo("Dupatta ka close-up bhi lena.");
        assertThat(ceoBody.get("createdAt").asText()).contains("IST");

        // The assigned Cameraperson (active assignee) comments too.
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        HttpResponse<String> camComment = camClient.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Okay. Outdoor shot bhi required hai?"));
        assertThat(camComment.statusCode()).isEqualTo(200);

        // Someone with no assignment on this plan at all cannot comment.
        TestApiClient outsiderClient = new TestApiClient(port);
        outsiderClient.login(outsiderEmail, "Passw0rd!");
        HttpResponse<String> outsiderComment = outsiderClient.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Not my stage"));
        assertThat(outsiderComment.statusCode()).isEqualTo(403);

        ContentPlan plan = contentPlanRepository.findById(java.util.UUID.fromString(planId)).orElseThrow();
        var comments = stageCommentRepository.findByContentPlanAndStageOrderByCreatedAtAsc(plan, LifecycleStage.SHOOTING);
        assertThat(comments).hasSize(2);
        assertThat(comments.get(0).getCommentText()).isEqualTo("Dupatta ka close-up bhi lena.");
        assertThat(comments.get(1).getCommentText()).isEqualTo("Okay. Outdoor shot bhi required hai?");
        assertThat(comments.get(1).getCommenter().getFullName()).isEqualTo("Cmt Cam");
    }

    /** ENG-050: a comment's own author may edit its text later; every edit is audited (old -> new). */
    @Test
    void commentAuthorCanEditOwnCommentAndItIsAudited() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Edit Cmt Cam", "e2e-editcmt-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Edit Cmt " + unique, cam);

        HttpResponse<String> posted = ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Outdoor shot bhi lena?"));
        String commentId = objectMapper.readTree(posted.body()).get("commentId").asText();

        HttpResponse<String> edited = ceo.postFormAjax(
                "/app/deliverables/" + planId + "/shooting/comments/" + commentId + "/edit",
                Map.of("commentText", "Nahi, indoor shoot hi rakho."));
        assertThat(edited.statusCode()).isEqualTo(200);
        JsonNode editedBody = objectMapper.readTree(edited.body());
        assertThat(editedBody.get("commentText").asText()).isEqualTo("Nahi, indoor shoot hi rakho.");

        var reloaded = stageCommentRepository.findById(java.util.UUID.fromString(commentId)).orElseThrow();
        assertThat(reloaded.getCommentText()).isEqualTo("Nahi, indoor shoot hi rakho.");
        assertThat(reloaded.getEditedAt()).isNotNull();

        var auditEntry = systemAuditLogRepository.findAllByOrderByEventTimestampDesc().stream()
                .filter(l -> "STAGE_COMMENT_EDITED".equals(l.getEventType()) && reloaded.getId().equals(l.getTargetEntityId()))
                .findFirst().orElseThrow();
        assertThat(auditEntry.getActionReason()).contains("Outdoor shot bhi lena?").contains("Nahi, indoor shoot hi rakho.");
    }

    /**
     * ENG-050: "sirf apne comment par" - own-comment-only, deliberately NOT bypassable by CEO/MM's
     * usual native authority. Delete is a soft delete: the row/text stay in the DB (never a hard
     * DELETE - see DbIntegrityEnforcementTest), only is_deleted flips and the API stops exposing it
     * for further edits.
     */
    @Test
    void onlyCommentAuthorCanEditOrDeleteAndDeleteIsSoftNotHard() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-owncmt-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Own Cmt Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Own Cmt " + unique, cam);

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        HttpResponse<String> posted = camClient.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Dupatta close-up bhi lena?"));
        String commentId = objectMapper.readTree(posted.body()).get("commentId").asText();

        // CEO (native authority everywhere else) still cannot edit or delete someone else's comment.
        HttpResponse<String> ceoEditAttempt = ceo.postFormAjax(
                "/app/deliverables/" + planId + "/shooting/comments/" + commentId + "/edit",
                Map.of("commentText", "CEO trying to rewrite it"));
        assertThat(ceoEditAttempt.statusCode()).isEqualTo(403);
        HttpResponse<String> ceoDeleteAttempt = ceo.postFormAjax(
                "/app/deliverables/" + planId + "/shooting/comments/" + commentId + "/delete", Map.of());
        assertThat(ceoDeleteAttempt.statusCode()).isEqualTo(403);

        // The author can edit their own.
        HttpResponse<String> camEdit = camClient.postFormAjax(
                "/app/deliverables/" + planId + "/shooting/comments/" + commentId + "/edit",
                Map.of("commentText", "Dupatta aur border close-up bhi lena?"));
        assertThat(camEdit.statusCode()).isEqualTo(200);

        // ...and soft-delete their own.
        HttpResponse<String> camDelete = camClient.postFormAjax(
                "/app/deliverables/" + planId + "/shooting/comments/" + commentId + "/delete", Map.of());
        assertThat(camDelete.statusCode()).isEqualTo(200);

        var reloaded = stageCommentRepository.findById(java.util.UUID.fromString(commentId)).orElseThrow();
        assertThat(reloaded.isDeleted()).isTrue();
        assertThat(reloaded.getDeletedAt()).isNotNull();
        // Never a hard delete - the row and its last text stay in the DB for audit purposes.
        assertThat(reloaded.getCommentText()).isEqualTo("Dupatta aur border close-up bhi lena?");

        // A second edit/delete attempt on an already-deleted comment is rejected.
        HttpResponse<String> editAfterDelete = camClient.postFormAjax(
                "/app/deliverables/" + planId + "/shooting/comments/" + commentId + "/edit",
                Map.of("commentText", "too late"));
        assertThat(editAfterDelete.statusCode()).isEqualTo(400);
    }

    @Test
    void shootAndEditCommentsNeverMixAcrossStages() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Mix Cam", "e2e-mix-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String editor = createUser(ceo, "Mix Editor", "e2e-mix-ed-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);
        String planId = approveIdeaAndGetContentPlanId(ceo, "Stage Mix " + unique, cam);

        assertThat(ceo.postFormAjax("/app/deliverables/" + planId + "/shooting/comments",
                Map.of("commentText", "Shoot-only note")).statusCode()).isEqualTo(200);

        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + editor + "\"}");
        assertThat(ceo.postFormAjax("/app/deliverables/" + planId + "/editing/comments",
                Map.of("commentText", "Edit-only note")).statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findById(java.util.UUID.fromString(planId)).orElseThrow();
        var shootComments = stageCommentRepository.findByContentPlanAndStageOrderByCreatedAtAsc(plan, LifecycleStage.SHOOTING);
        var editComments = stageCommentRepository.findByContentPlanAndStageOrderByCreatedAtAsc(plan, LifecycleStage.EDITING);
        assertThat(shootComments).hasSize(1);
        assertThat(shootComments.get(0).getCommentText()).isEqualTo("Shoot-only note");
        assertThat(editComments).hasSize(1);
        assertThat(editComments.get(0).getCommentText()).isEqualTo("Edit-only note");
    }

    @Test
    void publishingDescriptionIsNativeOnlyButAssignedPublisherCanComment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Pub Cam", "e2e-pubdesc-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, cam, "PERM_18_SHOOT_EXECUTION");
        String editor = createUser(ceo, "Pub Editor", "e2e-pubdesc-ed-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);
        grantExecutionPermission(ceo, editor, "PERM_19_EDIT_EXECUTION");
        String pubEmail = "e2e-pubdesc-pub-" + unique + "@kcpcbandhani.local";
        String pub = createUser(ceo, "Pub Publisher", pubEmail, PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (priority/schedule/folder link/initial output+publication scope/shoot
        // team) in one call and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Pub Desc " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/pubdesc-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cam + "\"],\"publisherUserIds\":[\"" + pub + "\"]}}");
        String planId = findContentPlanId(ideaId);
        String outputId = plannedOutputIdFor(planId);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login("e2e-pubdesc-cam-" + unique + "@kcpcbandhani.local", "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor + "\"],\"leadEditorUserId\":\"" + editor + "\"}");
        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login("e2e-pubdesc-ed-" + unique + "@kcpcbandhani.local", "Passw0rd!");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor + "\"],"
                        + "\"publisherUserIds\":[\"" + pub + "\"]}");

        // A Publisher (even the correctly assigned one) cannot set the Description - native only.
        TestApiClient pubClient = new TestApiClient(port);
        pubClient.login(pubEmail, "Passw0rd!");
        HttpResponse<String> pubDescAttempt = pubClient.postFormAjax("/app/deliverables/" + planId + "/publishing/description",
                Map.of("description", "Trying to set my own instructions"));
        assertThat(pubDescAttempt.statusCode()).isEqualTo(403);

        // CEO sets it.
        HttpResponse<String> ceoDesc = ceo.postFormAjax("/app/deliverables/" + planId + "/publishing/description",
                Map.of("description", "Instagram + Facebook, approved caption only."));
        assertThat(ceoDesc.statusCode()).isEqualTo(200);
        ContentPlan reloaded = contentPlanRepository.findById(java.util.UUID.fromString(planId)).orElseThrow();
        assertThat(reloaded.getPublishingDescription()).isEqualTo("Instagram + Facebook, approved caption only.");

        // But the assigned Publisher CAN comment on the Publishing thread.
        HttpResponse<String> pubComment = pubClient.postFormAjax("/app/deliverables/" + planId + "/publishing/comments",
                Map.of("commentText", "Caption approved, publishing now."));
        assertThat(pubComment.statusCode()).isEqualTo(200);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return response.get("userId").asText();
    }

    /** Candidate eligibility/execution is now permission-driven (OperationalEligibilityService). */
    private void grantExecutionPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> grant = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        if (grant.statusCode() != 201) {
            throw new IllegalStateException("Failed to grant " + permissionCode + " to " + userId + ": " + grant.body());
        }
    }

    /** Workflow redesign: Idea Review approval now carries every former Planning field (including
     * the initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA) - the
     * given cameraperson must already hold an active PERM_18_SHOOT_EXECUTION grant. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title, String camId) throws Exception {
        long unique = Instant.now().toEpochMilli();
        String pubId = createUser(ceo, "Discussion Pub", "e2e-discussion-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        grantExecutionPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/stagediscussion-" + title.hashCode() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        return findContentPlanId(ideaId);
    }

    private String findContentPlanId(String ideaIdText) {
        java.util.UUID ideaId = java.util.UUID.fromString(ideaIdText);
        Idea idea = ideaRepository.findById(ideaId).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        return plan.getId().toString();
    }

    private String plannedOutputIdFor(String contentPlanIdText) {
        ContentPlan plan = contentPlanRepository.findById(java.util.UUID.fromString(contentPlanIdText)).orElseThrow();
        return plannedOutputRepository.findByContentPlan(plan).stream()
                .findFirst().map(PlannedOutput::getId).map(java.util.UUID::toString).orElseThrow();
    }
}
