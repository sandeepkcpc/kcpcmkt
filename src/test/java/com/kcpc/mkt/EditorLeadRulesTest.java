package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.production.repository.EditingAssignmentRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Editor Lead rules, consolidated: wherever Editor(s) are assigned, Editor Lead must be required,
 * mandatory-when-active, and constrained to the selected Editor(s) - exactly like the existing
 * Cameraperson(s) + Shoot Lead relationship. This now includes the Idea Review Direct Edit
 * assignment path (ENG-095): Direct Edit has no earlier Shoot Review Approve to fold Editor Lead
 * into, so it's the only checkpoint for that Content Plan's Edit team and requires a Lead here too
 * - superseding the earlier ENG-091 "no Lead here" decision. Direct Publishing keeps its own,
 * unrelated guarantee: Publisher(s) never have a Lead concept at all. Real HTTP, real Postgres, no
 * mocking - same convention as ShootEditNextTeamAssignmentTest, which already covers the
 * mandatory/must-be-selected rules for the normal Shoot Review Approve flow; this file adds the
 * equivalent coverage for Direct Edit and the Publisher-has-no-Lead guarantee.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EditorLeadRulesTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    EditingAssignmentRepository editingAssignmentRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, String permission, long unique)
            throws Exception {
        String email = "e2e-edlead-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"EdLead " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"editor lead rules test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"editor lead rules test grant\"}");
        return userId;
    }

    /** Idea -> approved -> Shoot Started -> Submitted for Shoot Review, ready for a Shoot Review decision. */
    private String buildToShootReview(TestApiClient ceo, String camId, String camEmail, long unique) throws Exception {
        String publisherId = createUser(ceo, "shootpub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Shoot " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edlead-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        String planId = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        cam.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        return planId;
    }

    // ============================================================ Shoot Review Approve (normal flow)

    @Test
    void shootReviewApproveRequiresEditorLeadWhenEditorsSelected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-edlead-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);
        String editorId = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"]}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Editor Lead is mandatory");
    }

    @Test
    void shootReviewApproveRejectsLeadNotAmongSelectedEditors() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-edlead-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);
        String editorId = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String otherEditorId = createUser(ceo, "editor2", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique + 1);

        HttpResponse<String> response = ceo.post("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + otherEditorId + "\"}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Editor Lead must be one of the selected Editor(s)");
    }

    @Test
    void shootReviewApproveSucceedsWithLeadAmongSelectedEditorsAndRecordsIt() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camId = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String camEmail = "e2e-edlead-cam-" + unique + "@kcpcbandhani.local";
        String planId = buildToShootReview(ceo, camId, camEmail, unique);
        String editorId = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);

        JsonNode response = ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + editorId + "\"}");
        assertThat(response.get("status").asText()).isEqualTo("EA");

        var plan = contentPlanRepository.findById(UUID.fromString(planId)).orElseThrow();
        boolean recordedAsLead = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan).stream()
                .anyMatch(a -> a.getEditor().getId().toString().equals(editorId) && a.isLead());
        assertThat(recordedAsLead).isTrue();
    }

    // ============================================================ Idea Review Direct Edit (ENG-095: Editor Lead now mandatory here too)

    /** 1. Direct Edit shows Editor(s) + Editor Lead: the rendered page carries both fields, the
     * Lead <select> nested inside the same .kcpc-model-picker as the Editor(s) checklist (required
     * for model-picker.js's refreshLeadOptions to scope its lookup correctly). */
    @Test
    void directEditIdeaReviewPageShowsEditorsAndEditorLead() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Direct Edit Page " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String detailPage = ceo.get("/app/ideas/" + ideaId).body();
        assertThat(detailPage).contains("Editor(s) *");
        assertThat(detailPage).contains("Editor Lead *");
        assertThat(detailPage).contains("id=\"ideaLeadEditor\"");
        assertThat(detailPage).contains("class=\"kcpc-lead-select\"");
        // Structural proof the Lead <select> is nested inside the Editor(s) picker, not a sibling
        // living elsewhere - mirrors the Cameraperson(s)/Shoot Lead pairing's same requirement.
        int pickerStart = detailPage.indexOf("id=\"idea-review-editor-assignment-section\"");
        int editorCheckbox = detailPage.indexOf("name=\"editorUserIds\"", pickerStart);
        int leadSelect = detailPage.indexOf("id=\"ideaLeadEditor\"", pickerStart);
        assertThat(pickerStart).isGreaterThanOrEqualTo(0);
        assertThat(editorCheckbox).isGreaterThan(pickerStart);
        assertThat(leadSelect).isGreaterThan(editorCheckbox);
    }

    /** 3. Editor Lead is mandatory. */
    @Test
    void directEditIdeaReviewRequiresEditorLead() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String editorId = createUser(ceo, "direct-editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Direct Edit " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // No leadEditorUserId field at all.
        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edlead-direct-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        assertThat(response.statusCode()).as("Direct Edit must require an Editor Lead").isEqualTo(400);
        assertThat(response.body()).contains("Editor Lead is mandatory");
    }

    /** 4/5. Invalid/non-selected Editor Lead is rejected server-side - the same check that backs
     * "clear the Lead if the previously selected lead is no longer among the selected Editors":
     * if a stale lead id is ever submitted anyway (bypassing the client-side reset), the server
     * still refuses it rather than silently accepting a Lead who isn't a selected Editor. */
    @Test
    void directEditIdeaReviewRejectsEditorLeadNotAmongSelectedEditors() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String editorId = createUser(ceo, "direct-editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String otherEditorId = createUser(ceo, "direct-editor2", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique + 1);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Direct Edit Invalid " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edlead-direct-inv-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editorId + "\"],\"leadEditorUserId\":\"" + otherEditorId + "\","
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Editor Lead must be one of the selected Editor(s)");
    }

    /** 6. Direct Edit approval successfully saves both the Editor assignment and the Editor Lead. */
    @Test
    void directEditIdeaReviewApprovalSavesEditorsAndEditorLead() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String editorId = createUser(ceo, "direct-editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String otherEditorId = createUser(ceo, "direct-editor2", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique + 1);
        String publisherId = createUser(ceo, "direct-save-pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Direct Edit Save " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edlead-direct-save-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editorId + "\",\"" + otherEditorId + "\"],"
                        + "\"leadEditorUserId\":\"" + editorId + "\",\"stages\":[\"EDIT\",\"PUBLISHING\"],"
                        + "\"publisherUserIds\":[\"" + publisherId + "\"]}}");
        assertThat(response.statusCode()).isEqualTo(200);

        var plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
        var assignments = editingAssignmentRepository.findByContentPlanAndActiveTrue(plan);
        assertThat(assignments).hasSize(2);
        assertThat(assignments.stream().anyMatch(a -> a.getEditor().getId().toString().equals(editorId) && a.isLead()))
                .as("editorId must be recorded as the Editor Lead").isTrue();
        assertThat(assignments.stream().anyMatch(a -> a.getEditor().getId().toString().equals(otherEditorId) && !a.isLead()))
                .as("otherEditorId must be assigned but NOT the lead").isTrue();
    }

    @Test
    void directEditIdeaReviewStillRequiresAtLeastOneEditor() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Direct Edit NoEditor " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edlead-direct-noed-" + unique + "\","
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("At least one Editor must be assigned");
    }

    // ============================================================ Publisher has no Lead concept

    @Test
    void directPublishingApprovalHasNoPublisherLeadRequirement() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String publisherId = createUser(ceo, "direct-pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"EdLead Direct Pub " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // Publisher(s) only, no lead-anything field for Publishing - it has never had a Lead concept.
        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/edlead-directpub-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisherId + "\"],\"stages\":[\"PUBLISHING\"]}}");
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
