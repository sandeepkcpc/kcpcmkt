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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Pipeline: the Cancelled status tab. Cancelled Content Plans were always part of the
 * pipeline's row set (they render under All with a Cancelled status pill) - this tab only groups
 * them, using {@code WorkflowStatus.CAN}'s own display name, the same terminal status the reporting
 * and KPI logic already keys on. No new status exists anywhere.
 *
 * <p>The badge and the tab share one predicate by construction: the controller counts
 * {@code "Cancelled".equals(row.getStatus())} over the unfiltered rows, and
 * {@code PipelineDashboardService#matchesStage} filters on exactly the same expression - so the
 * count can never disagree with what the tab returns. These tests assert that agreement directly
 * rather than trusting it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PipelineCancelledTabTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String user(TestApiClient ceo, String name, String email, String roleId, String permission)
            throws Exception {
        String id = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"cancelled tab fixture\"}")
                .get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + id + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"cancelled tab fixture grant\"}");
        return id;
    }

    /** An approved plan carrying a distinctive SKU, so `q=` scopes the pipeline to this test's own
     *  rows regardless of what else is in the database. */
    private ContentPlan plan(TestApiClient ceo, long unique, String label, String skuTag) throws Exception {
        String camId = user(ceo, "CancelTab Cam " + label + " " + unique,
                "canceltab-cam-" + label + "-" + unique + "@kcpcbandhani.local",
                CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION");
        String pubId = user(ceo, "CancelTab Pub " + label + " " + unique,
                "canceltab-pub-" + label + "-" + unique + "@kcpcbandhani.local",
                PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION");
        String ideaId = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"CancelTab " + label + " " + unique + "\"}").get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(20) + "\","
                        + "\"skuReference\":\"" + skuTag + "\","
                        + "\"folderLink\":\"https://drive.example.com/canceltab-" + label + "-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        Idea idea = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }

    /** Cancels through the real governed admin action, never by poking workflow state. */
    private void cancel(TestApiClient ceo, ContentPlan plan) throws Exception {
        ceo.post("/api/v1/content-plans/" + plan.getId() + "/cancel",
                "{\"reason\":\"pipeline Cancelled tab regression fixture\"}");
    }

    private String pipeline(TestApiClient ceo, String skuTag, String stage) throws Exception {
        String stageParam = stage == null ? "" : "&stage=" + stage;
        return ceo.get("/app/pipeline?size=50&q=" + skuTag + stageParam).body();
    }

    /** The count badge rendered inside a given tab. NOTE these badges are deliberately GLOBAL -
     *  they count the tab's unfiltered row set, not the currently-searched subset (pre-existing
     *  behaviour shared by every tab), so the tests below assert DELTAS around their own fixture
     *  rather than absolute totals. */
    private static long tabCount(String html, String tabLabel) {
        Matcher m = Pattern.compile(Pattern.quote(tabLabel)
                + "\\s*<span class=\"pipeline-stage-count\">(\\d+)</span>").matcher(html);
        assertThat(m.find()).as("the '%s' tab must be rendered with a count badge", tabLabel).isTrue();
        return Long.parseLong(m.group(1));
    }

    /** The "Showing X to Y of Z entries" total. */
    private static long shownTotal(String html) {
        // Two shapes: "Showing 0 of 0 entries" when empty, "Showing X to Y of Z entries" otherwise.
        Matcher m = Pattern.compile("of (\\d+) entries").matcher(html);
        assertThat(m.find()).as("the result count line must be rendered").isTrue();
        return Long.parseLong(m.group(1));
    }

    // --- 1. the count is displayed, and it is correct ----------------------------------------------
    @Test
    void cancelledTabIsRenderedBesideCompletedWithTheRightCount() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "CANCELTAB-COUNT-" + unique;
        long before = tabCount(pipeline(ceo, sku, null), "Cancelled");
        ContentPlan cancelledOne = plan(ceo, unique, "C1", sku);
        ContentPlan cancelledTwo = plan(ceo, unique + 1, "C2", sku);
        plan(ceo, unique + 2, "LIVE", sku); // stays active
        cancel(ceo, cancelledOne);
        cancel(ceo, cancelledTwo);

        String html = pipeline(ceo, sku, null);
        assertThat(html).contains("stage=cancelled");
        assertThat(html).contains("pipeline-stage-tab cancelled");
        assertThat(tabCount(html, "Cancelled")).isEqualTo(before + 2);
        // Rendered after Completed in the tab strip.
        assertThat(html.indexOf("> Completed <span class=\"pipeline-stage-count\""))
                .as("Cancelled is rendered after Completed")
                .isLessThan(html.indexOf("> Cancelled <span class=\"pipeline-stage-count\""));
    }

    // --- 2. clicking Cancelled returns only cancelled content --------------------------------------
    @Test
    void cancelledTabReturnsOnlyCancelledContent() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "CANCELTAB-FILTER-" + unique;
        ContentPlan cancelled = plan(ceo, unique, "X", sku);
        ContentPlan active = plan(ceo, unique + 1, "A", sku);
        cancel(ceo, cancelled);

        String html = pipeline(ceo, sku, "cancelled");
        assertThat(html).contains(cancelled.getContentId());
        assertThat(html).as("active content must not appear under Cancelled")
                .doesNotContain(active.getContentId());
        assertThat(shownTotal(html)).isEqualTo(1);
    }

    /** The badge and the tab must agree - the whole point of counting with the same predicate. */
    @Test
    void theCancelledBadgeMatchesTheRowCountTheTabActuallyReturns() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "CANCELTAB-AGREE-" + unique;
        for (int i = 0; i < 3; i++) {
            cancel(ceo, plan(ceo, unique + i, "A" + i, sku));
        }
        plan(ceo, unique + 9, "KEEP", sku);

        // Both are global, so they must be equal to each other - that equality IS the guarantee
        // the requirement asks for ("count must match the actual filtered result count").
        String unscoped = ceo.get("/app/pipeline?size=500").body();
        long badge = tabCount(unscoped, "Cancelled");
        long returned = shownTotal(ceo.get("/app/pipeline?size=500&stage=cancelled").body());
        assertThat(returned).as("badge and returned rows must be the same number").isEqualTo(badge);
        assertThat(badge).as("the three just-cancelled plans are included").isGreaterThanOrEqualTo(3);
    }

    // --- 3. other status counts are unaffected -----------------------------------------------------
    @Test
    void cancellingContentDoesNotDisturbTheOtherStatusTabs() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "CANCELTAB-OTHERS-" + unique;
        ContentPlan toCancel = plan(ceo, unique, "TC", sku);
        plan(ceo, unique + 1, "S1", sku);
        plan(ceo, unique + 2, "S2", sku);

        // All three start at Shoot Assigned.
        String before = pipeline(ceo, sku, null);
        long shootBefore = tabCount(before, "Shoot");
        long cancelledBefore = tabCount(before, "Cancelled");
        long allBefore = tabCount(before, "All");
        long completedBefore = tabCount(before, "Completed");
        long performanceBefore = tabCount(before, "Performance");
        long editBefore = tabCount(before, "Edit");
        long publishingBefore = tabCount(before, "Publishing");

        cancel(ceo, toCancel);

        String after = pipeline(ceo, sku, null);
        assertThat(tabCount(after, "Cancelled")).as("exactly one more cancelled").isEqualTo(cancelledBefore + 1);
        assertThat(tabCount(after, "Shoot")).as("the cancelled plan leaves the Shoot tab").isEqualTo(shootBefore - 1);
        assertThat(tabCount(after, "Completed")).as("Completed untouched").isEqualTo(completedBefore);
        assertThat(tabCount(after, "Performance")).as("Performance untouched").isEqualTo(performanceBefore);
        assertThat(tabCount(after, "Edit")).as("Edit untouched").isEqualTo(editBefore);
        assertThat(tabCount(after, "Publishing")).as("Publishing untouched").isEqualTo(publishingBefore);
        // All still counts every row, cancelled included - cancelling moves a row between tabs,
        // it never removes it from the pipeline.
        assertThat(tabCount(after, "All")).isEqualTo(allBefore);
    }

    // --- 4. empty cancelled state -----------------------------------------------------------------
    @Test
    void withNothingCancelledTheTabShowsZeroAndAnEmptyTable() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "CANCELTAB-EMPTY-" + unique;
        ContentPlan active = plan(ceo, unique, "E", sku);

        // Nothing in THIS scope is cancelled: the Cancelled tab returns an empty table, and the
        // active row is not smuggled into it.
        String cancelledTab = pipeline(ceo, sku, "cancelled");
        assertThat(shownTotal(cancelledTab)).isEqualTo(0);
        assertThat(cancelledTab).doesNotContain(active.getContentId());
        assertThat(cancelledTab).contains("Showing 0 of 0 entries");
        // The tab itself still renders (with its global badge), rather than disappearing.
        assertThat(cancelledTab).contains("pipeline-stage-tab cancelled");
        assertThat(tabCount(cancelledTab, "Cancelled")).isGreaterThanOrEqualTo(0);
    }

    // --- pagination keeps working under the new tab ------------------------------------------------
    @Test
    void paginationWorksWithinTheCancelledTab() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "CANCELTAB-PAGE-" + unique;
        ContentPlan a = plan(ceo, unique, "P1", sku);
        ContentPlan b = plan(ceo, unique + 1, "P2", sku);
        cancel(ceo, a);
        cancel(ceo, b);

        String page1 = ceo.get("/app/pipeline?q=" + sku + "&stage=cancelled&size=1&page=1").body();
        assertThat(page1).contains("Showing 1 to 1 of 2 entries");
        String page2 = ceo.get("/app/pipeline?q=" + sku + "&stage=cancelled&size=1&page=2").body();
        assertThat(page2).contains("Showing 2 to 2 of 2 entries");
        // Between them the two pages cover exactly both cancelled plans, neither twice.
        assertThat(page1.contains(a.getContentId()) ^ page2.contains(a.getContentId())).isTrue();
        assertThat(page1.contains(b.getContentId()) ^ page2.contains(b.getContentId())).isTrue();
    }
}
