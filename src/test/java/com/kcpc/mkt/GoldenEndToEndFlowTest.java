package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Build-prompt §40: the golden end-to-end flow, automated. Login -&gt; Submit Idea -&gt; Approve
 * Idea (Content ID + Content Plan + Marks allocated atomically) -&gt; Planning -&gt; Planning Review
 * -&gt; Shoot assignment -&gt; Shoot -&gt; Shoot Review -&gt; Editor assignment -&gt; Edit -&gt; Edit Review -&gt;
 * Publishing -&gt; Actual Publication -&gt; Performance Due -&gt; Scorecard Draft -&gt; Scorecard Submit -&gt;
 * Completed.
 *
 * <p>Behaviour under test flows entirely through real HTTP against the governed API surface;
 * repositories are autowired only to resolve fixture IDs the API intentionally does not expose
 * as foreign keys in its response DTOs (e.g. a Content Plan's generated Planned Output ID),
 * exactly as {@code psql} lookups played the same role in the manual smoke scripts this test
 * automates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GoldenEndToEndFlowTest {

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

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void ideaToCompletedGoldenPath() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camEmail = "e2e-camera-" + unique + "@kcpcbandhani.local";
        String edEmail = "e2e-editor-" + unique + "@kcpcbandhani.local";
        String pubEmail = "e2e-publisher-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "E2E Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "E2E Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "E2E Publisher", pubEmail, PUBLISHER_ROLE_ID);
        // ENG-043: Start/Submit-style execution acts now require the actor to be the actively
        // assigned Cameraperson/Editor/Publisher - CEO/MM native authority no longer bypasses this.
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        // Business Role alone grants nothing - candidate eligibility/execution is permission-driven
        // (PERM_18/19/08, OperationalEligibilityService), same as every other Operational
        // Permission in this app's model - each explicit admin grant is required here.
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e golden path cameraperson grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + edId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e golden path editor grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e golden path publisher grant\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"E2E Golden Path " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        assertThat(approved.get("status").asText()).isEqualTo("PL");

        String contentPlanId = findContentPlanId(ideaId);
        assertThat(contentPlanId).isNotNull();

        String liveDate = LocalDate.now().plusDays(10).toString();
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + liveDate + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/e2e-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");

        String outputId = findPlannedOutputId(contentPlanId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}");

        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        JsonNode planApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision",
                "{\"approve\":true}");
        assertThat(planApproved.get("status").asText()).isEqualTo("SA");

        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"]}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("SAP");

        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments",
                "{\"editorUserId\":\"" + edId + "\"}");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubId + "\"}");
        pub.post("/api/v1/content-plans/" + contentPlanId + "/publishing/start", "");
        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        pub.postJson("/api/v1/content-plans/" + contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/e2e-" + unique + "\"}");

        String obligationId = findObligationId(contentPlanId);
        assertThat(obligationId).isNotNull();

        // V26: direct-entry Meta model - values are stored as-is, never derived (unlike the old
        // views3sec/plays -> hookRatePercent computation this test originally exercised).
        JsonNode draft = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"hookRatePercent\":80.00,\"hookRateIsNa\":false,\"holdRateIsNa\":true,"
                        + "\"views\":5000,\"avgViewDurationIsNa\":true}");
        assertThat(draft.get("hookRatePercent").asDouble()).isEqualTo(80.00);
        assertThat(draft.get("holdRateIsNa").asBoolean()).isTrue(); // SC-REQ-001-style: N/A, not 0

        JsonNode submitted = ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
        assertThat(submitted.get("submitted").asBoolean()).isTrue();

        JsonNode finalPlan = ceo.getJson("/api/v1/content-plans/" + contentPlanId);
        assertThat(finalPlan.get("status").asText()).isEqualTo("COMP");
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
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

    private String findObligationId(String contentPlanIdText) {
        return obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(contentPlanIdText)).stream()
                .findFirst().map(o -> o.getId().toString()).orElseThrow();
    }
}
