package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Administrative Actions Date Range fix - proves the backend end of this feature was ALREADY
 * correct (AdminReportingService#administrativeActionRows' Asia/Kolkata from/to conversion,
 * SystemAuditLogRepository's event_timestamp BETWEEN query) by driving it through a real HTTP
 * request, the same way a user's browser now can once reports-admin-actions.js's Filter button
 * actually submits startDate/endDate. This test does not touch backend code - it is the "prove it
 * already works" half of the fix; frontend-only tests (reports-admin-actions.test.js) cover the
 * From > To validation gate that file adds.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminActionsDateRangeFilterTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    @Test
    void dateRangeFiltersAdministrativeActionsToTheCorrectAsiaKolkataCalendarDay() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String fullName = "AdminActionsDateRange " + unique;
        String email = "aa-date-range-" + unique + "@kcpcbandhani.local";

        // Creating this user itself is not the audited action under test - the PERMISSION_GRANT
        // below is. The user's own creation reason is unrelated to Administrative Actions filtering.
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"admin actions date range test fixture\"}");
        String userId = user.get("userId").asText();

        // This is the real, governed admin action - creates a system_audit_log row with
        // event_type=PERMISSION_GRANTED and event_timestamp = now(), exactly like a real CEO
        // action would. AdminActionRow resolves its Content/User display to "User: <fullName>",
        // giving this test a marker unique enough to search for in the rendered HTML directly.
        HttpResponse<String> grantResponse = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"admin actions date range test fixture grant\"}");
        assertThat(grantResponse.statusCode()).isEqualTo(201);

        String marker = "User: " + fullName;
        LocalDate today = LocalDate.now(BUSINESS_ZONE);

        // A range that includes today (Asia/Kolkata) must include this action.
        HttpResponse<String> withinRange = ceo.get(
                "/app/reports/admin-actions?startDate=" + today + "&endDate=" + today);
        assertThat(withinRange.statusCode()).isEqualTo(200);
        assertThat(withinRange.body()).contains(marker);

        // A range that excludes today entirely must exclude it - proves the query is actually
        // being applied, not merely accepted and ignored (the exact bug this whole fix addresses).
        LocalDate farFuture = today.plusDays(10);
        HttpResponse<String> outsideRange = ceo.get(
                "/app/reports/admin-actions?startDate=" + farFuture + "&endDate=" + farFuture);
        assertThat(outsideRange.statusCode()).isEqualTo(200);
        assertThat(outsideRange.body()).doesNotContain(marker);

        // The existing actionType filter must still combine correctly with the new-to-the-UI date
        // range (this test's own proof that "existing filters still work" alongside the fix).
        HttpResponse<String> combined = ceo.get(
                "/app/reports/admin-actions?startDate=" + today + "&endDate=" + today + "&actionType=PERMISSION_GRANT");
        assertThat(combined.statusCode()).isEqualTo(200);
        assertThat(combined.body()).contains(marker);

        // An open-ended range (blank To) must also include today's action - the backend already
        // supports this (endDate == null -> Instant.now() as the upper bound).
        HttpResponse<String> openEnded = ceo.get("/app/reports/admin-actions?startDate=" + today);
        assertThat(openEnded.statusCode()).isEqualTo(200);
        assertThat(openEnded.body()).contains(marker);
    }

    @Test
    void newFilterButtonAndDateRangeErrorSpanAreRenderedInTheForm() throws Exception {
        TestApiClient ceo = ceo();
        String body = ceo.get("/app/reports/admin-actions").body();

        assertThat(body).contains("id=\"aaFilterBtn\"");
        assertThat(body).contains("type=\"submit\"");
        assertThat(body).contains("id=\"aaDateRangeError\"");
        // The existing field names/ids must be unchanged - no new parameter names introduced.
        assertThat(body).contains("name=\"startDate\" id=\"aaDateFrom\"");
        assertThat(body).contains("name=\"endDate\" id=\"aaDateTo\"");
        // Clear must still be present and pointing at the same bare URL as before.
        assertThat(body).contains("reports-clear");
    }
}
