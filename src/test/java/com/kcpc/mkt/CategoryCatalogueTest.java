package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.masterdata.domain.Category;
import com.kcpc.mkt.masterdata.repository.CategoryRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Category Catalogue (ENG-094): AdminMvcController's /app/admin/categories CRUD
 * (CategoryService), the permanent "N/A" default, and IdeaService/PlanningService validating
 * Planning Category against the live catalogue instead of unconstrained free text. Real HTTP,
 * real Postgres, no mocking - same convention as MarkCatalogueTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CategoryCatalogueTest {

    @LocalServerPort
    int port;

    @Autowired
    CategoryRepository categoryRepository;
    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "catcat-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"CatCat " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"category catalogue test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private String createIdea(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Category Catalogue Idea " + unique + "\"}");
        return idea.get("ideaId").asText();
    }

    private HttpResponse<String> approveWithCategory(TestApiClient ceo, String ideaId, String camId, String category,
                                                       long unique) throws Exception {
        String[] publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + publisher[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"category catalogue test fixture grant\"}");
        String categoryField = category == null ? "" : ",\"categoryText\":\"" + category + "\"";
        return ceo.post("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/catcat-" + unique + "\""
                        + categoryField + ","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + publisher[0] + "\"]}}");
    }

    // ================================================================== 1. N/A exists by default

    @Test
    void naExistsByDefault() {
        Category na = categoryRepository.findByNameIgnoreCase("N/A").orElseThrow();
        assertThat(na.isActive()).isTrue();
        assertThat(na.isDefaultCategory()).isTrue();
    }

    // ================================================================== 2. N/A cannot be deleted

    @Test
    void naCannotBeDeleted() throws Exception {
        TestApiClient ceo = ceo();
        Category na = categoryRepository.findByNameIgnoreCase("N/A").orElseThrow();
        HttpResponse<String> response = ceo.postForm("/app/admin/categories/" + na.getId() + "/delete",
                Map.of("catalogueReason", "attempted delete of default"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(categoryRepository.findById(na.getId())).isPresent();
    }

    // ================================================================== 3. N/A cannot be deactivated

    @Test
    void naCannotBeDeactivated() throws Exception {
        TestApiClient ceo = ceo();
        Category na = categoryRepository.findByNameIgnoreCase("N/A").orElseThrow();
        HttpResponse<String> response = ceo.postForm("/app/admin/categories/" + na.getId(),
                Map.of("isActive", "false", "catalogueReason", "attempted deactivate of default"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(categoryRepository.findById(na.getId()).orElseThrow().isActive()).isTrue();
    }

    // ================================================================== 4. Admin can create category

    @Test
    void adminCanCreateCategory() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String name = "Saree " + unique;
        HttpResponse<String> response = ceo.postForm("/app/admin/categories",
                Map.of("name", name, "catalogueReason", "test create"));
        assertThat(response.statusCode()).isEqualTo(302);
        Category created = categoryRepository.findByNameIgnoreCase(name).orElseThrow();
        assertThat(created.isActive()).isTrue();
        assertThat(created.isDefaultCategory()).isFalse();
    }

    // ================================================================== 5. Admin can update category

    @Test
    void adminCanUpdateCategory() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        ceo.postForm("/app/admin/categories", Map.of("name", "Lehenga " + unique, "catalogueReason", "test create"));
        Category created = categoryRepository.findByNameIgnoreCase("Lehenga " + unique).orElseThrow();

        String renamed = "Lehenga Renamed " + unique;
        HttpResponse<String> response = ceo.postForm("/app/admin/categories/" + created.getId(),
                Map.of("name", renamed, "catalogueReason", "test rename"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(categoryRepository.findById(created.getId()).orElseThrow().getName()).isEqualTo(renamed);
    }

    // ================================================================== 6. Admin can deactivate category

    @Test
    void adminCanDeactivateCategory() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        ceo.postForm("/app/admin/categories", Map.of("name", "Kurti " + unique, "catalogueReason", "test create"));
        Category created = categoryRepository.findByNameIgnoreCase("Kurti " + unique).orElseThrow();

        HttpResponse<String> response = ceo.postForm("/app/admin/categories/" + created.getId(),
                Map.of("isActive", "false", "catalogueReason", "test deactivate"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(categoryRepository.findById(created.getId()).orElseThrow().isActive()).isFalse();
    }

    // ================================================================== 7. Only active categories appear
    // ================================================================== 8. N/A appears first

    @Test
    void onlyActiveCategoriesAppearAndNaIsFirstInPlanningDropdown() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String activeName = "Gown " + unique;
        String inactiveName = "Sherwani " + unique;
        ceo.postForm("/app/admin/categories", Map.of("name", activeName, "catalogueReason", "active fixture"));
        ceo.postForm("/app/admin/categories", Map.of("name", inactiveName, "catalogueReason", "inactive fixture"));
        Category inactive = categoryRepository.findByNameIgnoreCase(inactiveName).orElseThrow();
        ceo.postForm("/app/admin/categories/" + inactive.getId(),
                Map.of("isActive", "false", "catalogueReason", "deactivate for dropdown test"));

        // The Idea Review approval page (idea-detail.jsp) renders the Planning Category dropdown
        // from the same ${categoryOptions} model attribute this asserts against.
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        String ideaId = createIdea(ceo, unique);
        String detailPage = ceo.get("/app/ideas/" + ideaId).body();

        assertThat(detailPage).contains(activeName);
        assertThat(detailPage).doesNotContain(inactiveName);

        int naIndex = detailPage.indexOf(">N/A<");
        int activeIndex = detailPage.indexOf(">" + activeName + "<");
        assertThat(naIndex).isGreaterThanOrEqualTo(0);
        assertThat(activeIndex).isGreaterThan(naIndex);
    }

    // ============================================================ 9. Historical records retain category

    @Test
    void existingRecordsRetainHistoricalCategoryAfterDeactivation() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String categoryName = "Anarkali " + unique;
        ceo.postForm("/app/admin/categories", Map.of("name", categoryName, "catalogueReason", "history fixture"));
        Category category = categoryRepository.findByNameIgnoreCase(categoryName).orElseThrow();

        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"category catalogue test fixture grant\"}");
        String ideaId = createIdea(ceo, unique);
        HttpResponse<String> approve = approveWithCategory(ceo, ideaId, cam[0], categoryName, unique);
        assertThat(approve.statusCode()).isEqualTo(200);

        ContentPlan plan = contentPlanRepository.findByIdea(
                ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow()).orElseThrow();
        assertThat(plan.getCategoryText()).isEqualTo(categoryName);

        // Deactivate the category after it's already been recorded on this Content Plan.
        ceo.postForm("/app/admin/categories/" + category.getId(),
                Map.of("isActive", "false", "catalogueReason", "deactivate after use"));

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getCategoryText()).isEqualTo(categoryName);

        // A NEW approval can no longer select the now-deactivated category.
        String[] cam2 = createUser(ceo, "cam2", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam2[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"category catalogue test fixture grant\"}");
        String ideaId2 = createIdea(ceo, unique + 1);
        HttpResponse<String> rejected = approveWithCategory(ceo, ideaId2, cam2[0], categoryName, unique + 1);
        assertThat(rejected.statusCode()).isEqualTo(400);
    }

    // ================================================================== 10. Duplicate names prevented

    @Test
    void duplicateCategoryNameRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String name = "Palazzo " + unique;
        HttpResponse<String> first = ceo.postForm("/app/admin/categories",
                Map.of("name", name, "catalogueReason", "first create"));
        assertThat(first.statusCode()).isEqualTo(302);

        HttpResponse<String> duplicate = ceo.postForm("/app/admin/categories",
                Map.of("name", name, "catalogueReason", "duplicate attempt"));
        assertThat(duplicate.statusCode()).isEqualTo(302);

        long count = categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .count();
        assertThat(count).isEqualTo(1);

        // Case-insensitive duplicate ("N/A" vs "n/a") is also rejected - never a second default row.
        HttpResponse<String> naDuplicate = ceo.postForm("/app/admin/categories",
                Map.of("name", "n/a", "catalogueReason", "duplicate N/A attempt"));
        assertThat(naDuplicate.statusCode()).isEqualTo(302);
        assertThat(categoryRepository.findAllByOrderByNameAsc().stream()
                .filter(c -> c.getName().equalsIgnoreCase("N/A")).count()).isEqualTo(1);
    }

    // ============================================================ extra: non-CEO access + delete-in-use

    @Test
    void nonCeoCannotAccessCategoryCatalogue() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] mm = createUser(ceo, "mm", MARKETING_MANAGER_ROLE_ID, unique);
        TestApiClient mmClient = new TestApiClient(port);
        mmClient.login(mm[1], "Passw0rd!");

        assertThat(mmClient.get("/app/admin/categories").statusCode()).isEqualTo(302);
        HttpResponse<String> denied = mmClient.postForm("/app/admin/categories",
                Map.of("name", "Should Not Exist " + unique, "catalogueReason", "should be denied"));
        assertThat(denied.statusCode()).isEqualTo(302);
        assertThat(categoryRepository.findByNameIgnoreCase("Should Not Exist " + unique)).isEmpty();
    }

    @Test
    void deletingCategoryReferencedByContentPlanIsRejected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String categoryName = "Referenced Category " + unique;
        ceo.postForm("/app/admin/categories", Map.of("name", categoryName, "catalogueReason", "reference fixture"));
        Category category = categoryRepository.findByNameIgnoreCase(categoryName).orElseThrow();

        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"category catalogue test fixture grant\"}");
        String ideaId = createIdea(ceo, unique);
        assertThat(approveWithCategory(ceo, ideaId, cam[0], categoryName, unique).statusCode()).isEqualTo(200);

        HttpResponse<String> delete = ceo.postForm("/app/admin/categories/" + category.getId() + "/delete",
                Map.of("catalogueReason", "attempted delete while referenced"));
        assertThat(delete.statusCode()).isEqualTo(302);
        // Still present - delete was refused because a Content Plan references it.
        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }
}
