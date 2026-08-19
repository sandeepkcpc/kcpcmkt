package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-070: Content Pipeline's server-side filter/sort/pagination query params on the existing
 * /app/pipeline route - a pure reporting-layer feature ({@code PipelineDashboardService#filterAndSort})
 * over the same rows {@link CeoPipelineDashboardTest} already exercises for correctness, so this
 * test only asserts filtering/sorting/pagination behavior itself, scoped to its own uniquely
 * identifiable fixture rows (via a unique SKU substring) to stay correct regardless of whatever
 * else already exists in the shared test database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PipelineFilterSortTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void filterSortAndPaginateWorkTogetherOverOwnFixtureRows() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String skuTag = "sku-" + unique;
        String highId = createPlanWithPriorityAndSku(ceo, "Filter Sort High " + unique, "HIGH", skuTag + "-a");
        String medId = createPlanWithPriorityAndSku(ceo, "Filter Sort Medium " + unique, "MEDIUM", skuTag + "-b");
        String lowId = createPlanWithPriorityAndSku(ceo, "Filter Sort Low " + unique, "LOW", skuTag + "-c");

        ContentPlan highPlan = contentPlanRepository.findById(UUID.fromString(highId)).orElseThrow();
        ContentPlan medPlan = contentPlanRepository.findById(UUID.fromString(medId)).orElseThrow();
        ContentPlan lowPlan = contentPlanRepository.findById(UUID.fromString(lowId)).orElseThrow();

        // Search (q) scopes to just these 3 fixture rows, regardless of anything else in the DB.
        String allThree = ceo.get("/app/pipeline?q=" + skuTag + "&size=50").body();
        assertThat(allThree).contains(highPlan.getContentId()).contains(medPlan.getContentId()).contains(lowPlan.getContentId());

        // The per-page selector's onchange URL and each sort link must carry exactly ONE "size="
        // query param - a duplicate (one from filterQueryString, one appended by the link itself)
        // would make HttpServletRequest#getParameter silently return the FIRST value, so choosing
        // a new page size would appear to do nothing.
        int perPageOnchangeStart = allThree.indexOf("id=\"pipelinePerPage\"");
        int perPageOnchangeEnd = allThree.indexOf("</select>", perPageOnchangeStart);
        String perPageOnchangeBlock = allThree.substring(perPageOnchangeStart, perPageOnchangeEnd);
        assertThat(countOccurrences(perPageOnchangeBlock, "size=")).isEqualTo(1);

        int sortLinkStart = allThree.indexOf("data-popup-target=\"popup-sku\"");
        int contentIdSortStart = allThree.lastIndexOf("pipeline-sort-link", sortLinkStart);
        int contentIdSortEnd = allThree.indexOf("</a>", contentIdSortStart);
        String contentIdSortHref = allThree.substring(contentIdSortStart, contentIdSortEnd);
        assertThat(countOccurrences(contentIdSortHref, "size=")).isEqualTo(1);

        // Priority filter isolates exactly the matching row.
        String highOnly = ceo.get("/app/pipeline?q=" + skuTag + "&priority=HIGH&size=50").body();
        assertThat(highOnly).contains(highPlan.getContentId());
        assertThat(highOnly).doesNotContain(medPlan.getContentId()).doesNotContain(lowPlan.getContentId());

        // Sort by priority ascending: HIGH(0) before MEDIUM(1) before LOW(2).
        String sortedAsc = ceo.get("/app/pipeline?q=" + skuTag + "&sortBy=priority&sortDir=asc&size=50").body();
        int highPos = sortedAsc.indexOf(highPlan.getContentId());
        int medPos = sortedAsc.indexOf(medPlan.getContentId());
        int lowPos = sortedAsc.indexOf(lowPlan.getContentId());
        assertThat(highPos).isPositive();
        assertThat(highPos).isLessThan(medPos);
        assertThat(medPos).isLessThan(lowPos);
        // The currently-sorted column header carries the sort indicator.
        assertThat(sortedAsc).contains("pipeline-sort-indicator");

        // Sort by priority descending: exact reverse order.
        String sortedDesc = ceo.get("/app/pipeline?q=" + skuTag + "&sortBy=priority&sortDir=desc&size=50").body();
        int lowPosDesc = sortedDesc.indexOf(lowPlan.getContentId());
        int medPosDesc = sortedDesc.indexOf(medPlan.getContentId());
        int highPosDesc = sortedDesc.indexOf(highPlan.getContentId());
        assertThat(lowPosDesc).isLessThan(medPosDesc);
        assertThat(medPosDesc).isLessThan(highPosDesc);

        // Pagination: 1 per page, sorted ascending by priority - page 1 is HIGH only, page 2 is
        // MEDIUM only, and the "Showing X to Y of Z" text reflects the 3-row filtered scope.
        String page1 = ceo.get("/app/pipeline?q=" + skuTag + "&sortBy=priority&sortDir=asc&size=1&page=1").body();
        assertThat(page1).contains(highPlan.getContentId());
        assertThat(page1).doesNotContain(medPlan.getContentId()).doesNotContain(lowPlan.getContentId());
        assertThat(page1).contains("Showing 1 to 1 of 3 entries");

        String page2 = ceo.get("/app/pipeline?q=" + skuTag + "&sortBy=priority&sortDir=asc&size=1&page=2").body();
        assertThat(page2).contains(medPlan.getContentId());
        assertThat(page2).doesNotContain(highPlan.getContentId()).doesNotContain(lowPlan.getContentId());
        assertThat(page2).contains("Showing 2 to 2 of 3 entries");

        // Delayed / Attention filter: a 4th plan pushed into Shoot Assigned with a past Planned
        // Shoot Date is flagged delayed; the other 3 (still at Planning, no shoot assignment yet)
        // are not, and delayed=true excludes them.
        String delayedPlanId = createDelayedPlan(ceo, "Filter Sort Delayed " + unique, skuTag + "-d");
        ContentPlan delayedPlan = contentPlanRepository.findById(UUID.fromString(delayedPlanId)).orElseThrow();
        String delayedOnly = ceo.get("/app/pipeline?q=" + skuTag + "&delayed=true&size=50").body();
        assertThat(delayedOnly).contains(delayedPlan.getContentId());
        assertThat(delayedOnly).doesNotContain(highPlan.getContentId())
                .doesNotContain(medPlan.getContentId()).doesNotContain(lowPlan.getContentId());
        assertThat(delayedOnly).contains("Delayed");

        // ENG-071: per-column Planned Shoot Date range isolates the delayed plan (its own Planned
        // Shoot Date falls inside the range) from the other 3 (no shoot date set at all yet).
        LocalDate shootDate = delayedPlan.getPlannedShootDate();
        String rangeIncluding = ceo.get("/app/pipeline?q=" + skuTag + "&plannedShootFrom=" + shootDate.minusDays(1)
                + "&plannedShootTo=" + shootDate.plusDays(1) + "&size=50").body();
        assertThat(rangeIncluding).contains(delayedPlan.getContentId());
        assertThat(rangeIncluding).doesNotContain(highPlan.getContentId())
                .doesNotContain(medPlan.getContentId()).doesNotContain(lowPlan.getContentId());

        String rangeExcluding = ceo.get("/app/pipeline?q=" + skuTag + "&plannedShootFrom=" + shootDate.plusDays(5)
                + "&plannedShootTo=" + shootDate.plusDays(10) + "&size=50").body();
        assertThat(rangeExcluding).doesNotContain(delayedPlan.getContentId());

        // ENG-071: per-column filter/sort popup markup is present (filter trigger + its popup) on
        // representative text/date/select columns, not the old big Filters drawer.
        assertThat(allThree).doesNotContain("pipeline-filters-drawer");
        assertThat(allThree).contains("data-popup-target=\"popup-sku\"").contains("id=\"popup-sku\"");
        assertThat(allThree).contains("data-popup-target=\"popup-plannedShoot\"").contains("id=\"popup-plannedShoot\"");
        assertThat(allThree).contains("data-popup-target=\"popup-status\"").contains("id=\"popup-status\"");
        assertThat(allThree).contains("Reset Filters");

        // Filters/search never bypass the Employee redirect - same authorization as the unfiltered view.
        String hrEmail = "pfs-hr-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "Pipeline Filter HR", hrEmail, "01926e3e-0001-7000-8000-000000000003");
        TestApiClient employee = new TestApiClient(port);
        employee.login(hrEmail, "Passw0rd!");
        HttpResponse<String> employeeAttempt = employee.get("/app/pipeline?priority=HIGH");
        assertThat(employeeAttempt.statusCode()).isEqualTo(302);
    }

    private String createPlanWithPriorityAndSku(TestApiClient ceo, String ideaTitle, String priority, String sku)
            throws Exception {
        assertThat(ceo.postForm("/app/ideas", java.util.Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                java.util.Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0")).statusCode())
                .isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        ceo.postJson("/api/v1/content-plans/" + plan.getId() + "/parameters",
                "{\"contentPriority\":\"" + priority + "\",\"skuReference\":\"" + sku + "\"}");
        return plan.getId().toString();
    }

    private String createDelayedPlan(TestApiClient ceo, String ideaTitle, String sku) throws Exception {
        String camEmail = "pfs-cam-" + Instant.now().toEpochMilli() + "@kcpcbandhani.local";
        String camId = createUser(ceo, "Filter Sort Cam", camEmail, CAMERA_PERSON_ROLE_ID);

        assertThat(ceo.postForm("/app/ideas", java.util.Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                java.util.Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0")).statusCode())
                .isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        ceo.postJson("/api/v1/content-plans/" + plan.getId() + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"skuReference\":\"" + sku
                        + "\",\"folderLink\":\"https://drive.example.com/pfs-" + sku + "\"}");
        String futureLive = LocalDate.now().plusDays(5).toString();
        String pastShoot = LocalDate.now().minusDays(2).toString();
        String pastEdit = LocalDate.now().minusDays(1).toString();
        HttpResponse<String> scheduleResp = ceo.post("/api/v1/content-plans/" + plan.getId() + "/schedule/urgent",
                "{\"plannedLiveDate\":\"" + futureLive + "\",\"shootDate\":\"" + pastShoot + "\",\"editDate\":\"" + pastEdit
                        + "\",\"urgencyReason\":\"pipeline filter/sort test fixture\"}");
        assertThat(scheduleResp.statusCode()).isEqualTo(200);
        assertThat(ceo.post("/api/v1/content-plans/" + plan.getId() + "/shooting-assignments",
                "{\"cameramanUserId\":\"" + camId + "\"}").statusCode()).isEqualTo(200);
        assertThat(ceo.post("/api/v1/content-plans/" + plan.getId() + "/planning-review/submit", "").statusCode())
                .isEqualTo(200);
        HttpResponse<String> decideResp = ceo.post("/api/v1/content-plans/" + plan.getId() + "/planning-review/decision",
                "{\"approve\":true}");
        assertThat(decideResp.statusCode()).isEqualTo(200);
        // Plan is now Shoot Assigned (SA) with a Planned Shoot Date 2 days in the past - delayed.

        return plan.getId().toString();
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"pipeline filter/sort test fixture\"}");
        return response.get("userId").asText();
    }

    private long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
