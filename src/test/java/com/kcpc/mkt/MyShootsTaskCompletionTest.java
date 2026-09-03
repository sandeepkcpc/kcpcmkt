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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "My Shoots" Upcoming vs Past for a Model/Talent is decided purely by whether their own personal
 * shoot task is still outstanding or already complete
 * (LandingMvcController#isModelShootTaskCompleted: has the Shoot phase reached its own terminal
 * event yet - Shoot Review approval, {@code APPROVE_SHOOT}, or an admin Skip Stage,
 * {@code SKIP_SHOOT_STAGE}?) - never the Content Plan's current overall WorkflowStatus, and never
 * the planned shoot date. Once that event has fired it is permanent history: no later downstream
 * stage (Edit Assigned, Edit Review, Ready for Publishing, ...) can ever move the row back to
 * Upcoming.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyShootsTaskCompletionTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "mstc-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MSTC " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"my shoots task completion test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my shoots task completion test fixture grant\"}");
    }

    /** Idea approved -> Shoot Assigned (SA), a Model linked via ContentPlanTalentEntry, shoot not
     * yet started - the Model's own task is still outstanding at this point. Returns the plan plus
     * the assigned Cameraperson's own id/login (needed for Start/Submit below - those are
     * hands-on execution endpoints, restricted to the actively assigned Cameraperson even for
     * CEO/MM - and for qualifyingRecipientUserIds on the eventual Approve call). */
    private Object[] buildToSAWithModel(TestApiClient ceo, String modelUserId, long unique) throws Exception {
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] planningPub = createUser(ceo, "planning-pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, planningPub[0], "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MSTC Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mstc-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"talentUserIds\":[\"" + modelUserId + "\"],"
                        + "\"publisherUserIds\":[\"" + planningPub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow()).orElseThrow();
        return new Object[] {plan, cam[0], cam[1]};
    }

    private String myShootsBody(String modelEmail) throws Exception {
        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(modelEmail, "Passw0rd!");
        return modelClient.get("/app/my-shoots").body();
    }

    private void assertInUpcomingOnly(String modelEmail, ContentPlan plan) throws Exception {
        String body = myShootsBody(modelEmail);
        int upcomingStart = body.indexOf("Upcoming Shoots</h2>");
        int pastStart = body.indexOf("Past Shoots</h2>");
        assertThat(upcomingStart).isGreaterThan(-1);
        assertThat(pastStart).isGreaterThan(upcomingStart);
        String upcomingSection = body.substring(upcomingStart, pastStart);
        String pastSection = body.substring(pastStart);
        assertThat(upcomingSection).contains(plan.getContentId());
        assertThat(pastSection).doesNotContain(plan.getContentId());
    }

    private void assertInPastOnly(String modelEmail, ContentPlan plan) throws Exception {
        String body = myShootsBody(modelEmail);
        int upcomingStart = body.indexOf("Upcoming Shoots</h2>");
        int pastStart = body.indexOf("Past Shoots</h2>");
        assertThat(upcomingStart).isGreaterThan(-1);
        assertThat(pastStart).isGreaterThan(upcomingStart);
        String upcomingSection = body.substring(upcomingStart, pastStart);
        String pastSection = body.substring(pastStart);
        assertThat(pastSection).contains(plan.getContentId());
        assertThat(upcomingSection).doesNotContain(plan.getContentId());
        // The Past row is historical-record-only - no Content ID link, no View/Action link, just
        // the plain id text and a "Completed" indicator (fragments/my-shoots.jsp).
        assertThat(pastSection).doesNotContain("href=\"" + "/app/deliverables/" + plan.getId());
        assertThat(pastSection).contains("Completed");
    }

    /** Once the Model's personal task is complete, a direct/typed URL to this same Content must be
     * rejected server-side too (DeliverableMvcController#view's early Model gate) - not just
     * absent from My Shoots' own View button. Same redirect-to-My-Shoots pattern the rest of this
     * app already uses for "you cannot reach this" (WorkflowParticipationInterceptor). */
    private void assertModelDeniedDirectAccess(String modelEmail, ContentPlan plan) throws Exception {
        TestApiClient modelClient = new TestApiClient(port);
        modelClient.login(modelEmail, "Passw0rd!");
        var response = modelClient.get("/app/deliverables/" + plan.getId());
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/app/my-shoots");
    }

    /** Test Case 1. */
    @Test
    void modelTaskStillOutstandingAtShootAssignedShowsInUpcomingOnly() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] model = createUser(ceo, "outstanding", MODEL_ROLE_ID, unique);
        Object[] fx = buildToSAWithModel(ceo, model[0], unique);
        ContentPlan plan = (ContentPlan) fx[0];

        assertInUpcomingOnly(model[1], plan);
    }

    /** Test Cases 2, 3, 5: completed at Shoot Review approval, stays Past through Edit Assigned and
     * all the way to Ready for Publishing - the Content's own further progress never moves it back. */
    @Test
    void modelTaskCompletedByShootApprovalMovesToPastAndStaysThroughDownstreamStages() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] model = createUser(ceo, "approved", MODEL_ROLE_ID, unique);
        Object[] fx = buildToSAWithModel(ceo, model[0], unique);
        ContentPlan plan = (ContentPlan) fx[0];
        String camId = (String) fx[1];
        String camEmail = (String) fx[2];
        String planId = plan.getId().toString();

        // Still outstanding before the shoot is even started.
        assertInUpcomingOnly(model[1], plan);

        // Start/Submit are hands-on execution, restricted to the actively assigned Cameraperson
        // (not even CEO's native authority bypasses that gate) - same as ShootTaskDetailTest.
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        assertInUpcomingOnly(model[1], plan); // still mid-shoot, still outstanding

        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        assertInUpcomingOnly(model[1], plan); // submitted for review, but not yet approved - still outstanding

        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");

        // Test Case 2: APPROVE_SHOOT just fired - the Model's task is complete now, and direct
        // access to this Content is denied from this point on regardless of anything downstream.
        assertInPastOnly(model[1], plan);
        assertModelDeniedDirectAccess(model[1], plan);

        // Test Case 3/4: further downstream (Edit started/submitted) - still Past, still denied,
        // never reverts.
        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        assertThat(editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertInPastOnly(model[1], plan);
        assertModelDeniedDirectAccess(model[1], plan);
        assertThat(editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        assertInPastOnly(model[1], plan);
        assertModelDeniedDirectAccess(model[1], plan);

        // Test Case 5: Content reaches Ready for Publishing - still Past and still denied for the Model.
        String[] publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        var editDecisionResponse = ceo.post("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}");
        assertThat(editDecisionResponse.statusCode()).as(editDecisionResponse.body()).isEqualTo(200);
        assertInPastOnly(model[1], plan);
        assertModelDeniedDirectAccess(model[1], plan);
    }

    /** Test Case 2 (alternate completing event): an admin explicitly Skip-Stages the whole Shoot
     * phase - this ends the Model's task exactly as definitively as a normal approval does. */
    @Test
    void modelTaskCompletedByShootStageSkipMovesToPastImmediately() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] model = createUser(ceo, "skipped", MODEL_ROLE_ID, unique);
        Object[] fx = buildToSAWithModel(ceo, model[0], unique);
        ContentPlan plan = (ContentPlan) fx[0];
        assertInUpcomingOnly(model[1], plan);

        String[] editor = createUser(ceo, "skipeditor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        Map<String, String> params = new HashMap<>();
        params.put("reason", "content already has stock footage, no shoot needed " + unique);
        params.put("editorUserIds", editor[0]);
        params.put("leadEditorUserId", editor[0]);
        var skipResponse = ceo.postFormAjax("/app/deliverables/" + plan.getId() + "/shooting/skip", params);
        assertThat(skipResponse.statusCode()).isEqualTo(200);

        assertInPastOnly(model[1], plan);
        assertModelDeniedDirectAccess(model[1], plan);
    }
}
