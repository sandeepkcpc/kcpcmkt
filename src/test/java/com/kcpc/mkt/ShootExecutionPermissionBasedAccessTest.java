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
 * DeliverableMvcController#view's Shoot Execution routing is decided purely by permission +
 * assignment + task-outstanding (OperationalEligibilityService already established this same
 * "permission-driven, never Business-Role-name-based" principle for WHO can be assigned/selected
 * as Cameraperson in the first place - this is the corresponding rule for the redesigned
 * execution screen itself). An HR Manager - an ordinary EMPLOYEE Business Role never previously
 * treated specially anywhere in this app - reaches exactly the same shoot-task-detail.jsp a
 * Camera Person or a Model does, provided they hold PERM_18_SHOOT_EXECUTION and are actually
 * assigned as the plan's Cameraperson. Without either, they get nothing extra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShootExecutionPermissionBasedAccessTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003"; // EMPLOYEE access class
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, long unique) throws Exception {
        String email = "sepb-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"SEPB " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + HR_MANAGER_ROLE_ID + "\",\"creationReason\":\"shoot execution permission test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private String[] createPublisher(TestApiClient ceo, String label, long unique) throws Exception {
        String email = "sepb-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"SEPB " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"shoot execution permission test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"shoot execution permission test grant\"}");
        return new String[] {userId, email};
    }

    @Test
    void hrManagerWithShootExecutionPermissionAndAssignmentGetsTheShootExecutionScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] hr = createUser(ceo, "assigned", unique);
        // The very act of being a valid camerapersonUserIds candidate already requires PERM_18
        // (OperationalEligibilityService#requireShootExecutionEligible, called from
        // IdeaService#approve) - Business Role is never checked, only the grant.
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + hr[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"shoot execution permission test grant\"}");

        String[] pub = createPublisher(ceo, "assignedpub", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"SEPB HR Assigned Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/sepb-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + hr[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient hrClient = new TestApiClient(port);
        hrClient.login(hr[1], "Passw0rd!");
        HttpResponse<String> response = hrClient.get("/app/deliverables/" + plan.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Shoot Task &mdash; " + plan.getContentId());
    }

    @Test
    void hrManagerWithPermissionButNotAssignedToThisPlanNeverGetsTheShootExecutionScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        // Holds PERM_18 globally, but this specific plan is assigned to someone else entirely -
        // permission alone is never enough without actual assignment/participation on THIS Content ID.
        String[] hrWithPermNotAssigned = createUser(ceo, "unassigned", unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + hrWithPermNotAssigned[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"shoot execution permission test grant\"}");
        String[] otherCam = createUser(ceo, "othercam", unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + otherCam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"shoot execution permission test grant\"}");

        String[] pub = createPublisher(ceo, "unassignedpub", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"SEPB HR Unassigned Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/sepb-unassigned-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + otherCam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient hrClient = new TestApiClient(port);
        hrClient.login(hrWithPermNotAssigned[1], "Passw0rd!");
        HttpResponse<String> response = hrClient.get("/app/deliverables/" + plan.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("Shoot Task &mdash;");
    }
}
