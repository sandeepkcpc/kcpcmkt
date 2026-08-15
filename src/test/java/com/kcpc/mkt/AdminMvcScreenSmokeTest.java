package com.kcpc.mkt;

import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UI/UX Design Specification v0.2 Group A (Operational Governance): User & Business Role
 * Administration, Operational-Permission Administration, and Master-Catalogue Management,
 * driven through real HTML form POSTs against the MVC screens.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminMvcScreenSmokeTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    @Test
    void adminScreensRenderAndAcceptWrites() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        assertOk(ceo.get("/app/admin/users"));
        assertOk(ceo.get("/app/admin/business-roles"));
        assertOk(ceo.get("/app/admin/catalogue"));

        String email = "admin-mvc-" + unique + "@kcpcbandhani.local";
        HttpResponse<String> createUser = ceo.postForm("/app/admin/users",
                Map.of("fullName", "Admin MVC User", "email", email, "password", "Passw0rd!",
                        "businessRoleId", CAMERA_PERSON_ROLE_ID, "creationReason", "mvc admin smoke test"));
        assertThat(createUser.statusCode()).isEqualTo(302);

        var newUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertOk(ceo.get("/app/admin/users/" + newUser.getId()));

        HttpResponse<String> grant = ceo.postForm("/app/admin/users/" + newUser.getId() + "/permission-grants",
                Map.of("permission", "PERM_02_PLANNING_EXECUTION", "scopeType", "GLOBAL",
                        "reason", "mvc admin smoke test grant"));
        assertThat(grant.statusCode()).isEqualTo(302);
        HttpResponse<String> userDetailAfterGrant = ceo.get("/app/admin/users/" + newUser.getId());
        assertOk(userDetailAfterGrant);
        assertThat(userDetailAfterGrant.body()).contains("PERM_02_PLANNING_EXECUTION");

        // The consolidated Permissions screen must also show this grant, across all users.
        HttpResponse<String> permissionsScreen = ceo.get("/app/admin/permissions");
        assertOk(permissionsScreen);
        assertThat(permissionsScreen.body()).contains("PERM_02_PLANNING_EXECUTION").contains(email);

        HttpResponse<String> createPlatform = ceo.postForm("/app/admin/catalogue/platforms",
                Map.of("platformName", "MvcSmokePlatform" + unique, "catalogueReason", "mvc admin smoke test"));
        assertThat(createPlatform.statusCode()).isEqualTo(302);

        HttpResponse<String> createChannel = ceo.postForm("/app/admin/catalogue/channels",
                Map.of("channelHandle", "mvc_smoke_channel_" + unique, "catalogueReason", "mvc admin smoke test"));
        assertThat(createChannel.statusCode()).isEqualTo(302);

        HttpResponse<String> catalogueAfter = ceo.get("/app/admin/catalogue");
        assertOk(catalogueAfter);
        assertThat(catalogueAfter.body()).contains("MvcSmokePlatform" + unique).contains("mvc_smoke_channel_" + unique);

        HttpResponse<String> createRole = ceo.postForm("/app/admin/business-roles",
                Map.of("roleName", "MVC Smoke Role " + unique, "accessClass", "EMPLOYEE"));
        assertThat(createRole.statusCode()).isEqualTo(302);
        HttpResponse<String> rolesAfter = ceo.get("/app/admin/business-roles");
        assertOk(rolesAfter);
        assertThat(rolesAfter.body()).contains("MVC Smoke Role " + unique);
    }

    private void assertOk(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("LazyInitializationException", "NullPointerException",
                "Whitelabel Error Page", "500 Internal Server Error");
    }
}
