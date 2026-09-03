package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
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
 * ENG-097/ENG-099: Publisher(s) is assigned at Idea Review approval (Planning) for EVERY valid
 * stage combo, and (ENG-099) is now MANDATORY for all three - Standard ({@code SHOOT,EDIT,
 * PUBLISHING}), Direct Edit ({@code EDIT,PUBLISHING}), and Direct Publishing ({@code PUBLISHING}
 * only) - not just Direct Publishing as before. Focused on the {@code IdeaService#approve}
 * validation/persistence layer itself (My Work's Upcoming/Active/History display is covered
 * separately by {@link PublisherUpcomingActiveHistoryTest}). Assigning a Publisher this early must
 * never activate Publishing (WorkflowStatus is untouched by it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningPublisherAssignmentTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
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
        String email = "ppat-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"PPAT " + label + " " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"planning publisher assignment test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"planning publisher assignment test fixture grant\"}");
    }

    private ContentPlan planFor(String ideaId) {
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    // ------------------------------------------------------------------ fails when no Publisher

    @Test
    void planningApprovalFailsWhenNoPublisherSelectedForFullPipelineCombo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "nopubfull", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT NoPubFull " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode response = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ppat-nopubfull-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"]}}");

        assertThat(response.get("message").asText()).contains("At least one Publisher must be assigned before approval");
        // The rejected approval has no side effects - no Content Plan was ever created for this Idea.
        assertThat(contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow()))
                .isEmpty();
    }

    @Test
    void planningApprovalFailsWhenNoPublisherSelectedForEditPlusPublishingCombo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "nopubeditor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT NoPubEdit " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode response = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/ppat-nopubedit-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}}");

        assertThat(response.get("message").asText()).contains("At least one Publisher must be assigned before approval");
    }

    @Test
    void planningApprovalFailsWhenNoPublisherSelectedForPublishingOnlyCombo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT PubOnlyNoPublisher " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode response = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/ppat-noPub-" + unique + "\"}}");

        assertThat(response.get("message").asText()).contains("At least one Publisher must be assigned before approval");
    }

    // ------------------------------------------------------------------ succeeds with Publisher(s)

    @Test
    void planningApprovalSucceedsWithOnePublisherForFullPipelineCombo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT FullPipeline " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ppat-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");

        // 1. Publisher assignment did NOT activate Publishing - status is still SA (Shoot Assigned) -
        // existing approval/assignment flow continues exactly as before.
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.SA);

        // 2. The PublishingAssignment row exists already, right now, before Shoot has even started.
        long activeCount = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher[0]))
                .count();
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void planningApprovalSucceedsWithOnePublisherForEditPlusPublishingCombo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editcombo", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] publisher = createUser(ceo, "pubeditcombo", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT EditPlusPub " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/ppat-editpub-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");

        assertThat(approved.get("status").asText()).isEqualTo("EA");
        ContentPlan plan = planFor(ideaId);
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher[0])).count()).isEqualTo(1);
    }

    @Test
    void planningApprovalSucceedsWithOnePublisherForPublishingOnlyCombo() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "puboonly", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT PubOnlyOk " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/ppat-pubonly-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");

        assertThat(approved.get("status").asText()).isEqualTo("RFP");
        ContentPlan plan = planFor(ideaId);
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher[0])).count()).isEqualTo(1);
    }

    @Test
    void planningApprovalSucceedsWithMultiplePublishersNoDuplicateAssignmentRows() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "multicam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisherA = createUser(ceo, "multipuba", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherA[0], "PERM_08_PUBLISHING_EXECUTION");
        String[] publisherB = createUser(ceo, "multipubb", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherB[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PPAT MultiPub " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ppat-multipub-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + publisherA[0] + "\",\"" + publisherB[0] + "\"]}}");

        assertThat(approved.get("status").asText()).isEqualTo("SA");
        ContentPlan plan = planFor(ideaId);
        var activeAssignments = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        // Exactly one row per selected Publisher - never merged, never duplicated.
        assertThat(activeAssignments).hasSize(2);
        assertThat(activeAssignments.stream().map(a -> a.getPublisher().getId().toString()))
                .containsExactlyInAnyOrder(publisherA[0], publisherB[0]);
    }
}
