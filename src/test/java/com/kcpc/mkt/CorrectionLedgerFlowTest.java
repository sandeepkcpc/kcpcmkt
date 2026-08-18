package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.domain.PredefinedRoleMarks;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CORR-001: predefined Mark corrections (API-OP-033, ERD-TBL-026), publication evidence
 * corrections (API-OP-041, ERD-TBL-027), and performance metric corrections (API-OP-046,
 * ERD-TBL-028). Also proves DB-001's append-only enforcement (ERD-CON-058) at the Postgres level,
 * independent of the application layer, by attempting a raw JDBC UPDATE against a correction row.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CorrectionLedgerFlowTest {

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
    PredefinedRoleMarksRepository predefinedRoleMarksRepository;
    @Autowired
    ActualPublicationEventRepository eventRepository;
    @Autowired
    DataSource dataSource;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void predefinedMarkCorrectionAppendsLedgerAndUpdatesActiveValues() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Correction Marks Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");

        // First correction: 1.0/1.0 -> 2.0/0.5.
        JsonNode first = ceo.postJson("/api/v1/ideas/" + ideaId + "/predefined-marks/corrections",
                "{\"newCamerapersonMarks\":2.0,\"newEditorMarks\":0.5,"
                        + "\"correctionReason\":\"Complexity re-evaluated.\"}");
        assertThat(first.get("priorCamerapersonMark").asDouble()).isEqualTo(1.0);
        assertThat(first.get("priorEditorMark").asDouble()).isEqualTo(1.0);
        assertThat(first.get("newCamerapersonMark").asDouble()).isEqualTo(2.0);
        assertThat(first.get("newEditorMark").asDouble()).isEqualTo(0.5);
        assertThat(first.get("supersedesCorrectionId").isNull()).isTrue();

        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
        PredefinedRoleMarks activeMarks = predefinedRoleMarksRepository.findByContentPlan(plan).orElseThrow();
        assertThat(activeMarks.getPredefinedCameramanMark().doubleValue()).isEqualTo(2.0);
        assertThat(activeMarks.getPredefinedEditorMark().doubleValue()).isEqualTo(0.5);

        // Second correction chains onto the first: prior must equal the first correction's new values.
        JsonNode second = ceo.postJson("/api/v1/ideas/" + ideaId + "/predefined-marks/corrections",
                "{\"newCamerapersonMarks\":3.0,\"newEditorMarks\":1.0,"
                        + "\"correctionReason\":\"Further re-evaluation after review.\"}");
        assertThat(second.get("priorCamerapersonMark").asDouble()).isEqualTo(2.0);
        assertThat(second.get("priorEditorMark").asDouble()).isEqualTo(0.5);
        assertThat(second.get("supersedesCorrectionId").asText()).isEqualTo(first.get("correctionId").asText());

        // ERD-CON-029: corrected marks must be from the controlled list.
        HttpResponse<String> rejected = ceo.post("/api/v1/ideas/" + ideaId + "/predefined-marks/corrections",
                "{\"newCamerapersonMarks\":1.7,\"newEditorMarks\":1.0,\"correctionReason\":\"Invalid value.\"}");
        assertThat(rejected.statusCode()).isEqualTo(400);

        // Mandatory reason.
        HttpResponse<String> noReason = ceo.post("/api/v1/ideas/" + ideaId + "/predefined-marks/corrections",
                "{\"newCamerapersonMarks\":1.0,\"newEditorMarks\":1.0,\"correctionReason\":\"\"}");
        assertThat(noReason.statusCode()).isEqualTo(400);
    }

    @Test
    void evidenceAndMetricCorrectionsPreserveOriginalImmutableRecords() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camEmail = "corr-camera-" + unique + "@kcpcbandhani.local";
        String edEmail = "corr-editor-" + unique + "@kcpcbandhani.local";
        String pubEmail = "corr-publisher-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Corr Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "Corr Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "Corr Publisher", pubEmail, PUBLISHER_ROLE_ID);
        // ENG-043: Start/Submit-style execution acts now require the actor to be the actively
        // assigned Cameraperson/Editor/Publisher - CEO/MM native authority no longer bypasses this.
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"correction test publisher grant\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Correction Evidence/Metric Test " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        String contentPlanId = findContentPlanId(ideaId);

        String liveDate = LocalDate.now().plusDays(10).toString();
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + liveDate + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/corr-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = findPlannedOutputId(contentPlanId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments",
                "{\"editorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubId + "\"}");
        pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");

        String originalEvidenceUrl = "https://instagram.com/p/corr-original-" + unique;
        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        pub.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"" + originalEvidenceUrl + "\"}");

        String eventId = eventRepository.findByPlannedOutputAndEventType(
                        plannedOutputRepository.findById(UUID.fromString(outputId)).orElseThrow(),
                        PublicationEventType.ORIGINAL)
                .stream().findFirst().orElseThrow().getId().toString();

        // --- Evidence correction: original event row must remain untouched. ---
        String correctedUrl = "https://instagram.com/p/corr-fixed-" + unique;
        JsonNode evidenceCorrection = ceo.postJson("/api/v1/publishing/events/" + eventId + "/evidence-corrections",
                "{\"correctedEvidenceUrl\":\"" + correctedUrl + "\",\"correctionReason\":\"Wrong link posted.\"}");
        assertThat(evidenceCorrection.get("priorEvidenceUrl").asText()).isEqualTo(originalEvidenceUrl);
        assertThat(evidenceCorrection.get("correctedEvidenceUrl").asText()).isEqualTo(correctedUrl);

        String stillOriginal = eventRepository.findById(UUID.fromString(eventId)).orElseThrow().getEvidenceUrl();
        assertThat(stillOriginal).isEqualTo(originalEvidenceUrl);

        HttpResponse<String> blankReason = ceo.post("/api/v1/publishing/events/" + eventId + "/evidence-corrections",
                "{\"correctedEvidenceUrl\":\"" + correctedUrl + "\",\"correctionReason\":\"\"}");
        assertThat(blankReason.statusCode()).isEqualTo(400);

        // --- Metric correction: requires a submitted (sealed) scorecard. ---
        String obligationId = obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(contentPlanId))
                .stream().findFirst().orElseThrow().getId().toString();

        HttpResponse<String> beforeSubmit = ceo.post(
                "/api/v1/performance/scorecards/" + obligationId + "/corrections",
                "{\"correctedLinkClicks\":410,\"correctionReason\":\"Too early.\"}");
        // No draft/submitted scorecard resolves to scorecardId yet, so this must not silently succeed.
        assertThat(beforeSubmit.statusCode()).isIn(404, 409, 400);

        ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"views3sec\":800,\"plays\":1000,\"averageWatchTimeSeconds\":12.5,\"videoLengthSeconds\":20.0,"
                        + "\"linkClicks\":400,\"impressions\":5000}");
        JsonNode submitted = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
        String scorecardId = submitted.get("scorecardId").asText();

        HttpResponse<String> notYetSubmittedAttempt = ceo.post(
                "/api/v1/performance/scorecards/" + UUID.randomUUID() + "/corrections",
                "{\"correctedLinkClicks\":410,\"correctionReason\":\"Unknown scorecard.\"}");
        assertThat(notYetSubmittedAttempt.statusCode()).isEqualTo(404);

        JsonNode metricCorrection = ceo.postJson("/api/v1/performance/scorecards/" + scorecardId + "/corrections",
                "{\"correctedLinkClicks\":410,\"correctionReason\":\"Analytics reconciliation added delayed clicks.\"}");
        assertThat(metricCorrection.get("priorClicks").asInt()).isEqualTo(400);
        assertThat(metricCorrection.get("newClicks").asInt()).isEqualTo(410);
        assertThat(metricCorrection.get("priorPlays").isNull()).isTrue(); // untouched field stays null

        // Sealed scorecard's own stored link_clicks must remain the original submitted value (ERD-CON-060).
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer storedClicks = jdbc.queryForObject(
                "SELECT link_clicks FROM creative_performance_scorecards WHERE scorecard_id = ?::uuid",
                Integer.class, scorecardId);
        assertThat(storedClicks).isEqualTo(400);

        // --- ERD-CON-058: append-only enforcement at the DB level, independent of the app layer. ---
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE performance_metric_corrections SET mandatory_reason = 'tampered' WHERE correction_id = ?::uuid",
                metricCorrection.get("correctionId").asText()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM publication_evidence_corrections WHERE correction_id = ?::uuid",
                evidenceCorrection.get("correctionId").asText()))
                .hasMessageContaining("append-only");
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"correction test fixture\"}");
        return response.get("userId").asText();
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
}
