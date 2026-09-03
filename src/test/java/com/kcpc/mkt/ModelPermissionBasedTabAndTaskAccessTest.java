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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "My Work" tab (and nav link) visibility for a Model/Talent is permission/assignment-driven,
 * exactly the same rule every other Employee already gets (LandingMvcController#myWork's
 * showShootTab/showEditTab/showPublishTab, and MvcNavigationAdvice#employeeHasMyWorkExecutionAccess)
 * - never a Business-Role check. "My Shoots" (their own participation screen) always stays
 * reachable regardless; "My Work" becomes additionally reachable the moment a Model holds a
 * qualifying execution permission, and shows exactly the stage tab(s) that permission (or a real
 * assignment) covers - nothing role-specific added, nothing role-specific removed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ModelPermissionBasedTabAndTaskAccessTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createModel(TestApiClient ceo, String label, long unique) throws Exception {
        String email = "mptt-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MPTT " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + MODEL_ROLE_ID + "\",\"creationReason\":\"model permission tab test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private String[] createPublisher(TestApiClient ceo, String label, long unique) throws Exception {
        String email = "mptt-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MPTT " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"model permission tab test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"model permission tab test grant\"}");
        return new String[] {userId, email};
    }

    @Test
    void modelWithNoExecutionPermissionSeesOnlyMyShootsNeverMyWork() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] model = createModel(ceo, "noperm", unique);

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(model[1], "Passw0rd!");
        String body = modelClient.get("/app/my-shoots").body();

        assertThat(body).contains(">My Shoots<");
        assertThat(body).doesNotContain(">My Work<");
    }

    @Test
    void modelGrantedEditExecutionAndAssignedAsEditorSeesMyWorkWithEditTabAndReachesEditTaskDetail() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] model = createModel(ceo, "editassigned", unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + model[0] + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"model permission tab test grant\"}");

        String[] pub = createPublisher(ceo, "editassignedpub", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MPTT Edit Assigned Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Edit (Stages = Edit + Publishing) reaches EA directly from Idea Review approval -
        // the Model is the sole Editor/Editor Lead here, same eligibility gate
        // (OperationalEligibilityService#requireEditExecutionEligible) any Editor must pass.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mptt-" + unique + "\","
                        + "\"editorUserIds\":[\"" + model[0] + "\"],\"leadEditorUserId\":\"" + model[0] + "\","
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(model[1], "Passw0rd!");

        // Nav: both My Shoots (always) and My Work (now reachable) show up.
        String navPage = modelClient.get("/app/my-shoots").body();
        assertThat(navPage).contains(">My Shoots<").contains(">My Work<");

        // My Work: Edit tab present (permission + real assignment), Shoot/Publishing tabs absent
        // (no permission, no assignment for either).
        String myWorkBody = modelClient.get("/app/my-work").body();
        assertThat(myWorkBody).contains("data-tab=\"edit\"");
        assertThat(myWorkBody).doesNotContain("data-tab=\"shoot\"").doesNotContain("data-tab=\"publish\"");
        assertThat(myWorkBody).contains(plan.getContentId());

        // The Content ID's own View Details opens the real Edit Execution screen, exactly like any
        // other Editor - permission + assignment + outstanding task, never Business Role.
        String taskDetail = modelClient.get("/app/deliverables/" + plan.getId()).body();
        assertThat(taskDetail).contains("Edit Task &mdash; " + plan.getContentId());
    }

    @Test
    void modelWithGlobalEditPermissionButNoAssignmentStillSeesEditTabJustEmpty() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] model = createModel(ceo, "globalpermonly", unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + model[0] + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"model permission tab test grant\"}");

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(model[1], "Passw0rd!");

        // Tab visibility comes from the permission alone (matches every other Employee's rule) -
        // no assignment needed to SEE the tab, only to execute a specific Content ID's task.
        String myWorkBody = modelClient.get("/app/my-work").body();
        assertThat(myWorkBody).contains("data-tab=\"edit\"");
        assertThat(myWorkBody).contains("No active edit tasks.");
    }
}
