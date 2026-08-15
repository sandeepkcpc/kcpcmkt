package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 4 (Reporting/KPIs) regression guard for a real defect this pass found and fixed:
 * {@code AuditRestController} originally returned {@code SystemAuditLog} entities directly,
 * which - because {@code actor} is an EAGER {@code User} association - serialized the actor's
 * bcrypt {@code passwordHash} into the JSON response body. Fixed by (1) projecting audit-log
 * reads through {@code AuditLogResponse} instead of the raw entity, and (2) marking
 * {@code User.getPasswordHash()} {@code @JsonIgnore} as defense-in-depth against any future
 * accidental direct serialization of a {@code User} (or an association that eagerly pulls one
 * in) anywhere else in the API surface.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportingApiSecurityTest {

    @LocalServerPort
    int port;

    @Test
    void auditLogNeverSerializesAPasswordHash() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        var response = ceo.get("/api/v1/audit/logs");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("passwordHash", "$2y$", "$2a$", "$2b$");
    }

    @Test
    void governedKpisAndTeamReportsRenderWithoutError() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode kpis = ceo.getJson("/api/v1/reports/kpis");
        assertThat(kpis.isArray()).isTrue();
        assertThat(kpis.size()).isEqualTo(30);
        for (JsonNode kpi : kpis) {
            assertThat(kpi.get("code").asText()).matches("KPI-0[0-3][0-9]");
            assertThat(kpi.get("displayValue").asText()).isNotBlank();
        }

        assertThat(ceo.get("/api/v1/team/workload").statusCode()).isEqualTo(200);
        assertThat(ceo.get("/api/v1/team/kpis").statusCode()).isEqualTo(200);
        assertThat(ceo.get("/api/v1/reports/administrative-actions").statusCode()).isEqualTo(200);
        assertThat(ceo.get("/api/v1/reports/delayed-deliverables").statusCode()).isEqualTo(200);
    }
}
