package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * API-OP-062: Multi-Format Data Export (JSON/CSV/XLSX) over the governed table union
 * (RTM-081) - authorization, format handling, and table-scope validation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExportApiTest {

    @LocalServerPort
    int port;

    @Test
    void jsonExportReturnsTheGovernedTableUnionAndNeverIdentityTables() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        var response = ceo.get("/api/v1/exports?format=JSON");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).contains("application/json");
        assertThat(response.body()).contains("\"ideas\"", "\"content_plans\"", "\"workflow_instances\"");
        // Identity/permission tables and password hashes must never appear in an export.
        assertThat(response.body()).doesNotContain("\"users\"", "\"permission_grants\"", "\"business_roles\"",
                "passwordHash", "password_hash");
    }

    @Test
    void csvExportRequiresExactlyOneTable() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        var singleTable = ceo.get("/api/v1/exports?format=CSV&tables=ideas");
        assertThat(singleTable.statusCode()).isEqualTo(200);
        assertThat(singleTable.headers().firstValue("Content-Type").orElseThrow()).contains("text/csv");

        var multiTable = ceo.get("/api/v1/exports?format=CSV&tables=ideas&tables=content_plans");
        assertThat(multiTable.statusCode()).isEqualTo(400);

        var unknownTable = ceo.get("/api/v1/exports?format=CSV&tables=users");
        assertThat(unknownTable.statusCode()).isEqualTo(400);
    }

    @Test
    void xlsxExportReturnsAWorkbook() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        var response = ceo.get("/api/v1/exports?format=XLSX");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.headers().firstValue("Content-Disposition").orElseThrow()).contains("attachment", ".xlsx");
    }

    @Test
    void exportIsManagementOnly() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = java.time.Instant.now().toEpochMilli();
        String email = "export-test-employee-" + unique + "@kcpcbandhani.local";
        ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Export Test Employee\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000004\","
                        + "\"creationReason\":\"export authorization test fixture\"}");

        TestApiClient employee = new TestApiClient(port);
        employee.login(email, "Passw0rd!");

        var response = employee.get("/api/v1/exports?format=JSON");
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
