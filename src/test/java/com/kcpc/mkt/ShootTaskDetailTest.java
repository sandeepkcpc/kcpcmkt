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
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

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
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");

        String pubEmail = "e2e-shoot-detail-pub-" + unique + "@kcpcbandhani.local";
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Shoot Detail Pub\",\"email\":\"" + pubEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Shoot Detail Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/shoot-detail-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

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
        // Workflow redesign: Editor team assignment now folds directly into this same Approve call
        // (ShootingService#decideShootReview) - SAP is no longer a resting status reachable through
        // this flow (SRV -> SAP -> EA all fire atomically here), so the plan lands on EA directly,
        // never observably pausing at "Shoot Approved" the way it used to before this feature.
        String editEmail = "e2e-shoot-detail-editor-" + unique + "@kcpcbandhani.local";
        JsonNode editUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Shoot Detail Editor\",\"email\":\"" + editEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + VIDEO_EDITOR_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String editId = editUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editId + "\"],\"leadEditorUserId\":\"" + editId + "\"}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");

        // The Cameraperson's own redesigned page window is SA/SIP/SRV/SAP only - once the plan has
        // moved on to EA, it correctly falls out of that window (their Shoot task is done); the
        // earlier rework decision remains reachable to a manager via the standard shell's "Review
        // Feedback History" panel (not the task-detail page's "Latest Reviewer Feedback"),
        // unchanged from before this feature (see DeliverableMvcController#view).
        String ceoAtEa = ceo.get("/app/deliverables/" + planId).body();
        assertThat(ceoAtEa).contains("Review Feedback History").contains("Approved").contains(reworkReason);
    }
}
