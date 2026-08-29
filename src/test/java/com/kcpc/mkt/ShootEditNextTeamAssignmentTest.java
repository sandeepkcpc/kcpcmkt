package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
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
 * Workflow redesign: next-stage team assignment now folds directly into the SAME Approve action -
 * Shoot Review Approve requires an Editor team (incl. Editor Lead), Edit Review Approve requires a
 * Publisher team (no Lead concept for Publishing - see PublishingService/ENG-036/ENG-044) - rather
 * than a separate assignment screen/step. See ShootingService#decideShootReview and
 * EditingService#decideEditReview.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShootEditNextTeamAssignmentTest {

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

    private String createUser(TestApiClient ceo, String label, String roleId, String permission, long unique)
            throws Exception {
        String email = "e2e-nta-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"NTA " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"next team assignment test fixture\"}");
        String userId = user.get("userId").asText();
        if (permission != null) {
            ceo.post("/api/v1/admin/permission-grants",
                    "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                            + "\"scopeType\":\"GLOBAL\",\"reason\":\"next team assignment test grant\"}");
        }
        return userId;
    }

    /** Idea -> approved -> Shoot Started -> Submitted for Shoot Review, ready for a Shoot Review decision. */
    private String buildToShootReview(TestApiClient ceo, String camId, String camEmail, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"NTA Shoot " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/nta-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        String planId = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        return planId;
    }

    /** Same as above, then also drives it through a valid Shoot Review approval (with the given
     * Editor as both the sole Editor and the Editor Lead) to reach Edit Review. */
    private String[] buildToEditReview(TestApiClient ceo, long unique) throws Exception {
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-nta-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);
        String editorId = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + editorId + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");
        TestApiClient editor = new TestApiClient(port);
        editor.login("e2e-nta-editor-" + unique + "@kcpcbandhani.local", "Passw0rd!");
        editor.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        return new String[] {planId, editorId};
    }

    // ---------------------------------------------------------------- Shoot Review -> Editor Assignment

    @Test
    void shootApprovalWithValidEditorAssignmentSucceedsAndAssignsTheEditorTeam() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-nta-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);
        String editorId = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);

        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + editorId + "\"}");

        assertThat(approved.get("status").asText()).isEqualTo("EA");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        var active = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getEditor().getId()).isEqualTo(UUID.fromString(editorId));
        assertThat(active.get(0).isLead()).isTrue();
    }

    @Test
    void shootApprovalWithoutEditorFailsValidation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-nta-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("At least one Editor must be assigned before approval");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SRV");
    }

    @Test
    void shootApprovalWithInvalidEditorLeadFailsValidation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-nta-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);
        String editorId = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String otherEditorId = createUser(ceo, "editor-other", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);

        // Lead not present in the selected Editor(s) list.
        HttpResponse<String> notSelected = ceo.post("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + otherEditorId + "\"}");
        assertThat(notSelected.statusCode()).isEqualTo(400);
        assertThat(notSelected.body()).contains("Editor Lead must be one of the selected Editor(s)");

        // Lead entirely missing.
        HttpResponse<String> missingLead = ceo.post("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"]}");
        assertThat(missingLead.statusCode()).isEqualTo(400);
        assertThat(missingLead.body()).contains("Editor Lead is mandatory");

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SRV");
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
    }

    // ---------------------------------------------------------------- Edit Review -> Publisher Assignment

    @Test
    void editApprovalWithValidPublisherAssignmentSucceedsAndAssignsThePublisherTeam() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] fx = buildToEditReview(ceo, unique);
        String planId = fx[0];
        String editorId = fx[1];
        String pubId = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);

        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");

        assertThat(approved.get("status").asText()).isEqualTo("RFP");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        var active = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getPublisher().getId()).isEqualTo(UUID.fromString(pubId));
    }

    @Test
    void editApprovalWithoutPublisherFailsValidation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] fx = buildToEditReview(ceo, unique);
        String planId = fx[0];
        String editorId = fx[1];

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"]}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("At least one Publisher must be assigned before approval");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("ERV");
    }

    /** Publisher Assignment has no Lead concept (unlike Editor/Cameraperson) - see ENG-036/ENG-044 -
     * so the equivalent robustness check here is an unresolvable Publisher id, not an invalid Lead. */
    @Test
    void editApprovalWithUnresolvablePublisherIdFailsValidation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] fx = buildToEditReview(ceo, unique);
        String planId = fx[0];
        String editorId = fx[1];
        String bogusPublisherId = UUID.randomUUID().toString();

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"],"
                        + "\"publisherUserIds\":[\"" + bogusPublisherId + "\"]}");

        assertThat(response.statusCode()).isEqualTo(404);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("ERV");
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
    }

    // ---------------------------------------------------------------- Reject/Retain: no next-team required

    @Test
    void shootReviewRequestReworkDoesNotRequireEditorAssignment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-nta-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);

        JsonNode rework = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":false,\"reason\":\"Reshoot needed, lighting issue\"}");

        assertThat(rework.get("status").asText()).isEqualTo("SIP");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
    }

    @Test
    void editReviewRequestReworkDoesNotRequirePublisherAssignment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] fx = buildToEditReview(ceo, unique);
        String planId = fx[0];

        JsonNode rework = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":false,\"reason\":\"Colour grading needs another pass\"}");

        assertThat(rework.get("status").asText()).isEqualTo("ED");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
    }
}
