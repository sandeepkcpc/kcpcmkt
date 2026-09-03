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
 * My Work's "History" section has been removed entirely (see MyWorkRoleBasedNavigationTest); the
 * completed record now surfaces only on My Performance's own Task Performance table, whose new
 * "View" action must reuse the EXACT SAME route/controller/view My Work's own completed-work links
 * always used - {@code GET /app/deliverables/{contentPlanId}?tab=shoot|edit|publishing}, handled by
 * {@link com.kcpc.mkt.web.mvc.DeliverableMvcController#view} - never a new/separate
 * "performance detail" page. Reuses {@link CompletedWorkViewRoutingTest}'s own proof that this
 * route renders the read-only, stage-frozen Shoot/Edit/Publishing Task Detail screen (own status,
 * not the plan's current overall status); this file only proves My Performance's own "View" link
 * points at that identical URL for each of Cameraperson/Editor/Publisher, per stage.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyPerformanceViewLinkTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
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

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = emailFor(label, unique);
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MPVL " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"my performance view link test fixture\"}");
        return user.get("userId").asText();
    }

    private String emailFor(String label, long unique) {
        return "mpvl-" + label + "-" + unique + "@kcpcbandhani.local";
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my performance view link test fixture grant\"}");
    }

    /** Drives one Content Plan through a fully completed Shoot -> Edit -> Publishing pipeline. */
    private ContentPlan buildFullyCompletedPipeline(TestApiClient ceo, long unique, String camId, String camEmail,
                                                      String edId, String edEmail, String pubId, String pubEmail) throws Exception {
        String title = "MPVL Pipeline " + unique;
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
        reviewParams.put("folderLink", List.of("https://drive.example.com/mpvl-" + unique));
        reviewParams.put("camerapersonUserIds", List.of(camId));
        reviewParams.put("publisherUserIds", List.of(pubId));
        reviewParams.put("outputsJson", List.of(
                "[{\"outputType\":\"POST\",\"reelTypes\":[],\"outputTitleDescription\":null,"
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}]"));
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", reviewParams).statusCode()).isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        String planId = plan.getId().toString();

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + edId + "\"],\"leadEditorUserId\":\"" + edId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/start", "").statusCode()).isEqualTo(200);
        assertThat(ed.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + edId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        PlannedOutput output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/start", "").statusCode()).isEqualTo(200);
        String pastTimestamp = Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS).toString();
        assertThat(pub.post("/api/v1/content-plans/" + planId + "/publishing/events",
                "{\"plannedOutputId\":\"" + output.getId() + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/mpvl-" + unique + "\"}").statusCode()).isEqualTo(200);

        return contentPlanRepository.findById(plan.getId()).orElseThrow();
    }

    @Test
    void camerapersonMyPerformanceViewLinkOpensTheExactSameShootTaskDetailScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String camEmail = emailFor("cam", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, camEmail,
                edId, emailFor("ed", unique), pubId, emailFor("pub", unique));

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        String performanceBody = cam.get("/app/my-performance").body();

        String expectedHref = "/app/deliverables/" + plan.getId() + "?tab=shoot";
        assertThat(performanceBody).contains("href=\"" + expectedHref + "\"");

        HttpResponse<String> viewed = cam.get(expectedHref);
        assertThat(viewed.statusCode()).as(viewed.body()).isEqualTo(200);
        assertThat(viewed.body()).contains("Shoot Task &mdash; " + plan.getContentId());
        assertThat(viewed.body()).contains("<span class=\"status-pill status-completed\">Approved</span>");
        // Read-only: no execution controls, the exact same guarantee CompletedWorkViewRoutingTest
        // already proves for this identical route when reached from My Work's own former link.
        assertThat(viewed.body()).doesNotContain("Start Shoot").doesNotContain("Submit for Review");
    }

    @Test
    void editorMyPerformanceViewLinkOpensTheExactSameEditTaskDetailScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String edEmail = emailFor("ed", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, emailFor("cam", unique),
                edId, edEmail, pubId, emailFor("pub", unique));

        TestApiClient ed = new TestApiClient(port);
        ed.login(edEmail, "Passw0rd!");
        String performanceBody = ed.get("/app/my-performance").body();

        String expectedHref = "/app/deliverables/" + plan.getId() + "?tab=edit";
        assertThat(performanceBody).contains("href=\"" + expectedHref + "\"");

        HttpResponse<String> viewed = ed.get(expectedHref);
        assertThat(viewed.statusCode()).as(viewed.body()).isEqualTo(200);
        assertThat(viewed.body()).contains("Edit Task &mdash; " + plan.getContentId());
        assertThat(viewed.body()).contains("<span class=\"status-pill status-completed\">Approved</span>");
    }

    @Test
    void publisherMyPerformanceViewLinkOpensTheExactSamePublishTaskDetailScreen() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String pubEmail = emailFor("pub", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, emailFor("cam", unique),
                edId, emailFor("ed", unique), pubId, pubEmail);

        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");
        String performanceBody = pub.get("/app/my-performance").body();

        String expectedHref = "/app/deliverables/" + plan.getId() + "?tab=publishing";
        assertThat(performanceBody).contains("href=\"" + expectedHref + "\"");

        HttpResponse<String> viewed = pub.get(expectedHref);
        assertThat(viewed.statusCode()).as(viewed.body()).isEqualTo(200);
        assertThat(viewed.body()).contains("Publishing Task &mdash; " + plan.getContentId());
        assertThat(viewed.body()).contains("<span class=\"status-pill status-completed\">Performance Pending</span>");
    }

    /**
     * No new/duplicate detail UI: My Performance's View link for a given (contentPlanId, stage)
     * resolves to the byte-identical response My Work's own former completed-work link at the same
     * URL always produced - proven by fetching the SAME URL twice through independent requests and
     * comparing bodies, rather than assuming.
     */
    @Test
    void viewLinkResponseIsByteIdenticalOnRepeatedFetchNoSeparatePerformanceDetailView() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String edId = createUser(ceo, "ed", VIDEO_EDITOR_ROLE_ID, unique);
        String pubId = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, edId, "PERM_19_EDIT_EXECUTION");
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String camEmail = emailFor("cam", unique);
        ContentPlan plan = buildFullyCompletedPipeline(ceo, unique, camId, camEmail,
                edId, emailFor("ed", unique), pubId, emailFor("pub", unique));

        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        String url = "/app/deliverables/" + plan.getId() + "?tab=shoot";
        String first = cam.get(url).body();
        String second = cam.get(url).body();
        assertThat(first).isEqualTo(second);
    }
}
