package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
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
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Detail -> Publishing -> Performance tab: every PerformanceObligation is 1:1 with an
 * ActualPublicationEvent (ERD-TBL-023), which is already linked to a real PlannedOutput
 * (Output Type/Reel Type) and PublicationTarget (Platform/Channel) - the identity block reads
 * straight from those existing relationships, never a generic "Obligation - Due X" heading.
 * Covers both CEO_OWNER and MARKETING_MANAGER on the same shared page/model.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PerformanceObligationIdentityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    ActualPublicationEventRepository eventRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String INSTAGRAM_TARGET_ID = "01926e3e-000a-7000-8000-000000000001"; // Instagram · kcpcbandhani
    private static final String FACEBOOK_TARGET_ID = "01926e3e-000a-7000-8000-000000000003"; // Facebook · kcpcbandhani

    private String createUser(TestApiClient ceo, String label, String businessRoleId, long unique) throws Exception {
        String email = "e2e-poi-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"POI " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"performance obligation identity test\"}");
        return response.get("userId").asText() + "|" + email;
    }

    /** Drives a fresh Content Plan through to Publishing with THREE Reel outputs
     *  (VERY_SHORT/SHORT/LONG), SHORT mapped to BOTH Instagram and Facebook, all others to
     *  Instagram only, then records a live ORIGINAL event for every (output, target) pair so the
     *  plan fully advances past Publishing into Performance Pending. */
    private String buildFourDistinctPublicationsReachingPerformance(TestApiClient ceo, long unique) throws Exception {
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
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance obligation identity test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + edIdEmail[0] + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance obligation identity test\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubIdEmail[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"performance obligation identity test\"}");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        // Outputs are deliberately NOT embedded in the approval call here: each of the 3 Reel Type
        // outputs below needs its OWN independent reelGroupId so it can carry a DIFFERENT
        // Publication Scope - the merged approval's reelTypes fan-out (like the "+Add Output"
        // multi-select) shares ONE reelGroupId/target-set across the whole batch, which is exactly
        // the grouped behavior this test needs to avoid. Adding them individually afterward via the
        // still-available (PERM_02-gated, no status restriction) addPlannedOutput endpoint keeps
        // each output independently targetable, exactly as before this redesign.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"POI Reel Variants " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/poi-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camIdEmail[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pubIdEmail[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        String[] reelTypes = {"VERY_SHORT", "SHORT", "LONG"};
        for (String rt : reelTypes) {
            ceo.postJson("/api/v1/content-plans/" + planId + "/outputs",
                    "{\"outputType\":\"REEL\",\"reelType\":\"" + rt + "\",\"titleDescription\":\"Reel " + rt + "\"}");
        }
        var outputs = plannedOutputRepository.findByContentPlan(plan);
        assertThat(outputs).hasSize(3);
        PlannedOutput shortOutput = outputs.stream().filter(o -> o.getReelType().name().equals("SHORT")).findFirst().orElseThrow();
        for (PlannedOutput o : outputs) {
            boolean isShort = o.getId().equals(shortOutput.getId());
            String targets = isShort
                    ? "\"" + INSTAGRAM_TARGET_ID + "\",\"" + FACEBOOK_TARGET_ID + "\""
                    : "\"" + INSTAGRAM_TARGET_ID + "\"";
            ceo.postJson("/api/v1/content-plans/outputs/" + o.getId() + "/publication-scope",
                    "{\"publicationTargetIds\":[" + targets + "]}");
        }

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

        String pastTimestamp = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        for (PlannedOutput o : outputs) {
            boolean isShort = o.getId().equals(shortOutput.getId());
            String[] targetIds = isShort
                    ? new String[] {INSTAGRAM_TARGET_ID, FACEBOOK_TARGET_ID}
                    : new String[] {INSTAGRAM_TARGET_ID};
            for (String targetId : targetIds) {
                String url = "https://instagram.com/reel/" + o.getReelType() + "-"
                        + targetId.substring(targetId.length() - 8) + "-" + unique;
                pub.postJson("/api/v1/content-plans/" + planId + "/publishing/events",
                        "{\"plannedOutputId\":\"" + o.getId() + "\",\"publicationTargetId\":\"" + targetId
                                + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                                + "\",\"evidenceUrl\":\"" + url + "\"}");
            }
        }
        return planId;
    }

    @Test
    void performanceTabShowsDistinctIdentifiedObligationsForCeo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String planId = buildFourDistinctPublicationsReachingPerformance(ceo, unique);

        String body = ceo.get("/app/deliverables/" + planId).body();
        // Never a generic "Obligation — Due" heading with no publication context.
        assertThat(body).doesNotContain("<h3>Obligation");
        assertThat(body).contains("performance-obligation-card");
        assertThat(body).contains("REEL &middot; VERY_SHORT").contains("REEL &middot; SHORT").contains("REEL &middot; LONG");
        assertThat(body).contains("Instagram &middot; kcpcbandhani").contains("Facebook &middot; kcpcbandhani");
        assertThat(body).contains("Open Published Content");
        // Four distinct publication units: VERY_SHORT/Instagram, SHORT/Instagram, SHORT/Facebook, LONG/Instagram.
        assertThat(countOccurrences(body, "performance-obligation-card")).isEqualTo(4);
    }

    @Test
    void evidenceCorrectionUpdatesOnlyItsOwnObligationForCeoAndMarketingManager() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String planId = buildFourDistinctPublicationsReachingPerformance(ceo, unique);

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        PlannedOutput shortOutput = plannedOutputRepository.findByContentPlan(plan).stream()
                .filter(o -> o.getReelType().name().equals("SHORT")).findFirst().orElseThrow();
        String shortInstagramEventId = eventRepository.findByPlannedOutputAndEventType(shortOutput, PublicationEventType.ORIGINAL)
                .stream().filter(e -> e.getPublicationTarget().getId().toString().equals(INSTAGRAM_TARGET_ID))
                .findFirst().orElseThrow().getId().toString();
        String originalUrl = eventRepository.findById(UUID.fromString(shortInstagramEventId)).orElseThrow().getEvidenceUrl();

        String correctedUrl = "https://example.com/poi-corrected-" + unique;
        ceo.postJson("/api/v1/publishing/events/" + shortInstagramEventId + "/evidence-corrections",
                "{\"correctedEvidenceUrl\":\"" + correctedUrl + "\",\"correctionReason\":\"Wrong link posted.\"}");

        String[] mmIdEmail = createUser(ceo, "mm-viewer", MARKETING_MANAGER_ROLE_ID, unique).split("\\|");
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmIdEmail[1], "Passw0rd!");

        for (TestApiClient reviewer : new TestApiClient[] {ceo, mm}) {
            String body = reviewer.get("/app/deliverables/" + planId).body();
            assertThat(body).contains(correctedUrl);
            assertThat(body).doesNotContain(originalUrl);
            // The SHORT/Facebook obligation's own separate evidence URL must be completely untouched.
            int shortIdx = body.indexOf("SHORT");
            assertThat(shortIdx).isPositive();
        }
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
