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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assignee-selection pickers (Shoot/Edit/Publisher/Model) must show each candidate's current
 * active-task count alongside their name, using the exact same "active" definition Team Workload's
 * Assignee Load panel already uses (see {@code AssigneeActiveWindows}/
 * {@code AssigneeWorkloadCountService}) - never a hardcoded/derived-elsewhere number. This test
 * drives the real HTTP surface (no mocking) to prove: a candidate with zero active tasks shows
 * "0 Active Tasks"; a candidate with several active Shoot assignments shows the real count; a
 * Content Plan that has moved past the Shoot stage no longer contributes to that count (completed/
 * moved-on work is excluded); the Shoot Assignee picker actually renders these counts in its
 * checklist markup; and the count changes immediately after a Reassign, without needing any cache
 * invalidation (it is computed fresh from real assignment rows on every page render).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssigneeWorkloadCountDisplayTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void shootAssigneePickerShowsRealActiveTaskCountsAndUpdatesAfterReassignment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camAEmail = "e2e-workload-cam-a-" + unique + "@kcpcbandhani.local";
        String camBEmail = "e2e-workload-cam-b-" + unique + "@kcpcbandhani.local";
        String camAId = createUser(ceo, "Workload Cam A " + unique, camAEmail);
        String camBId = createUser(ceo, "Workload Cam B " + unique, camBEmail);
        grantShootExecution(ceo, camAId);
        grantShootExecution(ceo, camBId);

        TestApiClient camA = new TestApiClient(port);
        camA.login(camAEmail, "Passw0rd!");

        // Cam A: two Content Plans driven all the way to Shoot Assigned (SA) - both inside the
        // Shoot active window, so Cam A must show exactly 2 Active Tasks.
        String plan1Id = createPlanAssignedToShoot(ceo, camAId, unique, "P1");
        String plan2Id = createPlanAssignedToShoot(ceo, camAId, unique, "P2");

        // A third Cam A plan is driven PAST the Shoot stage (Shoot Approved) - it must NOT be
        // counted, proving completed/moved-on work is excluded, not just "is there a row at all".
        String plan3Id = createPlanAssignedToShoot(ceo, camAId, unique, "P3");
        camA.post("/api/v1/content-plans/" + plan3Id + "/shooting/start", "");
        camA.post("/api/v1/content-plans/" + plan3Id + "/shooting/review/submit", "");
        // Workflow redesign: Shoot Review Approve now requires an Editor team assignment in the
        // same call (ShootingService#decideShootReview) - a throwaway Editor here, unrelated to
        // this test's own subject (Shoot workload counts).
        String plan3EditorId = createEditorUser(ceo, "Workload Editor " + unique);
        JsonNode plan3Approved = ceo.postJson("/api/v1/content-plans/" + plan3Id + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camAId + "\"],"
                        + "\"editorUserIds\":[\"" + plan3EditorId + "\"],\"leadEditorUserId\":\"" + plan3EditorId + "\"}");
        assertThat(plan3Approved.get("status").asText()).isEqualTo("EA");

        // Cam B has never been assigned anywhere yet - must show 0 Active Tasks, not "-"/blank/omitted.
        // A brand-new, still-unassigned Planning-stage plan is where both candidates actually appear
        // in the "who can I assign" checklist (an already-assigned candidate is filtered out of it).
        String observerPlanId = createFreshPlanningPlan(ceo, unique, "OBS");

        HttpResponse<String> beforeReassign = ceo.get("/app/deliverables/" + observerPlanId);
        assertThat(beforeReassign.statusCode()).isEqualTo(200);
        String bodyBefore = beforeReassign.body();
        assertThat(bodyBefore).contains("Workload Cam A " + unique).contains("Workload Cam B " + unique);
        assertThat(extractCandidateCountLabel(bodyBefore, "Workload Cam A " + unique)).isEqualTo("2 Active Tasks");
        assertThat(extractCandidateCountLabel(bodyBefore, "Workload Cam B " + unique)).isEqualTo("0 Active Tasks");

        // Reassign plan2's Shoot task from Cam A to Cam B - Cam A drops to 1 (only plan1 remains
        // active; plan3 stays excluded), Cam B rises to 1. No manual refresh/cache step needed: the
        // count is recomputed straight from the assignment rows on the very next render.
        HttpResponse<String> reassignResponse = ceo.post("/api/v1/content-plans/" + plan2Id + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + camBId + "\"],\"reason\":\"workload test reassignment\"}");
        assertThat(reassignResponse.statusCode()).isEqualTo(200);

        HttpResponse<String> afterReassign = ceo.get("/app/deliverables/" + observerPlanId);
        assertThat(afterReassign.statusCode()).isEqualTo(200);
        String bodyAfter = afterReassign.body();
        assertThat(extractCandidateCountLabel(bodyAfter, "Workload Cam A " + unique)).isEqualTo("1 Active Task");
        assertThat(extractCandidateCountLabel(bodyAfter, "Workload Cam B " + unique)).isEqualTo("1 Active Task");
    }

    /** Pulls the "(N Active Task(s))" label that immediately follows a given candidate's name in
     * the rendered Shoot Assignee checklist markup. */
    private static String extractCandidateCountLabel(String html, String candidateName) {
        int nameIndex = html.indexOf(candidateName);
        assertThat(nameIndex).as("candidate '%s' present in rendered page", candidateName).isGreaterThanOrEqualTo(0);
        int openParen = html.indexOf('(', nameIndex);
        int closeParen = html.indexOf(')', openParen);
        assertThat(openParen).as("'(' after candidate name").isGreaterThanOrEqualTo(0);
        assertThat(closeParen).as("')' after '('").isGreaterThan(openParen);
        return html.substring(openParen + 1, closeParen);
    }

    private String createUser(TestApiClient ceo, String fullName, String email) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"workload count test fixture\"}");
        return response.get("userId").asText();
    }

    private void grantShootExecution(TestApiClient ceo, String userId) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"workload count test grant\"}");
    }

    private String createEditorUser(TestApiClient ceo, String fullName) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + fullName.toLowerCase().replace(" ", "-")
                        + "@kcpcbandhani.local\",\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + VIDEO_EDITOR_ROLE_ID
                        + "\",\"creationReason\":\"workload count test fixture\"}");
        String editorId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editorId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"workload count test grant\"}");
        return editorId;
    }

    private String createPublisherUser(TestApiClient ceo, String fullName) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + fullName.toLowerCase().replace(" ", "-")
                        + "@kcpcbandhani.local\",\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + PUBLISHER_ROLE_ID
                        + "\",\"creationReason\":\"workload count test fixture\"}");
        String publisherId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisherId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"workload count test grant\"}");
        return publisherId;
    }

    /** Workflow redesign: Idea Review approval now carries every former Planning field (including
     * the initial Shoot Team and initial Output/Publication Scope) in one call and transitions
     * straight to Shoot Assigned (SA), never PL/PLRV/PLAP - parameterized by which cameraperson
     * gets assigned. */
    private String createPlanAssignedToShoot(TestApiClient ceo, String cameramanUserId, long unique, String label)
            throws Exception {
        String liveDate = LocalDate.now().plusDays(10).toString();
        String publisherId = createPublisherUser(ceo, "Workload Pub " + label + " " + unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Workload Count " + label + " " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/workload-" + label + "-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cameramanUserId + "\"],"
                        + "\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        return findContentPlanId(ideaId);
    }

    /** A Content Plan at Shoot Assigned (SA) whose own Shoot Team is a throwaway cameraperson
     * (neither Cam A nor Cam B) - the state the Shoot Assignee "who can I assign" checklist is
     * rendered from (it lists every eligible candidate NOT YET assigned to THIS plan), so both a
     * fully-loaded and a zero-task candidate can be observed side by side without either of them
     * being filtered out as already-assigned here. Workflow redesign: approval always requires at
     * least one Cameraperson, so a truly unassigned plan is no longer reachable via the API. */
    private String createFreshPlanningPlan(TestApiClient ceo, long unique, String label) throws Exception {
        String observerCamId = createUser(ceo, "Workload Observer Cam " + unique, "e2e-workload-observer-" + unique + "@kcpcbandhani.local");
        grantShootExecution(ceo, observerCamId);
        String observerPublisherId = createPublisherUser(ceo, "Workload Observer Pub " + unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Workload Count " + label + " " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/workload-observer-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + observerCamId + "\"],"
                        + "\"publisherUserIds\":[\"" + observerPublisherId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        return findContentPlanId(ideaId);
    }

    private String findContentPlanId(String ideaIdText) {
        UUID ideaId = UUID.fromString(ideaIdText);
        Idea idea = ideaRepository.findById(ideaId).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        return plan.getId().toString();
    }

    private String findPlannedOutputId(String contentPlanIdText) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanIdText)).orElseThrow();
        return plannedOutputRepository.findByContentPlan(plan).stream()
                .findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
    }
}
