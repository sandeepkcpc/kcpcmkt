package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
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
 * Regression coverage for the Model(s)-style chip-picker added to Shoot/Edit/Publishing
 * Assignment (docs/IMPLEMENTATION_DECISIONS.md ENG-035): idempotent add, remove, remove-when-
 * unassigned 404, and the brand-new Publishing Assignment's RFP-only window + PERM_08 gating.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssignmentPickerTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    ShootingAssignmentRepository shootingAssignmentRepository;
    @Autowired
    EditingAssignmentRepository editingAssignmentRepository;
    @Autowired
    PublishingAssignmentRepository publishingAssignmentRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void shootingAssignmentAddIsIdempotentAndRemoveEndsItThenRemoveAgain404s() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Idempotent Cam", "e2e-idem-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        // Workflow redesign: Idea Review approval always requires at least one Cameraperson - make
        // this test's own "cam" that mandatory initial assignee, so "add" below is exercised as the
        // (still meaningful) idempotent-re-add case rather than a genuinely-first assignment.
        String planId = approveIdeaAndGetContentPlanId(ceo, "Idempotent Shoot " + unique, cam);

        HttpResponse<String> add1 = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + cam + "\"}");
        HttpResponse<String> add2 = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + cam + "\"}");
        assertThat(add1.statusCode()).isEqualTo(200);
        assertThat(add2.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(1);

        HttpResponse<String> remove = ceo.post(
                "/api/v1/content-plans/" + planId + "/shooting-assignments/" + cam + "/remove", "");
        assertThat(remove.statusCode()).isEqualTo(200);
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();

        HttpResponse<String> removeAgain = ceo.post(
                "/api/v1/content-plans/" + planId + "/shooting-assignments/" + cam + "/remove", "");
        assertThat(removeAgain.statusCode()).isEqualTo(404);
    }

    @Test
    void editingAssignmentMvcAjaxAddIsIdempotentAndRemoveEndsIt() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-editpicker-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Edit Picker Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String editor = createUser(ceo, "Edit Picker Editor", "e2e-editpicker-ed-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        String planId = approveIdeaAndGetContentPlanId(ceo, "Edit Picker " + unique, cam);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        // Workflow redesign: Editor team assignment now folds directly into this same Approve call
        // (ShootingService#decideShootReview) - "editor" becomes the mandatory initial Editor here,
        // so the add1/add2 calls below exercise the standalone endpoint's idempotent re-add, same
        // reframing already applied to the initial Cameraperson elsewhere in this file.
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor + "\"],\"leadEditorUserId\":\"" + editor + "\"}");

        // MVC chip-picker's no-JS batch-add form field (plural), driven via the AJAX branch the
        // JS layer uses - proves the new field name works end to end, not just the legacy singular one.
        HttpResponse<String> add1 = ceo.postFormAjax("/app/deliverables/" + planId + "/editing/assignments",
                Map.of("editorUserIds", editor));
        HttpResponse<String> add2 = ceo.postFormAjax("/app/deliverables/" + planId + "/editing/assignments",
                Map.of("editorUserIds", editor));
        assertThat(add1.statusCode()).isEqualTo(200);
        assertThat(add2.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(1);

        HttpResponse<String> remove = ceo.postFormAjax("/app/deliverables/" + planId + "/editing/assignments/remove",
                Map.of("editorUserId", editor));
        assertThat(remove.statusCode()).isEqualTo(200);
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
    }

    /**
     * Real bug found from a user screenshot: assigning the first Editor auto-transitions the plan
     * SAP -> EA (EditingService.assignEditor's own existing behavior); the JSP's Editor Assignment
     * picker was gated on status == 'SAP' only, so on any page reload after that first assignment
     * the whole section (chips, checklist, AND the Edit Lead dropdown) vanished, even though the
     * service layer explicitly still allows assigning further Editors at EA. Workflow redesign:
     * Shoot Review Approve now folds in the first Editor directly (ShootingService#
     * decideShootReview), so the plan lands on EA with editor1 already assigned from the start,
     * rather than via a separate first assign call - the regression this test guards against
     * (the picker/Lead dropdown vanishing once status is EA, not SAP) is still fully exercised:
     * reload the page and assert the picker is still present at EA, then assign a second editor
     * while already at EA.
     */
    @Test
    void editAssignmentPickerAndLeadDropdownStayVisibleAndUsableAfterFirstEditorMovesStatusToEA() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-eareload-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "EA Reload Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String editor1 = createUser(ceo, "EA Reload Editor 1", "e2e-eareload-ed1-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        String editor2 = createUser(ceo, "EA Reload Editor 2", "e2e-eareload-ed2-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        String planId = approveIdeaAndGetContentPlanId(ceo, "EA Reload " + unique, cam);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        JsonNode shootApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor1 + "\"],\"leadEditorUserId\":\"" + editor1 + "\"}");
        assertThat(shootApproved.get("status").asText()).isEqualTo("EA");

        String pageAfterReload = ceo.get("/app/deliverables/" + planId).body();
        assertThat(pageAfterReload).contains("kcpc-assignment-picker").contains("Edit Lead").contains(editor1);

        HttpResponse<String> assign2 = ceo.postFormAjax("/app/deliverables/" + planId + "/editing/assignments",
                Map.of("editorUserIds", editor2));
        assertThat(assign2.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(2);
    }

    @Test
    void publishingAssignmentOnlyValidAtRfpAndRequiresPublishingPermission() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-pubpicker-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Pub Picker Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String editorEmail = "e2e-pubpicker-ed-" + unique + "@kcpcbandhani.local";
        String editor = createUser(ceo, "Pub Picker Editor", editorEmail, VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        String publisher = createUser(ceo, "Pub Picker Publisher", "e2e-pubpicker-pub-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID); // deliberately NOT the Publisher Business Role, to prove the 403 path below
        // Idea Review approval always requires at least one Publisher now - a throwaway one here,
        // distinct from "publisher" (the subject of the 403/permission narrative below).
        String ideaReviewPublisher = createUser(ceo, "Pub Picker IR Publisher",
                "e2e-pubpicker-irpub-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (including the initial output+publication scope+shoot team) and transitions
        // straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Pub Picker " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/pubpicker-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cam + "\"],\"publisherUserIds\":[\"" + ideaReviewPublisher + "\"]}}");
        String planId = findContentPlanId(ideaId);
        String outputId = plannedOutputIdFor(planId);
        // ENG-043: Start/Submit execution acts now require an actively assigned Cameraperson/Editor.
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editorEmail, "Passw0rd!");

        // Attempting Publisher assignment before RFP (still Shoot Assigned) must be rejected as an
        // invalid workflow transition, not silently accepted.
        HttpResponse<String> tooEarly = ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + publisher + "\"}");
        assertThat(tooEarly.statusCode()).isEqualTo(409);

        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        // Workflow redesign: Editor/Publisher team assignment now folds directly into the Shoot/
        // Edit Review Approve calls themselves. A throwaway Publisher (pre-granted PERM_08) is used
        // here specifically so "publisher"'s own no-permission-yet narrative below (tooEarly/
        // forbidden/granted-afterward) stays unaffected by this fold-in requirement.
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor + "\"],\"leadEditorUserId\":\"" + editor + "\"}");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        String throwawayPublisher = createUser(ceo, "Pub Picker Throwaway Publisher",
                "e2e-pubpicker-throwaway-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        grantExecutionPermission(ceo, throwawayPublisher, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor + "\"],"
                        + "\"publisherUserIds\":[\"" + throwawayPublisher + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");
        // Only "publisher" (the subject of the rest of this test) should end up as the active
        // Publisher, so the size==1 assertions below stay meaningful - remove both the Idea Review
        // fold-in Publisher and the Edit Review throwaway one.
        ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments/" + ideaReviewPublisher + "/remove", "");
        ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments/" + throwawayPublisher + "/remove", "");

        // ENG-044: Publisher assignment is native CEO/MM only now, regardless of PERM_08 - this
        // user has neither, so still 403 (the actual server-side reason flipped from "no PERM_08
        // grant" to "not native authority", but the boundary this test cares about - an ordinary
        // Employee cannot touch Publisher assignment - still holds).
        TestApiClient noPermUser = new TestApiClient(port);
        noPermUser.login("e2e-pubpicker-pub-" + unique + "@kcpcbandhani.local", "Passw0rd!");
        HttpResponse<String> forbidden = noPermUser.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + publisher + "\"}");
        assertThat(forbidden.statusCode()).isEqualTo(403);

        // Assignee-side eligibility (PERM_08, scoped to PUBLISHING) is now required regardless of
        // Business Role - grant it explicitly here to prove CEO can assign a non-canonical-role
        // user who holds the permission (the exact HR-Manager-style scenario permission-driven
        // eligibility is meant to support), not because Business Role stopped mattering entirely.
        grantExecutionPermission(ceo, publisher, "PERM_08_PUBLISHING_EXECUTION");

        // CEO (native authority) assigns - idempotent on a repeat call.
        HttpResponse<String> add1 = ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + publisher + "\"}");
        HttpResponse<String> add2 = ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + publisher + "\"}");
        assertThat(add1.statusCode()).isEqualTo(200);
        assertThat(add2.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(1);

        HttpResponse<String> remove = ceo.post(
                "/api/v1/content-plans/" + planId + "/publishing/assignments/" + publisher + "/remove", "");
        assertThat(remove.statusCode()).isEqualTo(200);
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();

        HttpResponse<String> removeAgain = ceo.post(
                "/api/v1/content-plans/" + planId + "/publishing/assignments/" + publisher + "/remove", "");
        assertThat(removeAgain.statusCode()).isEqualTo(404);
    }

    /**
     * ENG-044: a working Publisher (holds a PERM_08 grant AND is the actively assigned Publisher on
     * this plan - everything they need to execute their own task) must still be unable to touch
     * Publisher assignment at all - not assign a second Publisher, not remove anyone (including
     * themselves), not even see it via the API - while remaining fully able to execute (Start
     * Publishing) their own assignment. Proves the assignment/execution split lands exactly where
     * the user asked: "Publisher user ko sirf apna assigned work execute karna chahiye."
     */
    @Test
    void assignedPublisherWithPerm08CanExecuteButCannotTouchPublisherAssignment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-pubexec-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Pub Exec Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String editorEmail = "e2e-pubexec-ed-" + unique + "@kcpcbandhani.local";
        String editor = createUser(ceo, "Pub Exec Editor", editorEmail, VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        String pubAEmail = "e2e-pubexec-puba-" + unique + "@kcpcbandhani.local";
        String pubA = createUser(ceo, "Pub Exec Publisher A", pubAEmail, CAMERA_PERSON_ROLE_ID);
        String pubB = createUser(ceo, "Pub Exec Publisher B", "e2e-pubexec-pubb-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);
        // Idea Review approval always requires at least one Publisher now - a throwaway one here,
        // removed by CEO before the hasSize(1) assertion below so only pubA remains active.
        String ideaReviewPublisher = createUser(ceo, "Pub Exec IR Publisher",
                "e2e-pubexec-irpub-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field (including the initial output+publication scope+shoot team) and transitions
        // straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Pub Exec " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/pubexec-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + cam + "\"],\"publisherUserIds\":[\"" + ideaReviewPublisher + "\"]}}");
        String planId = findContentPlanId(ideaId);
        String outputId = plannedOutputIdFor(planId);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editorEmail, "Passw0rd!");
        TestApiClient pubAClient = new TestApiClient(port);
        pubAClient.login(pubAEmail, "Passw0rd!");

        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        // Workflow redesign: Editor/Publisher team assignment now folds directly into the Shoot/
        // Edit Review Approve calls themselves - assignee-side eligibility (PERM_08) is required
        // before Edit Review Approve will succeed, so grant it to Publisher A before that call
        // rather than after, same requirement as before this redesign, just moved earlier.
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor + "\"],\"leadEditorUserId\":\"" + editor + "\"}");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        grantExecutionPermission(ceo, pubA, "PERM_08_PUBLISHING_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor + "\"],"
                        + "\"publisherUserIds\":[\"" + pubA + "\"]}");

        // Publisher A cannot assign a second Publisher, even though they hold PERM_08.
        HttpResponse<String> assignAttempt = pubAClient.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubB + "\"}");
        assertThat(assignAttempt.statusCode()).isEqualTo(403);

        // Publisher A cannot remove anyone's assignment either - not Publisher B (never assigned
        // anyway) and not even their own.
        HttpResponse<String> removeOwnAttempt = pubAClient.post(
                "/api/v1/content-plans/" + planId + "/publishing/assignments/" + pubA + "/remove", "");
        assertThat(removeOwnAttempt.statusCode()).isEqualTo(403);

        // Only pubA (the subject of this test) should end up as the active Publisher.
        ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments/" + ideaReviewPublisher + "/remove", "");

        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).hasSize(1);

        // But Publisher A CAN still execute their own assigned work - Start Publishing succeeds.
        HttpResponse<String> start = pubAClient.post("/api/v1/content-plans/" + planId + "/publishing/start", "");
        assertThat(start.statusCode()).isEqualTo(200);

        ContentPlan afterStart = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(afterStart.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PUBG");
    }

    @Test
    void shootLeadMustBeAnActiveAssigneeAndClearsWhenThatAssigneeIsRemoved() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam1 = createUser(ceo, "Lead Cam 1", "e2e-lead-cam1-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        String cam2 = createUser(ceo, "Lead Cam 2", "e2e-lead-cam2-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        String notAssigned = createUser(ceo, "Lead Not Assigned", "e2e-lead-notassigned-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);
        // Workflow redesign: cam1 becomes the mandatory initial Shoot Team member assigned at Idea
        // Review approval itself - the add call below is then an idempotent re-add, same as before.
        String planId = approveIdeaAndGetContentPlanId(ceo, "Lead Shoot " + unique, cam1);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam1 + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam2 + "\"}");

        // Someone not on the assignment at all is rejected, not silently accepted.
        HttpResponse<String> invalidLead = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments/lead",
                "{\"cameramanUserId\":\"" + notAssigned + "\"}");
        assertThat(invalidLead.statusCode()).isEqualTo(400);

        HttpResponse<String> setCam1 = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments/lead",
                "{\"cameramanUserId\":\"" + cam1 + "\"}");
        assertThat(setCam1.statusCode()).isEqualTo(200);
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getCameraperson().getId().toString().equals(cam1)).findFirst().orElseThrow().isLead()).isTrue();

        // Switching the Lead to cam2 must leave at most one active Lead (the DB partial unique
        // index would reject a second concurrently-true row - this proves the service clears the
        // old one first).
        HttpResponse<String> setCam2 = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments/lead",
                "{\"cameramanUserId\":\"" + cam2 + "\"}");
        assertThat(setCam2.statusCode()).isEqualTo(200);
        var activeAfterSwitch = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(activeAfterSwitch.stream().filter(com.kcpc.mkt.production.domain.ShootingAssignment::isLead)).hasSize(1);
        assertThat(activeAfterSwitch.stream()
                .filter(com.kcpc.mkt.production.domain.ShootingAssignment::isLead).findFirst().orElseThrow()
                .getCameraperson().getId().toString()).isEqualTo(cam2);

        // Removing the current Lead's assignment must clear leadership - no active row is Lead afterward.
        HttpResponse<String> removeLead = ceo.post(
                "/api/v1/content-plans/" + planId + "/shooting-assignments/" + cam2 + "/remove", "");
        assertThat(removeLead.statusCode()).isEqualTo(200);
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .noneMatch(com.kcpc.mkt.production.domain.ShootingAssignment::isLead)).isTrue();

        // Clearing an already-clear Lead (null) is a safe no-op, not an error.
        HttpResponse<String> clearAgain = ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments/lead",
                "{}");
        assertThat(clearAgain.statusCode()).isEqualTo(200);
    }

    @Test
    void editLeadMustBeAnActiveAssignee() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-editlead-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Edit Lead Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String editor = createUser(ceo, "Edit Lead Editor", "e2e-editlead-ed-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        String notAssigned = createUser(ceo, "Edit Lead Not Assigned", "e2e-editlead-notassigned-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID);
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        String planId = approveIdeaAndGetContentPlanId(ceo, "Lead Edit " + unique, cam);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor + "\"],\"leadEditorUserId\":\"" + editor + "\"}");

        HttpResponse<String> invalidLead = ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments/lead",
                "{\"editorUserId\":\"" + notAssigned + "\"}");
        assertThat(invalidLead.statusCode()).isEqualTo(400);

        HttpResponse<String> setLead = ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments/lead",
                "{\"editorUserId\":\"" + editor + "\"}");
        assertThat(setLead.statusCode()).isEqualTo(200);
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .filter(a -> a.getEditor().getId().toString().equals(editor)).findFirst().orElseThrow().isLead()).isTrue();
    }

    /**
     * ENG-041: the chip-picker's single "Assign Cameraperson(s)" button replaced the old two-button
     * assign-then-set-lead flow. This drives the new combined {@code /shooting-assignments/team}
     * endpoint exactly as the JS layer now does - one request assigns both Camerapersons and sets
     * the Shoot Lead together - and separately proves the "baaki selected assignees ka is_lead =
     * false" half by re-submitting with a different Lead and no new assignees.
     */
    @Test
    void shootTeamSingleRequestAssignsCamerapersonsAndSetsLeadTogether() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam1 = createUser(ceo, "Team Cam 1", "e2e-team-cam1-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        String cam2 = createUser(ceo, "Team Cam 2", "e2e-team-cam2-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        // Workflow redesign: cam1 becomes the mandatory initial Shoot Team member assigned at Idea
        // Review approval itself - assignCameraperson is idempotent, so re-assigning cam1 via the
        // team endpoint below is a no-op and the final active count is still exactly 2 (cam1+cam2).
        String planId = approveIdeaAndGetContentPlanId(ceo, "Shoot Team " + unique, cam1);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        HttpResponse<String> combined = ceo.postFormMulti("/app/deliverables/" + planId + "/shooting-assignments/team",
                Map.of("cameramanUserIds", java.util.List.of(cam1, cam2), "leadUserId", java.util.List.of(cam1)));
        assertThat(combined.statusCode()).isEqualTo(302);

        var active = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(2);
        assertThat(active.stream().filter(a -> a.getCameraperson().getId().toString().equals(cam1))
                .findFirst().orElseThrow().isLead()).isTrue();
        assertThat(active.stream().filter(a -> a.getCameraperson().getId().toString().equals(cam2))
                .findFirst().orElseThrow().isLead()).isFalse();

        // Re-submit with no new assignees, just a different Lead - proves the "other selected
        // assignees' is_lead = false" half of the single-button contract, not just the initial set.
        HttpResponse<String> leadOnly = ceo.postFormMulti("/app/deliverables/" + planId + "/shooting-assignments/team",
                Map.of("leadUserId", java.util.List.of(cam2)));
        assertThat(leadOnly.statusCode()).isEqualTo(302);
        var activeAfterLeadSwitch = shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(activeAfterLeadSwitch.stream().filter(a -> a.getCameraperson().getId().toString().equals(cam1))
                .findFirst().orElseThrow().isLead()).isFalse();
        assertThat(activeAfterLeadSwitch.stream().filter(a -> a.getCameraperson().getId().toString().equals(cam2))
                .findFirst().orElseThrow().isLead()).isTrue();
    }

    /**
     * "Ek hi transaction/request me" (user's explicit requirement): if the Lead half of the combined
     * call is invalid, the whole request must roll back - the newly-staged Cameraperson must NOT end
     * up assigned either, even though the assign step runs first inside the same method.
     */
    @Test
    void shootTeamInvalidLeadRollsBackTheNewlyStagedAssigneeToo() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Rollback Cam", "e2e-team-rollback-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        String notAssigned = createUser(ceo, "Rollback Not Assigned", "e2e-team-rollback-na-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID);
        // Workflow redesign: approval always requires its own mandatory initial Cameraperson -
        // use a throwaway one (distinct from "cam", which must stay genuinely unassigned so the
        // rollback assertion below still proves the newly-staged assignee never lands).
        String throwawayCam = createUser(ceo, "Rollback Throwaway Cam", "e2e-team-rollback-throwaway-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Shoot Team Rollback " + unique, throwawayCam);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        HttpResponse<String> combined = ceo.postFormMultiAjax("/app/deliverables/" + planId + "/shooting-assignments/team",
                Map.of("cameramanUserIds", java.util.List.of(cam), "leadUserId", java.util.List.of(notAssigned)));
        assertThat(combined.statusCode()).isEqualTo(400);
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .noneMatch(a -> a.getCameraperson().getId().toString().equals(cam)))
                .as("the newly-staged assignee must not end up assigned after the Lead half rolls back").isTrue();
    }

    /** Edit-side equivalent of {@link #shootTeamSingleRequestAssignsCamerapersonsAndSetsLeadTogether}. */
    @Test
    void editTeamSingleRequestAssignsEditorsAndSetsLeadTogether() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String camEmail = "e2e-editteam-cam-" + unique + "@kcpcbandhani.local";
        String cam = createUser(ceo, "Edit Team Cam", camEmail, CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String editor1 = createUser(ceo, "Edit Team Editor 1", "e2e-editteam-ed1-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        String editor2 = createUser(ceo, "Edit Team Editor 2", "e2e-editteam-ed2-" + unique + "@kcpcbandhani.local",
                VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION");
        // Workflow redesign: Planning is folded into Idea Review - approval carries every former
        // Planning field and transitions straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        String planId = approveIdeaAndGetContentPlanId(ceo, "Edit Team " + unique, cam);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        // Workflow redesign: editor1 becomes the mandatory initial Editor folded into this same
        // Approve call - the combined team call below then re-adds editor1 (idempotent) and adds
        // editor2 fresh, same final state (editor1+editor2 active, editor1 Lead) as before.
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"],"
                        + "\"editorUserIds\":[\"" + editor1 + "\"],\"leadEditorUserId\":\"" + editor1 + "\"}");

        HttpResponse<String> combined = ceo.postFormMulti("/app/deliverables/" + planId + "/editing/assignments/team",
                Map.of("editorUserIds", java.util.List.of(editor1, editor2), "leadUserId", java.util.List.of(editor1)));
        assertThat(combined.statusCode()).isEqualTo(302);

        var active = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(active).hasSize(2);
        assertThat(active.stream().filter(a -> a.getEditor().getId().toString().equals(editor1))
                .findFirst().orElseThrow().isLead()).isTrue();
        assertThat(active.stream().filter(a -> a.getEditor().getId().toString().equals(editor2))
                .findFirst().orElseThrow().isLead()).isFalse();
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return response.get("userId").asText();
    }

    /**
     * Same as {@link #createUser(TestApiClient, String, String, String)} but also grants the given
     * explicit execution permission (PERM_18/19/08) immediately after creation - candidate
     * eligibility and execution are now permission-driven (OperationalEligibilityService), not
     * Business-Role-name-driven, so any fixture user meant to be assignable/executable needs this.
     */
    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId,
                               String executionPermission) throws Exception {
        String userId = createUser(ceo, fullName, email, businessRoleId);
        grantExecutionPermission(ceo, userId, executionPermission);
        return userId;
    }

    private void grantExecutionPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        HttpResponse<String> grant = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        if (grant.statusCode() != 201) {
            throw new IllegalStateException("Failed to grant " + permissionCode + " to " + userId + ": " + grant.body());
        }
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
        long unique = Instant.now().toEpochMilli();
        String publisherId = createUser(ceo, "APT Default Publisher " + unique,
                "apt-default-pub-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/apt-" + Instant.now().toEpochMilli() + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        return findContentPlanId(ideaId);
    }

    private String findContentPlanId(String ideaIdText) {
        UUID ideaId = UUID.fromString(ideaIdText);
        Idea idea = ideaRepository.findById(ideaId).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        return plan.getId().toString();
    }

    private String plannedOutputIdFor(String contentPlanIdText) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanIdText)).orElseThrow();
        return plannedOutputRepository.findByContentPlan(plan).stream()
                .findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
    }
}
