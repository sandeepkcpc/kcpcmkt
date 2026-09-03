package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Skip Stage (ENG-090): ShootingService#skipShootStage / EditingService#skipEditStage, driven via
 * DeliverableMvcController's real HTTP surface (POST /app/deliverables/{id}/shooting/skip and
 * /editing/skip), same real-Postgres/no-mocking convention as EditUserFlowTest. Covers: skip from
 * every eligible status per stage, required-field validation, invalid-status rejection,
 * PERM_20_SKIP_STAGE gating distinct from PERM_05/PERM_07 review authority, and open-hold blocking.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SkipStageFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    WorkflowTransitionHistoryRepository transitionHistoryRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "skip-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Skip " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"skip stage test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"skip stage test fixture grant\"}");
    }

    /** Idea approved -> Shoot Assigned (SA), shoot not yet started. */
    private String[] buildToSA(TestApiClient ceo, long unique) throws Exception {
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        // Publisher(s) is now mandatory at Planning approval for every stage combo (ENG-099) - this
        // Planning-time assignment is separate from whatever Publisher a later skip-edit call in
        // the same test picks (PublishingService#assignPublisher is idempotent per (plan, publisher)
        // pair, so a different skip-time Publisher just adds a second real assignment, never a
        // duplicate of this one).
        String[] planningPublisher = createUser(ceo, "planningpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, planningPublisher[0], "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Skip Stage " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/skip-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + planningPublisher[0] + "\"]}}");
        String planId = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();
        return new String[] {planId, cam[0], cam[1]};
    }

    /** SA -> Shoot In Progress (SIP). */
    private String[] buildToSIP(TestApiClient ceo, long unique) throws Exception {
        String[] fx = buildToSA(ceo, unique);
        TestApiClient cam = new TestApiClient(port);
        cam.login(fx[2], "Passw0rd!");
        cam.post("/api/v1/content-plans/" + fx[0] + "/shooting/start", "");
        return fx;
    }

    /** SIP -> Shoot Review (SRV). */
    private String[] buildToSRV(TestApiClient ceo, long unique) throws Exception {
        String[] fx = buildToSIP(ceo, unique);
        TestApiClient cam = new TestApiClient(port);
        cam.login(fx[2], "Passw0rd!");
        cam.post("/api/v1/content-plans/" + fx[0] + "/shooting/review/submit", "");
        return fx;
    }

    /** SRV -> Edit Assigned (EA) via a real Shoot Review approval (not skip). Returns planId/editorId/editorEmail. */
    private String[] buildToEA(TestApiClient ceo, long unique) throws Exception {
        String[] fx = buildToSRV(ceo, unique);
        String planId = fx[0];
        String camId = fx[1];
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}");
        return new String[] {planId, editor[0], editor[1]};
    }

    /** EA -> Editing (ED). */
    private String[] buildToED(TestApiClient ceo, long unique) throws Exception {
        String[] fx = buildToEA(ceo, unique);
        TestApiClient editor = new TestApiClient(port);
        editor.login(fx[2], "Passw0rd!");
        editor.post("/api/v1/content-plans/" + fx[0] + "/editing/start", "");
        return fx;
    }

    /** ED -> Edit Review (ERV). */
    private String[] buildToERV(TestApiClient ceo, long unique) throws Exception {
        String[] fx = buildToED(ceo, unique);
        TestApiClient editor = new TestApiClient(port);
        editor.login(fx[2], "Passw0rd!");
        editor.post("/api/v1/content-plans/" + fx[0] + "/editing/review/submit", "");
        return fx;
    }

    private WorkflowStatus statusOf(String planId) {
        return contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode();
    }

    private HttpResponse<String> skipShoot(TestApiClient actor, String planId, String reason, String editorId,
                                            String leadId) throws Exception {
        java.util.LinkedHashMap<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("reason", reason);
        if (editorId != null) {
            params.put("editorUserIds", editorId);
        }
        if (leadId != null) {
            params.put("leadEditorUserId", leadId);
        }
        return actor.postFormAjax("/app/deliverables/" + planId + "/shooting/skip", toStringMap(params));
    }

    private HttpResponse<String> skipEdit(TestApiClient actor, String planId, String reason, String publisherId) throws Exception {
        java.util.LinkedHashMap<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("reason", reason);
        if (publisherId != null) {
            params.put("publisherUserIds", publisherId);
        }
        return actor.postFormAjax("/app/deliverables/" + planId + "/editing/skip", toStringMap(params));
    }

    private Map<String, String> toStringMap(java.util.LinkedHashMap<String, Object> src) {
        java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
        src.forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    @Test
    void skipShootSucceedsFromShootAssigned() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSA(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String reason = "No cameraperson available " + unique;

        HttpResponse<String> response = skipShoot(ceo, fx[0], reason, editor[0], editor[0]);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(statusOf(fx[0])).isEqualTo(WorkflowStatus.EA);

        var history = transitionHistoryRepository
                .findByWorkflowInstanceOrderByTransitionTimestampAsc(
                        contentPlanRepository.findById(UUID.fromString(fx[0])).orElseThrow().getWorkflowInstance());
        assertThat(history).anyMatch(t -> "SKIP_SHOOT_STAGE".equals(t.getTriggerCommand())
                && t.getToStatusCode() == WorkflowStatus.EA && reason.equals(t.getTransitionReason()));

        JsonNode auditLogs = ceo.getJson("/api/v1/audit/logs?actionType=SHOOT_STAGE_SKIPPED");
        boolean found = false;
        for (JsonNode entry : auditLogs) {
            if (entry.get("targetEntityId").asText().equals(fx[0])) {
                found = true;
                assertThat(entry.get("actionReason").asText()).isEqualTo(reason);
            }
        }
        assertThat(found).as("SHOOT_STAGE_SKIPPED audit entry").isTrue();
    }

    @Test
    void skipShootSucceedsFromShootInProgress() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSIP(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");

        HttpResponse<String> response = skipShoot(ceo, fx[0], "Skipping mid-shoot " + unique, editor[0], editor[0]);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(statusOf(fx[0])).isEqualTo(WorkflowStatus.EA);
    }

    @Test
    void skipShootSucceedsFromShootReviewAndClosesOpenCycle() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSRV(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");

        HttpResponse<String> response = skipShoot(ceo, fx[0], "Skipping at review " + unique, editor[0], editor[0]);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(statusOf(fx[0])).isEqualTo(WorkflowStatus.EA);

        // Content Detail's Review Feedback History shows "Stage Skipped", not "Approved"/"Rework".
        String detail = ceo.get("/app/deliverables/" + fx[0]).body();
        assertThat(detail).contains("Stage Skipped");
    }

    @Test
    void skipEditSucceedsFromEachEligibleStatus() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        String[] eaFx = buildToEA(ceo, unique + 1);
        assertThat(skipEdit(ceo, eaFx[0], "No editor available " + unique, publisher[0]).statusCode()).isEqualTo(200);
        assertThat(statusOf(eaFx[0])).isEqualTo(WorkflowStatus.RFP);

        String[] edFx = buildToED(ceo, unique + 2);
        assertThat(skipEdit(ceo, edFx[0], "Skipping mid-edit " + unique, publisher[0]).statusCode()).isEqualTo(200);
        assertThat(statusOf(edFx[0])).isEqualTo(WorkflowStatus.RFP);

        String[] ervFx = buildToERV(ceo, unique + 3);
        HttpResponse<String> response = skipEdit(ceo, ervFx[0], "Skipping at review " + unique, publisher[0]);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(statusOf(ervFx[0])).isEqualTo(WorkflowStatus.RFP);
    }

    @Test
    void skipShootRejectsBlankReason() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSA(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);

        HttpResponse<String> response = skipShoot(ceo, fx[0], "", editor[0], editor[0]);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("reason is mandatory");
    }

    @Test
    void skipShootRejectsMissingEditors() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSA(ceo, unique);

        HttpResponse<String> response = skipShoot(ceo, fx[0], "reason " + unique, null, null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Editor");
    }

    @Test
    void skipShootRejectsMissingLead() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSA(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);

        HttpResponse<String> response = skipShoot(ceo, fx[0], "reason " + unique, editor[0], null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Editor Lead is mandatory");
    }

    @Test
    void skipEditRejectsMissingPublishers() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToEA(ceo, unique);

        HttpResponse<String> response = skipEdit(ceo, fx[0], "reason " + unique, null);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Publisher");
    }

    @Test
    void skipShootRejectsInvalidStatus() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        // Already past the Shoot phase (RFP) - skip-shoot must be rejected, not silently no-op.
        String[] fx = buildToEA(ceo, unique);
        String[] editor = createUser(ceo, "editor2", VIDEO_EDITOR_ROLE_ID, unique);

        HttpResponse<String> response = skipShoot(ceo, fx[0], "reason " + unique, editor[0], editor[0]);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("WORKFLOW_INVALID_TRANSITION");
    }

    @Test
    void skipEditRejectsInvalidStatus() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        // Still in the Shoot phase (SA) - skip-edit must be rejected.
        String[] fx = buildToSA(ceo, unique);
        String[] publisher = createUser(ceo, "publisher2", PUBLISHER_ROLE_ID, unique);

        HttpResponse<String> response = skipEdit(ceo, fx[0], "reason " + unique, publisher[0]);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("WORKFLOW_INVALID_TRANSITION");
    }

    @Test
    void skipShootRequiresPermOperationalNotJustReviewAuthority() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSA(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");

        // Employee-tier actor with NO grants at all - 403, not native authority.
        String[] noGrant = createUser(ceo, "no-grant", CAMERA_PERSON_ROLE_ID, unique);
        TestApiClient noGrantClient = new TestApiClient(port);
        noGrantClient.login(noGrant[1], "Passw0rd!");
        HttpResponse<String> denied = skipShoot(noGrantClient, fx[0], "reason " + unique, editor[0], editor[0]);
        assertThat(denied.statusCode()).isEqualTo(403);

        // Employee-tier actor holding ONLY PERM_05_SHOOT_REVIEW (review authority), not
        // PERM_20_SKIP_STAGE - still 403, proving Skip is a distinct, separately-granted permission.
        String[] reviewOnly = createUser(ceo, "review-only", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, reviewOnly[0], "PERM_05_SHOOT_REVIEW");
        TestApiClient reviewOnlyClient = new TestApiClient(port);
        reviewOnlyClient.login(reviewOnly[1], "Passw0rd!");
        HttpResponse<String> stillDenied = skipShoot(reviewOnlyClient, fx[0], "reason " + unique, editor[0], editor[0]);
        assertThat(stillDenied.statusCode()).isEqualTo(403);

        // Same employee, now granted PERM_20_SKIP_STAGE (GLOBAL scope) - succeeds. Also needs
        // PERM_06_EDIT_ASSIGNMENT: skipShootStage reuses EditingService#assignEditTeam unchanged
        // (same as decideShootReview's own Approve path), which re-authorizes the SAME actor as
        // the Editor-assigner - for a non-native employee that's a real second permission
        // requirement, just as it already is for a non-native Shoot Review Approve today (masked
        // for CEO/MM in the other tests only because native authority bypasses both checks).
        grantPermission(ceo, reviewOnly[0], "PERM_20_SKIP_STAGE");
        grantPermission(ceo, reviewOnly[0], "PERM_06_EDIT_ASSIGNMENT");
        HttpResponse<String> allowed = skipShoot(reviewOnlyClient, fx[0], "reason " + unique, editor[0], editor[0]);
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(statusOf(fx[0])).isEqualTo(WorkflowStatus.EA);
    }

    @Test
    void skipBlockedByOpenHold() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToSIP(ceo, unique);
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);

        assertThat(ceo.postForm("/app/deliverables/" + fx[0] + "/hold",
                Map.of("reason", "investigating " + unique)).statusCode()).isEqualTo(302);

        HttpResponse<String> response = skipShoot(ceo, fx[0], "reason " + unique, editor[0], editor[0]);
        assertThat(response.statusCode()).isEqualTo(409);
    }
}
