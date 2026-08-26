package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI/UX Design Specification v0.2 Group B (Analytics & Reporting, §7) and Group C (Data Export,
 * §8), driven through real HTTP GETs against the MVC/JSP screens. Regression guard for ENG-031:
 * {@code KpiValue}, {@code AuditLogResponse}, and {@code DelayedDeliverableRow} must stay plain
 * classes with {@code getX()} accessors, never records, or classic JSP EL throws
 * {@code PropertyNotFoundException} at render time - which the security filter chain previously
 * masked as a misleading 302 redirect to {@code /login?reason=auth} rather than a 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportsGroupBMvcScreenSmokeTest {

    @LocalServerPort
    int port;

    @Test
    void groupBAndCScreensRenderWithoutError() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertOk(ceo.get("/app/reports/workload"));
        assertOk(ceo.get("/app/reports/team-kpis"));
        // KPI Dashboard (permission-driven redesign): 5 views (Overview default, plus Workflow &
        // SLA / Content & Publishing / Quality & Reviews / Performance via ?view=), superseding the
        // old flat KPI-001..030 console at this same route.
        HttpResponse<String> overview = ceo.get("/app/reports/kpis");
        assertOk(overview);
        assertThat(overview.body()).contains("Marketing KPI Dashboard")
                .contains("class=\"kpi-view-tab active\"").contains("Active WIP").contains("Idea").contains("Publish Funnel");
        // Overview's chart pass was scoped back out (rollback request) - the funnel is the
        // pre-chart stacked-block presentation again, never the .kpi-hbar-chart proportional bars
        // used by the other 4 views below.
        assertThat(overview.body()).contains("kpi-funnel").doesNotContain("kpi-hbar-chart");

        // Visual analytics phase (still in effect for the other 4 views): every view's
        // server-rendered chart markup (inline SVG donuts / CSS+EL bar charts, no chart library,
        // no client-side calculation) must render without error alongside the pre-existing
        // tables/cards it enhances.

        HttpResponse<String> workflow = ceo.get("/app/reports/kpis?view=workflow");
        assertOk(workflow);
        assertThat(workflow.body()).contains("Within SLA % by Stage").contains("kpi-hbar-chart")
                .contains("Delay Aging").contains("kpi-segbar-legend");

        HttpResponse<String> content = ceo.get("/app/reports/kpis?view=content");
        assertOk(content);
        assertThat(content.body()).contains("kpi-donut-svg") // Planned Output Mix + Target Completion
                .contains("Planned Output Mix").contains("Planned vs Published Targets")
                .contains("ORIGINAL vs REPOST");

        HttpResponse<String> quality = ceo.get("/app/reports/kpis?view=quality");
        assertOk(quality);
        assertThat(quality.body()).contains("Rework Rate by Stage").contains("kpi-hbar-chart");

        HttpResponse<String> performance = ceo.get("/app/reports/kpis?view=performance");
        assertOk(performance);
        assertThat(performance.body()).contains("kpi-hbar-chart")
                .contains("ORIGINAL vs REPOST &middot; Avg Hook Rate %").contains("ORIGINAL vs REPOST &middot; Avg Views");
        // Invalid view falls back to Overview, never a 500/blank screen.
        HttpResponse<String> invalidView = ceo.get("/app/reports/kpis?view=not-a-real-view");
        assertOk(invalidView);
        assertThat(invalidView.body()).contains("Active WIP");

        // Empty-state branches (no data in range) must render safely too - a zero-count donut/
        // segmented-bar must never divide by zero or render a misleading empty shape. Overview's
        // reverted stacked-block funnel has no such guard to check (it never divides), but it must
        // still render a zero-submitted period without error.
        assertOk(ceo.get("/app/reports/kpis?startDate=2000-01-01&endDate=2000-01-02"));
        HttpResponse<String> emptyContent = ceo.get("/app/reports/kpis?view=content&startDate=2000-01-01&endDate=2000-01-02");
        assertOk(emptyContent);
        HttpResponse<String> emptyWorkflow = ceo.get("/app/reports/kpis?view=workflow&startDate=2000-01-01&endDate=2000-01-02");
        assertOk(emptyWorkflow);
        HttpResponse<String> emptyPerformance = ceo.get("/app/reports/kpis?view=performance&startDate=2000-01-01&endDate=2000-01-02");
        assertOk(emptyPerformance);
        assertOk(ceo.get("/app/reports/admin-actions"));
        assertOk(ceo.get("/app/reports/delayed"));
        assertOk(ceo.get("/app/audit"));
        assertOk(ceo.get("/app/export"));
    }

    private void assertOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("LazyInitializationException", "NullPointerException",
                "PropertyNotFoundException", "Whitelabel Error Page", "500 Internal Server Error");
    }
}
