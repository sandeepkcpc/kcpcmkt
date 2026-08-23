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

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage for two Content Detail -&gt; Performance defects:
 * (1) Save Draft did not reappear on reload because the draft form never carried {@code value="..."}
 * attributes back from the already-persisted {@code CreativePerformanceScorecard} row (the row itself
 * was always saved and readable - {@code DeliverableMvcController} already loaded it into
 * {@code scorecardsByObligation} - deliverable-detail.jsp simply never read it back into the inputs).
 * (2) Correct a metric only ever exposed Corrected Link Clicks because
 * {@code DeliverableMvcController#correctMetric} hard-wired every other {@code PerformanceService
 * #correctMetrics} parameter to {@code null}, even though the service/entity/DTO already supported
 * every metric (API-OP-046).
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

    /** Builds a Content Plan with {@code reelTypes.length} REEL outputs, each its own obligation, all -> Instagram. */
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
                "{\"granteeUserId\":\"" + pubIdEmail[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance draft/correction test\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PDC Reel Variants " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review", "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/pdc-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camIdEmail[0] + "\"}");

        for (String rt : reelTypes) {
            ceo.postJson("/api/v1/content-plans/" + planId + "/outputs",
                    "{\"outputType\":\"REEL\",\"reelType\":\"" + rt + "\",\"titleDescription\":\"Reel " + rt + "\"}");
        }
        var outputs = plannedOutputRepository.findByContentPlan(plan);
        assertThat(outputs).hasSize(reelTypes.length);
        for (PlannedOutput o : outputs) {
            ceo.postJson("/api/v1/content-plans/outputs/" + o.getId() + "/publication-scope",
                    "{\"publicationTargetIds\":[\"" + INSTAGRAM_TARGET_ID + "\"]}");
        }

        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camIdEmail[0] + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + edIdEmail[0] + "\"}");
        ed.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edIdEmail[0] + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubIdEmail[0] + "\"}");
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
        Map<String, String> ob1Draft = Map.of("views3sec", "1200", "plays", "1500",
                "averageWatchTimeSeconds", "8.40", "videoLengthSeconds", "15.00", "linkClicks", "90", "impressions", "6000");
        HttpResponse<String> save1 = ceo.postForm(base + "/performance/" + ob1 + "/draft", ob1Draft);
        assertThat(save1.statusCode()).isEqualTo(302);

        String afterSave1 = ceo.get(base).body();
        String ob1Window = cardWindow(afterSave1, ob1);
        assertThat(ob1Window).contains("name=\"views3sec\" value=\"1200\"");
        assertThat(ob1Window).contains("name=\"plays\" value=\"1500\"");
        assertThat(ob1Window).contains("name=\"averageWatchTimeSeconds\" value=\"8.40\"");
        assertThat(ob1Window).contains("name=\"videoLengthSeconds\" value=\"15.00\"");
        assertThat(ob1Window).contains("name=\"linkClicks\" value=\"90\"");
        assertThat(ob1Window).contains("name=\"impressions\" value=\"6000\"");
        assertThat(ob1Window).contains("Draft saved");

        // C: obligation isolation - obligation 2's card must show none of obligation 1's values,
        // and must still be in the untouched "no draft" state.
        String ob2WindowBefore = cardWindow(afterSave1, ob2);
        assertThat(ob2WindowBefore).doesNotContain("value=\"1200\"").doesNotContain("value=\"1500\"")
                .doesNotContain("value=\"90\"");
        assertThat(ob2WindowBefore).doesNotContain("Draft saved");
        assertThat(ob2WindowBefore).contains("Save a draft first");

        // Save a different draft on obligation 2 and confirm obligation 1's own values are untouched.
        // Every value deliberately distinct from obligation 1's, including impressions - a
        // whole-page "doesNotContain" check below would otherwise (falsely) trip on a coincidental
        // shared number, e.g. impressions=1500 colliding with obligation 1's plays=1500.
        Map<String, String> ob2Draft = Map.of("views3sec", "400", "plays", "600",
                "averageWatchTimeSeconds", "3.10", "videoLengthSeconds", "9.00", "linkClicks", "20", "impressions", "1700");
        assertThat(ceo.postForm(base + "/performance/" + ob2 + "/draft", ob2Draft).statusCode()).isEqualTo(302);
        String afterSave2 = ceo.get(base).body();
        assertThat(cardWindow(afterSave2, ob1)).contains("name=\"plays\" value=\"1500\""); // unaffected by ob2's save
        assertThat(cardWindow(afterSave2, ob2)).contains("name=\"plays\" value=\"600\"")
                .doesNotContain("value=\"1500\"").doesNotContain("value=\"1200\"");

        // B: modify obligation 1's draft (a real prefilled form resubmits every field; only Plays changes).
        Map<String, String> ob1DraftUpdated = Map.of("views3sec", "1200", "plays", "1800",
                "averageWatchTimeSeconds", "8.40", "videoLengthSeconds", "15.00", "linkClicks", "90", "impressions", "6000");
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/draft", ob1DraftUpdated).statusCode()).isEqualTo(302);
        String afterUpdate = ceo.get(base).body();
        String ob1WindowUpdated = cardWindow(afterUpdate, ob1);
        assertThat(ob1WindowUpdated).contains("name=\"plays\" value=\"1800\"").doesNotContain("value=\"1500\"");
        assertThat(ob1WindowUpdated).contains("name=\"views3sec\" value=\"1200\""); // other fields preserved
        // obligation 2 must still show its own draft, unaffected by obligation 1's update.
        assertThat(cardWindow(afterUpdate, ob2)).contains("name=\"plays\" value=\"600\"");

        // E: final submit uses the correct obligation/draft - only obligation 1 becomes submitted.
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/submit", Map.of()).statusCode()).isEqualTo(302);
        String afterSubmit = ceo.get(base).body();
        String ob1Submitted = cardWindow(afterSubmit, ob1);
        assertThat(ob1Submitted).contains("Hook Rate:").contains("Correct a metric");
        assertThat(ob1Submitted).doesNotContain("Save Draft");
        String ob2StillDraft = cardWindow(afterSubmit, ob2);
        assertThat(ob2StillDraft).contains("name=\"plays\" value=\"600\"").contains("Submit Scorecard (final)");
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

        // Obligation 1: Link Clicks marked N/A on this scorecard (e.g. platform/output doesn't
        // support it) - must not be offered as a correctable metric for THIS scorecard.
        Map<String, String> ob1Draft = new java.util.HashMap<>(Map.of("views3sec", "50000", "plays", "15000",
                "averageWatchTimeSeconds", "8.40", "videoLengthSeconds", "15.00", "impressions", "20000"));
        ob1Draft.put("clicksIsNa", "true");
        // Drain the flash-message slot after every mutating POST (Spring's flash map is matched
        // FIFO against the next request to the same redirect target) so a later assertion never
        // reads a stale message queued by an earlier action in this same setup sequence.
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/draft", ob1Draft).statusCode()).isEqualTo(302);
        ceo.get(base);
        assertThat(ceo.postForm(base + "/performance/" + ob1 + "/submit", Map.of()).statusCode()).isEqualTo(302);
        ceo.get(base);

        // Obligation 2: a fully normal scorecard, submitted, to prove correction scoping.
        Map<String, String> ob2Draft = Map.of("views3sec", "9000", "plays", "12000",
                "averageWatchTimeSeconds", "5.00", "videoLengthSeconds", "10.00", "linkClicks", "300", "impressions", "8000");
        assertThat(ceo.postForm(base + "/performance/" + ob2 + "/draft", ob2Draft).statusCode()).isEqualTo(302);
        ceo.get(base);
        assertThat(ceo.postForm(base + "/performance/" + ob2 + "/submit", Map.of()).statusCode()).isEqualTo(302);
        ceo.get(base);

        String scorecardId1 = correctionScorecardId(planId, ob1);

        // A: dropdown reflects applicable metrics for THIS scorecard - Link Clicks excluded on ob1.
        String bodyBeforeCorrection = ceo.get(base).body();
        String ob1Card = cardWindow(bodyBeforeCorrection, ob1);
        assertThat(ob1Card).contains("<option value=\"views3sec\">3-sec Views</option>")
                .contains("<option value=\"plays\">Plays</option>")
                .contains("<option value=\"watchTime\">Avg Watch</option>")
                .contains("<option value=\"videoLength\">Video Length</option>")
                .contains("<option value=\"impressions\">Impressions</option>")
                .doesNotContain("<option value=\"linkClicks\">Link Clicks</option>");
        String ob2Card = cardWindow(bodyBeforeCorrection, ob2);
        assertThat(ob2Card).contains("<option value=\"linkClicks\">Link Clicks</option>");
        // Current Value shown for Plays before any correction is the raw submitted value.
        assertThat(ob1Card).contains("data-metric=\"plays\"");
        assertThat(ob1Card.substring(ob1Card.indexOf("data-metric=\"plays\"")))
                .contains("Current Value: 15000");

        // E: missing/blank reason fails cleanly (flash error, no Whitelabel, no correction persisted).
        int correctionsBefore = correctionRepository.findAll().size();
        HttpResponse<String> blankReason = ceo.postForm(base + "/performance/scorecards/" + scorecardId1 + "/corrections",
                Map.of("correctedPlays", "15500", "correctionReason", ""));
        assertThat(blankReason.statusCode()).isEqualTo(302);
        String afterBlankReason = ceo.get(base).body();
        assertThat(afterBlankReason).doesNotContain("Whitelabel Error");
        assertThat(afterBlankReason).contains("mandatory");
        assertThat(correctionRepository.findAll()).hasSize(correctionsBefore); // nothing persisted

        // B + C: correct Plays once - only Plays' effective value changes; the sealed scorecard's
        // own stored value is never mutated (ERD-CON-060).
        assertThat(ceo.postForm(base + "/performance/scorecards/" + scorecardId1 + "/corrections",
                Map.of("correctedPlays", "15500", "correctionReason", "Analytics updated")).statusCode()).isEqualTo(302);

        Integer rawPlaysAfterCorrection = jdbcPlays(scorecardId1);
        assertThat(rawPlaysAfterCorrection).isEqualTo(15000); // sealed row untouched

        String afterFirstCorrection = ceo.get(base).body();
        String ob1AfterFirst = cardWindow(afterFirstCorrection, ob1);
        assertThat(ob1AfterFirst.substring(ob1AfterFirst.indexOf("data-metric=\"plays\"")))
                .contains("Current Value: 15500");
        assertThat(ob1AfterFirst).contains("Plays: 15000 &rarr; 15500");
        // F: obligation 2's own effective Plays must be completely unaffected by obligation 1's correction.
        String ob2AfterFirst = cardWindow(afterFirstCorrection, ob2);
        assertThat(ob2AfterFirst.substring(ob2AfterFirst.indexOf("data-metric=\"plays\"")))
                .contains("Current Value: 12000");
        assertThat(ob2AfterFirst).doesNotContain("15500");

        // D: a second correction on the same metric - latest correction wins, but history keeps both.
        assertThat(ceo.postForm(base + "/performance/scorecards/" + scorecardId1 + "/corrections",
                Map.of("correctedPlays", "15850", "correctionReason", "Final analytics sync")).statusCode()).isEqualTo(302);
        String afterSecondCorrection = ceo.get(base).body();
        String ob1AfterSecond = cardWindow(afterSecondCorrection, ob1);
        assertThat(ob1AfterSecond.substring(ob1AfterSecond.indexOf("data-metric=\"plays\"")))
                .contains("Current Value: 15850");
        assertThat(ob1AfterSecond).contains("Plays: 15000 &rarr; 15500").contains("Plays: 15500 &rarr; 15850");
        // Correcting Plays must never touch Impressions/other metrics on the same scorecard.
        assertThat(ob1AfterSecond.substring(ob1AfterSecond.indexOf("data-metric=\"impressions\"")))
                .contains("Current Value: 20000");

        // G: Marketing Manager sees the identical corrected context (same JSP path, same permission gate).
        String[] mmIdEmail = createUser(ceo, "mm-viewer", MARKETING_MANAGER_ROLE_ID, unique).split("\\|");
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmIdEmail[1], "Passw0rd!");
        String mmBody = mm.get(base).body();
        String ob1Mm = cardWindow(mmBody, ob1);
        assertThat(ob1Mm.substring(ob1Mm.indexOf("data-metric=\"plays\"")))
                .contains("Current Value: 15850");
        assertThat(ob1Mm).contains("Plays: 15000 &rarr; 15500").contains("Plays: 15500 &rarr; 15850");

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

    private Integer jdbcPlays(String scorecardId) {
        return jdbcTemplate().queryForObject(
                "SELECT plays FROM creative_performance_scorecards WHERE scorecard_id = ?::uuid", Integer.class, scorecardId);
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplateBean;

    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate() {
        return jdbcTemplateBean;
    }
}
