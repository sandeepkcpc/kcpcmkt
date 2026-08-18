package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task visibility on the Employee's "My Work" screen comes from an active assignment, never
 * Designation/Business Role alone, and (for Shoot specifically, since a Cameraperson can be
 * assigned during Planning itself, before Planning Review even starts) not until Planning has
 * actually been Approved. Explicit user request: a Cameraperson assigned mid-Planning must not
 * see the deliverable in My Work until the plan reaches Shoot Assigned (SA) or later.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyWorkVisibilityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void camerapersonSeesTaskOnlyAfterPlanningIsApprovedNotAtAssignmentTime() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-mywork-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MyWork Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork Visibility " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        // Assigned while the plan is still at PL (Shoot Assignment happens during Planning itself,
        // before Planning Review even starts) - matches how the real UI's Shoot Assignment picker works.
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        String beforeApproval = cam.get("/app/my-work").body();
        assertThat(beforeApproval).doesNotContain(plan.getContentId());

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/mywork-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");

        String afterApproval = cam.get("/app/my-work").body();
        assertThat(afterApproval).contains(plan.getContentId());
    }

    /**
     * Explicit user request: once a stage's own review has decided and the plan moved on, that
     * assignment must drop out of Active Assignments and appear only in the read-only "My
     * Completed Work / History" section (own stage summary + Approved result, nothing about the
     * next stage's operational detail).
     */
    @Test
    void camerapersonTaskMovesFromActiveToCompletedHistoryOnceShootIsApproved() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-mywork-history-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MyWork History Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MyWork History " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/mywork-history-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        // Still shooting (SA) - active, not yet in history.
        String[] halvesDuringShoot = splitOnHistoryHeader(cam.get("/app/my-work").body());
        assertThat(halvesDuringShoot[0]).contains(plan.getContentId());
        assertThat(halvesDuringShoot[1]).doesNotContain(plan.getContentId());

        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("SAP");

        // Shoot Approved - moved out of Active Assignments, into Completed history with the
        // Approve outcome, and never appears in both places at once.
        String[] halvesAfterApproval = splitOnHistoryHeader(cam.get("/app/my-work").body());
        assertThat(halvesAfterApproval[0]).doesNotContain(plan.getContentId());
        assertThat(halvesAfterApproval[1]).contains(plan.getContentId()).contains("SHOOT").contains("Approved");
    }

    private String[] splitOnHistoryHeader(String body) {
        int splitIndex = body.indexOf("My Completed Work / History");
        assertThat(splitIndex).isPositive();
        return new String[] {body.substring(0, splitIndex), body.substring(splitIndex)};
    }
}
