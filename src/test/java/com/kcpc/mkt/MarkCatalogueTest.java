package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.marks.domain.MarkCatalogueEntry;
import com.kcpc.mkt.marks.repository.MarkCatalogueEntryRepository;
import com.kcpc.mkt.marks.repository.PredefinedRoleMarksRepository;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mark Catalogue (ENG-092): AdminMvcController's /app/admin/marks CRUD (MarkCatalogueService),
 * and IdeaService validating Cameraperson/Editor/Model Mark values against the live catalogue
 * instead of the old hardcoded [0, 0.5, 1.0, 2.0, 3.0] list. Real HTTP, real Postgres, no mocking -
 * same convention as EditUserFlowTest/SkipStageFlowTest/IdeaApprovalStagesTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MarkCatalogueTest {

    @LocalServerPort
    int port;

    @Autowired
    MarkCatalogueEntryRepository markCatalogueEntryRepository;
    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PredefinedRoleMarksRepository predefinedRoleMarksRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "markcat-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MarkCat " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"mark catalogue test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    @Test
    void ceoCanCreateEditAndDeleteMarkEntry() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        // NUMERIC(3,1) - exactly one decimal digit, built as an exact string (no floating-point
        // arithmetic) so it round-trips through the form POST/DB without rounding surprises.
        String value = markValueString(40 + (int) (unique % 60));

        HttpResponse<String> create = ceo.postForm("/app/admin/marks",
                Map.of("roleType", "CAMERAPERSON", "markValue", value, "catalogueReason", "test create"));
        assertThat(create.statusCode()).isEqualTo(302);

        String marksPage = ceo.get("/app/admin/marks").body();
        assertThat(marksPage).contains("Cameraperson Mark");

        MarkCatalogueEntry entry = markCatalogueEntryRepository.findAllByOrderByRoleTypeAscMarkValueAsc().stream()
                .filter(e -> e.getMarkValue().compareTo(new java.math.BigDecimal(value)) == 0).findFirst().orElseThrow();
        assertThat(entry.isActive()).isTrue();

        HttpResponse<String> update = ceo.postForm("/app/admin/marks/" + entry.getId(),
                Map.of("isActive", "false", "catalogueReason", "test deactivate"));
        assertThat(update.statusCode()).isEqualTo(302);
        assertThat(markCatalogueEntryRepository.findById(entry.getId()).orElseThrow().isActive()).isFalse();

        HttpResponse<String> delete = ceo.postForm("/app/admin/marks/" + entry.getId() + "/delete",
                Map.of("catalogueReason", "test delete"));
        assertThat(delete.statusCode()).isEqualTo(302);
        assertThat(markCatalogueEntryRepository.findById(entry.getId())).isEmpty();
    }

    @Test
    void nonCeoCannotAccessMarkCatalogue() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        // Marketing Manager holds native management authority elsewhere in the app, but Mark
        // Catalogue is deliberately CEO_OWNER-only, no delegation (ENG-092 design decision).
        String[] mm = createUser(ceo, "mm", MARKETING_MANAGER_ROLE_ID, unique);
        TestApiClient mmClient = new TestApiClient(port);
        mmClient.login(mm[1], "Passw0rd!");

        assertThat(mmClient.get("/app/admin/marks").statusCode()).isEqualTo(302);
        HttpResponse<String> denied = mmClient.postForm("/app/admin/marks",
                Map.of("roleType", "CAMERAPERSON", "markValue", "0.9", "catalogueReason", "should be denied"));
        // Real form POST always redirects (errorMessage flash) - assert the entry was never created.
        assertThat(denied.statusCode()).isEqualTo(302);
        assertThat(markCatalogueEntryRepository.findAllByOrderByRoleTypeAscMarkValueAsc().stream()
                .noneMatch(e -> e.getMarkValue().doubleValue() == 0.9)).isTrue();
    }

    @Test
    void duplicateRoleAndValueRejected() throws Exception {
        TestApiClient ceo = ceo();
        // 1.0 already exists for CAMERAPERSON from the V36 seed.
        HttpResponse<String> create = ceo.postForm("/app/admin/marks",
                Map.of("roleType", "CAMERAPERSON", "markValue", "1.0", "catalogueReason", "duplicate attempt"));
        assertThat(create.statusCode()).isEqualTo(302);
        String marksPage = ceo.get("/app/admin/marks").body();
        // No visible way to assert the flash error directly via a second GET (flash consumed on
        // first read) - assert instead that no second CAMERAPERSON/1.0 row was created.
        long count = markCatalogueEntryRepository.findAllByOrderByRoleTypeAscMarkValueAsc().stream()
                .filter(e -> e.getRoleType() == com.kcpc.mkt.marks.domain.RoleType.CAMERAPERSON
                        && e.getMarkValue().doubleValue() == 1.0)
                .count();
        assertThat(count).isEqualTo(1);
        assertThat(marksPage).isNotNull();
    }

    private String createIdea(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Mark Catalogue Idea " + unique + "\"}");
        return idea.get("ideaId").asText();
    }

    @Test
    void approvingWithNewCatalogueValueSucceeds() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pub[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String ideaId = createIdea(ceo, unique);

        // 0.1 is the new value added by ENG-092 - never valid under the old hardcoded list.
        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":0.1,\"editorMark\":0.1,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/markcat-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void approvingWithValueRemovedFromCatalogueRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pub[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String ideaId = createIdea(ceo, unique);

        // 2.0 was removed from the allowed set by V36 (only [0, 0.1, 0.5, 1.0] remain). Publisher
        // is included so this hits the Mark Catalogue check (which fires after the now-unconditional
        // Publisher check), not a false-positive missing-Publisher error.
        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":2.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/markcat-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Mark Catalogue");
    }

    @Test
    void correctingToNonCatalogueValueRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pub[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String ideaId = createIdea(ceo, unique);
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/markcat-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");

        HttpResponse<String> response = ceo.post("/api/v1/ideas/" + ideaId + "/predefined-marks/corrections",
                "{\"newCamerapersonMarks\":2.0,\"newEditorMarks\":1.0,\"newModelMarks\":1.0,"
                        + "\"correctionReason\":\"attempted non-catalogue correction\"}");
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void deletingCatalogueEntryDoesNotAffectAlreadyCreatedContentPlan() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        // A fresh, throwaway catalogue value (never touches the shared V36 seed rows other tests
        // rely on) so this test is fully self-contained and leaves no cross-test side effects.
        String freshValue = markValueString(20 + (int) (unique % 700));
        ceo.postForm("/app/admin/marks", Map.of("roleType", "CAMERAPERSON",
                "markValue", freshValue, "catalogueReason", "fresh test value"));
        MarkCatalogueEntry freshEntry = markCatalogueEntryRepository.findAllByOrderByRoleTypeAscMarkValueAsc().stream()
                .filter(e -> e.getRoleType() == com.kcpc.mkt.marks.domain.RoleType.CAMERAPERSON
                        && e.getMarkValue().compareTo(new java.math.BigDecimal(freshValue)) == 0)
                .findFirst().orElseThrow();

        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pub[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"mark catalogue test fixture grant\"}");
        String ideaId = createIdea(ceo, unique);
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":" + freshValue + ",\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/markcat-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        var plan = contentPlanRepository.findByIdea(
                ideaRepository.findById(java.util.UUID.fromString(ideaId)).orElseThrow()).orElseThrow();
        assertThat(predefinedRoleMarksRepository.findByContentPlan(plan).orElseThrow()
                .getPredefinedCameramanMark().compareTo(new java.math.BigDecimal(freshValue))).isEqualTo(0);

        // Deleting the catalogue entry must not touch the already-created Content Plan's own
        // recorded mark value - there's no FK, per design.
        ceo.postForm("/app/admin/marks/" + freshEntry.getId() + "/delete", Map.of("catalogueReason", "cleanup"));

        assertThat(markCatalogueEntryRepository.findById(freshEntry.getId())).isEmpty();
        assertThat(predefinedRoleMarksRepository.findByContentPlan(plan).orElseThrow()
                .getPredefinedCameramanMark().compareTo(new java.math.BigDecimal(freshValue))).isEqualTo(0);
    }

    /** Builds an exact "X.Y" decimal string (no floating-point arithmetic) from tenths - e.g.
     * markValueString(43) -&gt; "4.3" - safe for NUMERIC(3,1) round-tripping through form POSTs. */
    private static String markValueString(int tenths) {
        return (tenths / 10) + "." + (tenths % 10);
    }
}
