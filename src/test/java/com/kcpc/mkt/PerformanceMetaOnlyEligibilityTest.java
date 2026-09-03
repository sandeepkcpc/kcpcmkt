package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.reporting.service.KpiDashboardService;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V26 (Convert Performance Tracking to Meta-Only Direct Metrics): regression coverage for the
 * platform-eligibility gate itself (PerformanceEligibilityService, applied at obligation creation,
 * the Performance tab query, completion, and overdue) - the scenarios not already covered by the
 * (now Meta-model-rewritten) PerformanceDraftAndCorrectionTest/CorrectionLedgerFlowTest/
 * PerformanceObligationIdentityTest/GoldenEndToEndFlowTest. Driven through the real HTTP path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PerformanceMetaOnlyEligibilityTest {

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
    UserRepository userRepository;
    @Autowired
    KpiDashboardService kpiDashboardService;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String INSTAGRAM_TARGET_ID = "01926e3e-000a-7000-8000-000000000001"; // Meta
    private static final String YOUTUBE_TARGET_ID = "01926e3e-000a-7000-8000-000000000002"; // non-Meta
    private static final String FACEBOOK_TARGET_ID = "01926e3e-000a-7000-8000-000000000003"; // Meta
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private String createUser(TestApiClient ceo, String label, String businessRoleId, long unique) throws Exception {
        String email = "e2e-meta-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Meta " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"performance meta-only eligibility test\"}");
        return response.get("userId").asText() + "|" + email;
    }

    /** Builds a single-POST-output Content Plan through the full Planning -&gt; Shoot -&gt;
     * Edit -&gt; Publishing workflow (every content type, including POST, passes through all
     * stages - matches GoldenEndToEndFlowTest's own governed flow), scoped to exactly
     * {@code targetIds}, and publishes an ORIGINAL event to each of those targets 3 days ago (past
     * the +2-day due date). */
    private String buildAndPublish(TestApiClient ceo, long unique, String label, String... targetIds) throws Exception {
        String[] camIdEmail = createUser(ceo, "cam-" + label, CAMERA_PERSON_ROLE_ID, unique).split("\\|");
        String[] edIdEmail = createUser(ceo, "ed-" + label, "01926e3e-0001-7000-8000-000000000005", unique).split("\\|");
        String[] pubIdEmail = createUser(ceo, "pub-" + label, PUBLISHER_ROLE_ID, unique).split("\\|");
        TestApiClient cam = new TestApiClient(port);
        cam.login(camIdEmail[1], "Passw0rd!");
        TestApiClient ed = new TestApiClient(port);
        ed.login(edIdEmail[1], "Passw0rd!");
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubIdEmail[1], "Passw0rd!");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camIdEmail[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance meta-only eligibility test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + edIdEmail[0] + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance meta-only eligibility test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubIdEmail[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance meta-only eligibility test\"}");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        String targetIdsJson = String.join(",", java.util.Arrays.stream(targetIds).map(t -> "\"" + t + "\"").toList());
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Meta Eligibility " + label + " " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/meta-" + label + "-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\",\"publicationTargetIds\":[" + targetIdsJson + "]}],"
                        + "\"camerapersonUserIds\":[\"" + camIdEmail[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pubIdEmail[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).get(0);
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camIdEmail[0] + "\"],"
                        + "\"editorUserIds\":[\"" + edIdEmail[0] + "\"],\"leadEditorUserId\":\"" + edIdEmail[0] + "\"}");
        ed.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edIdEmail[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pubIdEmail[0] + "\"]}");
        pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "");

        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        for (String targetId : targetIds) {
            pub.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                    "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + targetId
                            + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                            + "\",\"evidenceUrl\":\"https://example.com/meta-" + label + "-" + unique + "\"}");
        }
        return planId;
    }

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    // ------------------------------------------------------------------ platform eligibility

    @Test
    void youTubePublicationNeverCreatesAPerformanceObligation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        String planId = buildAndPublish(ceo(), unique, "yt-only", YOUTUBE_TARGET_ID);
        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        assertThat(obligations).isEmpty();
    }

    @Test
    void instagramAndFacebookEachCreateTheirOwnObligation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        String planId = buildAndPublish(ceo(), unique, "ig-fb", INSTAGRAM_TARGET_ID, FACEBOOK_TARGET_ID);
        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        assertThat(obligations).hasSize(2);
        List<String> platformNames = obligations.stream()
                .map(o -> o.getEvent().getPublicationTarget().getPlatform().getPlatformName()).sorted().toList();
        assertThat(platformNames).containsExactly("Facebook", "Instagram");
    }

    @Test
    void mixedPlatformContentOnlyShowsMetaObligationsInThePerformanceTab() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = buildAndPublish(ceo, unique, "mixed", INSTAGRAM_TARGET_ID, YOUTUBE_TARGET_ID, FACEBOOK_TARGET_ID);

        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        assertThat(obligations).as("YouTube must never get an obligation row at all").hasSize(2);

        String body = ceo.get("/app/deliverables/" + planId + "?tab=performance").body();
        int cardCount = body.split("data-obligation-id=\"", -1).length - 1;
        assertThat(cardCount).as("Performance tab must show exactly the 2 Meta obligations, never a YouTube card").isEqualTo(2);
        // Scoped to the Performance tab panel specifically - other tabs (e.g. Publishing's own
        // platform/target pickers) legitimately list every active platform, including YouTube.
        int performanceTabStart = body.indexOf("data-tab-panel=\"performance\"");
        assertThat(performanceTabStart).isNotNegative();
        String performanceTabHtml = body.substring(performanceTabStart);
        assertThat(performanceTabHtml).doesNotContain("YouTube");
    }

    // ------------------------------------------------------------------ completion

    @Test
    void contentWithMetaAndNonMetaTargetsCompletesOnceOnlyTheMetaObligationIsSubmitted() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = buildAndPublish(ceo, unique, "completion", INSTAGRAM_TARGET_ID, YOUTUBE_TARGET_ID);

        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        assertThat(obligations).hasSize(1); // only the Instagram one exists at all
        String obligationId = obligations.get(0).getId().toString();

        assertThat(ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"hookRatePercent\":40.00,\"hookRateIsNa\":false,\"holdRatePercent\":20.00,\"holdRateIsNa\":false,"
                        + "\"views\":9000,\"averageViewDurationSeconds\":5.0,\"avgViewDurationIsNa\":false}")).isNotNull();
        ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");

        JsonNode finalPlan = ceo.getJson("/api/v1/content-plans/" + planId);
        // Completion must not be blocked waiting for a YouTube performance record that will never exist.
        assertThat(finalPlan.get("status").asText()).isEqualTo("COMP");
    }

    // ------------------------------------------------------------------ overdue

    @Test
    void nonMetaPublicationNeverBecomesPerformanceOverdue() throws Exception {
        long unique = Instant.now().toEpochMilli();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        long overdueBefore = kpiDashboardService.performance(
                userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow(),
                today.minusDays(30), today).getPerformanceOverdue();

        buildAndPublish(ceo(), unique, "yt-overdue", YOUTUBE_TARGET_ID);

        long overdueAfter = kpiDashboardService.performance(
                userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow(),
                today.minusDays(30), today).getPerformanceOverdue();
        // The YouTube event's due date (publish + 2 days) is already in the past and it is never
        // submitted, but since no obligation was ever created for it, it must not move this count.
        assertThat(overdueAfter).isEqualTo(overdueBefore);
    }

    // ------------------------------------------------------------------ cycle isolation

    @Test
    void repostMetaPublicationGetsItsOwnObligationAndDueDateIndependentOfTheOriginal() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = buildAndPublish(ceo, unique, "cycle", INSTAGRAM_TARGET_ID);

        List<PerformanceObligation> beforeRepost = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        assertThat(beforeRepost).hasSize(1);
        PerformanceObligation originalObligation = beforeRepost.get(0);

        // Complete the ORIGINAL cycle's obligation first - Reopen for Performance is only valid
        // from Completed (AdminActionService#reopenForPerformance), same as any other Reopen.
        ceo.postJson("/api/v1/performance-obligations/" + originalObligation.getId() + "/scorecard/draft",
                "{\"hookRatePercent\":25.00,\"hookRateIsNa\":false,\"holdRatePercent\":12.00,\"holdRateIsNa\":false,"
                        + "\"views\":6000,\"averageViewDurationSeconds\":2.5,\"avgViewDurationIsNa\":false}");
        ceo.postJson("/api/v1/performance-obligations/" + originalObligation.getId() + "/scorecard/submit", "");
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.COMP);

        // Reopen for Publishing (admin action, COMP -> RFP - AdminActionService#reopenForPublishing)
        // then record a REPOST to the same Instagram target - must get its own, second, independent
        // obligation (own due date), never satisfied by the ORIGINAL's already-submitted scorecard.
        assertThat(ceo.postForm("/app/deliverables/" + planId + "/reopen-publishing", Map.of("reason", "repost needed"))
                .statusCode()).isEqualTo(302);

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).get(0);
        String[] pubIdEmail = createUser(ceo, "pub-repost", PUBLISHER_ROLE_ID, unique).split("\\|");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubIdEmail[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance meta-only eligibility test\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubIdEmail[0] + "\"}");
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubIdEmail[1], "Passw0rd!");
        pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "");

        HttpResponse<String> repostResponse = pub.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + INSTAGRAM_TARGET_ID
                        + "\",\"eventType\":\"REPOST\",\"actualPublicationTimestamp\":\"" + Instant.now() + "\","
                        + "\"evidenceUrl\":\"https://example.com/repost-" + unique + "\"}");
        assertThat(repostResponse.statusCode()).isEqualTo(200);

        List<PerformanceObligation> afterRepost = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        assertThat(afterRepost).hasSize(2); // ORIGINAL's obligation preserved, REPOST gets its own
        PerformanceObligation repostObligation = afterRepost.stream()
                .filter(o -> !o.getId().equals(originalObligation.getId())).findFirst().orElseThrow();
        assertThat(repostObligation.getPerformanceDueDate()).isNotEqualTo(originalObligation.getPerformanceDueDate());
        assertThat(repostObligation.isCompleted()).isFalse(); // the ORIGINAL's submission never satisfies this one
    }

    // ------------------------------------------------------------------ authorization

    @Test
    void userWithoutPerformanceUpdateAuthorityCannotSubmitAScorecard() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = buildAndPublish(ceo, unique, "authz", INSTAGRAM_TARGET_ID);
        String obligationId = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId))
                .get(0).getId().toString();

        String[] outsiderIdEmail = createUser(ceo, "outsider", CAMERA_PERSON_ROLE_ID, unique).split("\\|");
        TestApiClient outsider = new TestApiClient(port);
        outsider.login(outsiderIdEmail[1], "Passw0rd!");

        HttpResponse<String> draftAttempt = outsider.post("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"hookRatePercent\":10.00,\"hookRateIsNa\":false,\"views\":100}");
        assertThat(draftAttempt.statusCode()).isEqualTo(403);
    }

    @Test
    void authorizedUserCanSubmitAScorecard() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String planId = buildAndPublish(ceo, unique, "authz-ok", INSTAGRAM_TARGET_ID);
        String obligationId = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId))
                .get(0).getId().toString();

        JsonNode draft = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"hookRatePercent\":33.00,\"hookRateIsNa\":false,\"holdRatePercent\":15.00,\"holdRateIsNa\":false,"
                        + "\"views\":7000,\"averageViewDurationSeconds\":3.2,\"avgViewDurationIsNa\":false}");
        assertThat(draft.get("hookRatePercent").asDouble()).isEqualTo(33.00);
        JsonNode submitted = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
        assertThat(submitted.get("submitted").asBoolean()).isTrue();
    }
}
