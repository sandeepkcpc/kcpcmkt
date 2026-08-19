package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.support.TestApiClient;
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
 * Build-prompt §7 / Continuation-Prompt Section 7: golden-path variants not covered by
 * {@link GoldenEndToEndFlowTest} - Reject, Retain/Reopen, Planning Review rework, Urgent
 * Planning, the exactly-5-day Standard/Urgent boundary (BRS-REQ-093), Hold/Resume blocking a
 * review submission, the Performance Due Date gate (ERD-CON-016: due date = publication + 2
 * days), multiple publication events + Target N/A + reversal + Repost, and Completed-deliverable
 * Reopen for Publishing (regression guard for the ENG-0xx `AdminActionService` target-status fix
 * found earlier this project: {@code PUBLISHING_REOPEN} must land on {@code PUBG}, not {@code RFP}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkflowVariantsE2ETest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PerformanceObligationRepository obligationRepository;
    @Autowired
    PublicationTargetNaRecordRepository naRecordRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_2 = "01926e3e-000a-7000-8000-000000000002";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    @Test
    void ideaRejectFlowRequiresReasonAndTerminatesAtRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Reject Flow " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // AC-017.1: rejection reason is mandatory.
        HttpResponse<String> missingReason = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"REJECT\"}");
        assertThat(missingReason.statusCode()).isEqualTo(400);

        JsonNode rejected = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"REJECT\",\"reason\":\"Not aligned with current campaign\"}");
        assertThat(rejected.get("status").asText()).isEqualTo("RJ");

        // A rejected idea's review gate is closed - a second decision attempt is an invalid transition.
        HttpResponse<String> secondDecision = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        assertThat(secondDecision.statusCode()).isEqualTo(409);
    }

    @Test
    void ideaRetainAndReopenFlowReturnsToPendingApproval() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Retain Flow " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // BRS-REQ-018: Retain does not require a reason.
        JsonNode retained = ceo.postJson("/api/v1/ideas/" + ideaId + "/review", "{\"decision\":\"RETAIN\"}");
        assertThat(retained.get("status").asText()).isEqualTo("RET");

        JsonNode reopened = ceo.postJson("/api/v1/ideas/" + ideaId + "/reopen", "");
        assertThat(reopened.get("status").asText()).isEqualTo("PA");

        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        assertThat(approved.get("status").asText()).isEqualTo("PL");
    }

    @Test
    void planningReviewReworkReturnsToPlanningThenApproves() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camId = createUser(ceo, "Rework Camera", "e2e-rework-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Rework Flow " + unique);
        preparePlanningParameters(ceo, contentPlanId, camId, LocalDate.now().plusDays(10).toString());

        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        HttpResponse<String> missingReason = ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision",
                "{\"approve\":false}");
        assertThat(missingReason.statusCode()).isEqualTo(400);

        JsonNode reworked = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision",
                "{\"approve\":false,\"reason\":\"Folder link is broken, please fix\"}");
        assertThat(reworked.get("status").asText()).isEqualTo("PL");

        // Resubmit without any further changes - parameters are still complete - and approve.
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision",
                "{\"approve\":true}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
    }

    @Test
    void standardScheduleExactly5DayBoundaryAndUrgentScheduleBelowIt() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String planA = approveIdeaAndGetContentPlanId(ceo, "Boundary Exactly5 " + unique);
        String exactly5 = LocalDate.now().plusDays(5).toString();
        HttpResponse<String> exactly5Response = ceo.post("/api/v1/content-plans/" + planA + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + exactly5 + "\"}");
        assertThat(exactly5Response.statusCode()).isEqualTo(200);

        String planB = approveIdeaAndGetContentPlanId(ceo, "Boundary Under5 " + unique);
        String under5 = LocalDate.now().plusDays(4).toString();
        HttpResponse<String> under5Response = ceo.post("/api/v1/content-plans/" + planB + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + under5 + "\"}");
        assertThat(under5Response.statusCode()).isEqualTo(400);

        // BRS-REQ-093: the same sub-5-day date must succeed via Urgent Planning Mode.
        JsonNode urgent = ceo.postJson("/api/v1/content-plans/" + planB + "/schedule/urgent",
                "{\"plannedLiveDate\":\"" + under5 + "\",\"shootDate\":\"" + LocalDate.now() + "\",\"editDate\":\""
                        + LocalDate.now().plusDays(1) + "\",\"urgencyReason\":\"Client moved the launch date up\"}");
        assertThat(urgent.get("plannedLiveDate").asText()).isEqualTo(under5);
    }

    @Test
    void holdBlocksShootReviewSubmitUntilResumed() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-hold-cam-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Hold Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Hold Flow " + unique);
        preparePlanningParameters(ceo, contentPlanId, camId, LocalDate.now().plusDays(10).toString());
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");

        HttpResponse<String> holdResponse = ceo.post("/api/v1/content-plans/" + contentPlanId + "/hold",
                "{\"reason\":\"Location access delayed\"}");
        assertThat(holdResponse.statusCode()).isEqualTo(200);

        // ERD-CON-061/062: an open Hold blocks progression - Shoot Review submission is rejected.
        HttpResponse<String> blockedSubmit = cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        assertThat(blockedSubmit.statusCode()).isEqualTo(409);

        // A second Hold while one is already open is also rejected (ERD-CON-062).
        HttpResponse<String> doubleHold = ceo.post("/api/v1/content-plans/" + contentPlanId + "/hold",
                "{\"reason\":\"duplicate\"}");
        assertThat(doubleHold.statusCode()).isEqualTo(409);

        HttpResponse<String> resumeResponse = ceo.post("/api/v1/content-plans/" + contentPlanId + "/resume", "");
        assertThat(resumeResponse.statusCode()).isEqualTo(200);

        HttpResponse<String> submitAfterResume = cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        assertThat(submitAfterResume.statusCode()).isEqualTo(200);
    }

    @Test
    void scorecardEntryBlockedBeforePerformanceDueDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-due-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-due-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-due-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Due Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Due Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Due Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pub = loginNewClient(pubEmail);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = advanceToReadyForPublishing(ceo, cam, ed, "Due Date Flow " + unique, camId, edId);
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments", "{\"publisherUserId\":\"" + pubId + "\"}");
        pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");
        String outputId = findPlannedOutputId(contentPlanId);

        // Publish "now" -> performance_due_date = today + 2 (ERD-CON-016) - strictly in the future.
        pub.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_1
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + Instant.now()
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/due-" + unique + "\"}");

        String obligationId = findObligationId(contentPlanId);
        HttpResponse<String> tooEarly = ceo.post("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"views3sec\":800,\"plays\":1000,\"averageWatchTimeSeconds\":12.5,\"videoLengthSeconds\":20.0,"
                        + "\"linkClicks\":0,\"clicksIsNa\":true,\"impressions\":5000}");
        assertThat(tooEarly.statusCode()).isEqualTo(400);
    }

    @Test
    void multiplePublicationEventsTargetNaReversalAndRepost() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-na-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-na-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-na-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Na Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Na Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Na Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pub = loginNewClient(pubEmail);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Multi-Target Flow " + unique);
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/na-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = findPlannedOutputId(contentPlanId);
        // Two publication targets mapped for this single output.
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\",\"" + TARGET_2 + "\"]}");

        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments", "{\"editorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments", "{\"publisherUserId\":\"" + pubId + "\"}");
        pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");

        // Publish live to Target 1 only - scope is not yet resolved (Target 2 still pending).
        JsonNode firstEvent = pub.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_1
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + Instant.now()
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/na-t1-" + unique + "\"}");
        JsonNode midPlan = ceo.getJson("/api/v1/content-plans/" + contentPlanId);
        assertThat(midPlan.get("status").asText()).isEqualTo("PUBG");

        // Repost to Target 1 - a second event against the same output/target, distinct from the ORIGINAL.
        // Must happen while still PUBG (API-OP-040): once scope resolves the deliverable leaves PUBG and a
        // further event requires Reopen for Publishing first (API-OP-052), exercised separately below.
        HttpResponse<String> repost = pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_1
                        + "\",\"eventType\":\"REPOST\",\"actualPublicationTimestamp\":\"" + Instant.now()
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/na-repost-" + unique + "\"}");
        assertThat(repost.statusCode()).isEqualTo(200);

        // Designate Target 2 N/A - now every mapped pair is live-or-N/A -> scope resolved (PP).
        HttpResponse<String> naDesignation = ceo.post("/api/v1/content-plans/" + contentPlanId + "/publishing/targets/na",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_2
                        + "\",\"reason\":\"Client declined this channel\"}");
        assertThat(naDesignation.statusCode()).isEqualTo(200);
        JsonNode resolvedPlan = ceo.getJson("/api/v1/content-plans/" + contentPlanId);
        assertThat(resolvedPlan.get("status").asText()).isEqualTo("PP");

        // Reverse the N/A designation (e.g. the client later approved the channel after all) - append-only linked record.
        String naRecordId = naRecordRepository.findByPlannedOutput(plannedOutputRepository.findById(UUID.fromString(outputId))
                        .orElseThrow()).stream()
                .max(java.util.Comparator.comparing(com.kcpc.mkt.publishing.domain.PublicationTargetNaRecord::getRecordedAt))
                .map(r -> r.getId().toString()).orElseThrow();
        HttpResponse<String> reversal = ceo.post("/api/v1/content-plans/" + contentPlanId + "/publishing/targets/na/"
                + naRecordId + "/reverse", "{\"reason\":\"Client approved the channel after all\"}");
        assertThat(reversal.statusCode()).isEqualTo(200);
    }

    /**
     * ENG-055: the Publishing checklist page shows one Pending row per (output, target) pair up
     * front, its bulk "Submit Published Tasks" POST (plannedOutputIds/publicationTargetIds/
     * evidenceUrls in lockstep + one shared date) can record more than one pair in a single
     * request, and completing the last pending pair auto-transitions the plan to PP - same
     * completion rule {@link #multiplePublicationEventsTargetNaReversalAndRepost} already covers
     * for the single-row endpoint, exercised here through the new bulk one instead. Also proves a
     * second Original for an already-completed pair is rejected at the service source (not just
     * hidden from the checklist UI).
     */
    @Test
    void publishingChecklistBulkSubmitRecordsMultiplePairsAndAutoCompletesOnTheLastOne() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-checklist-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-checklist-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-checklist-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Checklist Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Checklist Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Checklist Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pub = loginNewClient(pubEmail);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Checklist Flow " + unique);
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/checklist-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = findPlannedOutputId(contentPlanId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\",\"" + TARGET_2 + "\"]}");

        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments", "{\"editorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments", "{\"publisherUserId\":\"" + pubId + "\"}");
        pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");

        // Checklist page: both pairs Pending before anything is recorded, Submit disabled by default.
        // Scoped to the checklist table itself (not the whole page body) - ENG-068 gave a Publisher
        // viewing their own task a redesigned page with its own top-level status pill, so a
        // whole-page pill count would double-count that unrelated pill.
        HttpResponse<String> before = pub.get("/app/deliverables/" + contentPlanId);
        assertThat(before.body()).contains("id=\"publishing-checklist-submit\" disabled");
        assertThat(checklistTableHtml(before.body()).split(java.util.regex.Pattern.quote("status-pill status-pending"), -1).length - 1).isEqualTo(2);

        // Record Target 1 alone first (single-row endpoint) - scope not yet resolved, stays PUBG.
        HttpResponse<String> firstOriginal = pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_1
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + Instant.now()
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/checklist-t1-" + unique + "\"}");
        assertThat(firstOriginal.statusCode()).isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("PUBG");

        // A second Original for the SAME pair is rejected at the service - not silently duplicated.
        HttpResponse<String> duplicateOriginal = pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_1
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + Instant.now()
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/checklist-t1-dupe-" + unique + "\"}");
        assertThat(duplicateOriginal.statusCode()).isEqualTo(400);
        assertThat(duplicateOriginal.body()).contains("already exists");

        // Bulk-submit the remaining Target 2 through the checklist's actual POST shape - completes
        // scope. No actualPublicationTimestamp field (ENG-056): the server stamps Instant.now() itself.
        HttpResponse<String> bulk = pub.postFormMulti("/app/deliverables/" + contentPlanId + "/publishing/events/bulk",
                Map.of(
                        "plannedOutputIds", List.of(outputId),
                        "publicationTargetIds", List.of(TARGET_2),
                        "evidenceUrls", List.of("https://youtube.com/watch?v=checklist-t2-" + unique)));
        assertThat(bulk.statusCode()).isEqualTo(302);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("PP");

        HttpResponse<String> after = pub.get("/app/deliverables/" + contentPlanId);
        String afterTable = checklistTableHtml(after.body());
        assertThat(afterTable.split(java.util.regex.Pattern.quote("status-pill status-completed"), -1).length - 1).isEqualTo(2);
        assertThat(afterTable.split(java.util.regex.Pattern.quote("status-pill status-pending"), -1).length - 1).isEqualTo(0);
    }

    /** ENG-068: isolates the Publication Targets checklist table so pill-count assertions above are immune to any other status pill elsewhere on the page (e.g. the redesigned Publisher page's own top-level status card). */
    private static String checklistTableHtml(String pageBody) {
        int start = pageBody.indexOf("id=\"publishing-checklist-table\"");
        int end = pageBody.indexOf("</table>", start);
        return pageBody.substring(start, end);
    }

    @Test
    void completedDeliverableReopensForPublishingOntoPubgNotRfp() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-reopen-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-reopen-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-reopen-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Reopen Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Reopen Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Reopen Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pub = loginNewClient(pubEmail);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = driveToCompleted(ceo, cam, ed, pub, "Reopen Flow " + unique, camId, edId, pubId);
        JsonNode completed = ceo.getJson("/api/v1/content-plans/" + contentPlanId);
        assertThat(completed.get("status").asText()).isEqualTo("COMP");

        HttpResponse<String> reopen = ceo.post("/api/v1/content-plans/" + contentPlanId + "/reopen-publishing",
                "{\"reason\":\"Client requested an additional cross-post\"}");
        assertThat(reopen.statusCode()).isEqualTo(200);

        JsonNode reopened = ceo.getJson("/api/v1/content-plans/" + contentPlanId);
        assertThat(reopened.get("status").asText()).isEqualTo("PUBG");
    }

    // --- shared flow helpers -------------------------------------------------------------

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return response.get("userId").asText();
    }

    /** ENG-043: Start/Submit-style execution acts require the actively assigned employee, not CEO/MM native authority. */
    private TestApiClient loginNewClient(String email) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(email, "Passw0rd!");
        return client;
    }

    private void grantPublishingPermission(TestApiClient ceo, String publisherUserId) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisherUserId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e workflow-variants publisher grant\"}");
    }

    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        return findContentPlanId(ideaId);
    }

    private void preparePlanningParameters(TestApiClient ceo, String contentPlanId, String camId, String liveDate) throws Exception {
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + liveDate + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/" + contentPlanId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = findPlannedOutputId(contentPlanId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}");
    }

    /** ENG-043: {@code cam}/{@code ed} must already be logged in as the actively assigned Cameraperson/Editor. */
    private String advanceToReadyForPublishing(TestApiClient ceo, TestApiClient cam, TestApiClient ed, String title,
                                                String camId, String edId) throws Exception {
        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, title);
        preparePlanningParameters(ceo, contentPlanId, camId, LocalDate.now().plusDays(10).toString());
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments", "{\"editorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"]}");
        return contentPlanId;
    }

    /** ENG-043: {@code pub} must already be logged in as the actively assigned Publisher, with PERM_08 granted. */
    private String driveToCompleted(TestApiClient ceo, TestApiClient cam, TestApiClient ed, TestApiClient pub,
                                     String title, String camId, String edId, String pubId) throws Exception {
        String contentPlanId = advanceToReadyForPublishing(ceo, cam, ed, title, camId, edId);
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments", "{\"publisherUserId\":\"" + pubId + "\"}");
        pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");
        String outputId = findPlannedOutputId(contentPlanId);
        String pastTimestamp = Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS).toString();
        pub.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + TARGET_1
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/reopen-" + title.hashCode() + "\"}");
        String obligationId = findObligationId(contentPlanId);
        ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"views3sec\":800,\"plays\":1000,\"averageWatchTimeSeconds\":12.5,\"videoLengthSeconds\":20.0,"
                        + "\"linkClicks\":0,\"clicksIsNa\":true,\"impressions\":5000}");
        ceo.post("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
        return contentPlanId;
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

    private String findObligationId(String contentPlanIdText) {
        return obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(contentPlanIdText)).stream()
                .max(java.util.Comparator.comparing(o -> o.getEvent().getActualPublicationTimestamp()))
                .map(o -> o.getId().toString()).orElseThrow();
    }
}
