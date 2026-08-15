package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        HttpResponse<String> kpiConsole = ceo.get("/app/reports/kpis");
        assertOk(kpiConsole);
        assertThat(countOccurrences(kpiConsole.body(), "kpi-tile")).isEqualTo(30);
        assertOk(ceo.get("/app/reports/admin-actions"));
        assertOk(ceo.get("/app/reports/delayed"));
        assertOk(ceo.get("/app/audit"));
        assertOk(ceo.get("/app/export"));
    }

    private static int countOccurrences(String body, String needle) {
        Pattern pattern = Pattern.compile(Pattern.quote(needle));
        Matcher matcher = pattern.matcher(body);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private void assertOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("LazyInitializationException", "NullPointerException",
                "PropertyNotFoundException", "Whitelabel Error Page", "500 Internal Server Error");
    }
}
