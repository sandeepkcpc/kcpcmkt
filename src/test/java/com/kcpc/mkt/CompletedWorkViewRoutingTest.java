package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * My Work -&gt; Completed Work -&gt; "View Details" must open the SAME task-specific execution/detail
 * screen Active Work already sends the employee to (shoot-task-detail.jsp / edit-task-detail.jsp /
 * publish-task-detail.jsp), never the separate generic "Completed Task Details" snapshot page
 * (my-work-history-detail.jsp, still reachable at its own unchanged /app/my-work/history/{stage}/
 * {assignmentId} route for anyone still using it, but no longer what my-work.jsp's own links point
 * to). Routing is decided by {@link com.kcpc.mkt.web.mvc.DeliverableMvcController#view}'s existing
 * permission+assignment ownership check (the SAME one Active Work's identical branches already use)
 * plus the requested {@code ?tab=} - never by Business Role, and never merely by permission without
 * a real (still-current, not-reassigned-away) assignment on that exact plan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CompletedWorkViewRoutingTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = emailFor(label, unique);
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"CWVR " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"completed work routing test fixture\"}");
        return user.get("userId").asText();
    }

    private String emailFor(String label, long unique) {
        return "cwvr-" + label + "-" + unique + "@kcpcbandhani.local";
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"completed work routing test fixture grant\"}");
    }

    /**
     * Drives one Content Plan through Idea approval (Cameraperson = {@code camId}) -&gt; Shoot
     * (start/submit/approve, handing the Edit team to {@code edId}) -&gt; Edit (start/submit/approve,
     * handing Publishing to {@code pubId}) -&gt; Publishing (start + one ORIGINAL event, completing
     * it). Every one of camId/edId/pubId's own stage is fully complete by the time this returns.
     */
    private ContentPlan buildFullyCompletedPipeline(TestApiClient ceo, long unique, String camId, String camEmail,
                                                      String edId, String edEmail, String pubId, String pubEmail) throws Exception {
        String title = "CWVR Pipeline " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(20).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/cwvr-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(camId));
        reviewParams.put("publisherUserIds", List.of(pubId));
        reviewParams.put("outputsJson", List.of(
                "[{\"outputType\":\"POST\",\"reelTypes\":[],\"outputTitleDescription\":null,"
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}]"));
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams).statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "").statusCode()).isEqualTo(200);
        String pastTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString();
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/cwvr-" + unique + "\"}").statusCode()).isEqualTo(200);

        return contentPlanRepository.findById(plan.getId()).orElseThrow();
    }

    @Test
    void camerapersonCompletedShootViewDetailsOpensShootTaskDetailScreenReadOnly() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String camEmail = emailFor("cam", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, camEmail,
                edId, emailFor("ed", unique), pubId, emailFor("pub", unique));

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        HttpResponse<String> response = cam.get("/app/deliverables/" + plan.getId() + "?tab=shoot");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Shoot Task &mdash; " + plan.getContentId());
        // Read-only: the plan has moved well past Shoot (SA/SIP), so neither execution button the
        // active-task version of this exact screen would show is present here.
        assertThat(response.body()).doesNotContain("Start Shoot").doesNotContain("Submit for Review");
        // Regression: "Current Status" must show the Shoot task's own frozen outcome (Approved),
        // never the Content Plan's current overall status (which has since moved on to Publishing/
        // Performance Pending) - this was the exact reported bug.
        assertThat(response.body()).contains("<span class=\"status-pill status-completed\">Approved</span>");
        assertThat(response.body()).doesNotContain("<span class=\"status-pill status-completed\">Performance Pending</span>");
    }

    @Test
    void editorCompletedEditViewDetailsOpensEditTaskDetailScreenReadOnly() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String edEmail = emailFor("ed", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, emailFor("cam", unique),
                edId, edEmail, pubId, emailFor("pub", unique));

        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        HttpResponse<String> response = ed.get("/app/deliverables/" + plan.getId() + "?tab=edit");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Edit Task &mdash; " + plan.getContentId());
        assertThat(response.body()).doesNotContain("Start Edit").doesNotContain("Submit for Edit Review");
        // Regression: "Current Status" must show the Edit task's own frozen outcome (Approved),
        // never the Content Plan's current overall status (already at Publishing/PP by now).
        assertThat(response.body()).contains("<span class=\"status-pill status-completed\">Approved</span>");
        assertThat(response.body()).doesNotContain("<span class=\"status-pill status-completed\">Performance Pending</span>");
    }

    @Test
    void publisherCompletedPublishingViewDetailsOpensPublishTaskDetailScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String pubEmail = emailFor("pub", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, emailFor("cam", unique),
                edId, emailFor("ed", unique), pubId, pubEmail);

        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        HttpResponse<String> response = pub.get("/app/deliverables/" + plan.getId() + "?tab=publishing");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Publishing Task &mdash; " + plan.getContentId());
        // Regression: Publishing's own completion status ("Performance Pending", its normal
        // resting state once publication scope resolves) must display correctly - this is what
        // the task itself genuinely completed to, not a masked/mismatched status.
        assertThat(response.body()).contains("<span class=\"status-pill status-completed\">Performance Pending</span>");
    }

    /** Regression: an employee whose task is still genuinely IN PROGRESS (never completed) must
     * keep seeing the real, live, current status - this fix only changes what a COMPLETED task
     * displays, never an active one. */
    @Test
    void activeShootTaskStillDisplaysItsRealLiveStatus() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String camEmail = emailFor("cam", unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");

        String title = "CWVR Active Idea " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();
        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(10).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/cwvr-active-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(camId));
        reviewParams.put("publisherUserIds", List.of(pubId));
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams).statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();

        // Freshly assigned, never started - genuinely still at "Assigned", not approved.
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        HttpResponse<String> response = cam.get("/app/deliverables/" + plan.getId());

        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Shoot Task &mdash; " + plan.getContentId());
        assertThat(response.body()).contains("<span class=\"status-pill status-assigned\">Assigned</span>");
        assertThat(response.body()).contains("Start Shoot"); // still a real, live, actionable task
    }

    /** A Model granted SHOOT_EXECUTION and actually assigned as the Cameraperson (permission +
     * real assignment, never Business Role) gets exactly the same completed-task routing any
     * other qualifying employee does. */
    @Test
    void modelWithShootExecutionAndRealAssignmentCompletedShootOpensShootTaskDetailScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String modelId = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        grantPermission(ceo, modelId, "PERM_18_SHOOT_EXECUTION");
        String modelEmail = emailFor("model", unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");

        String title = "CWVR Model Idea " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();
        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(10).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/cwvr-model-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(modelId));
        reviewParams.put("publisherUserIds", List.of(pubId));
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams).statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient model = new TestApiClient(port);
        model.login(modelEmail, "Passw0rd!");
        assertThat(model.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(model.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);

        String edId = createUser(ceo, "modeled", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + modelId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        HttpResponse<String> response = model.get("/app/deliverables/" + planId + "?tab=shoot");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(response.body()).contains("Shoot Task &mdash; " + plan.getContentId());
    }

    /** Each employee's own completed-task link opens THEIR stage, never a co-worker's, even
     * though every "View Details" link on this shared Content ID resolves to the same base URL
     * pattern (only the ?tab= and the viewer's own session differ). */
    @Test
    void multipleEmployeesOnSameContentEachOnlyReachTheirOwnCompletedTaskScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String camEmail = emailFor("cam", unique);
        String edEmail = emailFor("ed", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, camEmail,
                edId, edEmail, pubId, emailFor("pub", unique));

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        // cam trying the Editor's own tab must NOT get the Edit Task screen - cam was never the
        // EditingAssignment holder on this plan.
        HttpResponse<String> camOnEditTab = cam.get("/app/deliverables/" + plan.getId() + "?tab=edit");
        assertThat(camOnEditTab.statusCode()).isEqualTo(200);
        assertThat(camOnEditTab.body()).doesNotContain("Edit Task &mdash; " + plan.getContentId());

        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        // ed trying the Cameraperson's own tab must NOT get the Shoot Task screen.
        HttpResponse<String> edOnShootTab = ed.get("/app/deliverables/" + plan.getId() + "?tab=shoot");
        assertThat(edOnShootTab.statusCode()).isEqualTo(200);
        assertThat(edOnShootTab.body()).doesNotContain("Shoot Task &mdash; " + plan.getContentId());

        // Each of them still reaches their OWN screen correctly.
        assertThat(cam.get("/app/deliverables/" + plan.getId() + "?tab=shoot").body())
                .contains("Shoot Task &mdash; " + plan.getContentId());
        assertThat(ed.get("/app/deliverables/" + plan.getId() + "?tab=edit").body())
                .contains("Edit Task &mdash; " + plan.getContentId());
    }

    /** Security: an employee with no permission and no assignment on this plan at all must never
     * reach any task-specific screen via a direct/typed URL, regardless of ?tab=. */
    @Test
    void unauthorizedEmployeeCannotOpenAnotherEmployeesCompletedTaskViaDirectUrl() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, emailFor("cam", unique),
                edId, emailFor("ed", unique), pubId, emailFor("pub", unique));

        String outsiderId = createUser(ceo, "outsider", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, outsiderId, "PERM_18_SHOOT_EXECUTION"); // holds the permission, but no assignment here
        TestApiClient outsider = new TestApiClient(port);
        outsider.login(emailFor("outsider", unique), "Passw0rd!");

        HttpResponse<String> shootAttempt = outsider.get("/app/deliverables/" + plan.getId() + "?tab=shoot");
        assertThat(shootAttempt.statusCode()).isEqualTo(200);
        assertThat(shootAttempt.body()).doesNotContain("Shoot Task &mdash; " + plan.getContentId());

        HttpResponse<String> editAttempt = outsider.get("/app/deliverables/" + plan.getId() + "?tab=edit");
        assertThat(editAttempt.statusCode()).isEqualTo(200);
        assertThat(editAttempt.body()).doesNotContain("Edit Task &mdash; " + plan.getContentId());
    }

    /**
     * Reproduces the exact reported bug: the SAME employee completes Shoot, Edit, AND Publishing
     * on the SAME Content ID (a Model granted all three execution permissions, exactly the
     * screenshot's own scenario). Once Publishing itself is done, the plan's overall status rests
     * at PP ("Performance Pending") - a status {@code publishRelevantStatus} deliberately still
     * treats as the Publisher's own active/resting state (see that branch's comment). Before this
     * fix, that branch matched unconditionally for ANY {@code ?tab=} value (it never checked
     * {@code tab} at all) and ran BEFORE the tab-aware completed-task branches, so every "View
     * Details" click - Shoot's, Edit's, and Publishing's alike - all resolved to the SAME
     * publish-task-detail screen. Each stage's own tab must now open its own screen.
     */
    @Test
    void sameEmployeeCompletedAllThreeStagesOnOneContentIdEachTabOpensItsOwnScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String userId = createUser(ceo, "trio", MODEL_ROLE_ID, unique);
        grantPermission(ceo, userId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, userId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, userId, "PERM_08_PUBLISHING_EXECUTION");
        String email = emailFor("trio", unique);

        String title = "CWVR Trio Idea " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();
        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(20).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/cwvr-trio-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(userId));
        reviewParams.put("publisherUserIds", List.of(userId));
        reviewParams.put("outputsJson", List.of(
                "[{\"outputType\":\"POST\",\"reelTypes\":[],\"outputTitleDescription\":null,"
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}]"));
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams).statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient trio = new TestApiClient(port);
        trio.login(email, "Passw0rd!");

        // Shoot: same user is the Cameraperson AND becomes the Editor in the same approval.
        assertThat(trio.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(trio.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + userId + "\"],"
                        + "\"editorUserIds\":[\"" + userId + "\"],\"leadEditorUserId\":\"" + userId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        // Edit: same user completes it and becomes the Publisher too.
        assertThat(trio.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(trio.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + userId + "\"],"
                        + "\"publisherUserIds\":[\"" + userId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        // Publishing: same user completes it - plan now rests at PP.
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        assertThat(trio.post("/api/v1/content-plans/" + planId + "/publishing/start", "").statusCode()).isEqualTo(200);
        String pastTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString();
        assertThat(trio.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/cwvr-trio-" + unique + "\"}").statusCode()).isEqualTo(200);

        ContentPlan completedPlan = contentPlanRepository.findById(plan.getId()).orElseThrow();
        String contentId = completedPlan.getContentId();

        // Each tab must open its OWN screen - none of the three may collapse onto another,
        // regardless of which status the plan is now resting at overall.
        String shootBody = trio.get("/app/deliverables/" + planId + "?tab=shoot").body();
        assertThat(shootBody).contains("Shoot Task &mdash; " + contentId);
        assertThat(shootBody).doesNotContain("Edit Task &mdash; " + contentId)
                .doesNotContain("Publishing Task &mdash; " + contentId);

        String editBody = trio.get("/app/deliverables/" + planId + "?tab=edit").body();
        assertThat(editBody).contains("Edit Task &mdash; " + contentId);
        assertThat(editBody).doesNotContain("Shoot Task &mdash; " + contentId)
                .doesNotContain("Publishing Task &mdash; " + contentId);

        String publishBody = trio.get("/app/deliverables/" + planId + "?tab=publishing").body();
        assertThat(publishBody).contains("Publishing Task &mdash; " + contentId);
        assertThat(publishBody).doesNotContain("Shoot Task &mdash; " + contentId)
                .doesNotContain("Edit Task &mdash; " + contentId);
    }
}
