package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
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
 * ENG-097: Publisher My Work's Upcoming/Active/History classification. A {@code PublishingAssignment}
 * is the source of truth for "who is responsible" (never re-derived from workflow status/planned
 * date); WorkflowStatus alone decides which of the three buckets a row currently renders in:
 * <ul>
 * <li>Upcoming: assigned, plan still in Shoot/Edit (not yet {@code RFP}/{@code PUBG}, not closed out).</li>
 * <li>Active: assigned, plan currently {@code RFP}/{@code PUBG} (the pre-existing window, unchanged).</li>
 * <li>History: assigned, plan genuinely closed out ({@code COMP}/{@code CAN}/{@code RJ}/{@code RET}).</li>
 * </ul>
 * This is the fix for the pre-existing bug where an early-Planning-assigned Publisher (not yet at
 * RFP) was routed into "Completed Publishing Work" simply because it wasn't in the Active window -
 * {@link #publisherLifecycleMovesThroughUpcomingActiveHistoryCorrectly} asserts, at every stage of
 * one real plan's lifecycle, that the row is in EXACTLY one of the three tables, never more, never
 * the wrong one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PublisherUpcomingActiveHistoryTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PublishingAssignmentRepository publishingAssignmentRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private record TestUser(String id, String email, String password) {
    }

    private TestUser createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "puah-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"PUAH " + label + " " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"publisher upcoming/active/history test fixture\"}");
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

    /**
     * ENG-098: Upcoming Publisher work moved out of the Publishing tab's own sub-tabs entirely,
     * into the new top-level "Dashboard" tab - a single, non-duplicated home for it. The underlying
     * Upcoming/Active/History classification this whole file proves is unchanged; only the render
     * location moved, so this helper is repointed at the Dashboard panel's own boundaries instead
     * of the removed "publish-upcoming" sub-tab. End marker is the Publish panel's own start (the
     * "All" tab was removed, so Publish - gated by the same showPublishTab as Dashboard - is now
     * the next panel in the DOM for a Publisher-only fixture).
     */
    private String upcomingRegion(String body) {
        return tableRegion(body, "data-tab-panel=\"dashboard\"", "data-tab-panel=\"publish\"");
    }

    private String activeRegion(String body) {
        return tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");
    }

    /**
     * "History" no longer has a section on My Work at all - a genuinely closed-out Publisher
     * assignment now surfaces only on My Performance's own Task Performance table (same
     * {@code PublishingAssignment}/completion data, just relocated - see
     * {@link com.kcpc.mkt.web.mvc.LandingMvcController#myPerformance}). This fetches that page
     * directly rather than scraping My Work's body, since the content no longer lives there.
     *
     * <p>Scoped to the Task Performance table itself (not the whole page body): the header's
     * global "latest notifications" widget legitimately renders on every page, including this
     * one, and can mention this same Content ID via an unrelated notification (e.g. its original
     * assignment) regardless of whether the row is actually in this table.
     */
    private String myPerformanceBody(TestApiClient client) throws Exception {
        String body = client.get("/app/my-performance").body();
        return tableRegion(body, "Task Performance", "</table>");
    }

    /**
     * One real plan, followed through its entire lifecycle: Planning (SA) -> Shoot Approved (EA) ->
     * Edit Approved (RFP) -> Publishing completed (COMP). At every stage, the SAME Publisher's row
     * must be in exactly one of Upcoming/Active/History - covers acceptance Scenarios 1, 2, 3, 5.
     */
    @Test
    void publisherLifecycleMovesThroughUpcomingActiveHistoryCorrectly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");
        TestUser editor = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id, "PERM_19_EDIT_EXECUTION");
        TestUser publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PUAH Lifecycle " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(20) + "\","
                        + "\"folderLink\":\"https://drive.example.com/puah-lifecycle-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id + "\"],\"publisherUserIds\":[\"" + publisher.id + "\"],"
                        + "\"outputs\":[{\"outputType\":\"POST\",\"reelTypes\":[],\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}]}}");
        ContentPlan plan = planFor(ideaId);
        String planId = plan.getId().toString();

        TestApiClient publisherClient = loginAs(publisher);

        // --- Stage 1: SA (Planning only, Shoot not started) -> Upcoming, not Active. ---
        String bodyAtSa = publisherClient.get("/app/my-work").body();
        assertThat(upcomingRegion(bodyAtSa)).contains(plan.getContentId()).contains("SHOOT");
        assertThat(activeRegion(bodyAtSa)).doesNotContain(plan.getContentId());

        // --- Stage 2: EA (Shoot approved, Edit not started) -> still Upcoming (now Edit), not Active. ---
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id + "\"],\"leadEditorUserId\":\"" + editor.id + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        String bodyAtEa = publisherClient.get("/app/my-work").body();
        assertThat(upcomingRegion(bodyAtEa)).contains(plan.getContentId()).contains("EDIT");
        assertThat(activeRegion(bodyAtEa)).doesNotContain(plan.getContentId());

        // --- Stage 3: RFP (Edit approved, re-selecting the SAME Publisher) -> Active, not Upcoming,
        // and (being the Active window itself) not yet on My Performance either. ---
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher.id + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        // No duplicate: re-selecting Karan at Edit Review Approve must not create a second row.
        long activeAssignmentCount = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher.id))
                .count();
        assertThat(activeAssignmentCount).isEqualTo(1);

        String bodyAtRfp = publisherClient.get("/app/my-work").body();
        assertThat(activeRegion(bodyAtRfp)).contains(plan.getContentId());
        assertThat(upcomingRegion(bodyAtRfp)).doesNotContain(plan.getContentId());
        assertThat(myPerformanceBody(publisherClient)).doesNotContain(plan.getContentId());

        // --- Stage 4: COMP (genuinely completed) -> now on My Performance (My Work has no History
        // section at all any more - see MyWorkRoleBasedNavigationTest/this file's own class doc),
        // not Upcoming, not Active. ---
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        publisherClient.post("/api/v1/content-plans/" + planId + "/publishing/start", "");
        String pastTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString();
        publisherClient.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/puah-" + unique + "\"}");

        String bodyAtComp = publisherClient.get("/app/my-work").body();
        assertThat(activeRegion(bodyAtComp)).doesNotContain(plan.getContentId());
        assertThat(upcomingRegion(bodyAtComp)).doesNotContain(plan.getContentId());
        assertThat(myPerformanceBody(publisherClient)).contains(plan.getContentId());
    }

    @Test
    void upcomingTasksSortedByPlannedLiveDateAscending() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisher = createUser(ceo, "sortpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");
        TestUser camA = createUser(ceo, "sortcamA", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camA.id, "PERM_18_SHOOT_EXECUTION");
        TestUser camB = createUser(ceo, "sortcamB", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camB.id, "PERM_18_SHOOT_EXECUTION");

        // Full pipeline (shootStarts) - stays at SA (genuinely Upcoming, not yet Publishing) so
        // this test actually exercises the Upcoming table's own sort, not the Active one's.
        LocalDate later = LocalDate.now().plusDays(18);
        LocalDate earlier = LocalDate.now().plusDays(6);
        ContentPlan planLater = approveShootStartingWithPublisher(ceo, "Sort Upcoming Later " + unique, camA.id, publisher.id, later);
        ContentPlan planEarlier = approveShootStartingWithPublisher(ceo, "Sort Upcoming Earlier " + unique, camB.id, publisher.id, earlier);

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String region = upcomingRegion(body);

        int earlierIndex = region.indexOf(planEarlier.getContentId());
        int laterIndex = region.indexOf(planLater.getContentId());
        assertThat(earlierIndex).isPositive();
        assertThat(laterIndex).isPositive();
        assertThat(earlierIndex).as("earlier Planned Live Date must render before the later one").isLessThan(laterIndex);
    }

    /** Acceptance Scenario 4: Shoot AND Edit both skipped at Planning (Direct Publishing combo). */
    @Test
    void shootAndEditBothSkippedAtPlanningPublisherEntersActiveDirectlyNoDuplicate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisher = createUser(ceo, "directpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PUAH DirectPublishing " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/puah-direct-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisher.id + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");
        ContentPlan plan = planFor(ideaId);

        long activeCount = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher.id)).count();
        assertThat(activeCount).isEqualTo(1);

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        assertThat(activeRegion(body)).contains(plan.getContentId());
        assertThat(upcomingRegion(body)).doesNotContain(plan.getContentId());
        assertThat(myPerformanceBody(publisherClient)).doesNotContain(plan.getContentId());
    }

    /** Acceptance-adjacent: Shoot skipped at Planning, Edit proceeds normally - Publisher assignment survives, no duplicate. */
    @Test
    void shootSkippedAtPlanningEditContinuesNormallyPublisherSurvivesNoDuplicate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser editor = createUser(ceo, "shootskiped", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id, "PERM_19_EDIT_EXECUTION");
        TestUser publisher = createUser(ceo, "shootskippub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PUAH ShootSkip " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"editorMark\":0.5,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/puah-shootskip-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor.id + "\"],\"leadEditorUserId\":\"" + editor.id + "\","
                        + "\"publisherUserIds\":[\"" + publisher.id + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("EA");
        ContentPlan plan = planFor(ideaId);
        String planId = plan.getId().toString();

        // Assigned at Planning, Shoot never existed for this plan at all - already Upcoming.
        TestApiClient publisherClient = loginAs(publisher);
        assertThat(upcomingRegion(publisherClient.get("/app/my-work").body())).contains(plan.getContentId());

        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher.id + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        long activeCount = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher.id)).count();
        assertThat(activeCount).isEqualTo(1);
        assertThat(activeRegion(publisherClient.get("/app/my-work").body())).contains(plan.getContentId());
    }

    /**
     * CRITICAL scenario explicitly called out: Shoot proceeds normally, Edit is SKIPPED (not
     * chosen at Planning - a later, separate skip action once already at EA). Publisher assigned
     * at Planning must survive this skip untouched, with no duplicate and no broken transition.
     */
    @Test
    void shootNormalEditStageSkippedPublisherAssignmentSurvivesNoDuplicate() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "editskipcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");
        TestUser publisher = createUser(ceo, "editskippub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");
        grantPermission(ceo, cam.id, "PERM_20_SKIP_STAGE");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PUAH EditSkip " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/puah-editskip-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id + "\"],\"publisherUserIds\":[\"" + publisher.id + "\"]}}");
        ContentPlan plan = planFor(ideaId);
        String planId = plan.getId().toString();

        TestUser editor = createUser(ceo, "editskipdummy", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id, "PERM_19_EDIT_EXECUTION");
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id + "\"],\"leadEditorUserId\":\"" + editor.id + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        // Publisher already Upcoming (Stage: Edit) before the skip.
        TestApiClient publisherClient = loginAs(publisher);
        assertThat(upcomingRegion(publisherClient.get("/app/my-work").body())).contains(plan.getContentId());

        // Skip Edit entirely (a genuinely separate action from Planning's own stage combo choice) -
        // re-selecting the SAME already-assigned Publisher, as the endpoint mandates.
        java.util.Map<String, String> skipParams = new java.util.LinkedHashMap<>();
        skipParams.put("reason", "No editor available " + unique);
        skipParams.put("publisherUserIds", publisher.id);
        var skipResponse = ceo.postFormAjax("/app/deliverables/" + planId + "/editing/skip", skipParams);
        assertThat(skipResponse.statusCode()).isEqualTo(200);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("RFP");

        long activeCount = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getPublisher().getId().toString().equals(publisher.id)).count();
        assertThat(activeCount).isEqualTo(1);
        assertThat(activeRegion(publisherClient.get("/app/my-work").body())).contains(plan.getContentId());
    }

    @Test
    void multiplePublishersRemainSupportedAtPlanning() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisherA = createUser(ceo, "multi-a", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherA.id, "PERM_08_PUBLISHING_EXECUTION");
        TestUser publisherB = createUser(ceo, "multi-b", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherB.id, "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"PUAH MultiPub " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/puah-multipub-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisherA.id + "\",\"" + publisherB.id + "\"]}}");
        ContentPlan plan = planFor(ideaId);

        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(2);
        assertThat(activeRegion(loginAs(publisherA).get("/app/my-work").body())).contains(plan.getContentId());
        assertThat(activeRegion(loginAs(publisherB).get("/app/my-work").body())).contains(plan.getContentId());
    }

    private ContentPlan approvePublishingOnlyWithPublisher(TestApiClient ceo, String title, String publisherId, LocalDate liveDate) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/puah-sort-" + Instant.now().toEpochMilli() + "\","
                        + "\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        return planFor(ideaId);
    }

    /** Full pipeline (Shoot starts) - the plan stays at SA (genuinely Upcoming for Publishing). */
    private ContentPlan approveShootStartingWithPublisher(TestApiClient ceo, String title, String camId,
                                                            String publisherId, LocalDate liveDate) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/puah-sort-shoot-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        return planFor(ideaId);
    }
}
