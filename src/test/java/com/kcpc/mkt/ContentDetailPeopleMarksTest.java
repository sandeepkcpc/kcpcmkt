package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Content Detail -> Overview -> People: each assigned contributor's DECIDED mark for THIS content
 * plan - the role-level {@code PredefinedRoleMarks} value (cameramanMark/editorMark/modelMark),
 * set once at Idea Review approval in the same transaction that creates the ContentPlan itself
 * (IdeaService#approve). Deliberately NOT {@code PersonalMarkAttribution} (a different, later
 * concept - the per-person mark actually awarded only once that stage's own Review approves,
 * i.e. after submission) - an earlier version of this feature used that source by mistake, which
 * made the mark appear only after submission/completion instead of immediately on assignment.
 * Reuses the existing {@code marks} model attribute already loaded by DeliverableMvcController
 * (previously unused in this JSP) - no new repository, no new marks storage, no change to how
 * marks are decided/corrected or how PersonalMarkAttribution itself is awarded.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ContentDetailPeopleMarksTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "cdpm-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"CDPM " + label + " " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"content detail people marks test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"content detail people marks test fixture grant\"}");
    }

    private ContentPlan planFor(String ideaId) {
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    private String rowFor(String body, String label) {
        int labelIdx = body.indexOf(label);
        int end = body.indexOf("</div>", labelIdx);
        assertThat(labelIdx).as("'%s' row must be present", label).isPositive();
        return body.substring(labelIdx, end);
    }

    /**
     * The exact scenario the fix targets: Model and Cameraperson are both assigned at Idea Review
     * approval - before Shoot has even started, let alone been submitted or approved. Both must
     * already show their decided mark. Editor is not assigned yet at this point (full-pipeline
     * Editor assignment only happens later, at Shoot Review Approve) - its row correctly stays
     * "-" because no one is assigned, not because of the mark itself.
     */
    @Test
    void overviewShowsDecidedMarkImmediatelyOnAssignmentBeforeAnySubmission() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "immediate", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] model = createUser(ceo, "immediatemodel", MODEL_ROLE_ID, unique);
        String[] publisher = createUser(ceo, "immediatepub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"CDPM Immediate " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/cdpm-immediate-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"talentUserIds\":[\"" + model[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");
        String planId = planFor(ideaId).getId().toString();

        // No shooting/start, no shooting/review/submit, no decision at all - straight from
        // approval to the Overview page.
        String body = ceo.get("/app/deliverables/" + planId).body();

        String camRow = rowFor(body, "Camera Person(s)");
        assertThat(camRow).contains("CDPM immediate " + unique).contains("1.0");

        String modelRow = rowFor(body, "Model(s)");
        assertThat(modelRow).contains("CDPM immediatemodel " + unique).contains("0.1");

        // Editor is genuinely unassigned at this point - "-" is correct assignment-visibility
        // behavior (unchanged by this fix), not a marks problem.
        String editorRow = rowFor(body, "Editor(s)");
        assertThat(editorRow).contains("&mdash;");
    }

    /**
     * Editor becomes assigned via the Shoot Review Approve fold-in - but Edit itself has not
     * started, been submitted, or been approved. The decided Editor mark must already be visible.
     */
    @Test
    void overviewShowsDecidedEditorMarkAsSoonAsEditorIsAssignedBeforeEditWorkBegins() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "edcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] editor = createUser(ceo, "edassign", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] publisher = createUser(ceo, "edassignpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"CDPM EdAssign " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/cdpm-edassign-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");
        String planId = planFor(ideaId).getId().toString();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam[0] + "\"],"
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}");

        // Editor is now assigned (plan is at EA) but has not started/submitted/been approved.
        String body = ceo.get("/app/deliverables/" + planId).body();
        String editorRow = rowFor(body, "Editor(s)");
        assertThat(editorRow).contains("CDPM edassign " + unique).contains("(Lead)").contains("0.5");
    }

    /**
     * Three camerapersons, one decided mark (1.0) - directly mirrors the spec's own example (kat /
     * Vikram Rao / Rohan Kapoor (Lead)). All three must show the SAME decided value independently,
     * immediately after assignment - not divided, not totalled, not waiting on Shoot to start.
     */
    @Test
    void multipleAssignedCamerapersonsAllShowTheSameDecidedMarkImmediately() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] camA = createUser(ceo, "three-a", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camA[0], "PERM_18_SHOOT_EXECUTION");
        String[] camB = createUser(ceo, "three-b", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camB[0], "PERM_18_SHOOT_EXECUTION");
        String[] camC = createUser(ceo, "three-c", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, camC[0], "PERM_18_SHOOT_EXECUTION");
        String[] publisher = createUser(ceo, "threepub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"CDPM Three " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/cdpm-three-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camA[0] + "\",\"" + camB[0] + "\",\"" + camC[0] + "\"],"
                        + "\"leadCamerapersonUserId\":\"" + camC[0] + "\",\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");
        String planId = planFor(ideaId).getId().toString();

        // No shoot activity at all - straight from approval.
        String body = ceo.get("/app/deliverables/" + planId).body();
        String camRow = rowFor(body, "Camera Person(s)");

        assertThat(camRow).contains("CDPM three-a " + unique);
        assertThat(camRow).contains("CDPM three-b " + unique);
        assertThat(camRow).contains("CDPM three-c " + unique).contains("(Lead)");
        // Same decided value rendered once per contributor - not divided (0.1/1.0/etc would be
        // wrong), not summed. Exactly 3 mark cells, all populated with "1.0", none dashed.
        long markCellCount = camRow.split("content-detail-people-mark", -1).length - 1;
        assertThat(markCellCount).isEqualTo(3);
        assertThat(camRow).doesNotContain("&mdash;");
        long markValueCount = camRow.split("1.0", -1).length - 1;
        assertThat(markValueCount).isEqualTo(3);
    }

    /**
     * The mark must still be shown after the stage's work is actually submitted/approved (this
     * fix only removes the WAIT - it must not remove the mark once real work catches up).
     */
    @Test
    void markRemainsDisplayedAfterShootIsSubmittedAndApproved() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "afterapproval", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] editor = createUser(ceo, "afterapprovaled", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] publisher = createUser(ceo, "afterapprovalpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"CDPM AfterApproval " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/cdpm-afterapproval-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");
        String planId = planFor(ideaId).getId().toString();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam[0] + "\"],"
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}");

        String body = ceo.get("/app/deliverables/" + planId).body();
        String camRow = rowFor(body, "Camera Person(s)");
        assertThat(camRow).contains("CDPM afterapproval " + unique).contains("1.0");
    }

    /** Business rules 2-4: only the raw decided value, never a total/max, anywhere on the page. */
    @Test
    void noTotalOrMaxMarkIsEverRendered() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "nototal", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] model = createUser(ceo, "nototalmodel", MODEL_ROLE_ID, unique);
        String[] publisher = createUser(ceo, "nototalpub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"CDPM NoTotal " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/cdpm-nototal-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"talentUserIds\":[\"" + model[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");
        String planId = planFor(ideaId).getId().toString();

        String body = ceo.get("/app/deliverables/" + planId).body();
        assertThat(body).doesNotContain("/10").doesNotContain("Out of 10").doesNotContain("out of 10");
    }
}
