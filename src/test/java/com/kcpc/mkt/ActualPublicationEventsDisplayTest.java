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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Detail -> Publishing -> Actual Publication Events table (deliverable-detail.jsp):
 * 1. Multiple events for the SAME Platform x Channel target (one per Reel variation) must remain
 *    separately visible with their own correct Content Type - never deduplicated, never inferred
 *    from row order/timestamp (only from the event's own PlannedOutput#outputType/reelType).
 * 2. The Evidence column must show the CURRENT EFFECTIVE URL (latest correction if one exists),
 *    never the raw immutable original ActualPublicationEvent#evidenceUrl directly - this is the
 *    actual defect being fixed here (DeliverableMvcController previously never even queried
 *    PublicationEvidenceCorrectionRepository for this table).
 * Covers both CEO_OWNER and MARKETING_MANAGER, since both view this exact same page/fragment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActualPublicationEventsDisplayTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository eventRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001"; // Instagram · kcpcbandhani

    private String createUser(TestApiClient ceo, String label, String businessRoleId, long unique) throws Exception {
        String email = "e2e-apev-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"APEV " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"actual publication events display test\"}");
        return response.get("userId").asText() + "|" + email;
    }

    /** Drives a fresh Content Plan to Publishing, with THREE Reel outputs (VERY_SHORT/SHORT/LONG)
     *  all mapped to the same Instagram target and published as three separate ORIGINAL events. */
    private String[] buildThreeReelVariantsPublishedToSameTarget(TestApiClient ceo, long unique) throws Exception {
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
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"actual publication events display test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + edIdEmail[0] + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"actual publication events display test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubIdEmail[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"actual publication events display test\"}");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"APEV Reel Variants " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/apev-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camIdEmail[0] + "\"}");

        String[] outputIds = new String[3];
        String[] reelTypes = {"VERY_SHORT", "SHORT", "LONG"};
        for (int i = 0; i < reelTypes.length; i++) {
            ceo.postJson("/api/v1/content-plans/" + planId + "/outputs",
                    "{\"outputType\":\"REEL\",\"reelType\":\"" + reelTypes[i] + "\",\"titleDescription\":\"Reel " + reelTypes[i] + "\"}");
        }
        var outputs = plannedOutputRepository.findByContentPlan(plan);
        assertThat(outputs).hasSize(3);
        for (PlannedOutput o : outputs) {
            ceo.postJson("/api/v1/content-plans/outputs/" + o.getId() + "/publication-scope",
                    "{\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}");
        }

        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camIdEmail[0] + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + edIdEmail[0] + "\"}");
        ed.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edIdEmail[0] + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubIdEmail[0] + "\"}");
        pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "");

        String pastTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        for (int i = 0; i < outputs.size(); i++) {
            PlannedOutput o = outputs.get(i);
            String url = "https://instagram.com/reel/" + o.getReelType() + "-" + unique;
            pub.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                    "{\"plannedOutputId\":\"" + o.getId() + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                            + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                            + "\",\"evidenceUrl\":\"" + url + "\"}");
        }
        return new String[] {planId};
    }

    @Test
    void reelVariantsRemainSeparatelyVisibleWithCorrectContentTypeForCeo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String planId = buildThreeReelVariantsPublishedToSameTarget(ceo, unique)[0];

        String body = ceo.get("/app/deliverables/" + planId).body();
        assertThat(body).contains("Event Type").contains("Content Type");
        assertThat(body).contains("REEL &middot; VERY_SHORT");
        assertThat(body).contains("REEL &middot; SHORT");
        assertThat(body).contains("REEL &middot; LONG");
        // All three distinct rows for the SAME target must be present, not collapsed into one.
        assertThat(countOccurrences(body, "Instagram / kcpcbandhani")).isGreaterThanOrEqualTo(3);
    }

    @Test
    void evidenceCorrectionUpdatesContentDetailTableForCeoAndMarketingManager() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String planId = buildThreeReelVariantsPublishedToSameTarget(ceo, unique)[0];

        String originalUrl = "https://instagram.com/reel/VERY_SHORT-" + unique;
        String beforeBody = ceo.get("/app/deliverables/" + planId).body();
        assertThat(beforeBody).contains(originalUrl);

        // Same reliable lookup CorrectionLedgerFlowTest already uses, rather than scraping the
        // rendered HTML for the event id.
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        PlannedOutput veryShortOutput = plannedOutputRepository.findByContentPlan(plan).stream()
                .filter(o -> o.getReelType() == com.kcpc.mkt.planning.domain.ReelType.VERY_SHORT).findFirst().orElseThrow();
        String eventId = eventRepository.findByPlannedOutputAndEventType(veryShortOutput,
                        com.kcpc.mkt.publishing.domain.PublicationEventType.ORIGINAL)
                .stream().findFirst().orElseThrow().getId().toString();

        // --- First correction: original -> corrected-1. ---
        String correctedUrl1 = "https://example.com/apev-corrected-1-" + unique;
        JsonNode correction1 = ceo.postJson("/api/v1/publishing/events/" + eventId + "/evidence-corrections",
                "{\"correctedEvidenceUrl\":\"" + correctedUrl1 + "\",\"correctionReason\":\"Incorrect post URL entered.\"}");
        assertThat(correction1.get("priorEvidenceUrl").asText()).isEqualTo(originalUrl);

        String afterFirst = ceo.get("/app/deliverables/" + planId).body();
        assertThat(afterFirst).contains(correctedUrl1);
        assertThat(afterFirst).doesNotContain(originalUrl);

        // --- Second correction supersedes the first: corrected-1 -> corrected-2 (latest wins). ---
        String correctedUrl2 = "https://example.com/apev-corrected-2-" + unique;
        JsonNode correction2 = ceo.postJson("/api/v1/publishing/events/" + eventId + "/evidence-corrections",
                "{\"correctedEvidenceUrl\":\"" + correctedUrl2 + "\",\"correctionReason\":\"Real final URL.\"}");
        assertThat(correction2.get("priorEvidenceUrl").asText()).isEqualTo(correctedUrl1);
        assertThat(correction2.get("supersedesCorrectionId").asText()).isEqualTo(correction1.get("correctionId").asText());

        String afterSecond = ceo.get("/app/deliverables/" + planId).body();
        assertThat(afterSecond).contains(correctedUrl2);
        assertThat(afterSecond).doesNotContain(correctedUrl1);
        assertThat(afterSecond).doesNotContain(originalUrl);

        // Same page, same shared model attribute - Marketing Manager must see the identical
        // effective URL, not a role-specific reduced computation.
        String[] mmIdEmail = createUser(ceo, "mm-viewer", MARKETING_MANAGER_ROLE_ID, unique).split("\\|");
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmIdEmail[1], "Passw0rd!");
        String mmBody = mm.get("/app/deliverables/" + planId).body();
        assertThat(mmBody).contains(correctedUrl2);
        assertThat(mmBody).doesNotContain(correctedUrl1).doesNotContain(originalUrl);
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
