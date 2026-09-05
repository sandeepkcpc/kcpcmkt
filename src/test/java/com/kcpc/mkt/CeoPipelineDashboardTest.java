package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/changes/CEO_CONTENT_PIPELINE_18_COLUMN_CHANGE.md - drives one Content ID through Planning,
 * Shooting and Publishing with multi-valued Camerapersons/Editors/Models/Channels/Platforms, then
 * asserts the CEO Content Pipeline dashboard renders exactly the 18 required columns as one row,
 * with the clarified business semantics for each. Also proves the role boundary: Employees are
 * redirected away, Marketing Manager continues to see the same shared view.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CeoPipelineDashboardTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    CompanyChannelRepository companyChannelRepository;
    @Autowired
    PublicationTargetRepository publicationTargetRepository;
    @Autowired
    PerformanceObligationRepository performanceObligationRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003"; // EMPLOYEE access class
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String INSTAGRAM_PLATFORM_ID = "01926e3e-0008-7000-8000-000000000001";
    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";
    private static final String TARGET_YOUTUBE_KCPC = "01926e3e-000a-7000-8000-000000000002";

    @Test
    void eighteenColumnPipelineRendersOneRowPerContentIdWithMultiValueData() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String cam1Email = "pl-cam1-" + unique + "@kcpcbandhani.local";
        String cam1 = createUser(ceo, "Pipeline Cam One", cam1Email, CAMERA_PERSON_ROLE_ID);
        String cam2 = createUser(ceo, "Pipeline Cam Two", "pl-cam2-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        String ed1Email = "pl-ed1-" + unique + "@kcpcbandhani.local";
        String ed1 = createUser(ceo, "Pipeline Ed One", ed1Email, VIDEO_EDITOR_ROLE_ID);
        String ed2 = createUser(ceo, "Pipeline Ed Two", "pl-ed2-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);
        String pubEmail = "pl-pub-" + unique + "@kcpcbandhani.local";
        String pubId = createUser(ceo, "Pipeline Publisher", pubEmail, PUBLISHER_ROLE_ID);
        String pubId2 = createUser(ceo, "Pipeline Publisher Two", "pl-pub2-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        // ENG-043: Start/Submit-style execution acts now require the actor to be the actively
        // assigned Cameraperson/Editor/Publisher - CEO/MM native authority no longer bypasses this.
        TestApiClient cam1Client = new TestApiClient(port);
        cam1Client.login(cam1Email, "Passw0rd!");
        TestApiClient ed1Client = new TestApiClient(port);
        ed1Client.login(ed1Email, "Passw0rd!");
        TestApiClient pubClient = new TestApiClient(port);
        pubClient.login(pubEmail, "Passw0rd!");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam1 + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam2 + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + ed1 + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + ed2 + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test publisher grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId2 + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test publisher grant\"}");
        String model1 = createUser(ceo, "Aisha " + unique, "pl-model1-" + unique + "@kcpcbandhani.local", MODEL_ROLE_ID);
        String model2 = createUser(ceo, "Neha " + unique, "pl-model2-" + unique + "@kcpcbandhani.local", MODEL_ROLE_ID);
        String model3 = createUser(ceo, "Riya " + unique, "pl-model3-" + unique + "@kcpcbandhani.local", MODEL_ROLE_ID);

        // A second Channel so "all planned Channels" has more than one distinct value to prove.
        assertRedirect(ceo.postForm("/app/admin/catalogue/channels",
                Map.of("channelHandle", "pipeline-test-" + unique, "catalogueReason", "pipeline dashboard test fixture")));
        CompanyChannel newChannel = companyChannelRepository.findAll().stream()
                .filter(c -> c.getChannelHandle().equals("pipeline-test-" + unique)).findFirst().orElseThrow();
        assertRedirect(ceo.postForm("/app/admin/catalogue/targets",
                Map.of("platformId", INSTAGRAM_PLATFORM_ID, "channelId", newChannel.getId().toString(),
                        "targetName", "Pipeline Test Target " + unique, "catalogueReason", "pipeline dashboard test fixture")));
        PublicationTarget newTarget = publicationTargetRepository.findAll().stream()
                .filter(t -> t.getTargetName().equals("Pipeline Test Target " + unique)).findFirst().orElseThrow();

        // ENG-094: Category is now catalogue-validated - "Reels" must exist as an active Category
        // Catalogue entry before it can be submitted on approval below.
        assertRedirect(ceo.postForm("/app/admin/categories",
                Map.of("name", "Reels", "catalogueReason", "pipeline dashboard test fixture")));

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (priority/category/SKU/schedule/folder link/models/initial output+
        // publication scope/shoot team) in one form POST and transitions straight to Shoot Assigned
        // (SA), never PL/PLRV/PLAP.
        String plannedLiveDate = LocalDate.now().plusDays(10).toString();
        String ideaTitle = "Pipeline Dashboard Idea " + unique;
        HttpResponse<String> submit = ceo.postForm("/app/ideas",
                Map.of("title", ideaTitle, "referenceLink", "https://example.com/ref-" + unique));
        assertThat(submit.statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertRedirect(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.ofEntries(
                Map.entry("decision", java.util.List.of("APPROVE")),
                Map.entry("cameramanMark", java.util.List.of("1.0")),
                Map.entry("editorMark", java.util.List.of("1.0")),
                Map.entry("modelMark", java.util.List.of("1.0")),
                Map.entry("contentPriority", java.util.List.of("HIGH")),
                Map.entry("categoryText", java.util.List.of("Reels")),
                Map.entry("skuReference", java.util.List.of("SKU-" + unique)),
                Map.entry("plannedLiveDate", java.util.List.of(plannedLiveDate)),
                Map.entry("folderLink", java.util.List.of("https://drive.example.com/pipeline-" + unique)),
                Map.entry("modelUserIds", java.util.List.of(model1, model2, model3)),
                Map.entry("outputsJson", java.util.List.of("[{\"outputType\":\"POST\",\"publicationTargetIds\":[\""
                        + TARGET_INSTAGRAM_KCPC + "\",\"" + TARGET_YOUTUBE_KCPC + "\",\"" + newTarget.getId() + "\"]}]")),
                Map.entry("camerapersonUserIds", java.util.List.of(cam1, cam2)),
                Map.entry("publisherUserIds", java.util.List.of(pubId, pubId2)))));

        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        UUID planId = plan.getId();
        String base = "/app/deliverables/" + planId;
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();

        assertRedirect(cam1Client.postForm(base + "/shooting/start", Map.of()));
        assertRedirect(cam1Client.postForm(base + "/shooting/review/submit", Map.of()));
        assertRedirect(ceo.postForm(base + "/shooting/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", cam1,
                        "editorUserIds", ed1, "leadEditorUserId", ed1)));

        assertRedirect(ceo.postForm(base + "/editing/assignments", Map.of("editorUserId", ed2)));
        assertRedirect(ed1Client.postForm(base + "/editing/start", Map.of()));
        assertRedirect(ed1Client.postForm(base + "/editing/review/submit", Map.of()));
        assertRedirect(ceo.postForm(base + "/editing/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", ed1, "publisherUserIds", pubId)));

        assertRedirect(pubClient.postForm(base + "/publishing/start", Map.of()));
        String pastDate = LocalDate.now().minusDays(3).toString();
        // Every mapped target's publication obligation must resolve (event or N/A) before the
        // workflow advances past Publishing into Performance Pending (BR-025) - record all three.
        assertRedirect(pubClient.postForm(base + "/publishing/events",
                Map.of("plannedOutputId", output.getId().toString(), "publicationTargetId", TARGET_INSTAGRAM_KCPC,
                        "eventType", "ORIGINAL", "actualPublicationTimestamp", pastDate,
                        "evidenceUrl", "https://instagram.com/p/pipeline-" + unique)));
        assertRedirect(pubClient.postForm(base + "/publishing/events",
                Map.of("plannedOutputId", output.getId().toString(), "publicationTargetId", TARGET_YOUTUBE_KCPC,
                        "eventType", "ORIGINAL", "actualPublicationTimestamp", pastDate,
                        "evidenceUrl", "https://youtube.com/watch?v=pipeline-" + unique)));
        assertRedirect(pubClient.postForm(base + "/publishing/events",
                Map.of("plannedOutputId", output.getId().toString(), "publicationTargetId", newTarget.getId().toString(),
                        "eventType", "ORIGINAL", "actualPublicationTimestamp", pastDate,
                        "evidenceUrl", "https://instagram.com/p/pipeline-alt-" + unique)));
        // Plan is now at Performance Pending (PP).

        HttpResponse<String> pipeline = ceo.get("/app/pipeline");
        assertThat(pipeline.statusCode()).isEqualTo(200);
        String body = pipeline.body();

        // ENG-074: Content Pipeline redesigned again into a flat single-header-row 20-column
        // table (back to the original column order/set - Reference Link/Category/Head/Channels
        // are visible columns again, and Priority/Action are no longer separate columns: Priority
        // moved to the filter bar only, and Content ID's own link IS the "action").
        assertThat(body).containsSubsequence("Content ID", "SKU", "Idea", "Reference Link / Note", "Category",
                "Channels", "Head", "Camera Person", "Models", "Video Editor", "Publisher", "Drive Link",
                "Planned Shoot Date", "Planned Edit Date", "Planned Live Date",
                "Actual Shoot Date", "Actual Edit Date", "Actual Live Date", "Platforms", "Performance", "Status");
        assertThat(countOccurrences(body, plan.getContentId())).isEqualTo(1);

        assertThat(body).contains("<a href=\"/app/deliverables/" + planId + "\">" + plan.getContentId() + "</a>");
        assertThat(body).contains("SKU-" + unique);
        assertThat(body).contains(ideaTitle);
        assertThat(body).contains("href=\"https://example.com/ref-" + unique + "\"");
        assertThat(body).contains("Reels");
        assertThat(body).contains("kcpcbandhani").contains("pipeline-test-" + unique);
        assertThat(body).contains("KCPC CEO"); // Actor = preparedBy, the CEO saved the parameters above.
        assertThat(body).contains("Pipeline Cam One").contains("Pipeline Cam Two");
        assertThat(body).contains("Aisha").contains("Neha").contains("Riya");
        assertThat(body).contains("Pipeline Ed One").contains("Pipeline Ed Two");
        assertThat(body).contains("Pipeline Publisher").contains("Pipeline Publisher Two");
        assertThat(body).contains("aria-label=\"Open Drive Link\"").contains("pipeline-link-icon");
        assertThat(body).contains(plannedLiveDate); // Planned Live Date
        assertThat(body).contains("Instagram").contains("YouTube");
        assertThat(body).contains(">Pending<"); // Performance state at PP
        assertThat(body).contains("performance-cell clickable")
                .contains("href=\"/app/deliverables/" + planId + "#performance\"");
        assertThat(body).contains("Performance Pending"); // human-readable Status, not raw "PP"
        assertThat(body).doesNotContain(">PP<", ">COMP<", ">CAN<");

        // Actual Shoot/Edit Date = the date the Shoot/Edit Review gate was approved, i.e. today
        // (both decisions above were just made). Actual Live Date = the earliest ORIGINAL
        // publication event's date, i.e. pastDate (all three events above used that same date).
        // Scoped to this Content ID's own row - the dashboard renders every plan in the test DB,
        // so an unscoped count would also pick up unrelated fixtures' dates.
        int rowStart = body.indexOf(plan.getContentId());
        String row = body.substring(rowStart, body.indexOf("</tr>", rowStart));
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString();
        assertThat(countOccurrences(row, ">" + today + "<")).isEqualTo(2); // Actual Shoot Date + Actual Edit Date
        assertThat(row).contains(">" + pastDate + "<"); // Actual Live Date

        // Employee cannot bypass authorization to reach the CEO/MM pipeline view.
        String hrUserId = createUser(ceo, "Pipeline HR Employee", "pl-hr-" + unique + "@kcpcbandhani.local", HR_MANAGER_ROLE_ID);
        TestApiClient employee = new TestApiClient(port);
        employee.login("pl-hr-" + unique + "@kcpcbandhani.local", "Passw0rd!");
        HttpResponse<String> employeeAttempt = employee.get("/app/pipeline");
        assertThat(employeeAttempt.statusCode()).isEqualTo(302); // redirected away, never rendered
        // HR Manager is a non-production EMPLOYEE Business Role (WorkflowParticipationInterceptor,
        // ENG Business-Role-Workspace change) - deny-by-default sends it to /app/ideas for any
        // /app/** URL outside the My Ideas family, not the old generic /app/home.
        assertThat(employeeAttempt.headers().firstValue("Location").orElseThrow()).contains("/app/ideas");
        assertThat(employeeAttempt.body()).doesNotContain("pipeline-table");

        // Existing Marketing Manager behaviour does not regress - MM still sees the same view.
        String mmUserId = createUser(ceo, "Pipeline MM", "pl-mm-" + unique + "@kcpcbandhani.local", MARKETING_MANAGER_ROLE_ID);
        TestApiClient mm = new TestApiClient(port);
        mm.login("pl-mm-" + unique + "@kcpcbandhani.local", "Passw0rd!");
        HttpResponse<String> mmPipeline = mm.get("/app/pipeline");
        assertThat(mmPipeline.statusCode()).isEqualTo(200);
        assertThat(mmPipeline.body()).contains("pipeline-table").contains(plan.getContentId());

        assertThat(hrUserId).isNotBlank();
        assertThat(mmUserId).isNotBlank();
    }

    @Test
    void pipelineRendersSkuNaAndHandlesAMinimalNotYetPlannedContentIdSafely() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        // Workflow redesign: a Content Plan can no longer exist with nothing planned at all -
        // Idea Review approval now requires Priority/Planned Live Date/Folder Link/at least one
        // Cameraperson before a plan is even created. This still exercises the closest surviving
        // "minimal" shape: SKU N/A (derived server-side from a blank skuReference, no separate
        // checkbox), no Category, no Models, no planned outputs/targets, no publication events -
        // proving the dashboard never breaks on a barely-planned Content ID.
        String camEmail = "pl-minimal-cam-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Pipeline Minimal Cam", camEmail, CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        String pubId = createUser(ceo, "Pipeline Minimal Pub", "pl-minimal-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test publisher grant\"}");

        String ideaTitle = "Pipeline Minimal Idea " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertRedirect(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", java.util.List.of("APPROVE"),
                "cameramanMark", java.util.List.of("1.0"),
                "editorMark", java.util.List.of("1.0"),
                "modelMark", java.util.List.of("1.0"),
                "contentPriority", java.util.List.of("LOW"),
                "plannedLiveDate", java.util.List.of(LocalDate.now().plusDays(10).toString()),
                "folderLink", java.util.List.of("https://drive.example.com/pl-minimal-" + unique),
                "camerapersonUserIds", java.util.List.of(camId),
                "publisherUserIds", java.util.List.of(pubId))));
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();

        HttpResponse<String> pipeline = ceo.get("/app/pipeline");
        assertThat(pipeline.statusCode()).isEqualTo(200);
        assertThat(pipeline.body()).doesNotContain("LazyInitializationException", "NullPointerException",
                "Whitelabel Error Page", "500 Internal Server Error");
        assertThat(pipeline.body()).contains(plan.getContentId());
        assertThat(pipeline.body()).contains(">N/A<");
        // Content with exactly one Publisher assigned.
        assertThat(pipeline.body()).contains("Pipeline Minimal Pub");
    }

    /**
     * Content without a Publisher assigned: Publisher is mandatory at Idea Review approval (an
     * earlier, already-established business rule - see IdeaService#approve), so the only real,
     * reachable "currently zero active Publishers" state on a live Content ID is the existing
     * Reopen-for-Publishing flow (POST /reopen-publishing on a COMPLETED plan - see
     * WorkflowVariantsE2ETest), which deliberately ends every active PublishingAssignment so a
     * repost cycle never silently inherits the prior Publisher - landing on RFP gated on a fresh
     * Assign Publisher, exactly like first-time Publishing. Camera Person/Video Editor are
     * untouched by that action, so this also proves the Publisher column's own lookup is
     * independent of the other assignment columns, not just "blank when everything is blank".
     */
    @Test
    void pipelineShowsDashWhenNoPublisherIsCurrentlyActive() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camEmail = "pl-nopub-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "pl-nopub-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "pl-nopub-pub-" + unique + "@kcpcbandhani.local";
        String camId = createUser(ceo, "NoPub Camera", camEmail, CAMERA_PERSON_ROLE_ID);
        String edId = createUser(ceo, "NoPub Editor", edEmail, VIDEO_EDITOR_ROLE_ID);
        String pubId = createUser(ceo, "NoPub Publisher", pubEmail, PUBLISHER_ROLE_ID);
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + edId + "\",\"permission\":\"PERM_19_EDIT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline dashboard test fixture grant\"}");

        String ideaTitle = "NoPub Idea " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertRedirect(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", java.util.List.of("APPROVE"),
                "cameramanMark", java.util.List.of("1.0"),
                "editorMark", java.util.List.of("1.0"),
                "modelMark", java.util.List.of("1.0"),
                "contentPriority", java.util.List.of("LOW"),
                "plannedLiveDate", java.util.List.of(LocalDate.now().plusDays(10).toString()),
                "folderLink", java.util.List.of("https://drive.example.com/nopub-" + unique),
                "outputsJson", java.util.List.of("[{\"outputType\":\"POST\",\"publicationTargetIds\":[\""
                        + TARGET_INSTAGRAM_KCPC + "\"]}]"),
                "camerapersonUserIds", java.util.List.of(camId),
                "publisherUserIds", java.util.List.of(pubId))));
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        UUID planId = plan.getId();
        String base = "/app/deliverables/" + planId;
        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();

        // Drive to Completed.
        assertRedirect(cam.postForm(base + "/shooting/start", Map.of()));
        assertRedirect(cam.postForm(base + "/shooting/review/submit", Map.of()));
        assertRedirect(ceo.postForm(base + "/shooting/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", camId,
                        "editorUserIds", edId, "leadEditorUserId", edId)));
        assertRedirect(ed.postForm(base + "/editing/start", Map.of()));
        assertRedirect(ed.postForm(base + "/editing/review/submit", Map.of()));
        assertRedirect(ceo.postForm(base + "/editing/review/decision",
                Map.of("approve", "true", "qualifyingRecipientUserIds", edId, "publisherUserIds", pubId)));
        assertRedirect(pub.postForm(base + "/publishing/start", Map.of()));
        String pastDate = LocalDate.now().minusDays(3).toString();
        assertRedirect(pub.postForm(base + "/publishing/events",
                Map.of("plannedOutputId", output.getId().toString(), "publicationTargetId", TARGET_INSTAGRAM_KCPC,
                        "eventType", "ORIGINAL", "actualPublicationTimestamp", pastDate,
                        "evidenceUrl", "https://instagram.com/p/nopub-" + unique)));
        var obligationId = performanceObligationRepository.findByEvent_ContentPlan_Id(planId).stream()
                .max(java.util.Comparator.comparing(o -> o.getEvent().getActualPublicationTimestamp()))
                .map(o -> o.getId().toString()).orElseThrow();
        ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"views3sec\":800,\"plays\":1000,\"averageWatchTimeSeconds\":12.5,\"videoLengthSeconds\":20.0,"
                        + "\"linkClicks\":0,\"clicksIsNa\":true,\"impressions\":5000}");
        assertThat(ceo.post("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "").statusCode())
                .isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + planId).get("status").asText()).isEqualTo("COMP");

        // Reopen for Publishing: every active PublishingAssignment ends - zero active Publishers
        // until a fresh one is assigned, matching first-time Publishing's own Assign Publisher gate.
        HttpResponse<String> reopen = ceo.post("/api/v1/content-plans/" + planId + "/reopen-publishing",
                "{\"reason\":\"NoPub regression test reopen\"}");
        assertThat(reopen.statusCode()).isEqualTo(200);
        assertThat(ceo.getJson("/api/v1/content-plans/" + planId).get("status").asText()).isEqualTo("RFP");

        HttpResponse<String> pipeline = ceo.get("/app/pipeline");
        assertThat(pipeline.statusCode()).isEqualTo(200);
        String body = pipeline.body();
        // Scoped to the table body, not the whole page: this plan just completed, so CEO's own
        // header notification dropdown (rendered on every page, including this one - see
        // MyPerformanceTest's own identical fix earlier) legitimately mentions this same Content ID
        // too ("... has been completed"), which would otherwise be found first by a page-wide search.
        int tableBodyStart = body.indexOf("<tbody>");
        int rowStart = body.indexOf(plan.getContentId(), tableBodyStart);
        String row = body.substring(rowStart, body.indexOf("</tr>", rowStart));
        // Camera Person/Video Editor are untouched by the reopen - only Publisher is now dashed.
        assertThat(row).contains("NoPub Camera").contains("NoPub Editor");
        assertThat(row).doesNotContain("NoPub Publisher");
    }

    private long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private void assertRedirect(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(302);
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"pipeline dashboard test fixture\"}");
        return response.get("userId").asText();
    }
}
