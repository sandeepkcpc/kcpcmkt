package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.repository.PersonalMarkAttributionRepository;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.ContentPlanTalentEntryRepository;
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
 * ENG-096: the Team Marks section (Cameraperson/Editor/Model Mark) only applies to a role whose
 * corresponding production stage is actually part of the selected Stages -
 * Cameraperson/Model tied to Shoot, Editor tied to Edit (Shoot+Edit+Publishing or Edit+Publishing),
 * none of the three for Publishing-only. The UI-side fix (reviews-workspace.js/idea-detail.js
 * hiding/stripping the irrelevant fields) is covered by the JS test suite
 * (src/test/js/mark-section-stage-visibility.test.js); this file proves the backend is the actual
 * authority: IdeaService#approve only requires/validates a mark for a role whose stage is
 * included, stores an inert placeholder (NO_MARK = 0.0) for a skipped role regardless of what a
 * caller submits for it, and never creates a Model Mark attribution for a plan that skipped Shoot
 * even if talentUserIds is submitted anyway (the exact "stale hidden-field value" scenario a
 * hidden-but-not-disabled UI field could otherwise leak through).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MarkSectionStageVisibilityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PredefinedRoleMarksRepository predefinedRoleMarksRepository;
    @Autowired
    ContentPlanTalentEntryRepository talentEntryRepository;
    @Autowired
    PersonalMarkAttributionRepository personalMarkAttributionRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "marksvis-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MarksVis " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"marks visibility test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"marks visibility test fixture grant\"}");
    }

    private String createIdea(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MarksVis Idea " + unique + "\"}");
        return idea.get("ideaId").asText();
    }

    private HttpResponse<String> approve(TestApiClient ceo, String ideaId, String marksJson, String planningJson)
            throws Exception {
        return ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\"" + marksJson + ",\"planning\":" + planningJson + "}");
    }

    private ContentPlan planFor(String ideaId) {
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    // --- A / F: Shoot+Edit+Publishing unaffected - all 3 submitted marks stored exactly as given. ---
    @Test
    void fullPipelineStoresAllThreeSubmittedMarksUnchanged() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId,
                ",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1",
                "{\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/marksvis-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"SHOOT\",\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        var marks = predefinedRoleMarksRepository.findByContentPlan(planFor(ideaId)).orElseThrow();
        assertThat(marks.getPredefinedCameramanMark()).isEqualByComparingTo("1.0");
        assertThat(marks.getPredefinedEditorMark()).isEqualByComparingTo("0.5");
        assertThat(marks.getPredefinedModelMark()).isEqualByComparingTo("0.1");
    }

    // --- B: Edit+Publishing - only Editor Mark is required/stored as submitted; Camera/Model
    // become the inert NO_MARK placeholder without the caller ever sending them. ---
    @Test
    void directEditStoresOnlySubmittedEditorMarkCameraAndModelBecomeZero() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, ",\"editorMark\":0.5",
                "{\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/marksvis-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        var marks = predefinedRoleMarksRepository.findByContentPlan(planFor(ideaId)).orElseThrow();
        assertThat(marks.getPredefinedEditorMark()).isEqualByComparingTo("0.5");
        assertThat(marks.getPredefinedCameramanMark()).isEqualByComparingTo("0.0");
        assertThat(marks.getPredefinedModelMark()).isEqualByComparingTo("0.0");
    }

    // --- C: Publishing-only - no marks sent at all, all 3 become the inert NO_MARK placeholder. ---
    @Test
    void publishingOnlyStoresNoMarksEverythingBecomesZero() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "publisher", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisher[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, "",
                "{\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/marksvis-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        var marks = predefinedRoleMarksRepository.findByContentPlan(planFor(ideaId)).orElseThrow();
        assertThat(marks.getPredefinedCameramanMark()).isEqualByComparingTo("0.0");
        assertThat(marks.getPredefinedEditorMark()).isEqualByComparingTo("0.0");
        assertThat(marks.getPredefinedModelMark()).isEqualByComparingTo("0.0");
    }

    // --- Backend-authoritative proof: even if a Cameraperson/Model Mark IS submitted for a plan
    // that skips Shoot (the exact "stale hidden-field value" scenario), it is silently ignored -
    // never validated, never stored - never accidentally attributed. Approval is not rejected for
    // sending it; the extra values are simply discarded, exactly like camerapersonUserIds already
    // is for this same combination. ---
    @Test
    void directEditIgnoresACamerapersonOrModelMarkSubmittedAnywayNeverStoresIt() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId,
                ",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":1.0",
                "{\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/marksvis-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        var marks = predefinedRoleMarksRepository.findByContentPlan(planFor(ideaId)).orElseThrow();
        assertThat(marks.getPredefinedEditorMark()).isEqualByComparingTo("0.5");
        // The submitted 1.0 values for Cameraperson/Model must never land in storage - Shoot was
        // never part of this plan's pipeline.
        assertThat(marks.getPredefinedCameramanMark()).isEqualByComparingTo("0.0");
        assertThat(marks.getPredefinedModelMark()).isEqualByComparingTo("0.0");
    }

    // --- D/E guard: a stale Model/Talent selection submitted for a Shoot-skipped plan (e.g. left
    // checked from switching Stages away from Shoot) must never create a ContentPlanTalentEntry or
    // a PersonalMarkAttribution - Model participation only exists when Shoot is part of the
    // pipeline. ---
    @Test
    void directEditWithTalentUserIdsSubmittedAnywayCreatesNoModelAttribution() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        String[] model = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId, ",\"editorMark\":0.5",
                "{\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/marksvis-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"talentUserIds\":[\"" + model[0] + "\"],\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(talentEntryRepository.findByContentPlan(plan)).isEmpty();
        assertThat(personalMarkAttributionRepository.findByContentPlan(plan)).isEmpty();
    }

    // --- Regression sanity: the full pipeline's existing Model attribution behavior is unchanged -
    // talentUserIds submitted when Shoot IS part of the pipeline still creates the attribution. ---
    @Test
    void fullPipelineWithTalentStillCreatesModelAttributionUnchanged() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] model = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = createIdea(ceo, unique);

        HttpResponse<String> response = approve(ceo, ideaId,
                ",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":0.5",
                "{\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/marksvis-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],\"talentUserIds\":[\"" + model[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"SHOOT\",\"EDIT\",\"PUBLISHING\"]}");
        assertThat(response.statusCode()).isEqualTo(200);

        ContentPlan plan = planFor(ideaId);
        assertThat(talentEntryRepository.findByContentPlan(plan)).hasSize(1);
        var attributions = personalMarkAttributionRepository.findByContentPlan(plan);
        assertThat(attributions).hasSize(1);
        assertThat(attributions.get(0).getAttributedMarkValue()).isEqualByComparingTo("0.5");
    }
}
