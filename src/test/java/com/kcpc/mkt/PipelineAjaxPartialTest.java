package com.kcpc.mkt;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-081: the CEO/MM Content Pipeline's filter/sort/stage/pagination interactions run over
 * fetch() instead of a full page reload, but reuse the exact same route, controller method, and
 * PipelineDashboardService query/filter/sort/pagination logic as before - the only difference is
 * the view LandingMvcController#pipeline picks based on the X-Requested-With: fetch header (see
 * TestApiClient#getAjax, which sends the same header pipeline-dashboard.js's own fetch() calls
 * do). This test asserts that split is wired correctly: a plain (no-header) request still gets
 * the full page (the no-JS/bookmark/shared-link fallback), an AJAX request gets only the dynamic
 * region's HTML with no <html>/<head>/nav wrapper, and filtering still narrows results identically
 * over that AJAX path - i.e. this is purely a view-selection change, not a second implementation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PipelineAjaxPartialTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    @Test
    void plainRequestGetsFullPageAjaxRequestGetsPartialOnly() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String fullPage = ceo.get("/app/pipeline").body();
        // "pipeline-dashboard" alone (not the full ".js" filename) - cache-busting rewrites the
        // actual src to a content-hashed path (e.g. pipeline-dashboard-<hash>.js), see BrandLogoTest.
        assertThat(fullPage).contains("<!doctype html>").contains("<nav").contains("pipeline-dashboard");
        assertThat(fullPage).contains("id=\"pipelineDynamicRegion\"");
        assertThat(fullPage).contains("pipeline-stage-tabs").contains("id=\"pipelineFilterForm\"")
                .contains("id=\"pipelineTable\"").contains("pipeline-pagination").contains("pipeline-footer-note");

        String partial = ceo.getAjax("/app/pipeline").body();
        assertThat(partial).doesNotContain("<!doctype html>").doesNotContain("<nav").doesNotContain("pipeline-dashboard");
        // The #pipelineDynamicRegion wrapper div itself lives only in pipeline.jsp, not in the
        // shared fragment - the AJAX response IS exactly that div's new innerHTML, not the div.
        assertThat(partial).doesNotContain("id=\"pipelineDynamicRegion\"");
        assertThat(partial).contains("pipeline-stage-tabs").contains("id=\"pipelineFilterForm\"")
                .contains("id=\"pipelineTable\"").contains("pipeline-pagination").contains("pipeline-footer-note");
    }

    @Test
    void ajaxPartialHonorsTheSameFilterLogicAsTheFullPage() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String skuTag = "ajax-sku-" + unique;

        // Nothing matches yet - both the full page and the AJAX partial agree on zero results.
        String fullEmpty = ceo.get("/app/pipeline?q=" + skuTag + "&size=50").body();
        String ajaxEmpty = ceo.getAjax("/app/pipeline?q=" + skuTag + "&size=50").body();
        assertThat(fullEmpty).contains("Showing 0 of 0 entries");
        assertThat(ajaxEmpty).contains("Showing 0 of 0 entries");

        var camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Ajax Partial Cam\",\"email\":\"ajax-partial-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000004\","
                        + "\"creationReason\":\"ajax partial test fixture\"}");
        String camId = camUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"ajax partial test fixture grant\"}");
        var pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Ajax Partial Pub\",\"email\":\"ajax-partial-pub-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000008\","
                        + "\"creationReason\":\"ajax partial test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"ajax partial test fixture grant\"}");
        String ideaTitle = "Ajax Partial Test " + unique;
        assertThat(ceo.postForm("/app/ideas", java.util.Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        // Workflow redesign: Idea Review approval carries every former Planning field and transitions
        // straight to Shoot Assigned (SA), never PL/PLRV/PLAP.
        assertThat(ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", java.util.Map.of(
                "decision", java.util.List.of("APPROVE"),
                "cameramanMark", java.util.List.of("1.0"),
                "editorMark", java.util.List.of("1.0"),
                "modelMark", java.util.List.of("1.0"),
                "contentPriority", java.util.List.of("HIGH"),
                "plannedLiveDate", java.util.List.of(java.time.LocalDate.now().plusDays(10).toString()),
                "folderLink", java.util.List.of("https://drive.example.com/ajax-partial-" + unique),
                "camerapersonUserIds", java.util.List.of(camId),
                "publisherUserIds", java.util.List.of(pubId))).statusCode())
                .isEqualTo(302);
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        ceo.postJson("/api/v1/content-plans/" + plan.getId() + "/parameters",
                "{\"contentPriority\":\"HIGH\",\"skuReference\":\"" + skuTag + "\"}");

        String ajaxAfterCreate = ceo.getAjax("/app/pipeline?q=" + skuTag + "&size=50").body();
        assertThat(ajaxAfterCreate).contains(plan.getContentId());
        String fullAfterCreate = ceo.get("/app/pipeline?q=" + skuTag + "&size=50").body();
        assertThat(fullAfterCreate).contains(plan.getContentId());
    }
}
