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
 * ENG-066: the Video Editor "My Review Feedback" section on My Work - exact mirror of the Camera
 * Person one (ENG-062/063) - must be scoped strictly to EDIT_REVIEW decisions for plans this
 * Editor actually participated in (never IDEA_REVIEW/PLANNING_REVIEW/SHOOT_REVIEW/Publishing
 * data), grouped one card per Content ID with every historical rework decision still visible,
 * living inside the Active Work tab panel only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EditFeedbackTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";

    @Test
    void reworkThenApproveKeepsFullHistoryVisibleUnderTheCorrectContentIdAndLinksToTheDeliverable() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-edit-feedback-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Edit Feedback Test Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();
        String editorEmail = "e2e-edit-feedback-editor-" + unique + "@kcpcbandhani.local";
        JsonNode editorUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Edit Feedback Test Editor\",\"email\":\"" + editorEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + VIDEO_EDITOR_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String editorId = editorUser.get("userId").asText();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Edit Feedback Test " + unique + "\"}");
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
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/edit-feedback-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");

        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + editorId + "\"}");

        TestApiClient editor = new TestApiClient(port);
        editor.login(editorEmail, "Passw0rd!");
        editor.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        String reworkReason = "Colour grading thoda aur improve karo. " + unique;
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":false,\"reason\":\"" + reworkReason + "\"}");

        // Active Work: a task currently in rework shows the friendly "Rework Required" status and
        // a "View Feedback" action.
        String myWorkDuringRework = editor.get("/app/my-work").body();
        assertThat(myWorkDuringRework).contains("Rework Required");
        assertThat(myWorkDuringRework).contains("View Feedback");

        // My Review Feedback: correct Content ID, friendly REWORK REQUIRED badge, real reason text
        // (never invented), and a Content ID link into the actual deliverable.
        assertThat(myWorkDuringRework).contains(plan.getContentId());
        assertThat(myWorkDuringRework).contains(reworkReason);
        assertThat(myWorkDuringRework).contains("REWORK REQUIRED");
        assertThat(myWorkDuringRework).contains("href=\"/app/deliverables/" + planId + "\"");
        // Scoped to Edit Review only - never another gate's data on this Video Editor screen.
        assertThat(myWorkDuringRework).doesNotContain("IDEA_REVIEW").doesNotContain("PLANNING_REVIEW")
                .doesNotContain("SHOOT_REVIEW");

        // "My Review Feedback" lives INSIDE the Active Work tab panel only, same structural
        // guarantee as the Camera Person version (ENG-063).
        int activePanelIndex = myWorkDuringRework.indexOf("data-tab-panel=\"active\"");
        int feedbackIndex = myWorkDuringRework.indexOf("My Review Feedback");
        int historyPanelIndex = myWorkDuringRework.indexOf("data-tab-panel=\"history\"");
        assertThat(activePanelIndex).isPositive();
        assertThat(feedbackIndex).isGreaterThan(activePanelIndex);
        assertThat(historyPanelIndex).isGreaterThan(feedbackIndex);

        // Resubmit and approve - both decisions remain visible: latest (Approved) prominent, the
        // earlier Rework preserved under "View Feedback History".
        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");

        String myWorkAfterApproval = editor.get("/app/my-work").body();
        assertThat(myWorkAfterApproval).contains(plan.getContentId());
        assertThat(myWorkAfterApproval).contains("APPROVED");
        assertThat(myWorkAfterApproval).contains("View Feedback History");
        assertThat(myWorkAfterApproval).contains(reworkReason);
        assertThat(myWorkAfterApproval).contains("REWORK REQUIRED");
    }
}
