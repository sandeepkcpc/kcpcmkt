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
 * My Performance: the "Total Marks" KPI card and the "Mark" column are visible ONLY when the
 * logged-in employee holds at least one of the two execution permissions that already gate this
 * app's Shoot/Edit tabs everywhere else - {@code PERM_18_SHOOT_EXECUTION} or {@code
 * PERM_19_EDIT_EXECUTION} (see {@code LandingMvcController#myPerformance}'s {@code
 * markVisibilityEligible}, reusing the exact same {@code AuthorizationService#hasAnyActiveGrant}
 * mechanism {@code #myWork}'s own showShootTab/showEditTab already use). Publishing permission
 * alone, and Model/Talent participation alone, are both deliberately excluded - neither has a
 * RoleType/mark-attribution gate in this system (see {@link MyPerformanceTest}'s own class-level
 * note on Publisher), so their marks UI would only ever show empty/dash placeholders.
 *
 * <p>This file only proves the VISIBILITY gate itself (present/absent, KPI card count/layout).
 * The underlying mark values, Total Marks calculation, and every other My Performance behavior for
 * an already-eligible employee are unchanged and remain covered by {@link MyPerformanceTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyPerformanceMarkVisibilityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "mpmv-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MPMV " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"my performance mark visibility test fixture\"}");
        return user.get("userId").asText();
    }

    private String emailFor(String label, long unique) {
        return "mpmv-" + label + "-" + unique + "@kcpcbandhani.local";
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my performance mark visibility test fixture grant\"}");
    }

    private TestApiClient loginAs(String email) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(email, "Passw0rd!");
        return client;
    }

    private void assertMarksHidden(String body) {
        assertThat(body).doesNotContain("<span class=\"kpi-card-title\">Total Marks</span>");
        assertThat(body).doesNotContain("<th>Mark</th>");
        // The 2-card responsive layout takes over instead of leaving a blank card slot.
        assertThat(body).contains("class=\"kpi-cards kpi-cards-2\"");
        assertThat(body).doesNotContain("kpi-cards kpi-cards-3");
    }

    private void assertMarksVisible(String body) {
        assertThat(body).contains("<span class=\"kpi-card-title\">Total Marks</span>");
        assertThat(body).contains("<th>Mark</th>");
        assertThat(body).contains("class=\"kpi-cards kpi-cards-3\"");
        assertThat(body).doesNotContain("kpi-cards kpi-cards-2");
    }

    // ------------------------------------------------------------------ Case A: Publisher only

    @Test
    void publisherOnlyUserDoesNotSeeMarksUI() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String pubId = createUser(ceo, "pubonly", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");

        String body = loginAs(emailFor("pubonly", unique)).get("/app/my-performance").body();
        assertMarksHidden(body);
    }

    // ------------------------------------------------------------------ Case B: Publisher + Shoot

    @Test
    void publisherPlusShootPermissionUserSeesMarksUI() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String userId = createUser(ceo, "pubshoot", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, userId, "PERM_08_PUBLISHING_EXECUTION");
        grantPermission(ceo, userId, "PERM_18_SHOOT_EXECUTION");

        String body = loginAs(emailFor("pubshoot", unique)).get("/app/my-performance").body();
        assertMarksVisible(body);
    }

    // ------------------------------------------------------------------ Case C: Publisher + Edit

    @Test
    void publisherPlusEditPermissionUserSeesMarksUI() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String userId = createUser(ceo, "pubedit", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, userId, "PERM_08_PUBLISHING_EXECUTION");
        grantPermission(ceo, userId, "PERM_19_EDIT_EXECUTION");

        String body = loginAs(emailFor("pubedit", unique)).get("/app/my-performance").body();
        assertMarksVisible(body);
    }

    // ------------------------------------------------------------------ Case D: Shoot + Edit

    @Test
    void shootPlusEditPermissionUserSeesMarksUI() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String userId = createUser(ceo, "shootedit", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, userId, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, userId, "PERM_19_EDIT_EXECUTION");

        String body = loginAs(emailFor("shootedit", unique)).get("/app/my-performance").body();
        assertMarksVisible(body);
    }

    // ------------------------------------------------------------------ Shoot-only / Edit-only (sanity)

    @Test
    void shootOnlyUserSeesMarksUI() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String userId = createUser(ceo, "shootonly", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, userId, "PERM_18_SHOOT_EXECUTION");

        String body = loginAs(emailFor("shootonly", unique)).get("/app/my-performance").body();
        assertMarksVisible(body);
    }

    @Test
    void editOnlyUserSeesMarksUI() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String userId = createUser(ceo, "editonly", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, userId, "PERM_19_EDIT_EXECUTION");

        String body = loginAs(emailFor("editonly", unique)).get("/app/my-performance").body();
        assertMarksVisible(body);
    }

    // ------------------------------------------------------------------ Case E: Model/Talent only

    /**
     * A Model/Talent participant with NO Shoot/Edit execution permission of their own - even with a
     * REAL completed task and a REAL decided mark on record (proving this isn't merely "no data to
     * show"), the marks UI stays fully hidden. Model/Talent participation itself must never be
     * treated as a mark-eligibility signal.
     */
    @Test
    void modelOnlyUserDoesNotSeeMarksUIEvenWithARealCompletedTaskAndMark() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camId = createUser(ceo, "modelcase-cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camId, "PERM_18_SHOOT_EXECUTION");
        String editorId = createUser(ceo, "modelcase-ed", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editorId, "PERM_19_EDIT_EXECUTION");
        String pubId = createUser(ceo, "modelcase-pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");
        String modelId = createUser(ceo, "modelonly", MODEL_ROLE_ID, unique);
        // Deliberately NO permission grant at all for the Model - participation-only, exactly Case E.

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MPMV ModelOnly " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/mpmv-modelonly-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"talentUserIds\":[\"" + modelId + "\"],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        String planId = plan.getId().toString();

        // Drive Shoot through to Approved so the Model's completed-task row genuinely exists in
        // buildPerformanceRows (isShootTaskCompleted), with a real decided mark (1.0) attached.
        TestApiClient cam = loginAs(emailFor("modelcase-cam", unique));
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "").statusCode()).isEqualTo(200);
        assertThat(cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "").statusCode()).isEqualTo(200);
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + editorId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        String body = loginAs(emailFor("modelonly", unique)).get("/app/my-performance").body();
        // The row genuinely exists (real data, not merely absent) - proves the hiding is purely
        // permission-driven, never an accidental side effect of "no completed work".
        assertThat(body).contains(plan.getContentId());
        assertThat(body).contains("Model");
        assertMarksHidden(body);
    }

    // ------------------------------------------------------------------ Layout adjustment sanity

    /**
     * The KPI card wrapper's own responsive column-count class switches between the existing
     * kpi-cards-3 (mark-eligible) and kpi-cards-2 (not eligible) classes - both already defined in
     * app.css for other KPI card groups, reused verbatim rather than a new layout.
     */
    @Test
    void kpiCardWrapperUsesExistingTwoCardLayoutClassWhenMarksHidden() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String pubId = createUser(ceo, "layoutcheck", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pubId, "PERM_08_PUBLISHING_EXECUTION");

        String body = loginAs(emailFor("layoutcheck", unique)).get("/app/my-performance").body();
        // Still exactly the two remaining cards, nothing removed beyond Total Marks.
        assertThat(body).contains("<span class=\"kpi-card-title\">Tasks Completed</span>");
        assertThat(body).contains("<span class=\"kpi-card-title\">Delayed Tasks</span>");
        assertMarksHidden(body);
    }
}
