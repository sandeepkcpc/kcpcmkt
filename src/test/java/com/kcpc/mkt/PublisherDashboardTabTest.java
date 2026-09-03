package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
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
 * ENG-098: My Work -> Dashboard - a new, Publisher-only top-level tab (positioned before "All",
 * gated by the same {@code showPublishTab} condition) showing ONLY upcoming work. Pure UI addition
 * reusing 100% pre-existing data ({@code upcomingPublishWork}/{@code upcomingPublishingCount}/
 * {@code activePublishingCount}/{@code delayedPublishingCount}/{@code publishCompletedCount}, all
 * already computed by {@code LandingMvcController#myWork} for the Publishing tab's own Upcoming
 * sub-tab and KPI cards - see {@link PublisherUpcomingActiveHistoryTest}) - no new query, no new
 * delay calculation, no change to the existing "All"/"Publishing" tabs' own behavior. This file
 * only proves the NEW Dashboard tab itself renders correctly and reconciles with the pre-existing
 * data; the underlying Upcoming/Active/History classification, sorting, and duplicate-prevention
 * guarantees are already covered by {@link PublisherUpcomingActiveHistoryTest} and are not
 * re-proven here beyond a first-class Dashboard-specific check of each.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublisherDashboardTabTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PublishingAssignmentRepository publishingAssignmentRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private record TestUser(String id, String email, String password) {
    }

    private TestUser createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "pdt-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"PDT " + label + " " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"publisher dashboard tab test fixture\"}");
        return new TestUser(user.get("userId").asText(), email, "Passw0rd!");
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture grant\"}");
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email, user.password);
        return client;
    }

    private ContentPlan planFor(String ideaId) {
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    private String tableRegion(String body, String startMarker, String endMarker) {
        int start = body.indexOf(startMarker);
        int end = body.indexOf(endMarker, start);
        assertThat(start).as("start marker '%s' must be present", startMarker).isPositive();
        assertThat(end).as("end marker '%s' must be present after start", endMarker).isGreaterThan(start);
        return body.substring(start, end);
    }

    /** The whole Dashboard panel, from its own KPI cards through the end of its table - stops at the Publish panel's own start (the "All" tab was removed, so Publish - gated by the same showPublishTab as Dashboard - is now the next panel in the DOM for a Publisher-only fixture). */
    private String dashboardRegion(String body) {
        return tableRegion(body, "data-tab-panel=\"dashboard\"", "data-tab-panel=\"publish\"");
    }

    private String activeRegion(String body) {
        return tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");
    }

    /** Isolates one row's own Planned Date <td> class attribute, within an already-scoped region. */
    private String dateCellClass(String region, String contentId) {
        int rowStart = region.lastIndexOf("<tr>", region.indexOf(contentId));
        int rowEnd = region.indexOf("</tr>", rowStart);
        assertThat(rowStart).isPositive();
        assertThat(rowEnd).isGreaterThan(rowStart);
        String row = region.substring(rowStart, rowEnd);
        int tdClassIdx = row.indexOf("<td class=\"");
        if (tdClassIdx < 0) {
            return "";
        }
        int valueStart = tdClassIdx + "<td class=\"".length();
        int valueEnd = row.indexOf('"', valueStart);
        return row.substring(valueStart, valueEnd);
    }

    private ContentPlan approveShootStartingWithPublisher(TestApiClient ceo, String title, String camId,
                                                            String publisherId, LocalDate liveDate) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/pdt-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        return planFor(ideaId);
    }

    /**
     * Items 1-4: assigned at Planning -> Dashboard Upcoming, not Publishing Active; once the plan
     * reaches RFP, the SAME row leaves Dashboard Upcoming and appears in Publishing Active instead
     * - never both, never neither, and no duplicate assignment is created along the way.
     */
    @Test
    void publisherAssignedAtPlanningAppearsInDashboardThenMovesToActiveOnPublishingActivation() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");
        TestUser editor = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id, "PERM_19_EDIT_EXECUTION");
        TestUser publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveShootStartingWithPublisher(ceo, "PDT Dashboard " + unique, cam.id, publisher.id,
                LocalDate.now().plusDays(10));
        String planId = plan.getId().toString();

        TestApiClient publisherClient = loginAs(publisher);

        // 1/2: assigned at Planning (still SA) - Dashboard Upcoming, NOT Publishing Active.
        String bodyAtSa = publisherClient.get("/app/my-work").body();
        assertThat(dashboardRegion(bodyAtSa)).contains(plan.getContentId()).contains("SHOOT");
        assertThat(activeRegion(bodyAtSa)).doesNotContain(plan.getContentId());

        // Advance to RFP (Shoot approved, Edit approved with the SAME already-assigned Publisher re-selected).
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id + "\"],\"leadEditorUserId\":\"" + editor.id + "\"}");
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher.id + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        // 6: no duplicate assignment despite the Publisher being re-selected at Edit Review Approve.
        long activeAssignmentCount = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher.id)).count();
        assertThat(activeAssignmentCount).isEqualTo(1);

        // 3/4: the SAME row has left Dashboard Upcoming and is now in Publishing Active - never both.
        String bodyAtRfp = publisherClient.get("/app/my-work").body();
        assertThat(dashboardRegion(bodyAtRfp)).doesNotContain(plan.getContentId());
        assertThat(activeRegion(bodyAtRfp)).contains(plan.getContentId());
    }

    /** Item 5: Shoot + Edit both skipped at Planning (Direct Publishing) - Publisher becomes directly Active, never Upcoming. */
    @Test
    void shootAndEditBothSkippedPublisherIsDirectlyActiveNeverInDashboardUpcoming() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisher = createUser(ceo, "directpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PDT DirectPublishing " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/pdt-direct-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisher.id + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");
        ContentPlan plan = planFor(ideaId);

        String body = loginAs(publisher).get("/app/my-work").body();
        assertThat(activeRegion(body)).contains(plan.getContentId());
        assertThat(dashboardRegion(body)).doesNotContain(plan.getContentId());
    }

    /** Item 7: Dashboard Upcoming Tasks table is sorted by Planned Live Date ascending. */
    @Test
    void dashboardUpcomingTasksSortedByPlannedLiveDateAscending() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisher = createUser(ceo, "sortpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");
        TestUser camA = createUser(ceo, "sortcamA", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camA.id, "PERM_18_SHOOT_EXECUTION");
        TestUser camB = createUser(ceo, "sortcamB", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camB.id, "PERM_18_SHOOT_EXECUTION");

        LocalDate later = LocalDate.now().plusDays(18);
        LocalDate earlier = LocalDate.now().plusDays(6);
        ContentPlan planLater = approveShootStartingWithPublisher(ceo, "PDT Sort Later " + unique, camA.id, publisher.id, later);
        ContentPlan planEarlier = approveShootStartingWithPublisher(ceo, "PDT Sort Earlier " + unique, camB.id, publisher.id, earlier);

        String region = dashboardRegion(loginAs(publisher).get("/app/my-work").body());
        int earlierIndex = region.indexOf(planEarlier.getContentId());
        int laterIndex = region.indexOf(planLater.getContentId());
        assertThat(earlierIndex).isPositive();
        assertThat(laterIndex).isPositive();
        assertThat(earlierIndex).as("earlier Planned Live Date must render before the later one").isLessThan(laterIndex);
    }

    /**
     * Item 9: delay behavior is reused unchanged - a Dashboard Upcoming row past its own Planned
     * Live Date gets the SAME existing red/pink cell class as Active rows already do (no new/
     * different calculation), while the "Delayed" KPI card stays scoped to Active tasks only,
     * exactly as it already is on the Publishing tab itself (not expanded to also count Upcoming).
     */
    @Test
    void dashboardUpcomingDelayedCellReusesExistingHighlightAndDelayedKpiStaysActiveOnly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisher = createUser(ceo, "delaypub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");
        TestUser cam = createUser(ceo, "delaycam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");

        ContentPlan plan = approveShootStartingWithPublisher(ceo, "PDT Delay " + unique, cam.id, publisher.id,
                LocalDate.now().plusDays(5));
        // Backdate directly on the entity (bypassing the API's future-date validation, which only
        // guards creation) - the same established pattern used throughout this session's Publishing
        // delay tests.
        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        LocalDate pastLiveDate = LocalDate.now().minusDays(2);
        reloaded.applyReschedule(pastLiveDate, pastLiveDate, pastLiveDate);
        contentPlanRepository.save(reloaded);

        String body = loginAs(publisher).get("/app/my-work").body();
        String region = dashboardRegion(body);
        assertThat(region).contains(plan.getContentId());
        assertThat(dateCellClass(region, plan.getContentId())).contains("planned-date-delayed");

        // The "Delayed" KPI card on Dashboard is the SAME activePublishingCount-scoped
        // delayedPublishingCount already used on the Publishing tab - this Upcoming (not yet
        // Active) row must NOT be counted in it.
        assertThat(dashboardRegion(body)).contains("<span class=\"kpi-card-title\">Delayed</span><span class=\"kpi-card-count\">0</span>");
    }

    /** Dashboard tab is not shown at all for a role with no Publishing relevance (e.g. pure Cameraperson). */
    @Test
    void dashboardTabNotShownForNonPublisherRole() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "nopubcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");

        String body = loginAs(cam).get("/app/my-work").body();
        assertThat(body).doesNotContain("data-tab=\"dashboard\"");
    }
}
