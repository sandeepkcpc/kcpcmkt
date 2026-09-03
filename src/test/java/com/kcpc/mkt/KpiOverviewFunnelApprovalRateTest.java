package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-097 (Overview redesign) - Idea -&gt; Publish Funnel and the LOCKED Approval Rate formula
 * (Approved / Submitted * 100, never Approved / (Approved + Rejected)). §26 of the implementation
 * spec.
 *
 * <p>This runs against the shared kcpc_test database, which has accumulated many ideas dated
 * "today" from every other test class in this same long-running session - so a same-day date
 * range is NOT isolated to just this test's own fixture (unlike a fresh/empty database). Every
 * count assertion below is therefore a BEFORE/AFTER delta over this test's own fixture (isolates
 * this test's contribution exactly, regardless of any pre-existing pollution), and Approval Rate
 * correctness is verified as a mathematical property of the AFTER totals actually shown on the
 * page - {@code displayedRate == approved / submitted * 100} - rather than an assumed exact value,
 * which conclusively proves the NEW formula is in effect (the OLD formula would produce a
 * different number whenever submitted != approved + rejected, which this test's own fixture
 * guarantees by including Retained ideas).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KpiOverviewFunnelApprovalRateTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final Pattern SUBMITTED_PATTERN =
            Pattern.compile("kpi-funnel-submitted\"><span class=\"kpi-funnel-count\">(\\d+)<");
    private static final Pattern APPROVED_PATTERN =
            Pattern.compile("kpi-funnel-approved\"><span class=\"kpi-funnel-count\">(\\d+)<");
    private static final Pattern RETAINED_PATTERN = Pattern.compile("Retained <strong>(\\d+)</strong>");
    private static final Pattern REJECTED_PATTERN = Pattern.compile("Rejected <strong>(\\d+)</strong>");
    // The JSP's <c:choose>/<c:otherwise> spans two source lines (matching this file's own
    // pre-existing convention for every other percent card) - Jasper emits the inter-tag
    // whitespace as literal output regardless of which branch is selected, so "Approval Rate: "
    // and the value are not strictly adjacent in the rendered HTML.
    private static final Pattern APPROVAL_RATE_PATTERN =
            Pattern.compile("Approval Rate:\\s*(N/A|\\d+\\.\\d%)");

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String[] createUser(TestApiClient ceo, String label, long unique) throws Exception {
        return createUser(ceo, label, unique, CAMERA_PERSON_ROLE_ID);
    }

    private String[] createUser(TestApiClient ceo, String label, long unique, String businessRoleId) throws Exception {
        String email = "kpi-funnel-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"KpiFunnel " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"KPI funnel test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private String createIdea(TestApiClient ceo, long unique) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiFunnel Idea " + unique + "\"}");
        return idea.get("ideaId").asText();
    }

    private void approve(TestApiClient ceo, String ideaId, String camId, long unique) throws Exception {
        String[] pub = createUser(ceo, "pub", unique, PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pub[0] + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI funnel test fixture grant\"}");
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(15) + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-funnel-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
    }

    private void reject(TestApiClient ceo, String ideaId) throws Exception {
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review", "{\"decision\":\"REJECT\",\"reason\":\"KPI funnel test\"}");
    }

    private void retain(TestApiClient ceo, String ideaId) throws Exception {
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review", "{\"decision\":\"RETAIN\"}");
    }

    private String overviewHtmlForToday(TestApiClient ceo) throws Exception {
        LocalDate today = LocalDate.now();
        return ceo.get("/app/reports/kpis?view=overview&startDate=" + today + "&endDate=" + today).body();
    }

    private static int extract(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        assertThat(m.find()).as("pattern " + pattern + " should be present in the funnel HTML").isTrue();
        return Integer.parseInt(m.group(1));
    }

    @Test
    void submittedApprovedRetainedRejectedDeltasAreExactAndApprovalRateUsesTheLockedFormula() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + cam[0] + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI funnel test fixture grant\"}");

        String beforeHtml = overviewHtmlForToday(ceo);
        int submittedBefore = extract(SUBMITTED_PATTERN, beforeHtml);
        int approvedBefore = extract(APPROVED_PATTERN, beforeHtml);
        int retainedBefore = extract(RETAINED_PATTERN, beforeHtml);
        int rejectedBefore = extract(REJECTED_PATTERN, beforeHtml);

        // 13-submitted-idea shape mirroring the spec's own worked example (8 approved, 3 retained,
        // 2 rejected).
        for (int i = 0; i < 8; i++) {
            approve(ceo, createIdea(ceo, unique + 100 + i), cam[0], unique + 100 + i);
        }
        for (int i = 0; i < 3; i++) {
            retain(ceo, createIdea(ceo, unique + 200 + i));
        }
        for (int i = 0; i < 2; i++) {
            reject(ceo, createIdea(ceo, unique + 300 + i));
        }

        String afterHtml = overviewHtmlForToday(ceo);
        int submittedAfter = extract(SUBMITTED_PATTERN, afterHtml);
        int approvedAfter = extract(APPROVED_PATTERN, afterHtml);
        int retainedAfter = extract(RETAINED_PATTERN, afterHtml);
        int rejectedAfter = extract(REJECTED_PATTERN, afterHtml);

        // Deltas isolate this test's own fixture regardless of pre-existing same-day pollution.
        assertThat(submittedAfter - submittedBefore).isEqualTo(13);
        assertThat(approvedAfter - approvedBefore).isEqualTo(8);
        assertThat(retainedAfter - retainedBefore).isEqualTo(3);
        assertThat(rejectedAfter - rejectedBefore).isEqualTo(2);

        // Approval Rate must be exactly approved/submitted*100 on the ACTUAL totals shown - proves
        // the locked formula is in effect regardless of what those totals happen to be. This fixture
        // guarantees submittedAfter > approvedAfter + rejectedAfter (it adds 3 Retained), so the old
        // formula would necessarily produce a different number if it were still in use.
        assertThat(submittedAfter).isGreaterThan(approvedAfter + rejectedAfter);
        BigDecimal expectedRate = BigDecimal.valueOf(approvedAfter)
                .divide(BigDecimal.valueOf(submittedAfter), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        Matcher rateMatcher = APPROVAL_RATE_PATTERN.matcher(afterHtml);
        assertThat(rateMatcher.find()).isTrue();
        assertThat(rateMatcher.group(1)).isEqualTo(expectedRate + "%");

        assertThat(afterHtml).contains("Approved &divide; Submitted");
        assertThat(afterHtml).doesNotContain("Approved / (Approved + Rejected)");
    }

    @Test
    void zeroSubmittedShowsApprovalRateNotApplicableNeverZeroPercent() throws Exception {
        TestApiClient ceo = ceo();
        // A date range far in the past with zero ideas submitted that day.
        LocalDate longAgo = LocalDate.of(2020, 1, 1);
        String html = ceo.get("/app/reports/kpis?view=overview&startDate=" + longAgo + "&endDate=" + longAgo).body();
        assertThat(html).contains("Approval Rate: N/A");
        assertThat(html).doesNotContain("Approval Rate: 0%").doesNotContain("Approval Rate: 0.0%");
    }
}
