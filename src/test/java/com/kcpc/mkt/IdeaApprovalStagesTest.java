package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
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
 * Stages (ENG-091): IdeaService#approve's conditional Standard/Direct Edit/Direct Publishing
 * logic, driven via the REST idea-review endpoint (/api/v1/ideas/{id}/review -
 * IdeaReviewDecisionRequest.planning already deserializes the new
 * stages/editorUserIds/publisherUserIds fields with zero controller changes needed there - only
 * the two @RequestParam-based MVC controllers needed explicit new params). Real HTTP, real
 * Postgres, no mocking - same convention as SkipStageFlowTest/EditUserFlowTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaApprovalStagesTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    ShootingAssignmentRepository shootingAssignmentRepository;
    @Autowired
    EditingAssignmentRepository editingAssignmentRepository;
    @Autowired
    PublishingAssignmentRepository publishingAssignmentRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "stages-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Stages " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"stages test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"stages test fixture grant\"}");
    }

    private String createIdea(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Stages Idea " + unique + "\"}");
        return idea.get("ideaId").asText();
    }

    private HttpResponse<String> approve(TestApiClient ceo, String ideaId, String planningJson) throws Exception {
        return ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":"
                        + planningJson + "}");
    }

    private ContentPlan planFor(String ideaId) {
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    @Test
    void standardComboUnchangedFromBeforeStages() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisher = createUser(ceo, "standardpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"],"
                + "\"stages\":[\"SHOOT\",\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.SA);
        assertThat(plan.getShootStageSkipReason()).isNull();
        assertThat(plan.getEditStageSkipReason()).isNull();
        assertThat(shootingAssignmentRepository.findByContentPlan(plan)).hasSize(1);
    }

    @Test
    void directEditLandsAtEaWithEditorAssignedAsLeadNoShootingAssignment() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] publisher = createUser(ceo, "directeditpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],"
                + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.EA);
        assertThat(plan.getShootStageSkipReason()).isEqualTo("Stage not selected during planning");
        assertThat(plan.getEditStageSkipReason()).isNull();
        assertThat(shootingAssignmentRepository.findByContentPlan(plan)).isEmpty();
        var editingAssignments = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(editingAssignments).hasSize(1);
        // ENG-095: Direct Edit now requires (and records) an Editor Lead, exactly like the normal
        // Shoot Review Approve fold-in - superseding the earlier ENG-091 "no Lead here" decision.
        assertThat(editingAssignments.get(0).isLead()).isTrue();
    }

    @Test
    void directPublishingLandsAtRfpWithPublisherAssignedBothSkipReasonsSet() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.RFP);
        assertThat(plan.getShootStageSkipReason()).isEqualTo("Stage not selected during planning");
        assertThat(plan.getEditStageSkipReason()).isEqualTo("Stage not selected during planning");
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(1);

        // No Shoot/Edit artifacts required - the assigned Publisher can start Publishing immediately.
        TestApiClient publisherClient = new TestApiClient(port);
        publisherClient.login(publisher[1], "Passw0rd!");
        HttpResponse<String> startResponse = publisherClient.post(
                "/api/v1/content-plans/" + plan.getId() + "/publishing/start", "");
        assertThat(startResponse.statusCode()).isEqualTo(200);
    }

    @Test
    void absentStagesDefaultsToStandardForBackwardCompatibility() throws Exception {
        // Every pre-ENG-091 caller (existing tests, any legacy API consumer) never sends "stages"
        // at all - must keep behaving exactly as before, not be rejected.
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisher = createUser(ceo, "absentstagespub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"]}");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(planFor(ideaId).getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.SA);
    }

    @Test
    void explicitlyEmptyStagesRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\",\"stages\":[]}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid stage selection");
    }

    @Test
    void invalidStageComboRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        // SHOOT without EDIT is not one of the 3 valid starting-point combinations.
        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"stages\":[\"SHOOT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid stage selection");
    }

    @Test
    void directEditMissingEditorsRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Editor");
    }

    @Test
    void directPublishingMissingPublishersRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"stages\":[\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Publisher");
    }

    @Test
    void directEditPlanProceedsThroughNormalEditReviewApproveToRfp() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] irPublisher = createUser(ceo, "directedittorfppub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, irPublisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                + "\"folderLink\":\"https://drive.example.com/stages-" + unique + "\","
                + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                + "\"publisherUserIds\":[\"" + irPublisher[0] + "\"],"
                + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        ContentPlan plan = planFor(ideaId);
        String planId = plan.getId().toString();

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        assertThat(editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode())
                .isEqualTo(200);
        assertThat(editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode())
                .isEqualTo(200);

        // Edit Review Approve still folds in Publisher(s) exactly as it always has - unaffected by
        // how this plan started.
        String[] publisher = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        HttpResponse<String> decision = ceo.post("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}");
        assertThat(decision.statusCode()).isEqualTo(200);
        assertThat(planFor(ideaId).getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.RFP);
    }
}
