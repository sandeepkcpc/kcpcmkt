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
 * ENG-066: a Video Editor viewing their own Edit task at /app/deliverables/{id} gets the
 * redesigned Edit Task Detail page instead of the shared CEO/MM-oriented multi-stage shell -
 * mirrors {@link ShootTaskDetailTest} exactly, Edit-flavored data only, never a new backend
 * status/permission/transition. Unlike Shoot (which rests at SAP after approval), Edit Review
 * approval fires ERV-&gt;EAP-&gt;RFP atomically (no resting "Edit Approved" status), so once
 * approved the Editor falls back to the standard shared shell - asserted explicitly below.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EditTaskDetailTest {

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
    void editorSeesRedesignedPageThroughEaEdErvReworkThenFallsBackToStandardShellAfterApproval() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-edit-detail-cam-" + unique + "@kcpcbandhani.local";
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Edit Detail Cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String camId = camUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        String editorEmail = "e2e-edit-detail-editor-" + unique + "@kcpcbandhani.local";
        JsonNode editorUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Edit Detail Editor\",\"email\":\"" + editorEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + VIDEO_EDITOR_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String editorId = editorUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editorId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        String pubEmail = "e2e-edit-detail-pub-" + unique + "@kcpcbandhani.local";
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Edit Detail Publisher\",\"email\":\"" + pubEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"e2e test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Edit Detail Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edit-detail-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        // Workflow redesign: Editor team assignment now folds directly into this same Approve call.
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + editorId + "\"}");

        TestApiClient editor = new TestApiClient(port);
        editor.login(editorEmail, "Passw0rd!");

        // EA: redesigned page, "Assigned" status, primary action Start Edit, no management controls.
        String atAssigned = editor.get("/app/deliverables/" + planId).body();
        assertThat(atAssigned).contains("Edit Task").contains(plan.getContentId());
        assertThat(atAssigned).contains("class=\"breadcrumb\"").contains("Task Detail");
        assertThat(atAssigned).contains("Start Edit");
        assertThat(atAssigned).doesNotContain("Qualifying Editor").doesNotContain("Request Rework")
                .doesNotContain("Reassign").doesNotContain("Reschedule").doesNotContain("Cancel Deliverable");

        // Same plan, viewed by CEO: the standard shared shell, unaffected by this redesign.
        String ceoView = ceo.get("/app/deliverables/" + planId).body();
        assertThat(ceoView).doesNotContain("class=\"breadcrumb\"");
        assertThat(ceoView).contains("<h2>Edit</h2>"); // the existing generic Edit panel heading, unchanged

        editor.post("/api/v1/content-plans/" + planId + "/editing/start", "");

        // ED (first time, no rework yet): "In Progress", primary action Continue Edit.
        String atInProgress = editor.get("/app/deliverables/" + planId).body();
        assertThat(atInProgress).contains("In Progress");
        assertThat(atInProgress).contains("Continue Edit");
        assertThat(atInProgress).contains("No review feedback yet.");

        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");

        // ERV: no execution button, compact "Submitted for Review" status only.
        String atReview = editor.get("/app/deliverables/" + planId).body();
        assertThat(atReview).contains("Submitted for Review");
        assertThat(atReview).doesNotContain("Continue Edit").doesNotContain("Start Edit");

        String reworkReason = "Colour grading thoda aur improve karo " + unique;
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":false,\"reason\":\"" + reworkReason + "\"}");

        // ED again via REQUEST_REWORK - "Rework Required" friendly status, latest feedback card
        // shows the real reason, action button still "Continue Edit" (same existing endpoint, no
        // separate "resolve rework" flow), no other gate's data leaks onto this Editor screen.
        String atRework = editor.get("/app/deliverables/" + planId).body();
        assertThat(atRework).contains("Rework Required");
        assertThat(atRework).contains("Continue Edit");
        assertThat(atRework).contains("Latest Reviewer Feedback").contains("REWORK REQUIRED").contains(reworkReason);
        assertThat(atRework).doesNotContain("IDEA_REVIEW").doesNotContain("PLANNING_REVIEW").doesNotContain("SHOOT_REVIEW");

        editor.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");

        // Once approved, status is RFP immediately (no resting "Edit Approved" status, unlike
        // Shoot's SAP) - the Editor falls back to the standard shared shell, not the redesigned
        // page.
        String atApproved = editor.get("/app/deliverables/" + planId).body();
        assertThat(atApproved).doesNotContain("class=\"breadcrumb\"");
    }
}
