package com.kcpc.mkt;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real Upload -&gt; Validate/Preview -&gt; Confirm -&gt; Result Summary HTTP flow for
 * Administration -&gt; Users -&gt; Import Users, exactly as a CEO's browser would (multipart upload,
 * session-held bytes between Preview and Confirm, CSRF token as a real form field) - the
 * controller/session/service wiring is not exercised by {@link UserCsvImportTest}, which calls
 * {@code UserCsvImportService} directly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserCsvImportMvcFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    @Test
    void uploadPreviewConfirmFlowCreatesUserAndShowsGeneratedPasswordOnce() throws Exception {
        long unique = Instant.now().toEpochMilli();
        String email = "mvc-flow-" + unique + "@example.com";
        String csv = "full_name,email,business_role\nMvc Flow User " + unique + "," + email + ",Camera Person\n";
        TestApiClient ceo = ceo();
        ceo.primeCsrf();

        HttpResponse<String> preview = ceo.postMultipartFile("/app/admin/users/import/preview", "file", "users.csv",
                csv.getBytes(StandardCharsets.UTF_8), "text/csv");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("Row Detail").contains(email);

        HttpResponse<String> confirm = ceo.postForm("/app/admin/users/import/confirm", java.util.Map.of());
        assertThat(confirm.statusCode()).isEqualTo(200);
        assertThat(confirm.body()).contains("Imported");

        User created = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(created.isActive()).isTrue();
        assertThat(created.getBusinessRole().getRoleName()).isEqualTo("Camera Person");
        // A real generated password (not blank/placeholder) must appear exactly on this render.
        assertThat(confirm.body()).containsPattern("<code>[^<]{10,}</code>");
    }

    @Test
    void nonCeoCannotReachImportScreens() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String nonCeoEmail = "csvimport-noauth-" + unique + "@kcpcbandhani.local";
        HttpResponse<String> created = ceo.postForm("/app/admin/users",
                java.util.Map.of("fullName", "NonCeo Actor " + unique, "email", nonCeoEmail, "password", "Passw0rd!",
                        "businessRoleId", "01926e3e-0001-7000-8000-000000000004",
                        "creationReason", "csv import auth test fixture"));
        assertThat(created.statusCode()).isEqualTo(302);

        TestApiClient nonCeo = new TestApiClient(port);
        nonCeo.login(nonCeoEmail, "Passw0rd!");
        HttpResponse<String> resp = nonCeo.get("/app/admin/users/import");
        assertThat(resp.statusCode()).isEqualTo(302);
        assertThat(resp.headers().firstValue("Location").orElseThrow()).contains("/app/home");
    }
}
