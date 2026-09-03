package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.publishing.repository.PublicationTargetNaRecordRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
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
 * Reopen for Publishing (regression guard for the reopened-Publishing/REPOST defect fix:
 * {@code PUBLISHING_REOPEN} must land on {@code RFP}, the same "Ready for Publishing" state
 * first-time Publishing uses for Assign Publisher - landing directly on {@code PUBG} instead, as
 * this codebase did previously, silently skipped the Assign Publisher gate and let a stale
 * ORIGINAL-cycle assignment execute the repost).
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
    @Autowired
    ActualPublicationEventRepository eventRepository;
    @Autowired
    PublishingAssignmentRepository publishingAssignmentRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_2 = "01926e3e-000a-7000-8000-000000000002";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";

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
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0}");
        assertThat(secondDecision.statusCode()).isEqualTo(409);
    }

    @Test
    void ideaRetainAndReopenFlowReturnsToPendingApproval() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camId = createUser(ceo, "Retain Flow Cam", "e2e-retain-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grantShootExecutionPermission(ceo, camId);
        String pubId = createUser(ceo, "Retain Flow Pub", "e2e-retain-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        grantPublishingPermission(ceo, pubId);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Retain Flow " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // BRS-REQ-018: Retain does not require a reason.
        JsonNode retained = ceo.postJson("/api/v1/ideas/" + ideaId + "/review", "{\"decision\":\"RETAIN\"}");
        assertThat(retained.get("status").asText()).isEqualTo("RET");

        JsonNode reopened = ceo.postJson("/api/v1/ideas/" + ideaId + "/reopen", "");
        assertThat(reopened.get("status").asText()).isEqualTo("PA");

        // Workflow redesign: Idea Review approval carries every former Planning field and transitions
        // straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/retain-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
    }

    // NOTE (workflow redesign): the "Planning Review rework" test formerly here is retired, not
    // adapted - Planning Review (PLRV/PLAP) no longer exists as an active-workflow gate; a Content
    // Plan is now created already fully planned inside IdeaService#approve and transitions straight
    // to Shoot Assigned. There is no more "submit for Planning Review, get sent back to Planning,
    // fix, resubmit, approve" cycle to test - Idea Review itself (APPROVE/REJECT/RETAIN, tested
    // above) has no analogous "request rework" outcome. The equivalent rework-cycle behavior at a
    // still-live review gate (first-pass vs. rework denominators) is covered by
    // KpiDashboardServiceTest#firstPassApprovalVsReworkCycleAreDistinguished (Shoot Review) and
    // HighPriorityEdgeCaseTest#qualifyingCamerapersonListNeverShowsDuplicatesAfterAReworkCycle.

    /**
     * Workflow redesign: {@code /schedule/standard}/{@code /schedule/urgent} are no longer the
     * initial-planning endpoints (that validation now lives inline in IdeaService#approve) - they
     * remain live as the Reschedule path for an ALREADY-existing plan (no status gate), so this now
     * exercises the exact same BRS-REQ-093 boundary on that reschedule path instead.
     */
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
        grantShootExecutionPermission(ceo, camId);
        TestApiClient cam = loginNewClient(camEmail);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Hold Flow " + unique, camId);
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
        grantShootExecutionPermission(ceo, camId);
        grantEditExecutionPermission(ceo, edId);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = advanceToReadyForPublishing(ceo, cam, ed, "Due Date Flow " + unique, camId, edId, pubId);
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
        grantShootExecutionPermission(ceo, camId);
        grantEditExecutionPermission(ceo, edId);
        grantPublishingPermission(ceo, pubId);

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (including the initial output with TWO publication targets mapped) and
        // transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Multi-Target Flow " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/na-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\",\"" + TARGET_2 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        String contentPlanId = findContentPlanId(ideaId);
        String outputId = findPlannedOutputId(contentPlanId);
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        // Workflow redesign: Editor/Publisher team assignment now folds directly into the Shoot/
        // Edit Review Approve calls themselves.
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
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
        grantShootExecutionPermission(ceo, camId);
        grantEditExecutionPermission(ceo, edId);
        grantPublishingPermission(ceo, pubId);

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (including the initial output with TWO publication targets mapped) and
        // transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode checklistIdea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Checklist Flow " + unique + "\"}");
        String checklistIdeaId = checklistIdea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + checklistIdeaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/checklist-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\",\"" + TARGET_2 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        String contentPlanId = findContentPlanId(checklistIdeaId);
        String outputId = findPlannedOutputId(contentPlanId);
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        // Workflow redesign: Editor/Publisher team assignment now folds directly into the Shoot/
        // Edit Review Approve calls themselves.
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
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
    void completedDeliverableReopensForPublishingOntoRfpRequiringFreshPublisherAssignment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-reopen-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-reopen-ed-" + unique + "@kcpcbandhani.local";
        String pubAEmail = "e2e-reopen-puba-" + unique + "@kcpcbandhani.local";
        String pubBEmail = "e2e-reopen-pubb-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Reopen Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Reopen Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubAId = createUser(ceo, "Reopen Publisher A", pubAEmail, PUBLISHER_ROLE_ID);
        String pubBId = createUser(ceo, "Reopen Publisher B", pubBEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pubA = loginNewClient(pubAEmail);
        TestApiClient pubB = loginNewClient(pubBEmail);
        grantShootExecutionPermission(ceo, camId);
        grantEditExecutionPermission(ceo, edId);
        grantPublishingPermission(ceo, pubAId);
        grantPublishingPermission(ceo, pubBId);

        String contentPlanId = driveToCompleted(ceo, cam, ed, pubA, "Reopen Flow " + unique, camId, edId, pubAId);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("COMP");
        String outputId = findPlannedOutputId(contentPlanId);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        ActualPublicationEvent originalEvent = eventRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        String originalEvidenceUrl = originalEvent.getEvidenceUrl();
        Instant originalTimestamp = originalEvent.getActualPublicationTimestamp();

        // A: CEO reopens for Publishing - RFP (not PUBG, ERD/API-OP-052's own actual intent), no
        // REPOST event yet, ORIGINAL fully unchanged, and the ORIGINAL cycle's Publisher assignment
        // is no longer active (a repost cycle must never silently inherit it).
        HttpResponse<String> reopen = ceo.post("/api/v1/content-plans/" + contentPlanId + "/reopen-publishing",
                "{\"reason\":\"Reposting karni hai!\"}");
        assertThat(reopen.statusCode()).isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("RFP");
        assertThat(eventRepository.findByContentPlan(plan)).hasSize(1);
        ActualPublicationEvent stillOriginal = eventRepository.findByContentPlan(plan).get(0);
        assertThat(stillOriginal.getId()).isEqualTo(originalEvent.getId());
        assertThat(stillOriginal.getEvidenceUrl()).isEqualTo(originalEvidenceUrl);
        assertThat(stillOriginal.getActualPublicationTimestamp()).isEqualTo(originalTimestamp);
        assertThat(stillOriginal.getEventType()).isEqualTo(PublicationEventType.ORIGINAL);
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();

        // The ORIGINAL cycle's Publisher can no longer execute (no active assignment for THIS cycle)
        // until management explicitly reassigns - CEO/MM authority never bypasses this gate.
        HttpResponse<String> startBeforeReassignment =
                pubA.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");
        assertThat(startBeforeReassignment.statusCode()).isEqualTo(403);

        // B: CEO assigns a fresh Publisher for the repost cycle (deliberately a DIFFERENT person
        // than the ORIGINAL's Publisher, proving the choice is real, not a rubber stamp).
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubBId + "\"}");
        List<com.kcpc.mkt.publishing.domain.PublishingAssignment> active =
                publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getPublisher().getId().toString()).isEqualTo(pubBId);

        // C: Publisher B's Active Task correctly identifies this as a Repost cycle before even
        // starting - never a relabelled ORIGINAL.
        String taskBodyBeforeStart = pubB.get("/app/deliverables/" + contentPlanId).body();
        assertThat(taskBodyBeforeStart).contains("REPOST").contains("Start Repost").contains("Pending Repost");
        assertThat(pubB.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "").statusCode())
                .isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("PUBG");

        // D: Publisher B submits a genuinely NEW Evidence URL through the real MVC form endpoint
        // (no eventType field at all - PublishingService derives it), never the ORIGINAL's URL.
        String repostUrl1 = "https://instagram.com/p/reopen-repost1-" + unique;
        HttpResponse<String> repost1 = pubB.postForm("/app/deliverables/" + contentPlanId + "/publishing/events",
                Map.of("plannedOutputId", outputId, "publicationTargetId", TARGET_1,
                        "actualPublicationTimestamp", LocalDate.now().minusDays(3).toString(), "evidenceUrl", repostUrl1));
        assertThat(repost1.statusCode()).isEqualTo(302);

        List<ActualPublicationEvent> eventsAfterRepost1 = eventRepository.findByContentPlan(plan);
        assertThat(eventsAfterRepost1).hasSize(2);
        ActualPublicationEvent original = eventsAfterRepost1.stream()
                .filter(e -> e.getEventType() == PublicationEventType.ORIGINAL).findFirst().orElseThrow();
        ActualPublicationEvent repostEvent1 = eventsAfterRepost1.stream()
                .filter(e -> e.getEventType() == PublicationEventType.REPOST).findFirst().orElseThrow();
        assertThat(original.getEvidenceUrl()).isEqualTo(originalEvidenceUrl); // untouched
        assertThat(repostEvent1.getEvidenceUrl()).isEqualTo(repostUrl1);
        assertThat(repostEvent1.getId()).isNotEqualTo(original.getId());

        // E: single (output, target) pair -> the repost alone resolves this cycle's scope; the
        // workflow auto-advances exactly like first-time Publishing does (cycle-aware, not
        // satisfied by the OLD ORIGINAL that already existed before this cycle started).
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("PP");

        // F: the REPOST gets its OWN Performance obligation/scorecard, never reusing the ORIGINAL's
        // (already-submitted) one.
        PerformanceObligation originalObligation = obligationRepository.findByEvent(original).orElseThrow();
        PerformanceObligation repostObligation = obligationRepository.findByEvent(repostEvent1).orElseThrow();
        assertThat(repostObligation.getId()).isNotEqualTo(originalObligation.getId());
        assertThat(originalObligation.isCompleted()).isTrue();
        assertThat(repostObligation.isCompleted()).isFalse();

        // Drive the repost's own obligation to Completed exactly like driveToCompleted's tail does,
        // so a SECOND reopen (multiple repost cycles) can be exercised below.
        ceo.postJson("/api/v1/performance-obligations/" + repostObligation.getId() + "/scorecard/draft",
                "{\"views3sec\":900,\"plays\":1100,\"averageWatchTimeSeconds\":11.0,\"videoLengthSeconds\":18.0,"
                        + "\"linkClicks\":0,\"clicksIsNa\":true,\"impressions\":5200}");
        ceo.post("/api/v1/performance-obligations/" + repostObligation.getId() + "/scorecard/submit", "");
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("COMP");

        // G: reopen a SECOND time - fresh assignment required again (Publisher B's assignment from
        // the first repost cycle must not carry over either), CEO re-picks Publisher A this time
        // (any active Publisher is a valid choice, including the original ORIGINAL-cycle person).
        assertThat(ceo.post("/api/v1/content-plans/" + contentPlanId + "/reopen-publishing",
                "{\"reason\":\"One more repost - it's still trending!\"}").statusCode()).isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("RFP");
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubAId + "\"}");
        assertThat(pubA.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "").statusCode())
                .isEqualTo(200);
        String repostUrl2 = "https://instagram.com/p/reopen-repost2-" + unique;
        assertThat(pubA.postForm("/app/deliverables/" + contentPlanId + "/publishing/events",
                Map.of("plannedOutputId", outputId, "publicationTargetId", TARGET_1,
                        "actualPublicationTimestamp", LocalDate.now().minusDays(3).toString(), "evidenceUrl", repostUrl2))
                .statusCode()).isEqualTo(302);

        // H: full immutable history - ORIGINAL, REPOST, REPOST - three distinct rows, none overwritten.
        List<ActualPublicationEvent> finalEvents = eventRepository.findByContentPlan(plan);
        assertThat(finalEvents).hasSize(3);
        assertThat(finalEvents.stream().filter(e -> e.getEventType() == PublicationEventType.ORIGINAL).count()).isEqualTo(1);
        assertThat(finalEvents.stream().filter(e -> e.getEventType() == PublicationEventType.REPOST).count()).isEqualTo(2);
        assertThat(finalEvents.stream().map(ActualPublicationEvent::getEvidenceUrl))
                .containsExactlyInAnyOrder(originalEvidenceUrl, repostUrl1, repostUrl2);
        assertThat(eventRepository.findById(original.getId()).orElseThrow().getEvidenceUrl()).isEqualTo(originalEvidenceUrl);
    }

    /** Marketing Manager exercises the identical reopen-for-publishing gate, per existing permissions. */
    @Test
    void marketingManagerReopensForPublishingWithSameFreshAssignmentGate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        createUser(ceo, "Reopen MM", "e2e-reopen-mm-" + unique + "@kcpcbandhani.local", MARKETING_MANAGER_ROLE_ID);
        TestApiClient mm = loginNewClient("e2e-reopen-mm-" + unique + "@kcpcbandhani.local");

        String camEmail = "e2e-reopen-mm-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-reopen-mm-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-reopen-mm-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Reopen MM Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Reopen MM Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Reopen MM Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pub = loginNewClient(pubEmail);
        grantShootExecutionPermission(ceo, camId);
        grantEditExecutionPermission(ceo, edId);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = driveToCompleted(ceo, cam, ed, pub, "Reopen MM Flow " + unique, camId, edId, pubId);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("COMP");

        HttpResponse<String> reopen = mm.post("/api/v1/content-plans/" + contentPlanId + "/reopen-publishing",
                "{\"reason\":\"MM reposting karni hai!\"}");
        assertThat(reopen.statusCode()).isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("RFP");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();

        // MM (native authority, same as CEO) can assign a fresh Publisher exactly like CEO can.
        mm.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubId + "\"}");
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(1);
    }

    /**
     * Convenience shortcut: the Reopen for Publishing action's own form can optionally carry
     * Publisher(s) to assign in the same request, so CEO/MM don't need a second trip to the
     * Publishing tab for the common case. Selecting none must keep working exactly as before
     * (already covered by the other reopen tests above).
     */
    @Test
    void reopenForPublishingCanAssignPublisherInTheSameRequest() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-reopen-combo-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-reopen-combo-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-reopen-combo-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Reopen Combo Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Reopen Combo Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Reopen Combo Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = loginNewClient(camEmail);
        TestApiClient ed = loginNewClient(edEmail);
        TestApiClient pub = loginNewClient(pubEmail);
        grantShootExecutionPermission(ceo, camId);
        grantEditExecutionPermission(ceo, edId);
        grantPublishingPermission(ceo, pubId);

        String contentPlanId = driveToCompleted(ceo, cam, ed, pub, "Reopen Combo Flow " + unique, camId, edId, pubId);
        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("COMP");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();

        // One form submission: reason + the dropdown's chosen publisherUserId together.
        HttpResponse<String> reopenAndAssign = ceo.postForm("/app/deliverables/" + contentPlanId + "/reopen-publishing",
                Map.of("reason", "Reposting karni hai!", "publisherUserId", pubId));
        assertThat(reopenAndAssign.statusCode()).isEqualTo(302);

        assertThat(ceo.getJson("/api/v1/content-plans/" + contentPlanId).get("status").asText()).isEqualTo("RFP");
        List<com.kcpc.mkt.publishing.domain.PublishingAssignment> active =
                publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getPublisher().getId().toString()).isEqualTo(pubId);

        // The assigned Publisher can immediately Start Publishing - no second, separate assignment step needed.
        assertThat(pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "").statusCode())
                .isEqualTo(200);
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

    /** Candidate eligibility/execution is now permission-driven (OperationalEligibilityService). */
    private void grantShootExecutionPermission(TestApiClient ceo, String camerapersonUserId) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camerapersonUserId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e workflow-variants cameraperson grant\"}");
    }

    private void grantEditExecutionPermission(TestApiClient ceo, String editorUserId) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editorUserId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e workflow-variants editor grant\"}");
    }

    /** Workflow redesign: Idea Review approval always requires at least one Cameraperson - this
     * default overload creates a throwaway one (irrelevant to the caller's assertions) so plain
     * "just give me an approved plan" call sites don't need to care. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title) throws Exception {
        long unique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String camId = createUser(ceo, "Default Cam " + unique, "wve-default-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grantShootExecutionPermission(ceo, camId);
        return approveIdeaAndGetContentPlanId(ceo, title, camId);
    }

    /** Workflow redesign: Idea Review approval carries every former Planning field (including the
     * initial Shoot Team and initial Output/Publication Scope) in one call and transitions straight
     * to Shoot Assigned (SA), never PL/PLRV/PLAP - the given cameraperson must already hold an
     * active PERM_18_SHOOT_EXECUTION grant. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title, String camId) throws Exception {
        long unique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String pubId = createUser(ceo, "Default Pub " + unique, "wve-default-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        grantPublishingPermission(ceo, pubId);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/wve-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        return findContentPlanId(ideaId);
    }

    /** ENG-043: {@code cam}/{@code ed} must already be logged in as the actively assigned Cameraperson/Editor.
     * Workflow redesign: Editor/Publisher team assignment now folds directly into the Shoot/Edit
     * Review Approve calls themselves (see ShootingService#decideShootReview/EditingService#
     * decideEditReview) - {@code pubId} must already hold PERM_08, same as before this redesign. */
    private String advanceToReadyForPublishing(TestApiClient ceo, TestApiClient cam, TestApiClient ed, String title,
                                                String camId, String edId, String pubId) throws Exception {
        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, title, camId);
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
        return contentPlanId;
    }

    /** ENG-043: {@code pub} must already be logged in as the actively assigned Publisher, with PERM_08 granted. */
    private String driveToCompleted(TestApiClient ceo, TestApiClient cam, TestApiClient ed, TestApiClient pub,
                                     String title, String camId, String edId, String pubId) throws Exception {
        String contentPlanId = advanceToReadyForPublishing(ceo, cam, ed, title, camId, edId, pubId);
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
