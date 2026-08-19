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
 * ENG-064: a Camera Person viewing their own Shoot task at /app/deliverables/{id} gets the
 * redesigned Shoot Task Detail page instead of the shared CEO/MM-oriented multi-stage shell -
 * purely a presentation change, driven entirely by the existing WorkflowStatus/ReviewCycle/
 * WorkflowTransitionHistory data, never a new backend status, permission, or transition.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShootTaskDetailTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void camerapersonSeesRedesignedPageThroughTheFullLifecycleWhileCeoSeesTheStandardShell() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-shoot-detail-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Shoot Detail Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Shoot Detail Test " + unique + "\"}");
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
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/shoot-detail-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        // SA: redesigned page, "Assigned" status, primary action is Start Shoot, no management
        // controls (no Approve/Rework decision form, no qualifying-recipient picker, no assignment
        // pickers) anywhere on the page.
        String atAssigned = cam.get("/app/deliverables/" + planId).body();
        assertThat(atAssigned).contains("Shoot Task").contains(plan.getContentId());
        assertThat(atAssigned).contains("class=\"breadcrumb\"").contains("Task Detail");
        assertThat(atAssigned).contains("Start Shoot");
        assertThat(atAssigned).doesNotContain("Qualifying Cameraperson").doesNotContain("Request Rework")
                .doesNotContain("Reassign").doesNotContain("Reschedule").doesNotContain("Cancel Deliverable");

        // Same plan, viewed by CEO: the standard shared shell, unaffected by this redesign.
        String ceoView = ceo.get("/app/deliverables/" + planId).body();
        assertThat(ceoView).doesNotContain("class=\"breadcrumb\"");
        assertThat(ceoView).contains("<h2>Shoot</h2>"); // the existing generic Shoot panel heading, unchanged

        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");

        // SIP (first time, no rework yet): "In Progress", primary action Continue Shoot.
        String atInProgress = cam.get("/app/deliverables/" + planId).body();
        assertThat(atInProgress).contains("In Progress");
        assertThat(atInProgress).contains("Continue Shoot");
        assertThat(atInProgress).contains("No review feedback yet.");

        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");

        // SRV: no execution button, compact "Submitted for Review" status only.
        String atReview = cam.get("/app/deliverables/" + planId).body();
        assertThat(atReview).contains("Submitted for Review");
        assertThat(atReview).doesNotContain("Continue Shoot").doesNotContain("Start Shoot");

        String reworkReason = "Lighting is inconsistent, please reshoot " + unique;
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":false,\"reason\":\"" + reworkReason + "\"}");

        // SIP again, this time via REQUEST_REWORK - "Rework Required" friendly status, latest
        // feedback card shows the real reason, action button still says "Continue Shoot" (same
        // existing submit-for-review endpoint, not a separate "resolve rework" flow), and no other
        // gate's data (Idea/Planning/Edit Review) leaks onto this Camera Person screen.
        String atRework = cam.get("/app/deliverables/" + planId).body();
        assertThat(atRework).contains("Rework Required");
        assertThat(atRework).contains("Continue Shoot");
        assertThat(atRework).contains("Latest Reviewer Feedback").contains("REWORK REQUIRED").contains(reworkReason);
        assertThat(atRework).doesNotContain("IDEA_REVIEW").doesNotContain("PLANNING_REVIEW").doesNotContain("EDIT_REVIEW");

        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("SAP");

        // SAP: "Approved" status, no execution controls at all, but the earlier rework decision is
        // still reachable via "View Feedback History" - never lost.
        String atApproved = cam.get("/app/deliverables/" + planId).body();
        assertThat(atApproved).contains("Approved");
        assertThat(atApproved).doesNotContain("Continue Shoot").doesNotContain("Start Shoot")
                .doesNotContain("Submitted for Review");
        assertThat(atApproved).contains("Latest Reviewer Feedback").contains("APPROVED");
        assertThat(atApproved).contains("View Feedback History").contains(reworkReason);
    }
}
