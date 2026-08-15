package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Section 39 authentication checklist (subset covered end-to-end via real HTTP): valid login,
 * bad credentials, missing-cookie access, CSRF-missing rejection, logout revocation + reuse.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthenticationFlowTest {

    @LocalServerPort
    int port;

    @Test
    void badCredentialsAreRejected() throws Exception {
        TestApiClient client = new TestApiClient(port);
        var response = client.loginRaw("ceo@kcpcbandhani.local", "definitely-wrong");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void meWithoutCookieIsUnauthorized() throws Exception {
        TestApiClient client = new TestApiClient(port);
        var response = client.get("/api/v1/auth/me");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        // Authenticated (JWT cookie present), but simulate a client that omits the CSRF header -
        // the CSRF cookie alone is not enough (double-submit requires the header to match it).
        client.clearCsrfToken();
        var response = client.post("/api/v1/ideas", "{\"title\":\"Should be rejected\"}");
        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void validLoginThenLogoutRevokesTheToken() throws Exception {
        TestApiClient client = new TestApiClient(port);
        var loginBody = client.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        assertThat(loginBody.get("accessClass").asText()).isEqualTo("CEO_OWNER");

        var me = client.get("/api/v1/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        String jwtCookieValue = "KCPC_AT=" + client.currentCookieValue("KCPC_AT");

        var logout = client.post("/api/v1/auth/logout", "");
        assertThat(logout.statusCode()).isEqualTo(204);

        // A well-behaved client (this one included) discards the cookie once told to - so the
        // next call from THIS client correctly has nothing to send at all.
        var meAfterLogout = client.get("/api/v1/auth/me");
        assertThat(meAfterLogout.statusCode()).isEqualTo(401);
        assertThat(meAfterLogout.body()).contains("AUTH_TOKEN_MISSING");

        // Prove server-side revocation independently of that client-side courtesy: replay the
        // exact JWT captured before logout (a stale cache or malicious replay would do this)
        // and confirm the server itself rejects it as revoked, per the token registry.
        var replay = client.getWithRawCookie("/api/v1/auth/me", jwtCookieValue);
        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(replay.body()).contains("AUTH_TOKEN_REVOKED");
    }

    /**
     * Regression guard: {@code OncePerRequestFilter} skips itself by default on the container's
     * internal forward to {@code /error} (an unmapped URL's 404, or an uncaught exception's 500),
     * so without {@code JwtAuthenticationFilter.shouldNotFilterErrorDispatch() == false}, that
     * forward carries no authentication and {@code anyRequest().authenticated()} bounces even a
     * fully logged-in user to {@code /login?reason=auth} - indistinguishable from being logged
     * out, and masking the real 404/500. A broken/typo'd MVC link must surface as a real 404, not
     * a false "you've been logged out" redirect.
     */
    @Test
    void unmappedAppUrlReturnsA404NotALoginRedirectWhileAuthenticated() throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        var response = client.get("/app/this-path-does-not-exist");
        assertThat(response.statusCode()).isEqualTo(404);

        // The session itself must still be perfectly valid afterward - a real screen still works.
        var stillAuthenticated = client.get("/app/pipeline");
        assertThat(stillAuthenticated.statusCode()).isEqualTo(200);
    }
}
