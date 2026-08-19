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
 * ENG-062: the redesigned Camera Person "My Review Feedback" section on My Work must be scoped
 * strictly to SHOOT_REVIEW decisions for plans this Cameraperson actually participated in (never
 * IDEA_REVIEW/PLANNING_REVIEW/EDIT_REVIEW/Publishing data), grouped one card per Content ID with
 * every historical rework decision still visible (never overwritten/collapsed away), and a
 * currently-in-rework Active Work row must keep pointing the employee at that feedback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShootFeedbackTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void reworkThenApproveKeepsFullHistoryVisibleUnderTheCorrectContentIdAndLinksToTheDeliverable() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-feedback-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Feedback Test Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Shoot Feedback Test " + unique + "\"}");
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
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/feedback-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        String reworkReason = "Background change karo. Product ko thoda aur highlight karo. " + unique;
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":false,\"reason\":\"" + reworkReason + "\"}");

        // Active Work: a task currently in rework shows the friendly "Rework Required" status and
        // a "View Feedback" action (not a raw backend status/enum, not an inline execute action).
        String myWorkDuringRework = cam.get("/app/my-work").body();
        assertThat(myWorkDuringRework).contains("Rework Required");
        assertThat(myWorkDuringRework).contains("View Feedback");

        // My Review Feedback: shows the correct Content ID, the friendly REWORK REQUIRED badge, the
        // real reason text (never invented), and a Content ID link into the actual deliverable.
        assertThat(myWorkDuringRework).contains(plan.getContentId());
        assertThat(myWorkDuringRework).contains(reworkReason);
        assertThat(myWorkDuringRework).contains("REWORK REQUIRED");
        assertThat(myWorkDuringRework).contains("href=\"/app/deliverables/" + planId + "\"");
        // Scoped to Shoot Review only - never another gate's data on this Camera Person screen.
        assertThat(myWorkDuringRework).doesNotContain("IDEA_REVIEW").doesNotContain("PLANNING_REVIEW")
                .doesNotContain("EDIT_REVIEW");

        // ENG-063: "My Review Feedback" lives INSIDE the Active Work tab panel, not as a standalone
        // section outside the tabs - it must sit strictly between the Active Work panel opening and
        // the History panel opening, so my-work-tabs.js hides/shows it together with Active Work.
        int activePanelIndex = myWorkDuringRework.indexOf("data-tab-panel=\"active\"");
        int feedbackIndex = myWorkDuringRework.indexOf("My Review Feedback");
        int historyPanelIndex = myWorkDuringRework.indexOf("data-tab-panel=\"history\"");
        assertThat(activePanelIndex).isPositive();
        assertThat(feedbackIndex).isGreaterThan(activePanelIndex);
        assertThat(historyPanelIndex).isGreaterThan(feedbackIndex);

        // Resubmit and approve - the plan moves on, but BOTH decisions must remain visible: the
        // latest (Approved) shown prominently, the earlier Rework preserved under "View Feedback
        // History", not overwritten or collapsed away.
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("SAP");

        String myWorkAfterApproval = cam.get("/app/my-work").body();
        assertThat(myWorkAfterApproval).contains(plan.getContentId());
        assertThat(myWorkAfterApproval).contains("APPROVED");
        // The earlier rework decision's reason must still be present somewhere on the page (inside
        // the collapsible "View Feedback History"), never lost.
        assertThat(myWorkAfterApproval).contains("View Feedback History");
        assertThat(myWorkAfterApproval).contains(reworkReason);
        assertThat(myWorkAfterApproval).contains("REWORK REQUIRED");
    }
}
