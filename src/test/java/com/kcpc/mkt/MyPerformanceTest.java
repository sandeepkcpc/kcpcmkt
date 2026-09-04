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

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "My Performance" (/app/my-performance) - the employee-specific self-service performance
 * dashboard split out of My Work's former Marks sub-tab. Every scenario below drives the exact
 * same production API path {@link GoldenEndToEndFlowTest} and {@link MyShootsTest} already use
 * (Idea Review approval -&gt; Shoot -&gt; Shoot Review -&gt; Edit -&gt; Edit Review -&gt; Publishing) so the
 * marks/completion/delay data under test is real, not fabricated for the page.
 *
 * <p>Marks are asserted at their real stored scale (0/0.1/0.5/1.0, e.g. the Task Performance
 * table's Mark column showing plain "1.0") - never multiplied - per the explicit "do not
 * reinterpret the business rules" instruction this feature was built under. The Mark column shows
 * only the earned value; the Total Marks KPI card shows only the earned total too (a later
 * follow-up removed its "/ max" suffix - see {@link #totalMarksKpiShowsOnlyTheEarnedValueNoOutOfTotal()}).
 * ENG-100: the Total Marks KPI card and Mark column are now only rendered at all for a viewer who
 * holds PERM_18_SHOOT_EXECUTION or PERM_19_EDIT_EXECUTION - Publisher (Publishing permission alone)
 * and Model/Talent (participation, no execution permission of their own in this fixture) both have
 * the marks UI fully hidden, never shown with a fabricated/dash value - see
 * {@link MyPerformanceMarkVisibilityTest} for the dedicated permission-matrix coverage of that
 * rule; Publisher/Model still count toward Tasks Completed/Delayed Tasks as before.
 *
 * <p>Each test uses its own {@code long unique} (current epoch millis) to namespace users/idea
 * titles, so the fixed labels "cam"/"ed"/"pub"/"model" below never collide across test methods.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyPerformanceTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = emailFor(label, unique);
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MP " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"my performance test fixture\"}");
        return user.get("userId").asText();
    }

    private String emailFor(String label, long unique) {
        return "mp-" + label + "-" + unique + "@kcpcbandhani.local";
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my performance test fixture grant\"}");
    }

    /**
     * Creates the standard Cameraperson/Editor/Publisher/Model quartet for one test, each granted
     * their execution permission. Returns {@code [camId, edId, pubId, modelId]}.
     */
    private String[] createQuartet(TestApiClient ceo, long unique) throws Exception {
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        String modelId = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        return new String[] {camId, edId, pubId, modelId};
    }

    /**
     * Drives one Content Plan all the way from Idea approval through a completed Publish, with a
     * Cameraperson/Editor/Publisher/Model each earning their own real completed-task/mark record.
     * The Shoot/Edit planned dates are pinned to the PAST (via the same "urgent schedule" endpoint
     * {@link MyShootsTest} uses) so both the Shoot and Edit completions land after their own
     * planned date - a deterministic DELAYED case - while the planned live date stays in the
     * FUTURE, so the Publish completion lands before its planned date. This is real production
     * delay math (completedOn vs plannedDate), not a fixture shortcut. Expects the quartet from
     * {@link #createQuartet}.
     */
    private ContentPlan buildCompletedPipeline(TestApiClient ceo, long unique, String[] quartet) throws Exception {
        String camId = quartet[0];
        String edId = quartet[1];
        String pubId = quartet[2];
        String modelId = quartet[3];

        String title = "MP Pipeline " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(20).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/mp-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(camId));
        reviewParams.put("modelUserIds", List.of(modelId));
        reviewParams.put("publisherUserIds", List.of(pubId));
        reviewParams.put("outputsJson", List.of(
                "[{\"outputType\":\"POST\",\"reelTypes\":[],\"outputTitleDescription\":null,"
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}]"));
        HttpResponse<String> approve = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams);
        assertThat(approve.statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();

        // Pin Shoot/Edit planned dates to the past, planned live date stays in the future.
        String pastShootDate = LocalDate.now().minusDays(6).toString();
        String pastEditDate = LocalDate.now().minusDays(4).toString();
        String futureLiveDate = LocalDate.now().plusDays(20).toString();
        HttpResponse<String> scheduled = ceo.postForm("/app/deliverables/" + planId + "/schedule/urgent", Map.of(
                "plannedLiveDate", futureLiveDate, "shootDate", pastShootDate, "editDate", pastEditDate,
                "urgencyReason", "my performance test fixture - forcing a deterministic delay"));
        assertThat(scheduled.statusCode()).isEqualTo(302);

        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        TestApiClient ed = new TestApiClient(port);
        ed.login(emailFor("ed", unique), "Passw0rd!");
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        String outputId = output.getId().toString();
        TestApiClient pub = new TestApiClient(port);
        pub.login(emailFor("pub", unique), "Passw0rd!");
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "").statusCode()).isEqualTo(200);
        String pastTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString();
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/mp-" + unique + "\"}").statusCode()).isEqualTo(200);

        return contentPlanRepository.findById(plan.getId()).orElseThrow();
    }

    @Test
    void everyRoleSeesOwnCompletedWorkWithRealMarkScaleAndPublisherHasNoMark() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        ContentPlan plan = buildCompletedPipeline(ceo, unique, quartet);

        // Cameraperson: own row, Stage SHOOT, Role Cameraperson, real-scale mark shown as plain
        // "1.0" in the Task Performance table's Mark column - never "1.0 / 1.0" (the earned/max
        // ratio only ever appears on the Total Marks KPI card, not per-row in the table).
        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");
        String camBody = cam.get("/app/my-performance").body();
        assertThat(camBody).contains(plan.getContentId());
        assertThat(camBody).contains("Cameraperson");
        assertThat(camBody).contains("1.0").contains("Delayed");
        assertThat(camBody).doesNotContain("1.0 / 1.0"); // Mark column shows the earned value only, never "earned / max"

        // Editor: own row, Role Editor, mark shown as plain "1.0", also delayed (edit planned date was past).
        TestApiClient ed = new TestApiClient(port);
        ed.login(emailFor("ed", unique), "Passw0rd!");
        String edBody = ed.get("/app/my-performance").body();
        assertThat(edBody).contains(plan.getContentId());
        assertThat(edBody).contains("Editor");
        assertThat(edBody).contains("1.0");
        assertThat(edBody).doesNotContain("1.0 / 1.0");

        // Model: own row, Role Model, still present with its real decided mark data in the
        // underlying model (unchanged) - but ENG-100 (mark visibility): a Model/Talent participant
        // has no Shoot/Edit execution permission of their own in this fixture (createQuartet never
        // grants one), so the marks UI (Total Marks KPI card + Mark column) is fully hidden for
        // them, exactly like Publisher below - see MyPerformanceMarkVisibilityTest for the
        // dedicated permission-matrix coverage of this rule.
        TestApiClient model = new TestApiClient(port);
        model.login(emailFor("model", unique), "Passw0rd!");
        String modelBody = model.get("/app/my-performance").body();
        assertThat(modelBody).contains(plan.getContentId());
        assertThat(modelBody).contains("Model");
        assertThat(modelBody).doesNotContain("<span class=\"kpi-card-title\">Total Marks</span>");
        assertThat(modelBody).doesNotContain("<th>Mark</th>");

        // Publisher: own row present (Tasks Completed counts it), but - ENG-100 (mark visibility) -
        // Publishing permission alone is not mark-eligible, so the marks UI (Total Marks KPI card +
        // Mark column) is fully hidden for this Publisher-only user, not merely shown as "—".
        TestApiClient pub = new TestApiClient(port);
        pub.login(emailFor("pub", unique), "Passw0rd!");
        String pubBody = pub.get("/app/my-performance").body();
        assertThat(pubBody).contains(plan.getContentId());
        assertThat(pubBody).contains("Publisher");
        assertThat(pubBody).contains("Tasks Completed");
        assertThat(pubBody).doesNotContain("<span class=\"kpi-card-title\">Total Marks</span>");
        assertThat(pubBody).doesNotContain("<th>Mark</th>");
    }

    @Test
    void employeeCannotSeeAnotherEmployeesPerformanceData() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        String cam1Id = quartet[0];
        String cam1Email = emailFor("cam", unique);

        ContentPlan plan = buildCompletedPipeline(ceo, unique, quartet);

        // A second, entirely unrelated Cameraperson with no work of their own must see nothing of
        // cam1's plan, even though both share the same Business Role/permission.
        String cam2Id = createUser(ceo, "cam2", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam2Id, "PERM_18_SHOOT_EXECUTION");
        TestApiClient cam2 = new TestApiClient(port);
        cam2.login(emailFor("cam2", unique), "Passw0rd!");
        HttpResponse<String> cam2Page = cam2.get("/app/my-performance");
        assertThat(cam2Page.statusCode()).isEqualTo(200);
        assertThat(performanceTableRegion(cam2Page.body())).doesNotContain(plan.getContentId());

        // The route accepts no employee-identifying parameter at all - an attempt to smuggle one
        // in (userId/employeeId, as if this could redirect the view to cam1's data) is simply
        // ignored server-side; cam2 still sees only their own (empty) dataset, never cam1's.
        HttpResponse<String> tampered = cam2.get("/app/my-performance?userId=" + cam1Id + "&employeeId=" + cam1Id);
        assertThat(tampered.statusCode()).isEqualTo(200);
        assertThat(performanceTableRegion(tampered.body())).doesNotContain(plan.getContentId());

        // Sanity: cam1 themself still sees their own plan (proves the isolation above is real
        // privacy, not a page that shows nobody's data).
        TestApiClient cam1 = new TestApiClient(port);
        cam1.login(cam1Email, "Passw0rd!");
        assertThat(cam1.get("/app/my-performance").body()).contains(plan.getContentId());
    }

    @Test
    void dateRangeFilterScopesKpisAndTable() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        ContentPlan plan = buildCompletedPipeline(ceo, unique, quartet);

        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");

        // Completion happened "now" (test execution time) - a range entirely BEFORE today excludes it.
        String farPastFrom = LocalDate.now().minusDays(60).toString();
        String farPastTo = LocalDate.now().minusDays(30).toString();
        String excluded = cam.get("/app/my-performance?fromDate=" + farPastFrom + "&toDate=" + farPastTo).body();
        assertThat(performanceTableRegion(excluded)).doesNotContain(plan.getContentId());
        assertThat(excluded).contains("No performance records match these filters.");

        // A range spanning today includes it.
        String wideFrom = LocalDate.now().minusDays(30).toString();
        String wideTo = LocalDate.now().plusDays(1).toString();
        String included = cam.get("/app/my-performance?fromDate=" + wideFrom + "&toDate=" + wideTo).body();
        assertThat(included).contains(plan.getContentId());
    }

    /**
     * The Date Range filter must key off "Completed On" exclusively - never Planned Date (nor any
     * assigned/created/start date). {@link #buildCompletedPipeline} pins the Cameraperson's planned
     * shoot date 6 days in the PAST while the actual Shoot Review approval - and therefore this
     * row's real {@code completedOn} - happens "now" (test execution time). A range that covers the
     * planned date but not today must exclude the row; a range covering only today (nowhere near
     * the planned date) must include it. This is asserted against the KPI cards (Total Marks,
     * Tasks Completed), not just the table, since every KPI/summary section must honor the same rule.
     */
    @Test
    void dateRangeFiltersByCompletedOnNeverByPlannedDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        ContentPlan plan = buildCompletedPipeline(ceo, unique, quartet);

        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");

        // A range around the Cameraperson's PLANNED shoot date (6 days ago) - real completion
        // happened today, so this must EXCLUDE the row and show zeroed-out KPIs, even though the
        // planned date falls squarely inside this range.
        String plannedDateOnly = LocalDate.now().minusDays(6).toString();
        String excludedByPlannedDate = cam.get("/app/my-performance?fromDate=" + plannedDateOnly + "&toDate=" + plannedDateOnly).body();
        assertThat(performanceTableRegion(excludedByPlannedDate)).doesNotContain(plan.getContentId());
        assertThat(excludedByPlannedDate).contains("<span class=\"kpi-card-title\">Tasks Completed</span><span class=\"kpi-card-count\">0</span>");
        assertThat(totalMarksCard(excludedByPlannedDate)).contains("kpi-card-count\">0</span>").doesNotContain("kpi-card-count-max");

        // A range covering ONLY today (nowhere near the 6-days-ago planned date) - real completion
        // happened today, so this must INCLUDE the row and reflect it in every KPI/summary section.
        String todayOnly = LocalDate.now().toString();
        String includedByCompletedOn = cam.get("/app/my-performance?fromDate=" + todayOnly + "&toDate=" + todayOnly).body();
        assertThat(includedByCompletedOn).contains(plan.getContentId());
        assertThat(includedByCompletedOn).contains("<span class=\"kpi-card-title\">Tasks Completed</span><span class=\"kpi-card-count\">1</span>");
        assertThat(totalMarksCard(includedByCompletedOn)).contains("kpi-card-count\">1.0</span>").doesNotContain("kpi-card-count-max");
        assertThat(includedByCompletedOn).contains("Cameraperson"); // Role column value for this row
    }

    @Test
    void stageRoleStatusAndDelayFiltersScopeTheTable() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        ContentPlan plan = buildCompletedPipeline(ceo, unique, quartet);

        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");

        // Matching stage/role keeps the row; a non-matching stage/role hides it.
        assertThat(cam.get("/app/my-performance?stage=SHOOT").body()).contains(plan.getContentId());
        assertThat(performanceTableRegion(cam.get("/app/my-performance?stage=EDIT").body())).doesNotContain(plan.getContentId());
        assertThat(cam.get("/app/my-performance?role=Cameraperson").body()).contains(plan.getContentId());
        assertThat(performanceTableRegion(cam.get("/app/my-performance?role=Editor").body())).doesNotContain(plan.getContentId());

        // The Shoot planned date was pinned in the past (buildCompletedPipeline) and completion
        // happened "now" - this Cameraperson row is a real, deterministic DELAYED case.
        assertThat(cam.get("/app/my-performance?delay=DELAYED").body()).contains(plan.getContentId());
        assertThat(performanceTableRegion(cam.get("/app/my-performance?delay=ON_TIME").body())).doesNotContain(plan.getContentId());
        assertThat(cam.get("/app/my-performance?status=DELAYED").body()).contains(plan.getContentId());

        // Clear Filters (no params at all) returns the complete own-dataset again.
        assertThat(cam.get("/app/my-performance").body()).contains(plan.getContentId());
    }

    /**
     * ENG-100: a Publisher (Publishing permission only, no Shoot/Edit) never sees the Total Marks
     * KPI card at all - not even showing "0" - since Publishing permission alone is not
     * mark-eligible. Tasks Completed is entirely unaffected (Publisher's own completion/delay
     * stats are never hidden, only the marks-specific UI is).
     */
    @Test
    void publisherOnlyUserNeverSeesTheTotalMarksCardAtAll() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        buildCompletedPipeline(ceo, unique, quartet);

        TestApiClient pub = new TestApiClient(port);
        pub.login(emailFor("pub", unique), "Passw0rd!");
        String body = pub.get("/app/my-performance").body();

        assertThat(body).doesNotContain("<span class=\"kpi-card-title\">Total Marks</span>");
        assertThat(body).contains("<span class=\"kpi-card-title\">Tasks Completed</span><span class=\"kpi-card-count\">1</span>");
    }

    /**
     * Unlike Shoot/Edit (whose only completion signal is the ONE shared Shoot/Edit Review approval
     * event - there is no finer-grained per-teammate timestamp anywhere in this data model, per
     * inspection of ShootingAssignment/EditingAssignment/ShootingExecutionParticipant), Publishing
     * genuinely does carry a per-publisher signal: {@code ActualPublicationEvent.publishedBy} +
     * {@code actualPublicationTimestamp}. Two Publishers are co-assigned to the same plan here, but
     * only ONE of them personally records the publication event - proving "Completed On" is derived
     * from THAT specific Publisher's own event, never borrowed from a co-assignee's.
     */
    @Test
    void publisherCompletedOnIsPersonalNeverACoAssignedPublishersEvent() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        String pub2Id = createUser(ceo, "pub2", PUBLISHER_ROLE_ID, unique);
        String modelId = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        grantPermission(ceo, pub2Id, "PERM_08_PUBLISHING_EXECUTION");

        String title = "MP TwoPublishers " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        Map<String, List<String>> reviewParams = new HashMap<>();
        reviewParams.put("decision", List.of("APPROVE"));
        reviewParams.put("cameramanMark", List.of("1.0"));
        reviewParams.put("editorMark", List.of("1.0"));
        reviewParams.put("modelMark", List.of("1.0"));
        reviewParams.put("contentPriority", List.of("MEDIUM"));
        reviewParams.put("plannedLiveDate", List.of(LocalDate.now().plusDays(20).toString()));
        reviewParams.put("folderLink", List.of("https://drive.example.com/mp-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(camId));
        reviewParams.put("modelUserIds", List.of(modelId));
        reviewParams.put("publisherUserIds", List.of(pubId, pub2Id));
        reviewParams.put("outputsJson", List.of(
                "[{\"outputType\":\"POST\",\"reelTypes\":[],\"outputTitleDescription\":null,"
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}]"));
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams).statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        TestApiClient ed = new TestApiClient(port);
        ed.login(emailFor("ed", unique), "Passw0rd!");
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\",\"" + pub2Id + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        String outputId = output.getId().toString();

        // Only "pub" personally records the publication - "pub2" is a co-assigned Publisher who
        // never records anything themselves.
        TestApiClient pub = new TestApiClient(port);
        pub.login(emailFor("pub", unique), "Passw0rd!");
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "").statusCode()).isEqualTo(200);
        String pastTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString();
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/mp-" + unique + "\"}").statusCode()).isEqualTo(200);

        String todayOnly = LocalDate.now().toString();

        // "pub" personally recorded the event today - a date filter covering only today includes them.
        TestApiClient pubClient = new TestApiClient(port);
        pubClient.login(emailFor("pub", unique), "Passw0rd!");
        String pubFiltered = pubClient.get("/app/my-performance?fromDate=" + todayOnly + "&toDate=" + todayOnly).body();
        assertThat(pubFiltered).contains(plan.getContentId());

        // "pub2" co-assigned the same plan but personally recorded nothing. Unfiltered, the row
        // still appears (still an active assignee on a completed Publishing task), but it must NOT
        // have inherited "pub"'s completion date: the same today-only filter that included "pub"
        // must EXCLUDE "pub2", proving pub2's own Completed On is null, not borrowed from pub.
        TestApiClient pub2Client = new TestApiClient(port);
        pub2Client.login(emailFor("pub2", unique), "Passw0rd!");
        assertThat(pub2Client.get("/app/my-performance").body()).contains(plan.getContentId());
        String pub2Filtered = pub2Client.get("/app/my-performance?fromDate=" + todayOnly + "&toDate=" + todayOnly).body();
        assertThat(performanceTableRegion(pub2Filtered)).doesNotContain(plan.getContentId());
    }

    /**
     * Presentation-only follow-up: the Total Marks KPI card shows only the earned value (e.g.
     * "1.1"), never an "/ [max]" out-of-total suffix - the card's underlying {@code totalMarks}
     * value and the individual Task Performance rows' own Mark column are both completely
     * unaffected (same real stored scale, same per-row values as every other test in this file).
     */
    @Test
    void totalMarksKpiShowsOnlyTheEarnedValueNoOutOfTotal() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String[] quartet = createQuartet(ceo, unique);
        ContentPlan plan = buildCompletedPipeline(ceo, unique, quartet);

        TestApiClient cam = new TestApiClient(port);
        cam.login(emailFor("cam", unique), "Passw0rd!");
        String body = cam.get("/app/my-performance").body();

        // 1. Total Marks value is still rendered.
        String totalMarksCard = totalMarksCard(body);
        assertThat(totalMarksCard).contains("Total Marks</span>");
        assertThat(totalMarksCard).contains("kpi-card-count\">1.0</span>");

        // 2. The "/ [maximum]" out-of-total text is not rendered anywhere on the page.
        assertThat(body).doesNotContain("kpi-card-count-max");
        assertThat(totalMarksCard).doesNotContain(" / ");

        // 3. Individual task marks (Task Performance table's Mark column) remain unchanged -
        // plain earned value, same as every other test in this file.
        assertThat(body).contains(plan.getContentId());
        assertThat(body).contains("Cameraperson");
        assertThat(body).contains("1.0");
        assertThat(body).doesNotContain("1.0 / 1.0");
    }

    /** Isolates the Total Marks KPI card's own markup, so assertions can't accidentally match a different card. */
    private String totalMarksCard(String body) {
        int titleIdx = body.indexOf("Total Marks");
        int end = body.indexOf("kpi-card-subtitle", titleIdx);
        assertThat(titleIdx).as("Total Marks KPI card must be present").isPositive();
        assertThat(end).isGreaterThan(titleIdx);
        return body.substring(titleIdx, end);
    }

    /**
     * Isolates the Task Performance table itself, so a "this Content ID is not on My Performance"
     * check can't accidentally match the header's global "latest notifications" widget instead -
     * that widget renders on every page (including this one) and can legitimately mention this
     * same Content ID via an unrelated notification (e.g. its original assignment) regardless of
     * whether the row is actually in this filtered table.
     */
    private String performanceTableRegion(String body) {
        int start = body.indexOf("Task Performance");
        int end = body.indexOf("</table>", start);
        assertThat(start).as("Task Performance table must be present").isPositive();
        assertThat(end).as("table close tag must be present after start").isGreaterThan(start);
        return body.substring(start, end);
    }
}
