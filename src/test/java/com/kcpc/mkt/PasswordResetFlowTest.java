package com.kcpc.mkt;

import com.kcpc.mkt.security.PasswordResetService;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Self-service "Forgot Password" flow (PasswordResetService, AuthMvcController's /forgot-password
 * and /reset-password): drives the real HTTP surface exactly as a browser would (no mocking) -
 * only the raw reset token itself is obtained by calling {@link PasswordResetService#requestReset}
 * directly rather than parsing an email, since this environment has no mail/SMTP delivery to
 * intercept (the same reason the service logs the link instead - see its own javadoc). Every other
 * step (token validation, expiry, single-use, password hashing, session invalidation, audit) goes
 * through the real /reset-password endpoint and real Postgres, never a mock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PasswordResetFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    PasswordResetService passwordResetService;
    @Autowired
    DataSource dataSource;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    private String createTestUser(TestApiClient ceo, String email, String password) throws Exception {
        ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Password Reset Test User\",\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"password reset test fixture\"}");
        return email;
    }

    private HttpResponse<String> submitReset(String token, String newPassword, String confirmPassword) throws Exception {
        TestApiClient anon = new TestApiClient(port);
        anon.primeCsrf();
        return anon.postForm("/reset-password",
                Map.of("token", token == null ? "" : token, "newPassword", newPassword, "confirmPassword", confirmPassword));
    }

    @Test
    void validResetFlowChangesThePasswordSoOnlyTheNewOneWorks() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "reset-valid-" + unique + "@kcpcbandhani.local";
        createTestUser(ceo, email, "OldPassw0rd!");

        String rawToken = passwordResetService.requestReset(email, "127.0.0.1").orElseThrow();

        HttpResponse<String> resetResponse = submitReset(rawToken, "NewPassw0rd!", "NewPassw0rd!");
        assertThat(resetResponse.statusCode()).isEqualTo(200);
        assertThat(resetResponse.body()).contains("Your password has been reset");

        // Password actually changes: old password now fails, new password works.
        TestApiClient oldAttempt = new TestApiClient(port);
        assertThat(oldAttempt.loginRaw(email, "OldPassw0rd!").statusCode()).isEqualTo(401);

        TestApiClient newAttempt = new TestApiClient(port);
        assertThat(newAttempt.loginRaw(email, "NewPassw0rd!").statusCode()).isEqualTo(200);
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        HttpResponse<String> response = submitReset("not-a-real-token-at-all", "SomeNewPassw0rd!", "SomeNewPassw0rd!");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("This reset link is invalid");
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "reset-expired-" + unique + "@kcpcbandhani.local";
        createTestUser(ceo, email, "OldPassw0rd!");

        String rawToken = passwordResetService.requestReset(email, "127.0.0.1").orElseThrow();
        String tokenHash = PasswordResetService.sha256Hex(rawToken);
        new JdbcTemplate(dataSource).update(
                "update password_reset_tokens set expires_at = now() - interval '1 hour' where token_hash = ?",
                tokenHash);

        HttpResponse<String> response = submitReset(rawToken, "NewPassw0rd!", "NewPassw0rd!");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("This reset link has expired");

        // Password must NOT have changed - the old one still works.
        TestApiClient stillOld = new TestApiClient(port);
        assertThat(stillOld.loginRaw(email, "OldPassw0rd!").statusCode()).isEqualTo(200);
    }

    @Test
    void usedTokenCannotResetAgain() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "reset-reuse-" + unique + "@kcpcbandhani.local";
        createTestUser(ceo, email, "OldPassw0rd!");

        String rawToken = passwordResetService.requestReset(email, "127.0.0.1").orElseThrow();

        HttpResponse<String> firstUse = submitReset(rawToken, "FirstNewPassw0rd!", "FirstNewPassw0rd!");
        assertThat(firstUse.statusCode()).isEqualTo(200);
        assertThat(firstUse.body()).contains("Your password has been reset");

        // Single use only: the exact same token must be rejected on a second attempt, even with a
        // different candidate password, and must NOT overwrite the password set by the first use.
        HttpResponse<String> secondUse = submitReset(rawToken, "SecondNewPassw0rd!", "SecondNewPassw0rd!");
        assertThat(secondUse.statusCode()).isEqualTo(200);
        assertThat(secondUse.body()).contains("This reset link has already been used");

        TestApiClient firstPasswordStillWorks = new TestApiClient(port);
        assertThat(firstPasswordStillWorks.loginRaw(email, "FirstNewPassw0rd!").statusCode()).isEqualTo(200);
        TestApiClient secondPasswordRejected = new TestApiClient(port);
        assertThat(secondPasswordRejected.loginRaw(email, "SecondNewPassw0rd!").statusCode()).isEqualTo(401);
    }

    @Test
    void successfulResetInvalidatesAlreadyIssuedSessions() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "reset-session-" + unique + "@kcpcbandhani.local";
        createTestUser(ceo, email, "OldPassw0rd!");

        // Establish a real, currently-active session for this user BEFORE the reset.
        TestApiClient userSession = new TestApiClient(port);
        userSession.login(email, "OldPassw0rd!");
        assertThat(userSession.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        String activeJwtCookie = "KCPC_AT=" + userSession.currentCookieValue("KCPC_AT");

        String rawToken = passwordResetService.requestReset(email, "127.0.0.1").orElseThrow();
        HttpResponse<String> resetResponse = submitReset(rawToken, "NewPassw0rd!", "NewPassw0rd!");
        assertThat(resetResponse.statusCode()).isEqualTo(200);
        assertThat(resetResponse.body()).contains("Your password has been reset");

        // The pre-reset session must now be rejected as revoked, independent of whether the
        // original client itself would have kept sending it.
        HttpResponse<String> replay = userSession.getWithRawCookie("/api/v1/auth/me", activeJwtCookie);
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(replay.body()).contains("AUTH_TOKEN_REVOKED");
    }
}
