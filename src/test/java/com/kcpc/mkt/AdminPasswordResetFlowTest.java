package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Admin/CEO Password Reset (the primary reset path for this internal, email/SMS-infra-free app -
 * see PasswordResetService's own javadoc): CEO/Admin User Management -&gt; select employee -&gt;
 * Reset Password -&gt; a temporary password is generated and shown to the CEO once to copy/share
 * out-of-band -&gt; the employee's next login is forced through a "Change Password" screen before
 * anything else in the app is reachable. Drives the real HTTP surface (MVC form posts + real
 * redirects, real Postgres) - no mocking anywhere.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AdminPasswordResetFlowTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final Pattern TEMP_PASSWORD_PATTERN =
            Pattern.compile("id=\"adminTempPasswordValue\"[^>]*>([^<]+)</code>");

    private String createEmployee(TestApiClient ceo, String email, String password) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Admin Reset Test User\",\"email\":\"" + email + "\",\"password\":\"" + password
                        + "\",\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"admin reset test fixture\"}");
        return response.get("userId").asText();
    }

    /** The servlet container's redirect Location header is an absolute URL, and - on the very
     * first request that ever populates a flash-attribute HttpSession - may also carry a defensive
     * ";jsessionid=..." path parameter for clients the container doesn't yet know support cookies.
     * TestApiClient#get only accepts a path to append to its own base URL, so strip the scheme/
     * host/port back off; the ";jsessionid=..." segment is dropped too (rather than kept) since
     * Spring Security's StrictHttpFirewall rejects a semicolon in the request path by default, and
     * TestApiClient's shared CookieManager already carries the session via the Set-Cookie header
     * from the very same response, making the URL-embedded copy redundant anyway. */
    private static String pathOf(String location) throws Exception {
        String path;
        if (location.startsWith("http")) {
            java.net.URI uri = new java.net.URI(location);
            path = uri.getRawQuery() != null ? uri.getRawPath() + "?" + uri.getRawQuery() : uri.getRawPath();
        } else {
            path = location;
        }
        int semicolon = path.indexOf(';');
        return semicolon >= 0 ? path.substring(0, semicolon) : path;
    }

    /** Follows the reset-password POST's redirect (with the SAME client, so cookies persist) and
     * pulls the one-time temporary password out of the re-rendered admin-user-detail page. */
    private String resetAndCaptureTemporaryPassword(TestApiClient ceo, String userId, String reason) throws Exception {
        HttpResponse<String> postResponse = ceo.postForm("/app/admin/users/" + userId + "/reset-password",
                Map.of("reason", reason));
        assertThat(postResponse.statusCode()).isEqualTo(302);
        String location = pathOf(postResponse.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> detailPage = ceo.get(location);
        assertThat(detailPage.statusCode()).isEqualTo(200);
        Matcher matcher = TEMP_PASSWORD_PATTERN.matcher(detailPage.body());
        assertThat(matcher.find()).as("temporary password shown on the redirected page").isTrue();
        return matcher.group(1).trim();
    }

    @Test
    void ceoResetIssuesTemporaryPasswordForcesChangeAndInvalidatesOldSessions() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "admin-reset-" + unique + "@kcpcbandhani.local";
        String userId = createEmployee(ceo, email, "OriginalPassw0rd!");

        // A currently-active session BEFORE the admin reset.
        TestApiClient employeeSession = new TestApiClient(port);
        employeeSession.login(email, "OriginalPassw0rd!");
        assertThat(employeeSession.get("/api/v1/auth/me").statusCode()).isEqualTo(200);
        String preResetJwtCookie = "KCPC_AT=" + employeeSession.currentCookieValue("KCPC_AT");

        String temporaryPassword = resetAndCaptureTemporaryPassword(ceo, userId, "Employee requested reset");
        assertThat(temporaryPassword).matches("TEMP-\\d{6}-KCPC");

        // Session invalidation: the pre-reset session is now revoked.
        HttpResponse<String> replay = employeeSession.getWithRawCookie("/api/v1/auth/me", preResetJwtCookie);
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(replay.body()).contains("AUTH_TOKEN_REVOKED");

        // Audit event recorded: PASSWORD_RESET_BY_ADMIN, actor CEO, target the employee, reason preserved.
        JsonNode auditLogs = ceo.getJson("/api/v1/audit/logs?actionType=PASSWORD_RESET_BY_ADMIN");
        boolean found = false;
        for (JsonNode entry : auditLogs) {
            if (entry.get("targetEntityId").asText().equals(userId)) {
                found = true;
                assertThat(entry.get("actionReason").asText()).isEqualTo("Employee requested reset");
                assertThat(entry.get("eventCategory").asText()).isEqualTo("USER_ADMIN");
            }
        }
        assertThat(found).as("PASSWORD_RESET_BY_ADMIN audit entry for the target user").isTrue();

        // Old password no longer works; the temporary password does.
        TestApiClient oldPasswordAttempt = new TestApiClient(port);
        assertThat(oldPasswordAttempt.loginRaw(email, "OriginalPassw0rd!").statusCode()).isEqualTo(401);

        TestApiClient tempPasswordClient = new TestApiClient(port);
        assertThat(tempPasswordClient.loginRaw(email, temporaryPassword).statusCode()).isEqualTo(200);

        // Forced Change Password: every other /app/** page redirects to /app/change-password.
        HttpResponse<String> homeAttempt = tempPasswordClient.get("/app/home");
        assertThat(homeAttempt.statusCode()).isEqualTo(302);
        assertThat(homeAttempt.headers().firstValue("Location").orElseThrow()).contains("/app/change-password");

        HttpResponse<String> changePasswordPage = tempPasswordClient.get("/app/change-password");
        assertThat(changePasswordPage.statusCode()).isEqualTo(200);

        HttpResponse<String> changeResponse = tempPasswordClient.postForm("/app/change-password",
                Map.of("newPassword", "BrandNewPassw0rd!", "confirmPassword", "BrandNewPassw0rd!"));
        assertThat(changeResponse.statusCode()).isEqualTo(302);
        assertThat(changeResponse.headers().firstValue("Location").orElseThrow()).contains("/app/home");

        // Requirement cleared: normal navigation now works on the same session - /app/home for a
        // Camera Person always dispatches onward to /app/my-work (LandingMvcController#home, a
        // pre-existing, unrelated role-based redirect), so the real proof is that it no longer
        // redirects to /app/change-password, and the actual landing page now renders (200).
        HttpResponse<String> homeAfterChange = tempPasswordClient.get("/app/home");
        assertThat(homeAfterChange.statusCode()).isEqualTo(302);
        assertThat(homeAfterChange.headers().firstValue("Location").orElseThrow()).contains("/app/my-work");
        assertThat(tempPasswordClient.get("/app/my-work").statusCode()).isEqualTo(200);

        // Password actually changed again: temporary password no longer works, the chosen one does.
        TestApiClient tempAgain = new TestApiClient(port);
        assertThat(tempAgain.loginRaw(email, temporaryPassword).statusCode()).isEqualTo(401);
        TestApiClient finalPassword = new TestApiClient(port);
        assertThat(finalPassword.loginRaw(email, "BrandNewPassw0rd!").statusCode()).isEqualTo(200);
    }

    @Test
    void reasonIsMandatoryForAdminPasswordReset() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String email = "admin-reset-noreason-" + unique + "@kcpcbandhani.local";
        String userId = createEmployee(ceo, email, "OriginalPassw0rd!");

        HttpResponse<String> postResponse = ceo.postForm("/app/admin/users/" + userId + "/reset-password",
                Map.of("reason", ""));
        assertThat(postResponse.statusCode()).isEqualTo(302);
        String location = pathOf(postResponse.headers().firstValue("Location").orElseThrow());
        HttpResponse<String> detailPage = ceo.get(location);
        assertThat(detailPage.body()).doesNotContain("id=\"adminTempPasswordValue\"");

        // No password change happened - the original password still works.
        TestApiClient stillOriginal = new TestApiClient(port);
        assertThat(stillOriginal.loginRaw(email, "OriginalPassw0rd!").statusCode()).isEqualTo(200);
    }

    @Test
    void nonCeoCannotResetAnotherUsersPassword() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String targetEmail = "admin-reset-target-" + unique + "@kcpcbandhani.local";
        String targetUserId = createEmployee(ceo, targetEmail, "OriginalPassw0rd!");
        String attackerEmail = "admin-reset-attacker-" + unique + "@kcpcbandhani.local";
        createEmployee(ceo, attackerEmail, "AttackerPassw0rd!");

        TestApiClient attacker = new TestApiClient(port);
        attacker.login(attackerEmail, "AttackerPassw0rd!");

        HttpResponse<String> postResponse = attacker.postForm("/app/admin/users/" + targetUserId + "/reset-password",
                Map.of("reason", "trying to reset someone else's password"));
        // The MVC handler catches DomainException and redirects with a flash error (matching every
        // sibling admin action in this controller) - never a raw 500, and critically, never an
        // actual password change.
        assertThat(postResponse.statusCode()).isEqualTo(302);

        TestApiClient stillOriginal = new TestApiClient(port);
        assertThat(stillOriginal.loginRaw(targetEmail, "OriginalPassw0rd!").statusCode()).isEqualTo(200);
    }
}
