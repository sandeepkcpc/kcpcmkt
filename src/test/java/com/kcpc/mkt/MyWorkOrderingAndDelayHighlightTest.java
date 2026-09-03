package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
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
 * My Work stage tabs (Shoot/Edit/Publishing - the "All" tab has since been removed entirely, see
 * {@link MyWorkRoleBasedNavigationTest}): Planned Date ascending sort + a light red/pink highlight
 * on an already-delayed task's Planned Date cell. Both reuse existing data unchanged - {@code
 * ActiveWorkItem.plannedDate}/{@code delayed} - no new delay calculation and no change to which
 * rows appear (that's already covered by {@link MyWorkVisibilityTest}); this file only proves
 * ordering and cell styling.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyWorkOrderingAndDelayHighlightTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    @Test
    void shootActiveTasksAreSortedByShootDateAscending() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser cam = createUser(ceo, "MyWork Sort Cam " + unique, CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam.id + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        // Created out of order: the LATER shoot date first, the EARLIER shoot date second - if
        // rendering still used raw creation/insertion order, "Later" would appear before "Earlier".
        LocalDate later = LocalDate.now().plusDays(12);
        LocalDate earlier = LocalDate.now().plusDays(4);
        ContentPlan planLater = approveShootOnly(ceo, "Sort Shoot Later " + unique, cam.id, later.plusDays(3), later, later.plusDays(1));
        ContentPlan planEarlier = approveShootOnly(ceo, "Sort Shoot Earlier " + unique, cam.id, earlier.plusDays(3), earlier, earlier.plusDays(1));

        TestApiClient camClient = loginAs(cam);
        String body = camClient.get("/app/my-work").body();
        String shootRegion = tableRegion(body, "Active Shoot Tasks", "Need help or have questions?");

        int earlierIndex = shootRegion.indexOf(planEarlier.getContentId());
        int laterIndex = shootRegion.indexOf(planLater.getContentId());
        assertThat(earlierIndex).isPositive();
        assertThat(laterIndex).isPositive();
        assertThat(earlierIndex).as("earlier Shoot Date must render before the later one").isLessThan(laterIndex);
    }

    @Test
    void editActiveTasksAreSortedByEditDateAscending() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser cam = createUser(ceo, "MyWork Sort Ed Cam " + unique, CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam.id + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");
        TestUser editor = createUser(ceo, "MyWork Sort Editor " + unique, VIDEO_EDITOR_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + editor.id + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        LocalDate later = LocalDate.now().plusDays(14);
        LocalDate earlier = LocalDate.now().plusDays(6);
        ContentPlan planLater = advanceToEdit(ceo, cam, editor, "Sort Edit Later " + unique, later.plusDays(3), later.minusDays(2), later);
        ContentPlan planEarlier = advanceToEdit(ceo, cam, editor, "Sort Edit Earlier " + unique, earlier.plusDays(3), earlier.minusDays(2), earlier);

        TestApiClient editorClient = loginAs(editor);
        String body = editorClient.get("/app/my-work").body();
        String editRegion = tableRegion(body, "Active Edit Tasks", "Need help or have questions?");

        int earlierIndex = editRegion.indexOf(planEarlier.getContentId());
        int laterIndex = editRegion.indexOf(planLater.getContentId());
        assertThat(earlierIndex).isPositive();
        assertThat(laterIndex).isPositive();
        assertThat(earlierIndex).as("earlier Edit Date must render before the later one").isLessThan(laterIndex);
    }

    @Test
    void publishActiveTasksAreSortedByPlannedLiveDateAscending() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Sort Pub " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        LocalDate later = LocalDate.now().plusDays(16);
        LocalDate earlier = LocalDate.now().plusDays(5);
        ContentPlan planLater = approvePublishingOnly(ceo, "Sort Publish Later " + unique, publisher.id, later);
        ContentPlan planEarlier = approvePublishingOnly(ceo, "Sort Publish Earlier " + unique, publisher.id, earlier);

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");

        int earlierIndex = publishRegion.indexOf(planEarlier.getContentId());
        int laterIndex = publishRegion.indexOf(planLater.getContentId());
        assertThat(earlierIndex).isPositive();
        assertThat(laterIndex).isPositive();
        assertThat(earlierIndex).as("earlier Planned Live Date must render before the later one").isLessThan(laterIndex);
    }

    @Test
    void delayedShootDateCellGetsHighlightClassAndNonDelayedDoesNot() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser cam = createUser(ceo, "MyWork Delay Cam " + unique, CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam.id + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        LocalDate future = LocalDate.now().plusDays(7);
        ContentPlan onTime = approveShootOnly(ceo, "Delay Shoot OnTime " + unique, cam.id, future.plusDays(3), future, future.plusDays(1));
        ContentPlan pastDue = approveShootOnly(ceo, "Delay Shoot PastDue " + unique, cam.id, future.plusDays(3), future, future.plusDays(1));
        // Backdate directly on the entity (bypassing the API's future-date validation, which only
        // guards creation) - the same established pattern TeamWorkloadModelAndContentIdDrillDownTest
        // uses to simulate an already-delayed task without waiting on real time.
        ContentPlan reloaded = contentPlanRepository.findById(pastDue.getId()).orElseThrow();
        reloaded.applyReschedule(LocalDate.now().minusDays(3), reloaded.getPlannedEditDate(), reloaded.getPlannedLiveDate());
        contentPlanRepository.save(reloaded);

        TestApiClient camClient = loginAs(cam);
        String body = camClient.get("/app/my-work").body();
        String shootRegion = tableRegion(body, "Active Shoot Tasks", "Need help or have questions?");

        assertThat(shootRegion).contains(pastDue.getContentId()).contains(onTime.getContentId());
        assertThat(dateCellClass(shootRegion, pastDue.getContentId())).contains("planned-date-delayed");
        assertThat(dateCellClass(shootRegion, onTime.getContentId())).doesNotContain("planned-date-delayed");
    }

    /**
     * Publishing delay correction: {@code plannedLiveDate} strictly before today (Asia/Kolkata
     * business date) must now mark the task delayed - was previously hardcoded to never happen.
     * The example from the spec is used directly: today-2 must be delayed with delayDays=2. The
     * externally observable effects of delayDays are (a) the Planned Live Date cell's highlight
     * class, (b) the Delayed summary card count, and (c) - added in a later follow-up - the
     * Status column's "Delayed - N days" pill, matching Shoot/Edit (see
     * {@link #delayedPublishingTaskShowsDelayedStatusPillWithCorrectDayCount}).
     */
    @Test
    void publishingTaskWithPastPlannedLiveDateIsDelayedAndCellGetsHighlightClass() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub Delay " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        ContentPlan pastDue = approvePublishingOnly(ceo, "Pub Delay PastDue " + unique, publisher.id, LocalDate.now().plusDays(5));
        backdatePlannedLiveDate(pastDue.getId(), LocalDate.now().minusDays(2));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");

        assertThat(publishRegion).contains(pastDue.getContentId());
        assertThat(dateCellClass(publishRegion, pastDue.getContentId())).contains("planned-date-delayed");
    }

    /** plannedLiveDate == today and plannedLiveDate in the future must both stay NOT delayed. */
    @Test
    void publishingTaskWithTodayOrFuturePlannedLiveDateIsNotDelayedAndCellHasNoHighlightClass() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub NotDelayed " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        ContentPlan today = approvePublishingOnly(ceo, "Pub NotDelayed Today " + unique, publisher.id, LocalDate.now().plusDays(5));
        backdatePlannedLiveDate(today.getId(), LocalDate.now());
        ContentPlan future = approvePublishingOnly(ceo, "Pub NotDelayed Future " + unique, publisher.id, LocalDate.now().plusDays(9));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");

        assertThat(publishRegion).contains(today.getContentId()).contains(future.getContentId());
        assertThat(dateCellClass(publishRegion, today.getContentId())).doesNotContain("planned-date-delayed");
        assertThat(dateCellClass(publishRegion, future.getContentId())).doesNotContain("planned-date-delayed");
    }

    /**
     * Publishing Status column follow-up: mirrors the same "Delayed - N days" pill Shoot/Edit
     * already show, driven by the SAME {@code item.delayed}/{@code item.delayDays} the Planned
     * Live Date cell and Delayed card already use - no new delay calculation.
     */
    @Test
    void delayedPublishingTaskShowsDelayedStatusPillWithCorrectDayCount() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub StatusPill " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        ContentPlan twoDaysLate = approvePublishingOnly(ceo, "Pub StatusPill TwoDays " + unique, publisher.id, LocalDate.now().plusDays(5));
        backdatePlannedLiveDate(twoDaysLate.getId(), LocalDate.now().minusDays(2));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");
        String row = rowFor(publishRegion, twoDaysLate.getContentId());

        assertThat(row).contains("class=\"status-pill status-delayed\"").contains("Delayed &middot; 2 days");
    }

    /** Singular "day" (not "days") for exactly 1 day late - same pluralization rule Shoot/Edit already use. */
    @Test
    void delayedPublishingTaskWithOneDayLateUsesSingularDayText() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub StatusPillOne " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        ContentPlan oneDayLate = approvePublishingOnly(ceo, "Pub StatusPill OneDay " + unique, publisher.id, LocalDate.now().plusDays(5));
        backdatePlannedLiveDate(oneDayLate.getId(), LocalDate.now().minusDays(1));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");
        String row = rowFor(publishRegion, oneDayLate.getContentId());

        assertThat(row).contains("Delayed &middot; 1 day");
        assertThat(row).doesNotContain("1 days");
    }

    /**
     * A non-delayed Publishing task (today or future Planned Live Date) must keep rendering its
     * ordinary status pill exactly as before - no "Delayed" text, no status-delayed class.
     */
    @Test
    void nonDelayedPublishingTaskKeepsExistingStatusPill() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub StatusNormal " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        ContentPlan notDelayed = approvePublishingOnly(ceo, "Pub StatusNormal " + unique, publisher.id, LocalDate.now().plusDays(6));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");
        String row = rowFor(publishRegion, notDelayed.getContentId());

        assertThat(row).doesNotContain("status-delayed").doesNotContain("Delayed");
        assertThat(row).contains("class=\"status-pill");
    }

    /**
     * The Publishing "Delayed" summary card must reconcile exactly with the table: its count is
     * the number of active Publishing tasks with {@code item.delayed == true}, no independent
     * definition.
     */
    @Test
    void publishingDelayedSummaryCardCountMatchesTableDelayedTasks() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub Card " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        ContentPlan delayed1 = approvePublishingOnly(ceo, "Pub Card Delayed1 " + unique, publisher.id, LocalDate.now().plusDays(5));
        backdatePlannedLiveDate(delayed1.getId(), LocalDate.now().minusDays(1));
        ContentPlan delayed2 = approvePublishingOnly(ceo, "Pub Card Delayed2 " + unique, publisher.id, LocalDate.now().plusDays(5));
        backdatePlannedLiveDate(delayed2.getId(), LocalDate.now().minusDays(4));
        ContentPlan notDelayed = approvePublishingOnly(ceo, "Pub Card NotDelayed " + unique, publisher.id, LocalDate.now().plusDays(6));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");

        assertThat(dateCellClass(publishRegion, delayed1.getContentId())).contains("planned-date-delayed");
        assertThat(dateCellClass(publishRegion, delayed2.getContentId())).contains("planned-date-delayed");
        assertThat(dateCellClass(publishRegion, notDelayed.getContentId())).doesNotContain("planned-date-delayed");
        assertThat(body).contains("<span class=\"kpi-card-title\">Delayed</span><span class=\"kpi-card-count\">2</span>");
    }

    /** Publishing sorting must remain unaffected by the delay fix - reruns the same ascending-order proof. */
    @Test
    void publishingSortingRemainsAscendingAfterDelayFix() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "MyWork Pub SortRegress " + unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");

        LocalDate later = LocalDate.now().plusDays(18);
        LocalDate earlier = LocalDate.now().plusDays(6);
        ContentPlan planLater = approvePublishingOnly(ceo, "Pub SortRegress Later " + unique, publisher.id, later);
        ContentPlan planEarlier = approvePublishingOnly(ceo, "Pub SortRegress Earlier " + unique, publisher.id, earlier);
        // Also include one delayed row, sorted first - proves delay highlighting and ascending
        // order compose correctly rather than one interfering with the other.
        ContentPlan planDelayed = approvePublishingOnly(ceo, "Pub SortRegress Delayed " + unique, publisher.id, later);
        backdatePlannedLiveDate(planDelayed.getId(), LocalDate.now().minusDays(1));

        TestApiClient publisherClient = loginAs(publisher);
        String body = publisherClient.get("/app/my-work").body();
        String publishRegion = tableRegion(body, "Active Publishing Tasks", "Need help or have questions?");

        int delayedIndex = publishRegion.indexOf(planDelayed.getContentId());
        int earlierIndex = publishRegion.indexOf(planEarlier.getContentId());
        int laterIndex = publishRegion.indexOf(planLater.getContentId());
        assertThat(delayedIndex).isPositive();
        assertThat(earlierIndex).isPositive();
        assertThat(laterIndex).isPositive();
        assertThat(delayedIndex).as("the delayed (earliest) row must still render first").isLessThan(earlierIndex);
        assertThat(earlierIndex).as("earlier Planned Live Date must render before the later one").isLessThan(laterIndex);
        assertThat(dateCellClass(publishRegion, planDelayed.getContentId())).contains("planned-date-delayed");
    }

    // ------------------------------------------------------------------------------------------

    private record TestUser(String id, String email, String password) {
    }

    private TestUser createUser(TestApiClient ceo, String fullName, String roleId) throws Exception {
        String email = "e2e-" + fullName.toLowerCase().replace(" ", "-") + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return new TestUser(user.get("userId").asText(), email, "Passw0rd!");
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email, user.password);
        return client;
    }

    private ContentPlan approveShootOnly(TestApiClient ceo, String title, String camId,
                                          LocalDate liveDate, LocalDate shootDate, LocalDate editDate) throws Exception {
        long publisherUnique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        TestUser publisher = createUser(ceo, "MyWork ShootOnly Pub " + publisherUnique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher.id + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"shootDate\":\"" + shootDate + "\",\"editDate\":\"" + editDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/mywork-sort-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisher.id + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
    }

    private ContentPlan advanceToEdit(TestApiClient ceo, TestUser cam, TestUser editor, String title,
                                       LocalDate liveDate, LocalDate shootDate, LocalDate editDate) throws Exception {
        ContentPlan plan = approveShootOnly(ceo, title, cam.id, liveDate, shootDate, editDate);
        String planId = plan.getId().toString();
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id + "\"],\"leadEditorUserId\":\"" + editor.id + "\"}");
        return contentPlanRepository.findById(plan.getId()).orElseThrow();
    }

    /**
     * Backdates only {@code plannedLiveDate} directly on the entity (bypassing the API's
     * future-date validation, which only guards creation) - same established pattern as the Shoot
     * delay test above. Shoot/Edit dates are preserved as-is (null for a Publishing-only plan), so
     * {@code validateChronology()} inside {@code applyReschedule} is never at risk of rejecting it.
     */
    private void backdatePlannedLiveDate(UUID planId, LocalDate newLiveDate) {
        ContentPlan reloaded = contentPlanRepository.findById(planId).orElseThrow();
        reloaded.applyReschedule(reloaded.getPlannedShootDate(), reloaded.getPlannedEditDate(), newLiveDate);
        contentPlanRepository.save(reloaded);
    }

    private ContentPlan approvePublishingOnly(TestApiClient ceo, String title, String publisherId, LocalDate liveDate) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"stages\":[\"PUBLISHING\"],"
                        + "\"folderLink\":\"https://drive.example.com/mywork-sort-pub-" + Instant.now().toEpochMilli() + "\","
                        + "\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
    }

    private String tableRegion(String body, String startMarker, String endMarker) {
        int start = body.indexOf(startMarker);
        int end = body.indexOf(endMarker, start);
        assertThat(start).as("start marker '%s' must be present", startMarker).isPositive();
        assertThat(end).as("end marker '%s' must be present after start", endMarker).isGreaterThan(start);
        return body.substring(start, end);
    }

    /**
     * Returns the class attribute value of the row's Planned Date {@code <td>} for the given
     * Content ID. Only the Planned Date cell ever carries a {@code class} attribute directly on
     * its own {@code <td>} in this table (Content ID/Stage/Priority cells only ever put a class
     * on a nested {@code <a>}/{@code <span>}), so the literal {@code "<td class=\""} is unique to
     * it within one row - it is simply absent when the cell has no highlight (a non-delayed row),
     * which callers assert on directly.
     */
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

    /** Returns the full {@code <tr>...</tr>} markup for the row containing the given Content ID. */
    private String rowFor(String region, String contentId) {
        int rowStart = region.lastIndexOf("<tr>", region.indexOf(contentId));
        int rowEnd = region.indexOf("</tr>", rowStart);
        assertThat(rowStart).isPositive();
        assertThat(rowEnd).isGreaterThan(rowStart);
        return region.substring(rowStart, rowEnd);
    }
}
