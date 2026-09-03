package com.kcpc.mkt;

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

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "My Shoots" -> View, for a Model/Talent (DeliverableMvcController#view's Model gate/branch).
 * Whether clicking View opens the redesigned Shoot Execution screen (shoot-task-detail.jsp, the
 * same page a Camera Person gets) is decided purely by whether the Model actually holds a
 * currently-valid PERM_18_SHOOT_EXECUTION grant covering this plan AND their personal task is
 * still outstanding - never by Business Role alone, unlike the pre-existing Camera Person branch
 * this one sits beside. Without that grant (task still outstanding either way), View falls
 * through to the standard read-only shell exactly like any other viewer with no Shoot authority -
 * no permission is ever granted automatically by this routing. Once the task is COMPLETE, access
 * to this Content ends entirely (a 302 back to My Shoots, not merely a less-privileged 200 view) -
 * even holding PERM_18 no longer matters at that point. See MyShootsTaskCompletionTest for the
 * Upcoming/Past classification this same completion state also drives.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyShootsModelViewRoutingTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    @Test
    void modelWithoutShootExecutionPermissionNeverGetsTheShootExecutionScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String modelEmail = "msr-noperm-" + unique + "@kcpcbandhani.local";
        String modelId = createUser(ceo, "MSR NoPerm Model", modelEmail, MODEL_ROLE_ID);
        ContentPlan plan = createApprovedPlan(ceo, "MSR No Perm Idea " + unique, List.of(modelId));

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(modelEmail, "Passw0rd!");
        HttpResponse<String> response = modelClient.get("/app/deliverables/" + plan.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        // Falls through to the standard shell, not the redesigned Shoot Task page, and never
        // exposes hands-on Shoot Execution controls just because a shoot happens to be assigned.
        assertThat(response.body()).doesNotContain("Shoot Task &mdash;").doesNotContain("Start Shoot");
    }

    @Test
    void modelWithShootExecutionPermissionGetsTheShootExecutionScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String modelEmail = "msr-withperm-" + unique + "@kcpcbandhani.local";
        String modelId = createUser(ceo, "MSR WithPerm Model", modelEmail, MODEL_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + modelId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");
        ContentPlan plan = createApprovedPlan(ceo, "MSR With Perm Idea " + unique, List.of(modelId));

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(modelEmail, "Passw0rd!");
        HttpResponse<String> response = modelClient.get("/app/deliverables/" + plan.getId());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Shoot Task &mdash; " + plan.getContentId());
        // The Model is never the active ShootingAssignment holder (that's still the Cameraperson's
        // own execution task) - holding PERM_18 opens this screen, it does not hand the Model
        // hands-on Start/Submit controls that belong to a different assignee.
        assertThat(response.body()).doesNotContain("Start Shoot").doesNotContain("Submit for Review");
    }

    /** Test Case 7: even holding PERM_18, once the Model's task is already complete
     * (APPROVE_SHOOT has fired - see LandingMvcController#isModelShootTaskCompleted), the Model
     * loses access to this Content entirely - not just the Shoot Execution screen, the whole
     * /app/deliverables/{id} route server-side (a strict access rule, not merely "show a
     * different, less-privileged view" - see DeliverableMvcController#view's early Model gate). */
    @Test
    void modelWithPermissionButAlreadyCompletedTaskIsDeniedAccessEntirely() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String modelEmail = "msr-completed-" + unique + "@kcpcbandhani.local";
        String modelId = createUser(ceo, "MSR Completed Model", modelEmail, MODEL_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + modelId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");

        String camEmail = "msr-completed-cam-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "MSR Completed Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");

        String pubEmail = "msr-completed-pub-" + unique + "@kcpcbandhani.local";
        String pubId = createUser(ceo, "MSR Completed Pub", pubEmail, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");

        com.fasterxml.jackson.databind.JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"MSR Completed Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/msr-completed-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"talentUserIds\":[\"" + modelId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);

        String editEmail = "msr-completed-editor-" + unique + "@kcpcbandhani.local";
        String editId = createUser(ceo, "MSR Completed Editor", editEmail, "01926e3e-0001-7000-8000-000000000005");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");
        var decision = ceo.post("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editId + "\"],\"leadEditorUserId\":\"" + editId + "\"}");
        assertThat(decision.statusCode()).as(decision.body()).isEqualTo(200);

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(modelEmail, "Passw0rd!");
        HttpResponse<String> response = modelClient.get("/app/deliverables/" + plan.getId());

        // Denied entirely - not merely a less-privileged view of the same page. Redirected back to
        // My Shoots (the same "existing unauthorized/redirect" pattern this app already uses
        // elsewhere, e.g. WorkflowParticipationInterceptor), never a 200 with anything about this
        // Content, Shoot Execution or otherwise.
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/app/my-shoots");
    }

    /** Same fixture shape as MyShootsTest#createApprovedPlan - a throwaway Cameraperson keeps the
     * approval valid without affecting this test's own Model-specific assertions. */
    private ContentPlan createApprovedPlan(TestApiClient ceo, String ideaTitle, List<String> modelUserIds) throws Exception {
        long unique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String camId = createUser(ceo, "MSR Default Cam " + unique, "msr-default-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");
        String pubId = createUser(ceo, "MSR Default Pub " + unique, "msr-default-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots view routing test grant\"}");
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(10).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/msr-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(camId));
        reviewParams.put("modelUserIds", modelUserIds);
        reviewParams.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> approval = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams);
        assertThat(approval.statusCode()).isEqualTo(302);
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"my shoots view routing test fixture\"}");
        return response.get("userId").asText();
    }
}
