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
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
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
    @Autowired
    ShootingAssignmentRepository shootingAssignmentRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void twoQualifyingCamerapersonsEachReceiveTheFullMarkNeverSplit() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam1Email = "e2e-multicam1-" + unique + "@kcpcbandhani.local";
        String cam1 = createUser(ceo, "Multi Camera 1", cam1Email, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String cam2 = createUser(ceo, "Multi Camera 2", "e2e-multicam2-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        // ENG-043: Start/Submit execution acts now require an actively assigned Cameraperson.
        TestApiClient cam1Client = new TestApiClient(port);
        cam1Client.login(cam1Email, "Passw0rd!");
        String multicamPub = createUser(ceo, "Multi Camera Publisher", "e2e-multicam-pub-" + unique + "@kcpcbandhani.local",
                PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (including the initial output+publication scope+shoot team) and transitions
        // straight to Shoot Assigned (SA), never PL/PLRV/PLAP. Both Camerapersons are assigned
        // together in this same call (multiple assignments coexist, exactly as before).
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Multi Camera " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/multicam-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cam1 + "\",\"" + cam2 + "\"],"
                        + "\"publisherUserIds\":[\"" + multicamPub + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        String contentPlanId = findContentPlanId(ideaId);

        cam1Client.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        cam1Client.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        String throwawayEdId = createUser(ceo, "Multi Camera Throwaway Ed", "multicam-throwaway-ed-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam1 + "\",\"" + cam2 + "\"],"
                        + "\"editorUserIds\":[\"" + throwawayEdId + "\"],\"leadEditorUserId\":\"" + throwawayEdId + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        var attributions = markAttributionRepository.findByContentPlan(plan).stream()
                .filter(a -> a.getRoleType() == RoleType.CAMERAPERSON)
                .toList();
        assertThat(attributions).hasSize(2);
        for (var a : attributions) {
            // BFD/BRS: full predefined Mark to EVERY qualifying contributor - never split/averaged.
            assertThat(a.getAttributedMarkValue()).isEqualByComparingTo(new BigDecimal("1.0"));
        }
    }

    /**
     * ENG-053: shooting_execution_participants gets a fresh row every time Submit for Shoot Review
     * runs (ERD-TBL-038) - a Request Rework returns to SIP, not SA, so the same Cameraperson can
     * submit for review a second time without ever being reassigned, adding a second participant
     * row for the same person. The Qualifying Cameraperson(s) picker on the Shoot Review Decision
     * screen must still show that Cameraperson exactly once.
     */
    @Test
    void qualifyingCamerapersonListNeverShowsDuplicatesAfterAReworkCycle() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-reworkdup-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Rework Dup Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        String reworkPub = createUser(ceo, "Rework Dup Publisher", "e2e-reworkdup-pub-" + unique + "@kcpcbandhani.local",
                PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Rework Dup " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/reworkdup-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cam + "\"],\"publisherUserIds\":[\"" + reworkPub + "\"]}}");
        String contentPlanId = findContentPlanId(ideaId);

        camClient.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", ""); // participant row #1
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":false,\"reason\":\"Lighting needs to be fixed, please reshoot\"}"); // SRV -> SIP, no reassignment
        camClient.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", ""); // participant row #2, same Cameraperson

        var plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SRV");

        HttpResponse<String> page = ceo.get("/app/deliverables/" + contentPlanId);
        String checkboxMarker = "value=\"" + cam + "\" data-name=\"Rework Dup Cam\"";
        int occurrences = page.body().split(java.util.regex.Pattern.quote(checkboxMarker), -1).length - 1;
        assertThat(occurrences).isEqualTo(1);
    }

    /**
     * ENG-054/spec §10: Reassign now uses the same permission-driven eligibility rule as initial
     * assignment (PERM_18_SHOOT_EXECUTION, scoped to SHOOTING) - not Business Role. A direct API
     * call reassigning SHOOTING to someone without an explicit PERM_18 grant is rejected server-
     * side regardless of their Business Role; someone who holds the grant is accepted.
     */
    @Test
    void reassignRejectsANewAssigneeWithoutTheMatchingBusinessRoleButAcceptsAMatchingOne() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam1 = createUser(ceo, "Reassign Cam 1", "e2e-reassign-cam1-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        String cam2 = createUser(ceo, "Reassign Cam 2", "e2e-reassign-cam2-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        // Deliberately no PERM_18 grant - proves rejection is permission-driven, not role-driven.
        String editor = createUser(ceo, "Reassign No Grant Editor", "e2e-reassign-ed-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID);

        // Workflow redesign: cam1 becomes the mandatory initial Shoot Team member assigned at Idea
        // Review approval itself, landing directly on Shoot Assigned (SA) - Reassign is eligible
        // while the canonical stage is still Shoot (SA counts), same as before.
        String contentPlanId = approveIdeaAndGetContentPlanId(ceo, "Reassign Role Check " + unique, cam1);

        // No explicit PERM_18 grant - not a valid SHOOTING reassignee - rejected, no assignment created.
        ceo.postFormMulti("/app/deliverables/" + contentPlanId + "/reassign", java.util.Map.of(
                "taskStage", List.of("SHOOTING"),
                "newAssigneeUserIds", List.of(editor),
                "reason", List.of("Wrong role test")));
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getCameraperson().getId().equals(UUID.fromString(editor)))).isFalse();

        // Holds an explicit PERM_18 grant - a valid SHOOTING reassignee - accepted.
        ceo.postFormMulti("/app/deliverables/" + contentPlanId + "/reassign", java.util.Map.of(
                "taskStage", List.of("SHOOTING"),
                "newAssigneeUserIds", List.of(cam2),
                "reason", List.of("Correct role test")));
        List<com.kcpc.mkt.production.domain.ShootingAssignment> active =
                shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getCameraperson().getId()).isEqualTo(UUID.fromString(cam2));
    }

    @Test
    void twoQualifyingEditorsEachReceiveTheFullMarkNeverSplit() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-multied-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Multi Ed Camera", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String ed1Email = "e2e-multied1-" + unique + "@kcpcbandhani.local";
        String ed1 = createUser(ceo, "Multi Editor 1", ed1Email, VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        String ed2 = createUser(ceo, "Multi Editor 2", "e2e-multied2-" + unique + "@kcpcbandhani.local", VIDEO_EDITOR_ROLE_ID,
                "PERM_19_EDIT_EXECUTION");
        // ENG-043: Start/Submit execution acts now require an actively assigned Cameraperson/Editor.
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        TestApiClient ed1Client = new TestApiClient(port);
        ed1Client.login(ed1Email, "Passw0rd!");
        String multiedPub = createUser(ceo, "Multi Editor IR Publisher", "e2e-multied-irpub-" + unique + "@kcpcbandhani.local",
                PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");

        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Multi Editor " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/multied-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cam + "\"],\"publisherUserIds\":[\"" + multiedPub + "\"]}}");
        String contentPlanId = findContentPlanId(ideaId);

        camClient.post("/api/v1/content-plans/" + contentPlanId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + contentPlanId + "/shooting/review/submit", "");
        // ed1 folds into this same Approve call (workflow redesign); ed2 is added separately below,
        // both assignments coexist as before.
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + ed1 + "\"],\"leadEditorUserId\":\"" + ed1 + "\"}");

        // Both Editors assigned (multiple assignments coexist), then both qualify at Edit Review.
        ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/assignments", "{\"editorUserId\":\"" + ed2 + "\"}");
        ed1Client.post("/api/v1/content-plans/" + contentPlanId + "/editing/start", "");
        ed1Client.post("/api/v1/content-plans/" + contentPlanId + "/editing/review/submit", "");
        String throwawayPubId = createUser(ceo, "Multi Editor Throwaway Pub", "multied-throwaway-pub-" + unique + "@kcpcbandhani.local",
                PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + ed1 + "\",\"" + ed2 + "\"],"
                        + "\"publisherUserIds\":[\"" + throwawayPubId + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanId)).orElseThrow();
        var attributions = markAttributionRepository.findByContentPlan(plan).stream()
                .filter(a -> a.getRoleType() == RoleType.EDITOR)
                .toList();
        assertThat(attributions).hasSize(2);
        for (var a : attributions) {
            assertThat(a.getAttributedMarkValue()).isEqualByComparingTo(new BigDecimal("1.0"));
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

    /**
     * Same as {@link #createUser(TestApiClient, String, String, String)} but also grants the given
     * explicit execution permission (PERM_18/19) immediately after creation - candidate eligibility
     * and execution are now permission-driven (OperationalEligibilityService), not Business-Role-
     * name-driven, so any fixture user meant to be assignable/executable needs this.
     */
    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId,
                               String executionPermission) throws Exception {
        String userId = createUser(ceo, fullName, email, businessRoleId);
        HttpResponse<String> grant = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + executionPermission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        if (grant.statusCode() != 201) {
            throw new IllegalStateException("Failed to grant " + executionPermission + " to " + userId + ": " + grant.body());
        }
        return userId;
    }

    /** Workflow redesign: Idea Review approval always requires at least one Cameraperson - this
     * default overload creates a throwaway one (irrelevant to the caller's assertions) so plain
     * "just give me an approved plan" call sites don't need to care. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title) throws Exception {
        long unique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String camId = createUser(ceo, "Default Cam " + unique, "e2e-default-cam-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        return approveIdeaAndGetContentPlanId(ceo, title, camId);
    }

    /** Workflow redesign: Idea Review approval carries every former Planning field (including the
     * initial Shoot Team) in one call and transitions straight to Shoot Assigned (SA), never
     * PL/PLRV/PLAP - the given cameraperson must already hold an active PERM_18_SHOOT_EXECUTION grant. */
    private String approveIdeaAndGetContentPlanId(TestApiClient ceo, String title, String camId) throws Exception {
        long unique = Instant.now().toEpochMilli() + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000);
        String publisherId = createUser(ceo, "Default Publisher " + unique,
                "e2e-default-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/hpe-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisherId + "\"]}}");
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
