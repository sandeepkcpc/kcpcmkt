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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-068: a Publisher's own "My Work" screen (KPI cards + Active Work/History tabs, no Marks tab
 * - Publishing has no marks model) and the redesigned Publishing Task Detail page at
 * /app/deliverables/{id}, mirroring {@link EditTaskDetailTest}/{@link EditFeedbackTest}'s pattern.
 * Unlike Shoot/Edit, Publishing has no review/rework gate at all, so there is no feedback panel to
 * assert, and PP (unlike Edit's EAP) genuinely IS the Publisher's own final resting status - the
 * redesigned page keeps rendering there instead of falling back to the standard shell.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublisherScreenTest {

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
    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_YOUTUBE_KCPC = "01926e3e-000a-7000-8000-000000000002";

    @Test
    void publisherSeesRedesignedPageThroughRfpPubgToPerformancePending() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camEmail = "e2e-pub-cam-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Pub Detail Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        String editorEmail = "e2e-pub-editor-" + unique + "@kcpcbandhani.local";
        String editorId = createUser(ceo, "Pub Detail Editor", editorEmail, VIDEO_EDITOR_ROLE_ID);
        String pubEmail = "e2e-pub-detail-" + unique + "@kcpcbandhani.local";
        String pubId = createUser(ceo, "Pub Detail Publisher", pubEmail, PUBLISHER_ROLE_ID);
        // ENG-043: execution requires an explicit permission grant - Business Role alone doesn't
        // grant it (mirrors CeoPipelineDashboardTest). Candidate eligibility/execution is now
        // permission-driven (OperationalEligibilityService) for all three stages.
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"publisher screen test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editorId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"publisher screen test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"publisher screen test fixture grant\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Publisher Detail Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        String plannedLiveDate = LocalDate.now().plusDays(10).toString();
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard", "{\"plannedLiveDate\":\"" + plannedLiveDate + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/pub-detail-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        ceo.postJson("/api/v1/content-plans/outputs/" + output.getId() + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_INSTAGRAM_KCPC + "\",\"" + TARGET_YOUTUBE_KCPC + "\"]}");
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
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editorId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        ceo.postJson("/api/v1/content-plans/" + planId + "/publishing/assignments", "{\"publisherUserId\":\"" + pubId + "\"}");

        TestApiClient publisher = new TestApiClient(port);
        publisher.login(pubEmail, "Passw0rd!");

        // My Work: Publisher-flavored KPI cards + Active Work table, no Marks tab, "Start
        // Publishing" action, "1 / 2" Targets column not yet resolved... actually still 0 resolved.
        String myWorkAtRfp = publisher.get("/app/my-work").body();
        assertThat(myWorkAtRfp).contains("Active Publishing").contains("Pending Targets").contains(">Completed<");
        assertThat(myWorkAtRfp).doesNotContain("data-tab=\"marks\"");
        assertThat(myWorkAtRfp).contains(plan.getContentId());
        assertThat(myWorkAtRfp).contains("Pub Detail Publisher"); // Publisher(s) column
        assertThat(myWorkAtRfp).contains("0 / 2"); // Targets column - neither mapped target resolved yet
        assertThat(myWorkAtRfp).contains("Start Publishing");

        // RFP: redesigned page, "Ready for Publishing" status, primary action Start Publishing, no
        // management controls, no review-feedback concept anywhere on this page.
        String atRfp = publisher.get("/app/deliverables/" + planId).body();
        assertThat(atRfp).contains("Publishing Task").contains(plan.getContentId());
        assertThat(atRfp).contains("class=\"breadcrumb\"").contains("Task Detail");
        assertThat(atRfp).contains("Ready for Publishing");
        assertThat(atRfp).contains("Start Publishing");
        assertThat(atRfp).doesNotContain("Latest Reviewer Feedback").doesNotContain("Review Decision");
        assertThat(atRfp).doesNotContain("Reassign").doesNotContain("Reschedule").doesNotContain("Cancel Deliverable")
                .doesNotContain("Request Rework");

        // Same plan, viewed by CEO: the standard shared shell, unaffected by this redesign.
        String ceoView = ceo.get("/app/deliverables/" + planId).body();
        assertThat(ceoView).doesNotContain("class=\"breadcrumb\"");
        assertThat(ceoView).contains("<h2>Publishing</h2>");

        publisher.post("/api/v1/content-plans/" + planId + "/publishing/start", "");

        // PUBG: "Publishing" status/progress step current, checklist visible, no Start Publishing button anymore.
        String atPubg = publisher.get("/app/deliverables/" + planId).body();
        assertThat(atPubg).contains(">Publishing<");
        assertThat(atPubg).contains("publishing-checklist-table");
        assertThat(atPubg).doesNotContain("Start Publishing");

        String pastDate = LocalDate.now().minusDays(1).toString();
        publisher.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + TARGET_INSTAGRAM_KCPC
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastDate + "T00:00:00Z\","
                        + "\"evidenceUrl\":\"https://instagram.com/p/pub-detail-" + unique + "\"}");

        // Partial resolution: 1 of 2 targets live, still PUBG (scope not fully resolved yet).
        String atPartial = publisher.get("/app/deliverables/" + planId).body();
        assertThat(atPartial).contains("1 / 2 resolved"); // Publishing Information "Targets" row
        String myWorkPartial = publisher.get("/app/my-work").body();
        assertThat(myWorkPartial).contains("1 / 2"); // Active table Targets column
        assertThat(myWorkPartial).contains("Pending Targets");

        publisher.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + TARGET_YOUTUBE_KCPC
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastDate + "T00:00:00Z\","
                        + "\"evidenceUrl\":\"https://youtube.com/watch?v=pub-detail-" + unique + "\"}");
        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PP");

        // PP: unlike Edit's EAP asymmetry, Publishing genuinely rests here - the redesigned page
        // keeps rendering (not a fallback to the standard shell), "Performance Pending" progress
        // step done, both targets resolved.
        String atPp = publisher.get("/app/deliverables/" + planId).body();
        assertThat(atPp).contains("class=\"breadcrumb\"");
        assertThat(atPp).contains("Performance Pending");
        assertThat(atPp).contains("2 / 2 resolved");

        // Now falls out of Active Work into History on My Work (PUBLISH_ACTIVE_WINDOW is {RFP,
        // PUBG} only, mirroring Shoot's own SAP-falls-to-History precedent).
        String myWorkAtPp = publisher.get("/app/my-work").body();
        assertThat(myWorkAtPp).contains("No active publishing tasks.");
        assertThat(myWorkAtPp).contains("Completed Publishing Work").contains(plan.getContentId());
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"publisher screen test fixture\"}");
        return response.get("userId").asText();
    }
}
