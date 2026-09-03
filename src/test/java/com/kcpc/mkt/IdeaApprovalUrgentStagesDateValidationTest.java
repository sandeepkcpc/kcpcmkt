package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
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
 * ENG-093: Shoot Date/Edit Date requiredness at Idea Approval must depend on which Stages are
 * actually part of the pipeline, in BOTH Standard and Urgent Planning Mode - never on Planning
 * Mode alone. Before this fix, Urgent mode unconditionally required both Shoot Date and Edit Date
 * regardless of Stages, so approving with Planning Mode=Urgent + Stages=Edit+Publishing (Direct
 * Edit, Shoot skipped) incorrectly failed with a Shoot Date error. Real HTTP, real Postgres, no
 * mocking - same convention as IdeaApprovalStagesTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaApprovalUrgentStagesDateValidationTest {

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
        String email = "urgentstages-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"UrgentStages " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"urgent stages date test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"urgent stages date test fixture grant\"}");
    }

    private String createIdea(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Urgent Stages Date Idea " + unique + "\"}");
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

    // ================================================================== 1. Standard + Shoot/Edit/Publishing

    @Test
    void standardShootEditPublishingRequiresNoExplicitDatesAndDefaultsBoth() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisher = createUser(ceo, "standardsepub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        LocalDate liveDate = LocalDate.now().plusDays(10);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "\","
                + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"],"
                + "\"stages\":[\"SHOOT\",\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getPlannedShootDate()).isEqualTo(liveDate.minusDays(5));
        assertThat(plan.getPlannedEditDate()).isEqualTo(liveDate.minusDays(2));
    }

    // ================================================================== 2. Standard + Edit/Publishing

    @Test
    void standardEditPublishingRequiresNoShootDateAndDefaultsEditDateOnly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] publisher = createUser(ceo, "standardeppub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        LocalDate liveDate = LocalDate.now().plusDays(10);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "\","
                + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],"
                + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getPlannedShootDate()).isNull();
        assertThat(plan.getPlannedEditDate()).isEqualTo(liveDate.minusDays(2));
    }

    // ================================================================== 3. Standard + Publishing

    @Test
    void standardPublishingOnlyRequiresNeitherShootNorEditDate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        LocalDate liveDate = LocalDate.now().plusDays(10);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getPlannedShootDate()).isNull();
        assertThat(plan.getPlannedEditDate()).isNull();
        assertThat(plan.getPlannedLiveDate()).isEqualTo(liveDate);
    }

    // ================================================================== 4. Urgent + Shoot/Edit/Publishing

    @Test
    void urgentShootEditPublishingRequiresShootDateAndEditDate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisher = createUser(ceo, "urgentsepub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        LocalDate liveDate = LocalDate.now().plusDays(3);
        LocalDate shootDate = LocalDate.now().plusDays(1);
        LocalDate editDate = LocalDate.now().plusDays(2);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"planningMode\":\"URGENT\",\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                + "\"urgencyReason\":\"Client moved the launch up\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "\","
                + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"],"
                + "\"stages\":[\"SHOOT\",\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getPlannedShootDate()).isEqualTo(shootDate);
        assertThat(plan.getPlannedEditDate()).isEqualTo(editDate);

        // Negative: omitting Shoot Date here (Shoot IS part of the pipeline) must still fail.
        String ideaId2 = createIdea(ceo, unique + 1);
        HttpResponse<String> missingShoot = approve(ceo, ideaId2, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"planningMode\":\"URGENT\",\"editDate\":\"" + editDate + "\","
                + "\"urgencyReason\":\"Client moved the launch up\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "-2\","
                + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"stages\":[\"SHOOT\",\"EDIT\",\"PUBLISHING\"]}");
        assertThat(missingShoot.statusCode()).isEqualTo(400);
        assertThat(missingShoot.body()).contains("Shoot Date");
    }

    // ================================================================== 5. Urgent + Edit/Publishing (the fix)

    @Test
    void urgentDirectEditDoesNotRequireShootDateOnlyEditDate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] publisher = createUser(ceo, "urgenteppub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        LocalDate liveDate = LocalDate.now().plusDays(3);
        LocalDate editDate = LocalDate.now().plusDays(2);

        // The exact reported bug scenario: Urgent + Edit/Publishing, no Shoot Date sent at all.
        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"planningMode\":\"URGENT\",\"editDate\":\"" + editDate + "\","
                + "\"urgencyReason\":\"Client moved the launch up\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "\","
                + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],"
                + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).as("Urgent + Direct Edit must NOT produce a Shoot Date validation error")
                .isEqualTo(200);
        assertThat(response.body()).doesNotContain("Shoot Date");

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getPlannedShootDate()).isNull();
        assertThat(plan.getPlannedEditDate()).isEqualTo(editDate);

        // Negative: Edit IS part of the pipeline here, so omitting Edit Date must still fail.
        String ideaId2 = createIdea(ceo, unique + 1);
        HttpResponse<String> missingEdit = approve(ceo, ideaId2, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"planningMode\":\"URGENT\",\"urgencyReason\":\"Client moved the launch up\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "-2\","
                + "\"editorUserIds\":[\"" + editor[0] + "\"],\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(missingEdit.statusCode()).isEqualTo(400);
        assertThat(missingEdit.body()).contains("Edit Date");
    }

    // ================================================================== 6. Urgent + Publishing

    @Test
    void urgentDirectPublishingRequiresNeitherShootNorEditDate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);
        LocalDate liveDate = LocalDate.now().plusDays(3);

        HttpResponse<String> response = approve(ceo, ideaId, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"planningMode\":\"URGENT\",\"urgencyReason\":\"Client moved the launch up\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}");
        assertThat(response.statusCode()).as("Urgent + Direct Publishing must NOT require Shoot Date or Edit Date")
                .isEqualTo(200);
        assertThat(response.body()).doesNotContain("Shoot Date").doesNotContain("Edit Date");

        ContentPlan plan = planFor(ideaId);
        assertThat(plan.getPlannedShootDate()).isNull();
        assertThat(plan.getPlannedEditDate()).isNull();

        // Negative: Urgency Reason is still required regardless of stage.
        String ideaId2 = createIdea(ceo, unique + 1);
        HttpResponse<String> missingReason = approve(ceo, ideaId2, "{"
                + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                + "\"planningMode\":\"URGENT\","
                + "\"folderLink\":\"https://drive.example.com/urgentstages-" + unique + "-2\","
                + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}");
        assertThat(missingReason.statusCode()).isEqualTo(400);
        assertThat(missingReason.body()).contains("Urgency Reason");
    }
}
