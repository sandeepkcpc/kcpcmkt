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
import java.time.LocalDate;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeliverableMvcController#view's Model shoot-completion hard-deny (see the class-level Model
 * gate right after {@code shootTaskCompleted} is computed) must end only THAT Model's access to
 * their own finished Shoot task - never a genuinely different, still-open Edit/Publish execution
 * task the same Model separately holds on the exact same Content Plan. The architecture rule is
 * permission + assignment decide execution access (OperationalEligibilityService's own stated
 * principle), never Business Role - a Model is not an exception to that rule once they are
 * legitimately holding an execution permission and a real assignment.
 *
 * <p>Before the fix, the hard-deny fired unconditionally the moment the Model's OWN shoot task
 * was complete and they were linked as talent on the plan, redirecting to /app/my-shoots even
 * when they were also the actively assigned Editor/Publisher on that same Content - exactly the
 * "My Work -&gt; Edit -&gt; Start Edit -&gt; redirected to My Shoots" bug this test class guards against.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ModelExecutionTaskRoutingTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = emailFor(label, unique);
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"METR " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"model execution task routing test fixture\"}");
        return user.get("userId").asText();
    }

    private String emailFor(String label, long unique) {
        return "metr-" + label + "-" + unique + "@kcpcbandhani.local";
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"model execution task routing test fixture grant\"}");
    }

    /** Idea approved straight to Shoot Assigned, with {@code modelId} linked as talent (their own
     * Shoot task) and also included in the initial Cameraperson team alongside a real camera
     * operator - lets the same Model be BOTH the plan's talent (whose Shoot task ends at Shoot
     * Review approval) and, separately, a legitimate Edit/Publish assignee on this exact plan. */
    private String[] buildToSA(TestApiClient ceo, String modelId, long unique) throws Exception {
        String[] cam = {createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique), emailFor("cam", unique)};
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String planningPubId = createUser(ceo, "planning-pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, planningPubId, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"METR Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/metr-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"talentUserIds\":[\"" + modelId + "\"],"
                        + "\"publisherUserIds\":[\"" + planningPubId + "\"]}}");
        return cam;
    }

    private ContentPlan planFor(long unique) {
        var idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals("METR Idea " + unique)).findFirst().orElseThrow();
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }

    /** Test case 2: Model + EDIT_EXECUTION + a real Edit assignment on the SAME plan where their
     * own Shoot task (as talent) has already completed - must reach the Edit Execution screen,
     * never redirect to My Shoots. */
    @Test
    void modelWithCompletedShootTaskAndSeparateEditAssignmentReachesEditExecutionScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String modelId = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        grantPermission(ceo, modelId, "PERM_19_EDIT_EXECUTION");
        String[] cam = buildToSA(ceo, modelId, unique);
        ContentPlan plan = planFor(unique);
        String planId = plan.getId().toString();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);

        // Shoot Review Approval assigns the Edit team - the Model is included here as a real,
        // active Editor on this exact plan (never a fabricated/second-source assignment).
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam[0] + "\"],"
                        + "\"editorUserIds\":[\"" + modelId + "\"],\"leadEditorUserId\":\"" + modelId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");
        // The Model's own Shoot task (as talent) is now permanently complete - APPROVE_SHOOT fired.

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(emailFor("model", unique), "Passw0rd!");
        HttpResponse<String> response = modelClient.get("/app/deliverables/" + planId + "?tab=edit");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Edit Task &mdash; " + plan.getContentId());
        assertThat(response.body()).contains("Start Edit");

        // My Shoots itself is completely unaffected - the same completed Shoot task still shows
        // as Past there, this fix only stopped it from swallowing the SEPARATE Edit task.
        String myShootsBody = modelClient.get("/app/my-shoots").body();
        assertThat(myShootsBody).contains(plan.getContentId()).contains("Completed");
    }

    /** Test case 5: Model + PUBLISH_EXECUTION + a real Publishing assignment on the same plan -
     * same fix, Publishing side. */
    @Test
    void modelWithCompletedShootTaskAndSeparatePublishAssignmentReachesPublishExecutionScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String modelId = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        grantPermission(ceo, modelId, "PERM_08_PUBLISHING_EXECUTION");
        String[] cam = buildToSA(ceo, modelId, unique);
        ContentPlan plan = planFor(unique);
        String planId = plan.getId().toString();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);

        String edId = createUser(ceo, "ed", "01926e3e-0001-7000-8000-000000000005", unique);
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam[0] + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        TestApiClient edClient = new TestApiClient(port);
        edClient.login(emailFor("ed", unique), "Passw0rd!");
        assertThat(edClient.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(edClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);

        // Edit Review Approval assigns the Publisher team - the Model is included here as a real,
        // active Publisher on this exact plan.
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + modelId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(emailFor("model", unique), "Passw0rd!");
        HttpResponse<String> response = modelClient.get("/app/deliverables/" + planId + "?tab=publishing");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Publishing Task &mdash; " + plan.getContentId());
    }

    /** Test case 6 (security): a Model holding EDIT_EXECUTION permission but with NO actual Edit
     * assignment on this plan must still be denied - permission alone must never open an
     * arbitrary/another employee's execution task. The pre-fix hard-deny behavior (redirect to My
     * Shoots) is preserved exactly for this case. */
    @Test
    void modelWithEditExecutionPermissionButNoRealAssignmentStaysDenied() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String modelId = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        // Granted the permission, but never added to editorUserIds/publisherUserIds anywhere below -
        // no real assignment on this plan.
        grantPermission(ceo, modelId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, modelId, "PERM_08_PUBLISHING_EXECUTION");
        String[] cam = buildToSA(ceo, modelId, unique);
        ContentPlan plan = planFor(unique);
        String planId = plan.getId().toString();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);

        String edId = createUser(ceo, "ed", "01926e3e-0001-7000-8000-000000000005", unique);
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam[0] + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(emailFor("model", unique), "Passw0rd!");
        HttpResponse<String> response = modelClient.get("/app/deliverables/" + planId);

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/app/my-shoots");
    }
}
