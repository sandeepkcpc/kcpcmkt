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
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";

    @Test
    void shootingAssignmentAddIsIdempotentAndRemoveEndsItThenRemoveAgain404s() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createUser(ceo, "Idempotent Cam", "e2e-idem-cam-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID,
                "PERM_18_SHOOT_EXECUTION");
        String planId = approveIdeaAndGetContentPlanId(ceo, "Idempotent Shoot " + unique);

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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Edit Picker " + unique);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/editpicker-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");

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
     * service layer explicitly still allows assigning further Editors at EA. Reproduces the exact
     * sequence: assign editor 1 (SAP -> EA happens here), reload the page and assert the picker is
     * still present, then assign a second editor while already at EA.
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
        String planId = approveIdeaAndGetContentPlanId(ceo, "EA Reload " + unique);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/eareload-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");

        HttpResponse<String> assign1 = ceo.post("/api/v1/content-plans/" + planId + "/editing/assignments",
                "{\"editorUserId\":\"" + editor1 + "\"}");
        assertThat(assign1.statusCode()).isEqualTo(200);
        ContentPlan planAfterFirstAssign = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        assertThat(planAfterFirstAssign.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("EA");

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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Pub Picker " + unique);
        // ENG-043: Start/Submit execution acts now require an actively assigned Cameraperson/Editor.
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editorEmail, "Passw0rd!");

        // Attempting Publisher assignment before RFP (still in Planning) must be rejected as an
        // invalid workflow transition, not silently accepted.
        HttpResponse<String> tooEarly = ceo.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + publisher + "\"}");
        assertThat(tooEarly.statusCode()).isEqualTo(409);

        // Walk the plan to RFP.
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/pubpicker-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = plannedOutputIdFor(planId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + editor + "\"}");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Pub Exec " + unique);
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");
        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editorEmail, "Passw0rd!");
        TestApiClient pubAClient = new TestApiClient(port);
        pubAClient.login(pubAEmail, "Passw0rd!");

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/pubexec-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/outputs", "{\"outputType\":\"PHOTOGRAPHY\"}");
        String outputId = plannedOutputIdFor(planId);
        ceo.postJson("/api/v1/content-plans/outputs/" + outputId + "/publication-scope",
                "{\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + editor + "\"}");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor + "\"]}");

        // CEO (native) grants PERM_08 first - assignee-side eligibility is required before the
        // assignment itself will succeed - then assigns Publisher A, everything a working
        // Publisher needs.
        grantExecutionPermission(ceo, pubA, "PERM_08_PUBLISHING_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/publishing/assignments", "{\"publisherUserId\":\"" + pubA + "\"}");

        // Publisher A cannot assign a second Publisher, even though they hold PERM_08.
        HttpResponse<String> assignAttempt = pubAClient.post("/api/v1/content-plans/" + planId + "/publishing/assignments",
                "{\"publisherUserId\":\"" + pubB + "\"}");
        assertThat(assignAttempt.statusCode()).isEqualTo(403);

        // Publisher A cannot remove anyone's assignment either - not Publisher B (never assigned
        // anyway) and not even their own.
        HttpResponse<String> removeOwnAttempt = pubAClient.post(
                "/api/v1/content-plans/" + planId + "/publishing/assignments/" + pubA + "/remove", "");
        assertThat(removeOwnAttempt.statusCode()).isEqualTo(403);

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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Lead Shoot " + unique);
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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Lead Edit " + unique);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/editlead-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/assignments", "{\"editorUserId\":\"" + editor + "\"}");

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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Shoot Team " + unique);
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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Shoot Team Rollback " + unique);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();

        HttpResponse<String> combined = ceo.postFormMultiAjax("/app/deliverables/" + planId + "/shooting-assignments/team",
                Map.of("cameramanUserIds", java.util.List.of(cam), "leadUserId", java.util.List.of(notAssigned)));
        assertThat(combined.statusCode()).isEqualTo(400);
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();
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
        String planId = approveIdeaAndGetContentPlanId(ceo, "Edit Team " + unique);
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        TestApiClient camClient = new TestApiClient(port);
        camClient.login(camEmail, "Passw0rd!");

        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/editteam-" + unique + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + cam + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam + "\"]}");

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

    private String plannedOutputIdFor(String contentPlanIdText) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanIdText)).orElseThrow();
        return plannedOutputRepository.findByContentPlan(plan).stream()
                .findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
    }
}
