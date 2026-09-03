package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.ContentPlanTalentEntry;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
import com.kcpc.mkt.production.domain.ShootingAssignment;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
import com.kcpc.mkt.publishing.repository.PublishingAssignmentRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.ReassignmentAssigneeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shoot Stage Reassignment: the "New Assignee(s)" form on Content Detail -> Action Center ->
 * Reassign (SHOOTING only) now also lets Model(s)/Talent be changed alongside Cameraperson(s) in
 * the same call ({@code AdminActionService#reassign}'s new {@code newModelUserIds} parameter).
 * Model(s)/Talent uses the same replace semantics Planning's own Model(s) picker already uses
 * (delete all {@link ContentPlanTalentEntry} rows for the plan, recreate from the submission -
 * {@code ContentPlanTalentEntryRepository#deleteByContentPlan}) rather than the "end + create new
 * row" pattern {@link ShootingAssignment} uses, since talent entries have no active/inactive
 * concept. A {@code null} {@code newModelUserIds} (field omitted entirely) leaves Model(s)/Talent
 * completely untouched - only a non-null list (including empty) replaces.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ShootReassignmentModelTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    ContentPlanTalentEntryRepository talentEntryRepository;
    @Autowired
    ShootingAssignmentRepository shootingAssignmentRepository;
    @Autowired
    EditingAssignmentRepository editingAssignmentRepository;
    @Autowired
    PublishingAssignmentRepository publishingAssignmentRepository;
    @Autowired
    ReassignmentAssigneeRepository reassignmentAssigneeRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String TARGET_1 = "01926e3e-000a-7000-8000-000000000001";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "srm-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"SRM " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"shoot reassignment model test fixture\"}");
        return user.get("userId").asText();
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"shoot reassignment model test fixture grant\"}");
    }

    private String createCameraperson(TestApiClient ceo, String label, long unique) throws Exception {
        String id = createUser(ceo, label, CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, id, "PERM_18_SHOOT_EXECUTION");
        return id;
    }

    private String createModel(TestApiClient ceo, String label, long unique) throws Exception {
        return createUser(ceo, label, MODEL_ROLE_ID, unique);
    }

    /** Approves a fresh Idea straight to Shoot Assigned (SA) with the given initial camera team and
     * model/talent team, plus a Publisher (mandatory at Planning approval - unrelated to this
     * feature, just satisfying that pre-existing requirement). */
    private ContentPlan approveToShootAssigned(TestApiClient ceo, String title, List<String> camIds,
                                                List<String> modelIds, String pubId) throws Exception {
        StringBuilder camJson = new StringBuilder();
        for (int i = 0; i < camIds.size(); i++) {
            if (i > 0) camJson.append(',');
            camJson.append('"').append(camIds.get(i)).append('"');
        }
        StringBuilder modelJson = new StringBuilder();
        for (int i = 0; i < modelIds.size(); i++) {
            if (i > 0) modelJson.append(',');
            modelJson.append('"').append(modelIds.get(i)).append('"');
        }
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/srm-" + title.replaceAll("\\s+", "-") + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\",\"publicationTargetIds\":[\"" + TARGET_1 + "\"]}],"
                        + "\"camerapersonUserIds\":[" + camJson + "],"
                        + "\"talentUserIds\":[" + modelJson + "],"
                        + "\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
    }

    private Set<UUID> activeCameraIds(ContentPlan plan) {
        return shootingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .map(a -> a.getCameraperson().getId()).collect(Collectors.toSet());
    }

    /** Overview's own Model(s) field row - isolated from the rest of the page, since the Reassign
     * form's Model(s)/Talent picker also lists every Model candidate (checked or not) as an option
     * label, which would otherwise make a page-wide "contains"/"does not contain" check on a
     * person's name meaningless. */
    private String modelsFieldRow(String body) {
        int labelIdx = body.indexOf("content-detail-field-label\">Model(s)</span>");
        int rowEnd = body.indexOf("</div>", labelIdx);
        return body.substring(labelIdx, rowEnd);
    }

    private Set<UUID> modelIds(ContentPlan plan) {
        return talentEntryRepository.findByContentPlan(plan).stream()
                .filter(t -> t.getTalentUser() != null)
                .map(t -> t.getTalentUser().getId()).collect(Collectors.toSet());
    }

    // ------------------------------------------------------------------ 1/2/3/4/5: both fields change, multi-value

    /** Mirrors the task's own worked example: Camera {Rohan,Vikram} -> {Rohan,Vikram,Amit}, Model
     * {Kat} -> {Kat,Ananya} - both changed in one call, multiple values on both sides. */
    @Test
    void bothCamerapersonsAndModelsCanBeChangedTogetherInOneCall() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String rohan = createCameraperson(ceo, "rohan", unique);
        String vikram = createCameraperson(ceo, "vikram", unique);
        String amit = createCameraperson(ceo, "amit", unique);
        String kat = createModel(ceo, "kat", unique);
        String ananya = createModel(ceo, "ananya", unique);
        String pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM Both " + unique, List.of(rohan, vikram), List.of(kat), pub);
        assertThat(activeCameraIds(plan)).containsExactlyInAnyOrder(UUID.fromString(rohan), UUID.fromString(vikram));
        assertThat(modelIds(plan)).containsExactly(UUID.fromString(kat));

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + rohan + "\",\"" + vikram + "\",\"" + amit + "\"],"
                        + "\"newModelUserIds\":[\"" + kat + "\",\"" + ananya + "\"],\"reason\":\"adding a third cameraperson and a second model\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(activeCameraIds(reloaded)).containsExactlyInAnyOrder(
                UUID.fromString(rohan), UUID.fromString(vikram), UUID.fromString(amit));
        assertThat(modelIds(reloaded)).containsExactlyInAnyOrder(UUID.fromString(kat), UUID.fromString(ananya));
    }

    // ------------------------------------------------------------------ Camera-only change: models untouched

    @Test
    void reassigningCamerapersonsOnlyLeavesModelsUntouchedWhenFieldOmitted() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam1", unique);
        String cam2 = createCameraperson(ceo, "cam2", unique);
        String kat = createModel(ceo, "kat2", unique);
        String pub = createUser(ceo, "pub2", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM CamOnly " + unique, List.of(cam1), List.of(kat), pub);

        // newModelUserIds deliberately absent from the JSON body entirely - the "field never
        // touched" case (e.g. an older API caller unaware of this new field).
        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam2 + "\"],\"reason\":\"camera swap only\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(activeCameraIds(reloaded)).containsExactly(UUID.fromString(cam2));
        assertThat(modelIds(reloaded)).containsExactly(UUID.fromString(kat));
    }

    // ------------------------------------------------------------------ Model-only change: camera resubmitted unchanged

    @Test
    void reassigningModelsOnlyUpdatesModelsWhileCamerapersonsStayTheSame() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam3", unique);
        String kat = createModel(ceo, "kat3", unique);
        String ananya = createModel(ceo, "ananya3", unique);
        String pub = createUser(ceo, "pub3", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM ModelOnly " + unique, List.of(cam1), List.of(kat), pub);

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam1 + "\"],"
                        + "\"newModelUserIds\":[\"" + ananya + "\"],\"reason\":\"model swap only\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(activeCameraIds(reloaded)).containsExactly(UUID.fromString(cam1));
        assertThat(modelIds(reloaded)).containsExactly(UUID.fromString(ananya));
    }

    // ------------------------------------------------------------------ 6/7: Publisher and Edit assignment untouched

    @Test
    void publisherAndEditAssignmentsAreUnaffectedByShootReassignment() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam4", unique);
        String cam2 = createCameraperson(ceo, "cam5", unique);
        String kat = createModel(ceo, "kat4", unique);
        String ananya = createModel(ceo, "ananya4", unique);
        String pub = createUser(ceo, "pub4", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM PubEdit " + unique, List.of(cam1), List.of(kat), pub);
        var publisherBefore = publishingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .map(a -> a.getPublisher().getId()).collect(Collectors.toSet());
        assertThat(publisherBefore).containsExactly(UUID.fromString(pub));
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(plan)).isEmpty();

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam2 + "\"],"
                        + "\"newModelUserIds\":[\"" + ananya + "\"],\"reason\":\"publisher/edit isolation check\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        var publisherAfter = publishingAssignmentRepository.findByContentPlanAndActiveTrue(reloaded).stream()
                .map(a -> a.getPublisher().getId()).collect(Collectors.toSet());
        assertThat(publisherAfter).isEqualTo(publisherBefore);
        assertThat(editingAssignmentRepository.findByContentPlanAndActiveTrue(reloaded)).isEmpty();
    }

    // ------------------------------------------------------------------ 8: workflow status/stage untouched

    @Test
    void workflowStatusIsUnchangedByShootReassignment() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam6", unique);
        String cam2 = createCameraperson(ceo, "cam7", unique);
        String kat = createModel(ceo, "kat5", unique);
        String pub = createUser(ceo, "pub5", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM Status " + unique, List.of(cam1), List.of(kat), pub);
        WorkflowStatus before = contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode();
        assertThat(before).isEqualTo(WorkflowStatus.SA);

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam2 + "\"],\"reason\":\"status must not move\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        WorkflowStatus after = contentPlanRepository.findById(plan.getId()).orElseThrow()
                .getWorkflowInstance().getCurrentStatusCode();
        assertThat(after).isEqualTo(before);
    }

    // ------------------------------------------------------------------ 9: reason/audit handling unchanged

    @Test
    void reasonIsStillMandatoryAndAuditRowsAreRecordedForBothRoles() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam8", unique);
        String cam2 = createCameraperson(ceo, "cam9", unique);
        String kat = createModel(ceo, "kat6", unique);
        String ananya = createModel(ceo, "ananya6", unique);
        String pub = createUser(ceo, "pub6", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM Audit " + unique, List.of(cam1), List.of(kat), pub);

        // Blank reason still rejected exactly as before this feature.
        HttpResponse<String> blankReason = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam2 + "\"],"
                        + "\"newModelUserIds\":[\"" + ananya + "\"],\"reason\":\"\"}");
        assertThat(blankReason.statusCode()).isEqualTo(400);

        int auditRowsBefore = reassignmentAssigneeRepository.findAll().size();
        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam2 + "\"],"
                        + "\"newModelUserIds\":[\"" + ananya + "\"],\"reason\":\"real reason this time\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        int auditRowsAfter = reassignmentAssigneeRepository.findAll().size();
        // 1 PREVIOUS + 1 NEW for the Cameraperson side, 1 PREVIOUS + 1 NEW for the Model side.
        assertThat(auditRowsAfter - auditRowsBefore).isEqualTo(4);
    }

    // ------------------------------------------------------------------ Overlapping person (both Camera and Model)

    /** The DB audit constraint change this feature required: a person who is simultaneously a
     * Cameraperson and a Model on the same Content Plan (a real, already-reachable state - see the
     * production screenshot this feature was requested from) must not collide when both roles are
     * reassigned in the same call (see V38__reassignment_model_role.sql). */
    @Test
    void personWhoIsBothCameraAndModelOnTheSamePlanCanBeReassignedInBothRolesAtOnce() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String overlap = createUser(ceo, "overlap", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, overlap, "PERM_18_SHOOT_EXECUTION");
        String replacementCam = createCameraperson(ceo, "replacementcam", unique);
        String replacementModel = createModel(ceo, "replacementmodel", unique);
        String pub = createUser(ceo, "pub7", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        // "overlap" starts as BOTH the sole Cameraperson AND the sole Model on this plan.
        ContentPlan plan = approveToShootAssigned(ceo, "SRM Overlap " + unique, List.of(overlap), List.of(overlap), pub);
        assertThat(activeCameraIds(plan)).containsExactly(UUID.fromString(overlap));
        assertThat(modelIds(plan)).containsExactly(UUID.fromString(overlap));

        // Keep "overlap" as Cameraperson (resubmitted) but replace them as Model, in one call - the
        // exact shape that would violate the pre-migration UNIQUE(reassignment_id, user_id, set_side)
        // (two PREVIOUS rows and, since Cameraperson keeps "overlap", also a NEW/CAMERAPERSON row
        // for the same user within one reassignment_id).
        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + overlap + "\",\"" + replacementCam + "\"],"
                        + "\"newModelUserIds\":[\"" + replacementModel + "\"],\"reason\":\"resolve the camera/model overlap\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(activeCameraIds(reloaded)).containsExactlyInAnyOrder(
                UUID.fromString(overlap), UUID.fromString(replacementCam));
        assertThat(modelIds(reloaded)).containsExactly(UUID.fromString(replacementModel));
    }

    // ------------------------------------------------------------------ 12: Content Detail Overview reflects the update

    @Test
    void contentDetailOverviewReflectsTheUpdatedModelsImmediately() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam10", unique);
        String kat = createModel(ceo, "katorig", unique);
        String ananya = createModel(ceo, "ananyanew", unique);
        String pub = createUser(ceo, "pub8", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM Overview " + unique, List.of(cam1), List.of(kat), pub);
        String beforeBody = ceo.get("/app/deliverables/" + plan.getId()).body();
        assertThat(modelsFieldRow(beforeBody)).contains("SRM katorig");

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + plan.getId() + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + cam1 + "\"],"
                        + "\"newModelUserIds\":[\"" + ananya + "\"],\"reason\":\"overview refresh check\"}");
        assertThat(response.statusCode()).isEqualTo(200);

        String afterBody = ceo.get("/app/deliverables/" + plan.getId()).body();
        assertThat(modelsFieldRow(afterBody)).contains("SRM ananyanew");
        assertThat(modelsFieldRow(afterBody)).doesNotContain("SRM katorig");
    }

    // ------------------------------------------------------------------ MVC form path (browser submission)

    @Test
    void mvcReassignFormAcceptsNewModelUserIdsAlongsideNewAssigneeUserIds() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String cam1 = createCameraperson(ceo, "cam11", unique);
        String cam2 = createCameraperson(ceo, "cam12", unique);
        String kat = createModel(ceo, "kat7", unique);
        String ananya = createModel(ceo, "ananya7", unique);
        String pub = createUser(ceo, "pub9", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub, "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = approveToShootAssigned(ceo, "SRM MvcForm " + unique, List.of(cam1), List.of(kat), pub);

        ceo.postFormMulti("/app/deliverables/" + plan.getId() + "/reassign", java.util.Map.of(
                "taskStage", List.of("SHOOTING"),
                "newAssigneeUserIds", List.of(cam2),
                "newModelUserIds", List.of(ananya),
                "reason", List.of("browser form submission")));

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(activeCameraIds(reloaded)).containsExactly(UUID.fromString(cam2));
        assertThat(modelIds(reloaded)).containsExactly(UUID.fromString(ananya));
    }
}
