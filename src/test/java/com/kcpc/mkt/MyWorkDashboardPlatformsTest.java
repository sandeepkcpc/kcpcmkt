package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
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
 * My Work -&gt; Dashboard -&gt; Upcoming Tasks: the "Target" column is now "Platforms", rendered with
 * the EXACT SAME icon+count chip UI/data as Content Pipeline and Content Detail (fragments/
 * pipeline-platform-chip.jspf, {@code PipelinePlatformSummary}), built by the shared
 * {@code PipelineDashboardService#buildPlatformSummariesForPlan} - never a second rendering or data
 * implementation (see {@link PipelinePlatformPopoverTest} for the reference behavior this reuses
 * verbatim). A Publisher assigned at Planning time (ENG-097, before the plan reaches Publishing)
 * lands the row in Upcoming Tasks while Shoot is still in progress - see
 * {@link PublisherUpcomingActiveHistoryTest} for that classification's own coverage; this file only
 * proves the Platforms column itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyWorkDashboardPlatformsTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_YOUTUBE_KCPC = "01926e3e-000a-7000-8000-000000000002";

    private record TestUser(String id, String email) {
    }

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private TestUser createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "mwdp-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MWDP " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"my work dashboard platforms test fixture\"}");
        return new TestUser(user.get("userId").asText(), email);
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my work dashboard platforms test fixture grant\"}");
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email(), "Passw0rd!");
        return client;
    }

    /**
     * The fixture user in every test below has exactly one Content Plan (one Upcoming Tasks row)
     * on the whole page, so the chip assertions below scope to the full body rather than isolating
     * one {@code <tr>} - each platform chip's own popover carries its own nested {@code <table>}/
     * {@code <tr>} elements, which would make a naive "row" substring cut (matching the first
     * {@code </tr>} after the row starts) stop inside the first chip's own popover instead of at
     * the row's real end.
     */

    @Test
    void upcomingTasksColumnIsNamedPlatformsNotTarget() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisherUser = createUser(ceo, "colhdr", PUBLISHER_ROLE_ID, unique);
        // showPublishTab (and with it the Dashboard tab/Upcoming Tasks table) requires either the
        // execution permission or real assignment data - grant the permission so the empty-state
        // table (with its header row) still renders for this assertion.
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestApiClient publisher = loginAs(publisherUser);
        String body = publisher.get("/app/my-work").body();

        // The Upcoming Tasks table header renders regardless of row count - no fixture Content Plan
        // needed for this assertion.
        int upcomingHeaderStart = body.indexOf("Upcoming Tasks");
        assertThat(upcomingHeaderStart).as(body).isPositive();
        int theadEnd = body.indexOf("</thead>", upcomingHeaderStart);
        assertThat(theadEnd).isGreaterThan(upcomingHeaderStart);
        String upcomingHeaderRow = body.substring(upcomingHeaderStart, theadEnd);
        assertThat(upcomingHeaderRow).contains("<th>Platforms</th>");
        assertThat(upcomingHeaderRow).doesNotContain(">Target<").doesNotContain(">Targets<");
    }

    @Test
    void upcomingTaskWithMultiplePlatformsShowsOneChipPerPlatformWithCorrectCountsMatchingPipeline() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "multi", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MWDP Multi " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Publisher assigned at Planning time (ENG-097) while Shoot is still in progress - lands on
        // SA, genuinely Upcoming (not yet RFP/PUBG), with two Publication Targets on one output.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mwdp-multi-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"publisherUserIds\":[\"" + publisherUser.id() + "\"],"
                        + "\"outputs\":[{\"outputType\":\"POST\",\"reelTypes\":[],\"publicationTargetIds\":[\""
                        + TARGET_INSTAGRAM_KCPC + "\",\"" + TARGET_YOUTUBE_KCPC + "\"]}]}}");
        ContentPlan plan = resolvePlan(ideaId);

        TestApiClient publisher = loginAs(publisherUser);
        String myWorkBody = publisher.get("/app/my-work").body();
        assertThat(myWorkBody).contains(plan.getContentId());

        // Two distinct chips, one per platform - never a merged/summed single chip, never plain
        // platform-name text as the chip's own visible label.
        assertThat(myWorkBody).contains("class=\"pipeline-platform-chip");
        assertThat(myWorkBody.split("pipeline-platform-chip ").length - 1).as("one chip per platform").isEqualTo(2);
        assertThat(myWorkBody).contains("<span class=\"pipeline-platform-count\">&times;1</span>");
        assertThat(myWorkBody).contains("aria-label=\"Instagram: 0 of 1 published\"");
        assertThat(myWorkBody).contains("aria-label=\"YouTube: 0 of 1 published\"");
        assertThat(myWorkBody).contains("data-popup-target=");

        // Content Pipeline (CEO view) for the exact same Content ID must show the exact same counts -
        // both screens reuse the identical PipelinePlatformSummary data, so they can never disagree.
        String pipelineBody = ceo.get("/app/pipeline?q=" + plan.getContentId() + "&size=50").body();
        assertThat(pipelineBody).contains("aria-label=\"Instagram: 0 of 1 published\"");
        assertThat(pipelineBody).contains("aria-label=\"YouTube: 0 of 1 published\"");
    }

    @Test
    void upcomingTaskWithSinglePlatformShowsExactlyOneChip() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "singlecam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "single", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MWDP Single " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mwdp-single-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"publisherUserIds\":[\"" + publisherUser.id() + "\"],"
                        + "\"outputs\":[{\"outputType\":\"POST\",\"reelTypes\":[],\"publicationTargetIds\":[\""
                        + TARGET_INSTAGRAM_KCPC + "\"]}]}}");
        ContentPlan plan = resolvePlan(ideaId);

        TestApiClient publisher = loginAs(publisherUser);
        String body = publisher.get("/app/my-work").body();
        assertThat(body).contains(plan.getContentId());

        assertThat(body.split("pipeline-platform-chip ").length - 1).as("exactly one chip").isEqualTo(1);
        assertThat(body).contains("aria-label=\"Instagram: 0 of 1 published\"");
        assertThat(body).doesNotContain("YouTube");
    }

    private ContentPlan resolvePlan(String ideaId) {
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }
}
