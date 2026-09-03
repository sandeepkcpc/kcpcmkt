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
 * SKU, Category and Mark now show in the Content Information section of every employee-assigned
 * Task Detail screen (Shoot/Edit/Publish) - display-only additions, same underlying data the
 * Planning Workspace already captures at Idea Review approval (ContentPlan#skuReference/
 * #categoryText, PredefinedRoleMarks). Mark is role-specific per screen: Shoot Task Detail shows
 * the Cameraperson Mark, Edit Task Detail shows the Editor Mark; Publish Task Detail always shows
 * "—" since there is no dedicated Publisher Mark in the domain model. Real HTTP, real Postgres, no
 * mocking - same fixture conventions as TaskDetailReelTypeHiddenTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class TaskDetailContentInfoFieldsTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String TARGET_INSTAGRAM_KCPC = "01926e3e-000a-7000-8000-000000000001";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, String permission, long unique)
            throws Exception {
        String email = "e2e-contentinfo-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"ContentInfo " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"content info fields test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"content info fields test grant\"}");
        return new String[] {userId, email};
    }

    private String reelOutputsJson() {
        return "\"outputs\":[{\"outputType\":\"REEL\",\"reelTypes\":[\"SHORT\"],"
                + "\"publicationTargetIds\":[\"" + TARGET_INSTAGRAM_KCPC + "\"]}]";
    }

    /** categoryText is validated against the live Category Catalogue (IdeaService#approve ->
     * CategoryService#requireActiveNameOrBlank) - a made-up name is rejected, so the test must
     * create a real active entry first, same as MarkCatalogueTest does for mark values. */
    private void createCategory(TestApiClient ceo, String name) throws Exception {
        ceo.postForm("/app/admin/categories", java.util.Map.of("name", name, "catalogueReason", "content info fields test fixture"));
    }

    @Test
    void shootTaskDetailShowsSkuCategoryAndCameramanMark() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String category = "ContentInfo Shoot Category " + unique;
        createCategory(ceo, category);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"ContentInfo Shoot " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":0.5,\"editorMark\":1.0,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/contentinfo-shoot-" + unique + "\","
                        + "\"categoryText\":\"" + category + "\",\"skuReference\":\"SKU-SHOOT-" + unique + "\",\"skuNotApplicable\":false,"
                        + reelOutputsJson() + ","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        String page = camClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains("Shoot Task");
        assertThat(page).contains(">SKU<").contains("SKU-SHOOT-" + unique);
        assertThat(page).contains(">Category<").contains(category);
        assertThat(page).contains(">Mark<").contains("0.5");
    }

    @Test
    void editTaskDetailShowsSkuCategoryAndEditorMark() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String category = "ContentInfo Edit Category " + unique;
        createCategory(ceo, category);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"ContentInfo Edit " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Edit (Stages = Edit + Publishing) reaches EA directly from Idea Review approval.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":0.1,\"editorMark\":1.0,\"modelMark\":0.5,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/contentinfo-edit-" + unique + "\","
                        + "\"categoryText\":\"" + category + "\",\"skuReference\":\"SKU-EDIT-" + unique + "\",\"skuNotApplicable\":false,"
                        + reelOutputsJson() + ","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        String page = editorClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains("Edit Task");
        assertThat(page).contains(">SKU<").contains("SKU-EDIT-" + unique);
        assertThat(page).contains(">Category<").contains(category);
        assertThat(page).contains(">Mark<").contains("1.0");
    }

    @Test
    void publishTaskDetailShowsSkuCategoryAndDashForMark() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String category = "ContentInfo Publish Category " + unique;
        createCategory(ceo, category);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"ContentInfo Publish " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Publishing (Stages = Publishing only) reaches RFP directly from Idea Review approval.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":0.5,\"editorMark\":0.5,\"modelMark\":0.5,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/contentinfo-pub-" + unique + "\","
                        + "\"categoryText\":\"" + category + "\",\"skuReference\":\"SKU-PUB-" + unique + "\",\"skuNotApplicable\":false,"
                        + reelOutputsJson() + ","
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient pubClient = new TestApiClient(port);
        pubClient.login(publisher[1], "Passw0rd!");
        String page = pubClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains("Publish");
        assertThat(page).contains(">SKU<").contains("SKU-PUB-" + unique);
        assertThat(page).contains(">Category<").contains(category);
        // No dedicated Publisher Mark exists - always renders as a plain dash on this screen.
        assertThat(page).contains("<span class=\"summary-field-label\">Mark</span><span class=\"summary-field-value\">&mdash;</span>");
    }
}
