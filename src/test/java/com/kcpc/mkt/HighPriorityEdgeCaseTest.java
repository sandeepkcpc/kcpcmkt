package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.domain.RoleType;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Continuation-Prompt (2) §16 high-priority edge cases not yet covered by any existing test:
 * multi-contributor Mark attribution (full amount to each qualifying contributor, never split),
 * and delegated permission-grant expiry / revocation / out-of-scope rejection - as opposed to
 * {@code PermissionBoundaryTest}, which only covers "no grant at all".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HighPriorityEdgeCaseTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PersonalMarkAttributionRepository markAttributionRepository;
    @Autowired
    PermissionGrantRepository permissionGrantRepository;
    @Autowired
    UserRepository userRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void twoQualifyingCamerapersonsEachReceiveTheFullMarkNeverSplit() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam1 = createUser(ceo, "Multi Camera 1", "e2e-multicam1-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        String cam2 = createUser(ceo, "Multi Camera 2", "e2e-multicam2-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Multi Camera " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":2.0,\"editorMark\":1.0}");
        assertThat(approved.get("status").asText()).isEqualTo("PL");
        String contentPlanId = findContentPlanId(ideaId);

        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/multicam-" + unique + "\"}");
        // Both Camerapersons assigned during Planning (multiple assignments coexist).
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam1 + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam2 + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = findPlannedOutputId(contentPlanId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}");

        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam1 + "\",\"" + cam2 + "\"]}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("SAP");

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        var attributions = markAttributionRepository.findByContentPlan(plan).stream()
                .filter(a -> a.getRoleType() == RoleType.CAMERAPERSON)
                .toList();
        assertThat(attributions).hasSize(2);
        for (var a : attributions) {
            // BFD/BRS: full predefined Mark to EVERY qualifying contributor - never split/averaged.
            assertThat(a.getAttributedMarkValue()).isEqualByComparingTo(new BigDecimal("2.0"));
        }
    }

    @Test
    void twoQualifyingEditorsEachReceiveTheFullMarkNeverSplit() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Multi Ed Camera", "e2e-multied-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        String ed1 = createUser(ceo, "Multi Editor 1", "e2e-multied1-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);
        String ed2 = createUser(ceo, "Multi Editor 2", "e2e-multied2-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Multi Editor " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":3.0}");
        String contentPlanId = findContentPlanId(ideaId);

        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/multied-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = findPlannedOutputId(contentPlanId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}");

        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/planning-review/decision", "{\"approve\":true}");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");

        // Both Editors assigned (multiple assignments coexist), then both qualify at Edit Review.
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments", "{\"editorUserId\":\"" + ed1 + "\"}");
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments", "{\"editorUserId\":\"" + ed2 + "\"}");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ceo.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + ed1 + "\",\"" + ed2 + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        var attributions = markAttributionRepository.findByContentPlan(plan).stream()
                .filter(a -> a.getRoleType() == RoleType.EDITOR)
                .toList();
        assertThat(attributions).hasSize(2);
        for (var a : attributions) {
            assertThat(a.getAttributedMarkValue()).isEqualByComparingTo(new BigDecimal("3.0"));
        }
    }

    @Test
    void expiredDelegatedPermissionGrantIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "e2e-expired-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Expired Grant Employee", email, CAMERA_PERSON_ROLE_ID);

        Instant from = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant until = Instant.now().minus(5, ChronoUnit.DAYS);
        HttpResponse<String> grantResponse = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_02_PLANNING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"effectiveFrom\":\"" + from + "\",\"effectiveUntil\":\"" + until
                        + "\",\"reason\":\"e2e expired-grant test\"}");
        assertThat(grantResponse.statusCode()).isEqualTo(201);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Expired Grant Flow " + unique);

        TestApiClient employee = new TestApiClient(port);
        employee.login(email, "Passw0rd!");
        HttpResponse<String> attempt = employee.post("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        assertThat(attempt.statusCode()).isEqualTo(403);
        assertThat(attempt.body()).contains("PERM_OPERATIONAL_PERMISSION_EXPIRED");
    }

    @Test
    void revokedDelegatedPermissionGrantIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "e2e-revoked-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Revoked Grant Employee", email, CAMERA_PERSON_ROLE_ID);

        HttpResponse<String> grantResponse = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_02_PLANNING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e revoked-grant test\"}");
        assertThat(grantResponse.statusCode()).isEqualTo(201);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Revoked Grant Flow " + unique);

        // Prove the grant works BEFORE revocation - otherwise a false pass could hide an unrelated 403.
        TestApiClient employee = new TestApiClient(port);
        employee.login(email, "Passw0rd!");
        HttpResponse<String> beforeRevoke = employee.post("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        assertThat(beforeRevoke.statusCode()).isEqualTo(200);

        User employeeEntity = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        var grant = permissionGrantRepository.findByGrantee(employeeEntity).stream()
                .filter(g -> g.getPermission().name().equals("PERM_02_PLANNING_EXECUTION"))
                .findFirst().orElseThrow();
        HttpResponse<String> revokeResponse = ceo.post("/api/v1/admin/permission-grants/" + grant.getId() + "/revoke",
                "{\"reason\":\"e2e revoke test\"}");
        assertThat(revokeResponse.statusCode()).isEqualTo(200);

        HttpResponse<String> afterRevoke = employee.post("/api/v1/content-plans/" + contentPlanId + "/schedule/urgent",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(1) + "\",\"shootDate\":\"" + LocalDate.now()
                        + "\",\"editDate\":\"" + LocalDate.now() + "\",\"urgencyReason\":\"after-revoke attempt\"}");
        assertThat(afterRevoke.statusCode()).isEqualTo(403);
        // AuthorizationService fetches only active=true grants (findByGranteeAndPermissionAndActiveTrue);
        // a revoked grant is invisible to that query, so it is correctly treated as "never granted"
        // (PERM_OPERATIONAL_PERMISSION_REQUIRED), not "expired" - distinct from the still-fetched-but-
        // time-lapsed case covered by expiredDelegatedPermissionGrantIsRejected() above.
        assertThat(afterRevoke.body()).contains("PERM_OPERATIONAL_PERMISSION_REQUIRED");
    }

    @Test
    void stageRestrictedGrantOutsideItsScopeIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "e2e-outofscope-" + unique + "@kcpcbandhani.local";
        String userId = createUser(ceo, "Out Of Scope Employee", email, CAMERA_PERSON_ROLE_ID);

        // Grant PERM_02 (Planning Execution) restricted to the SHOOTING stage only - Planning-
        // stage actions require LifecycleStage.PLANNING context, which this grant does not cover.
        HttpResponse<String> grantResponse = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_02_PLANNING_EXECUTION\","
                        + "\"scopeType\":\"STAGE_RESTRICTED\",\"stages\":[\"SHOOTING\"],"
                        + "\"reason\":\"e2e out-of-scope test\"}");
        assertThat(grantResponse.statusCode()).isEqualTo(201);

        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Out Of Scope Flow " + unique);

        TestApiClient employee = new TestApiClient(port);
        employee.login(email, "Passw0rd!");
        HttpResponse<String> attempt = employee.post("/api/v1/content-plans/" + contentPlanId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        assertThat(attempt.statusCode()).isEqualTo(403);
        assertThat(attempt.body()).contains("PERM_OPERATIONAL_PERMISSION_OUT_OF_SCOPE");
    }

    // --- helpers ---------------------------------------------------------------------------

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return response.get("userId").asText();
    }

    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        return findContentPlanId(ideaId);
    }

    private String findContentPlanId(String ideaIdText) {
        UUID ideaId = UUID.fromString(ideaIdText);
        Idea idea = ideaRepository.findById(ideaId).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        return plan.getId().toString();
    }

    private String findPlannedOutputId(String contentPlanIdText) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanIdText)).orElseThrow();
        return plannedOutputRepository.findByContentPlan(plan).stream()
                .findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
    }
}
