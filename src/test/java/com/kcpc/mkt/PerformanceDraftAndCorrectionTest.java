package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.domain.PerformanceMetricCorrection;
import com.kcpc.mkt.performance.domain.PerformanceObligation;
import com.kcpc.mkt.performance.repository.PerformanceMetricCorrectionRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
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

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V26: Performance Draft/Correction coverage for the Meta-only direct-entry model (Hook Rate /
 * Hold Rate / Views / Average View Duration), driven through the real HTTP form path exactly like
 * a Publisher would use it - never fabricated/mocked. Originally written for the pre-V26 6-field
 * model; the workflow-building helper below is unchanged (getting a Content Plan to Performance
 * stage has nothing to do with which metric model is in effect), only the actual metric
 * assertions were rewritten for the new fields.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PerformanceDraftAndCorrectionTest {

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
    PerformanceMetricCorrectionRepository correctionRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String INSTAGRAM_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    private String createUser(TestApiClient ceo, String label, String businessRoleId, long unique) throws Exception {
        String email = "e2e-pdc-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"PDC " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"performance draft/correction test\"}");
        return response.get("userId").asText() + "|" + email;
    }

    /** Builds a Content Plan with {@code reelTypes.length} REEL outputs, each its own obligation, all -> Instagram
     * (Meta-eligible, so an obligation is created for each - V26). */
    private String buildPlanReachingPerformance(TestApiClient ceo, long unique, String... reelTypes) throws Exception {
        String[] camIdEmail = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique).split("\\|");
        String[] edIdEmail = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique).split("\\|");
        String[] pubIdEmail = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique).split("\\|");
        TestApiClient cam = new TestApiClient(port);
        cam.login(camIdEmail[1], "Passw0rd!");
        TestApiClient ed = new TestApiClient(port);
        ed.login(edIdEmail[1], "Passw0rd!");
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubIdEmail[1], "Passw0rd!");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camIdEmail[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance draft/correction test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + edIdEmail[0] + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance draft/correction test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubIdEmail[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance draft/correction test\"}");

        // Workflow redesign: Planning is folded into Idea Review, but (V31) the Idea Review
        // approval's own Planned Outputs grid has no Reel Type sub-selection any more - approval
        // here carries no outputs at all, and the REEL fan-out (one PlannedOutput per Reel Type,
        // all sharing one Publication Scope) is built afterwards via the Planning tab's own
        // "+ Add Output" (PlanningService#addPlannedOutputs, unchanged). Transitions straight to
        // Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PDC Reel Variants " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/pdc-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camIdEmail[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pubIdEmail[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();
        String base = "/app/deliverables/" + planId;

        assertThat(ceo.postFormMulti(base + "/outputs", java.util.Map.of(
                "outputType", java.util.List.of("REEL"),
                "reelTypes", java.util.Arrays.asList(reelTypes))).statusCode()).isEqualTo(302);
        var outputs = plannedOutputRepository.findByContentPlan(plan);
        assertThat(outputs).hasSize(reelTypes.length);
        UUID reelGroupId = outputs.get(0).getReelGroupId();
        assertThat(ceo.postForm(base + "/outputs/" + reelGroupId + "/targets",
                java.util.Map.of("publicationTargetIds", INSTAGRAM_TARGET_ID)).statusCode()).isEqualTo(302);
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

        // -3 days -> performance_due_date (= actual publication + 2 days) is already in the past,
        // which requireDueDateReached() demands before Save Draft/Submit/Correction are permitted.
        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        for (PlannedOutput o : outputs) {
            String url = "https://instagram.com/reel/" + o.getReelType() + "-" + unique;
            pub.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                    "{\"plannedOutputId\":\"" + o.getId() + "\",\"publicationTargetId\":\"" + INSTAGRAM_TARGET_ID
                            + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                            + "\",\"evidenceUrl\":\"" + url + "\"}");
        }
        return planId;
    }

    private String obligationIdFor(String planId, String reelType) {
        List<PerformanceObligation> obligations = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(planId));
        return obligations.stream()
                .filter(ob -> ob.getEvent().getPlannedOutput().getReelType().name().equals(reelType))
                .findFirst().orElseThrow().getId().toString();
    }

    /** Returns the substring of {@code body} for the one obligation card marked data-obligation-id="obligationId". */
    private String cardWindow(String body, String obligationId) {
        String marker = "data-obligation-id=\"" + obligationId + "\"";
        int idx = body.indexOf(marker);
        assertThat(idx).as("obligation %s card not found in page", obligationId).isNotNegative();
        int nextCard = body.indexOf("data-obligation-id=\"", idx + marker.length());
        return nextCard > 0 ? body.substring(idx, nextCard) : body.substring(idx);
    }

    @Test
    void draftPersistsOnReloadAndStaysIsolatedPerObligation() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String planId = buildPlanReachingPerformance(ceo, unique, "VERY_SHORT", "SHORT");
        String base = "/app/deliverables/" + planId;
        String ob1 = obligationIdFor(planId, "VERY_SHORT");
        String ob2 = obligationIdFor(planId, "SHORT");

        // D: no draft yet on either obligation is a valid initial state, not an error.
        String initialBody = ceo.get(base).body();
        assertThat(initialBody).doesNotContain("No draft scorecard exists yet for this obligation");
        assertThat(initialBody).doesNotContain("Whitelabel Error");
        assertThat(initialBody).contains("Save a draft first");

        // A: enter metrics on obligation 1 only, save draft, reload -> exact same values prefilled.
        Map<String, String> ob1Draft = Map.of("hookRatePercent", "42.50", "holdRatePercent", "28.75",
                "views", "125430", "averageViewDurationSeconds", "6.80");
        HttpResponse<String> save1 = ceo.postForm(base + "/performance/" + ob1 + "/draft", ob1Draft);
        assertThat(save1.statusCode()).isEqualTo(302);

        String afterSave1 = ceo.get(base).body();
        String ob1Window = cardWindow(afterSave1, ob1);
        assertThat(ob1Window).contains("name=\"hookRatePercent\" value=\"42.50\"");
        assertThat(ob1Window).contains("name=\"holdRatePercent\" value=\"28.75\"");
        assertThat(ob1Window).contains("name=\"views\" value=\"125430\"");
        assertThat(ob1Window).contains("name=\"averageViewDurationSeconds\" value=\"6.80\"");
        assertThat(ob1Window).contains("Draft saved");
        // The removed 6-field legacy model must never be requested in a new (post-V26) draft form.
        assertThat(ob1Window).doesNotContain("name=\"views3sec\"").doesNotContain("name=\"plays\"")
                .doesNotContain("name=\"averageWatchTimeSeconds\"").doesNotContain("name=\"videoLengthSeconds\"")
                .doesNotContain("name=\"linkClicks\"").doesNotContain("name=\"impressions\"");

        // C: obligation isolation - obligation 2's card must show none of obligation 1's values,
        // and must still be in the untouched "no draft" state.
        String ob2WindowBefore = cardWindow(afterSave1, ob2);
        assertThat(ob2WindowBefore).doesNotContain("value=\"42.50\"").doesNotContain("value=\"28.75\"")
                .doesNotContain("value=\"125430\"");
        assertThat(ob2WindowBefore).doesNotContain("Draft saved");
        assertThat(ob2WindowBefore).contains("Save a draft first");

        // Save a different draft on obligation 2 and confirm obligation 1's own values are untouched.
        // Every value deliberately distinct from obligation 1's - a whole-page "doesNotContain"
        // check below would otherwise (falsely) trip on a coincidental shared number.
        Map<String, String> ob2Draft = Map.of("hookRatePercent", "15.10", "holdRatePercent", "9.40",
                "views", "3200", "averageViewDurationSeconds", "2.10");
        assertThat(ceo.postForm(base + "/performance/" + ob2 + "/draft", ob2Draft).statusCode()).isEqualTo(302);
        String afterSave2 = ceo.get(base).body();
        assertThat(cardWindow(afterSave2, ob1)).contains("name=\"hookRatePercent\" value=\"42.50\""); // unaffected by ob2's save
        assertThat(cardWindow(afterSave2, ob2)).contains("name=\"hookRatePercent\" value=\"15.10\"")
                .doesNotContain("value=\"42.50\"");

        // B: modify obligation 1's draft (a real prefilled form resubmits every field; only Views changes).
        Map<String, String> ob1DraftUpdated = Map.of("hookRatePercent", "42.50", "holdRatePercent", "28.75",
                "views", "130000", "averageViewDurationSeconds", "6.80");
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/draft", ob1DraftUpdated).statusCode()).isEqualTo(302);
        String afterUpdate = ceo.get(base).body();
        String ob1WindowUpdated = cardWindow(afterUpdate, ob1);
        assertThat(ob1WindowUpdated).contains("name=\"views\" value=\"130000\"").doesNotContain("value=\"125430\"");
        assertThat(ob1WindowUpdated).contains("name=\"hookRatePercent\" value=\"42.50\""); // other fields preserved
        // obligation 2 must still show its own draft, unaffected by obligation 1's update.
        assertThat(cardWindow(afterUpdate, ob2)).contains("name=\"views\" value=\"3200\"");

        // E: final submit uses the correct obligation/draft - only obligation 1 becomes submitted.
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/submit", Map.of()).statusCode()).isEqualTo(302);
        String afterSubmit = ceo.get(base).body();
        String ob1Submitted = cardWindow(afterSubmit, ob1);
        assertThat(ob1Submitted).contains("Hook Rate:").contains("Correct a metric");
        assertThat(ob1Submitted).doesNotContain("Save Draft");
        String ob2StillDraft = cardWindow(afterSubmit, ob2);
        assertThat(ob2StillDraft).contains("name=\"views\" value=\"3200\"").contains("Submit Scorecard (final)");
    }

    @Test
    void metricCorrectionOffersApplicableMetricsAndLatestCorrectionWinsPerMetricPerScorecard() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String planId = buildPlanReachingPerformance(ceo, unique, "VERY_SHORT", "LONG");
        String base = "/app/deliverables/" + planId;
        String ob1 = obligationIdFor(planId, "VERY_SHORT");
        String ob2 = obligationIdFor(planId, "LONG");

        // Obligation 1: Hold Rate marked N/A on this scorecard (e.g. a static-image-style output) -
        // must not be offered as a correctable metric for THIS scorecard.
        Map<String, String> ob1Draft = new java.util.HashMap<>(Map.of("hookRatePercent", "50.00",
                "views", "80000", "averageViewDurationSeconds", "7.20"));
        ob1Draft.put("holdRateIsNa", "true");
        // Drain the flash-message slot after every mutating POST (Spring's flash map is matched
        // FIFO against the next request to the same redirect target) so a later assertion never
        // reads a stale message queued by an earlier action in this same setup sequence.
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/draft", ob1Draft).statusCode()).isEqualTo(302);
        ceo.get(base);
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/submit", Map.of()).statusCode()).isEqualTo(302);
        ceo.get(base);

        // Obligation 2: a fully normal scorecard, submitted, to prove correction scoping.
        Map<String, String> ob2Draft = Map.of("hookRatePercent", "30.00", "holdRatePercent", "18.00",
                "views", "40000", "averageViewDurationSeconds", "4.50");
        assertThat(ceo.postForm(base + "/performance/" + ob2 + "/draft", ob2Draft).statusCode()).isEqualTo(302);
        ceo.get(base);
        assertThat(ceo.postForm(base + "/performance/" + ob2 + "/submit", Map.of()).statusCode()).isEqualTo(302);
        ceo.get(base);

        String scorecardId1 = correctionScorecardId(planId, ob1);

        // A: dropdown reflects applicable metrics for THIS scorecard - Hold Rate excluded on ob1.
        String bodyBeforeCorrection = ceo.get(base).body();
        String ob1Card = cardWindow(bodyBeforeCorrection, ob1);
        assertThat(ob1Card).contains("<option value=\"hookRate\">Hook Rate</option>")
                .contains("<option value=\"views\">Views</option>")
                .contains("<option value=\"avgViewDuration\">Average View Duration</option>")
                .doesNotContain("<option value=\"holdRate\">Hold Rate</option>");
        String ob2Card = cardWindow(bodyBeforeCorrection, ob2);
        assertThat(ob2Card).contains("<option value=\"holdRate\">Hold Rate</option>");
        // Current Value shown for Views before any correction is the raw submitted value.
        assertThat(ob1Card).contains("data-metric=\"views\"");
        assertThat(ob1Card.substring(ob1Card.indexOf("data-metric=\"views\"")))
                .contains("Current Value: 80,000");

        // E: missing/blank reason fails cleanly (flash error, no Whitelabel, no correction persisted).
        int correctionsBefore = correctionRepository.findAll().size();
        HttpResponse<String> blankReason = ceo.postForm(base + "/performance/scorecards/" + scorecardId1 + "/corrections",
                Map.of("correctedViews", "82000", "correctionReason", ""));
        assertThat(blankReason.statusCode()).isEqualTo(302);
        String afterBlankReason = ceo.get(base).body();
        assertThat(afterBlankReason).doesNotContain("Whitelabel Error");
        assertThat(afterBlankReason).contains("mandatory");
        assertThat(correctionRepository.findAll()).hasSize(correctionsBefore); // nothing persisted

        // B + C: correct Views once - only Views' effective value changes; the sealed scorecard's
        // own stored value is never mutated (ERD-CON-060).
        assertThat(ceo.postForm(base + "/performance/scorecards/" + scorecardId1 + "/corrections",
                Map.of("correctedViews", "82000", "correctionReason", "Analytics updated")).statusCode()).isEqualTo(302);

        Long rawViewsAfterCorrection = jdbcViews(scorecardId1);
        assertThat(rawViewsAfterCorrection).isEqualTo(80000L); // sealed row untouched

        String afterFirstCorrection = ceo.get(base).body();
        String ob1AfterFirst = cardWindow(afterFirstCorrection, ob1);
        assertThat(ob1AfterFirst.substring(ob1AfterFirst.indexOf("data-metric=\"views\"")))
                .contains("Current Value: 82,000");
        assertThat(ob1AfterFirst).contains("Views: 80,000 &rarr; 82,000");
        // F: obligation 2's own effective Views must be completely unaffected by obligation 1's correction.
        String ob2AfterFirst = cardWindow(afterFirstCorrection, ob2);
        assertThat(ob2AfterFirst.substring(ob2AfterFirst.indexOf("data-metric=\"views\"")))
                .contains("Current Value: 40,000");
        assertThat(ob2AfterFirst).doesNotContain("82,000");

        // D: a second correction on the same metric - latest correction wins, but history keeps both.
        assertThat(ceo.postForm(base + "/performance/scorecards/" + scorecardId1 + "/corrections",
                Map.of("correctedViews", "85500", "correctionReason", "Final analytics sync")).statusCode()).isEqualTo(302);
        String afterSecondCorrection = ceo.get(base).body();
        String ob1AfterSecond = cardWindow(afterSecondCorrection, ob1);
        assertThat(ob1AfterSecond.substring(ob1AfterSecond.indexOf("data-metric=\"views\"")))
                .contains("Current Value: 85,500");
        assertThat(ob1AfterSecond).contains("Views: 80,000 &rarr; 82,000").contains("Views: 82,000 &rarr; 85,500");
        // Correcting Views must never touch Hook Rate/other metrics on the same scorecard.
        assertThat(ob1AfterSecond.substring(ob1AfterSecond.indexOf("data-metric=\"hookRate\"")))
                .contains("Current Value: 50.00");

        // G: Marketing Manager sees the identical corrected context (same JSP path, same permission gate).
        String[] mmIdEmail = createUser(ceo, "mm-viewer", MARKETING_MANAGER_ROLE_ID, unique).split("\\|");
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmIdEmail[1], "Passw0rd!");
        String mmBody = mm.get(base).body();
        String ob1Mm = cardWindow(mmBody, ob1);
        assertThat(ob1Mm.substring(ob1Mm.indexOf("data-metric=\"views\"")))
                .contains("Current Value: 85,500");
        assertThat(ob1Mm).contains("Views: 80,000 &rarr; 82,000").contains("Views: 82,000 &rarr; 85,500");

        List<PerformanceMetricCorrection> corrections = correctionRepository.findAll().stream()
                .filter(c -> c.getScorecard().getId().toString().equals(scorecardId1)).toList();
        assertThat(corrections).hasSize(2);
    }

    private String correctionScorecardId(String planId, String obligationId) throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String body = ceo.get("/app/deliverables/" + planId).body();
        String window = cardWindow(body, obligationId);
        int idx = window.indexOf("/performance/scorecards/");
        assertThat(idx).isPositive();
        int start = idx + "/performance/scorecards/".length();
        int end = window.indexOf("/corrections", start);
        return window.substring(start, end);
    }

    private Long jdbcViews(String scorecardId) {
        return jdbcTemplate().queryForObject(
                "SELECT meta_views FROM creative_performance_scorecards WHERE scorecard_id = ?::uuid", Long.class, scorecardId);
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplateBean;

    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return jdbcTemplateBean;
    }
}
