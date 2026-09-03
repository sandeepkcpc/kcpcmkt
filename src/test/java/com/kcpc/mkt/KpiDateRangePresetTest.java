package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KPI Dashboard Date Range Filter Enhancement: the Preset dropdown resolves entirely client-side
 * (reports-kpi-date-preset.js, covered separately by its own Node-vm test file for the pure
 * calendar-math/timezone cases) into concrete startDate/endDate before submit - what this file
 * proves server-side is ReportingMvcController#matchingDatePreset's reverse-lookup (which
 * <option> renders selected for a given concrete range), the inverted-range defense-in-depth
 * fallback, and that the range remains genuinely inclusive on both boundaries end-to-end through
 * the real Idea -&gt; Publish Funnel query. Current Work Ownership/Upcoming Channel Plan staying
 * current-state and the Funnel/Performance date bases staying unchanged are proven by this
 * session's existing KpiOverviewCurrentWorkOwnershipTest/KpiOverviewUpcomingChannelPlanTest/
 * KpiOverviewFunnelApprovalRateTest continuing to pass unmodified - this file does not duplicate
 * that coverage, since none of the calculations those tests exercise were touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KpiDateRangePresetTest {

    @LocalServerPort
    int port;

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private HttpResponse<String> kpiOverview(TestApiClient ceo, LocalDate from, LocalDate to) throws Exception {
        return ceo.get("/app/reports/kpis?startDate=" + from.format(ISO) + "&endDate=" + to.format(ISO));
    }

    /** Extracts the attribute text immediately after {@code id="fieldId"} up to the next '&gt;',
     * so readonly-presence can be asserted regardless of exact JSP-emitted whitespace/newlines. */
    private String attributesAfterId(String body, String fieldId) {
        int idIdx = body.indexOf("id=\"" + fieldId + "\"");
        assertThat(idIdx).isGreaterThan(-1);
        int tagEnd = body.indexOf('>', idIdx);
        return body.substring(idIdx, tagEnd);
    }

    // --- 1-6: each preset's exact boundary maps back to its own <option selected> -------------
    @Test
    void todayPresetIsReflectedAsSelected() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String body = kpiOverview(ceo(), today, today).body();
        assertThat(body).contains("<option value=\"today\" selected>");
    }

    @Test
    void yesterdayPresetIsReflectedAsSelected() throws Exception {
        LocalDate yesterday = LocalDate.now(BUSINESS_ZONE).minusDays(1);
        String body = kpiOverview(ceo(), yesterday, yesterday).body();
        assertThat(body).contains("<option value=\"yesterday\" selected>");
    }

    @Test
    void last7DaysPresetIsA7DayInclusiveWindowEndingToday() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String body = kpiOverview(ceo(), today.minusDays(6), today).body();
        assertThat(body).contains("<option value=\"last7\" selected>");
    }

    @Test
    void last30DaysPresetIsA30DayInclusiveWindowEndingToday() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String body = kpiOverview(ceo(), today.minusDays(29), today).body();
        assertThat(body).contains("<option value=\"last30\" selected>");
    }

    @Test
    void thisMonthPresetIsFirstOfMonthThroughToday() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        // When today IS the 1st, "first of month -> today" is the exact same single-day range as
        // Today itself - matchingDatePreset() deliberately checks "today" first (a more specific,
        // narrower match), so this genuine, calendar-inherent ambiguity is not testable on that one
        // day of the month; skip rather than assert a specific tie-break that isn't the point of
        // this test.
        Assumptions.assumeTrue(today.getDayOfMonth() != 1,
                "This Month collapses to exactly Today's range when today is the 1st - ambiguous by design");
        String body = kpiOverview(ceo(), today.withDayOfMonth(1), today).body();
        assertThat(body).contains("<option value=\"thisMonth\" selected>");
    }

    @Test
    void lastMonthPresetIsFirstThroughLastCalendarDayOfPreviousMonth() throws Exception {
        LocalDate firstOfThisMonth = LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
        LocalDate firstOfLastMonth = firstOfThisMonth.minusMonths(1);
        LocalDate lastOfLastMonth = firstOfThisMonth.minusDays(1);
        String body = kpiOverview(ceo(), firstOfLastMonth, lastOfLastMonth).body();
        assertThat(body).contains("<option value=\"lastMonth\" selected>");
    }

    // --- 7: Custom (a range matching none of the 6 presets) -----------------------------------
    @Test
    void nonMatchingRangeFallsBackToCustomAndFieldsStayEditable() throws Exception {
        // 45 days ago -> 40 days ago: deliberately outside every fixed preset window.
        LocalDate from = LocalDate.now(BUSINESS_ZONE).minusDays(45);
        LocalDate to = LocalDate.now(BUSINESS_ZONE).minusDays(40);
        String body = kpiOverview(ceo(), from, to).body();
        assertThat(body).contains("<option value=\"custom\" selected>");
        assertThat(attributesAfterId(body, "kpiDateFrom")).doesNotContain("readonly");
        assertThat(attributesAfterId(body, "kpiDateTo")).doesNotContain("readonly");
    }

    @Test
    void presetSelectedRangeRendersDateFieldsAsReadonly() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String body = kpiOverview(ceo(), today, today).body();
        assertThat(attributesAfterId(body, "kpiDateFrom")).contains("readonly");
        assertThat(attributesAfterId(body, "kpiDateTo")).contains("readonly");
    }

    // --- 8: From = To (a single-day range) is a completely valid, non-error state --------------
    @Test
    void fromEqualsToIsValidNotTreatedAsAnError() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        HttpResponse<String> response = kpiOverview(ceo(), today, today);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("Whitelabel Error Page");
    }

    // --- 9: From > To - backend defense-in-depth fallback (client-side blocks submission first;
    // this proves a hand-edited/direct URL bypassing that can never compute KPIs backwards). -----
    @Test
    void invertedRangeFallsBackToTheDefaultThirtyDayWindowServerSide() throws Exception {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        HttpResponse<String> response = kpiOverview(ceo(), today, today.minusDays(10)); // From > To
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        // Falls back to exactly the same default window the missing-param case already uses -
        // never the submitted (inverted) dates, and never a 500/error page.
        assertThat(body).doesNotContain("Whitelabel Error Page");
        assertThat(body).contains("value=\"" + today.minusDays(29).format(ISO) + "\"");
        assertThat(body).contains("value=\"" + today.format(ISO) + "\"");
    }

    // --- 10/11: boundary inclusion, proven end-to-end through the real Funnel query -------------
    @Test
    void fromAndToBoundaryBothIncludeAnIdeaSubmittedTodayUnderTheTodayPreset() throws Exception {
        TestApiClient ceo = ceo();
        long unique = System.currentTimeMillis();

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        String beforeBody = kpiOverview(ceo, today, today).body();
        long submittedBefore = extractFunnelSubmitted(beforeBody);

        ceo.postJson("/api/v1/ideas", "{\"title\":\"KPI Preset Boundary Idea " + unique + "\"}");

        String afterBody = kpiOverview(ceo, today, today).body();
        long submittedAfter = extractFunnelSubmitted(afterBody);

        // From = To = today, and the idea was just submitted today - both boundaries are the same
        // single day here, so this proves that day is genuinely included, not silently excluded by
        // an off-by-one on either edge.
        assertThat(submittedAfter).isEqualTo(submittedBefore + 1);
    }

    private long extractFunnelSubmitted(String body) {
        String marker = "kpi-funnel-stage kpi-funnel-submitted\"><span class=\"kpi-funnel-count\">";
        int markerIdx = body.indexOf(marker);
        assertThat(markerIdx).isGreaterThan(-1);
        int valueStart = markerIdx + marker.length();
        int valueEnd = body.indexOf('<', valueStart);
        return Long.parseLong(body.substring(valueStart, valueEnd).trim());
    }
}
