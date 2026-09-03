package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production fix: GET "/" (content.vedtri.com's root URL) must never 404/whitelabel-error, for
 * either an unauthenticated or an authenticated visitor - previously nothing mapped "/" at all,
 * so an authenticated request fell through Spring Security's authorizeHttpRequests check (which
 * already covered "/" via anyRequest().authenticated(), since it was never in the appFilterChain's
 * permitAll list) into DispatcherServlet with no handler. AuthMvcController#root() now maps "/"
 * itself, redirecting to the exact same /app/home landing target doLogin() already redirects to
 * after a fresh sign-in (LandingMvcController#home's existing role-appropriate dispatch) - no new
 * authorization rule was added or changed; "/" was already gated identically to every other
 * unmapped path, so an unauthenticated request still never reaches this new handler at all
 * (MvcAuthEntryPoint redirects it to /login first, exactly as before).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RootUrlRoutingTest {

    @LocalServerPort
    int port;

    // --- TEST 1: unauthenticated ---------------------------------------------------------------
    @Test
    void unauthenticatedRootRedirectsToLoginAndLoginPageLoads() throws Exception {
        TestApiClient client = new TestApiClient(port);

        HttpResponse<String> rootResponse = client.get("/");
        assertThat(rootResponse.statusCode()).isEqualTo(302);
        String location = rootResponse.headers().firstValue("Location").orElse("");
        assertThat(location).contains("/login");
        assertThat(location).doesNotContain("/app/"); // never lands an unauthenticated visitor on an app page

        HttpResponse<String> loginResponse = client.get("/login");
        assertThat(loginResponse.statusCode()).isEqualTo(200);
        assertThat(loginResponse.body()).doesNotContain("Whitelabel Error Page");
    }

    // --- TEST 2: authenticated -------------------------------------------------------------------
    @Test
    void authenticatedRootDoesNotRedirectToLoginAndReachesTheNormalLandingPage() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        HttpResponse<String> rootResponse = ceo.get("/");
        assertThat(rootResponse.statusCode()).isEqualTo(302);
        String location = rootResponse.headers().firstValue("Location").orElse("");
        assertThat(location).doesNotContain("/login");
        // Same landing dispatch a fresh login itself redirects to (LandingMvcController#home).
        assertThat(location).contains("/app/home");

        HttpResponse<String> homeResponse = ceo.get("/app/home");
        assertThat(homeResponse.statusCode()).isEqualTo(302);
        // CEO's own role-appropriate landing target (LandingMvcController#home).
        assertThat(homeResponse.headers().firstValue("Location").orElse("")).contains("/app/pipeline");

        HttpResponse<String> pipelineResponse = ceo.get("/app/pipeline");
        assertThat(pipelineResponse.statusCode()).isEqualTo(200);
    }

    // --- Regression: existing /login behavior is untouched ---------------------------------------
    @Test
    void loginPageStillReturns200Directly() throws Exception {
        TestApiClient client = new TestApiClient(port);
        assertThat(client.get("/login").statusCode()).isEqualTo(200);
    }

    // --- Regression: /app/** unauthenticated redirect is untouched -------------------------------
    @Test
    void unauthenticatedAppHomeStillRedirectsToLogin() throws Exception {
        TestApiClient client = new TestApiClient(port);
        HttpResponse<String> response = client.get("/app/home");
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/login");
    }

    // --- Regression: /api/v1/** authentication enforcement is untouched --------------------------
    @Test
    void unauthenticatedApiCallStillReturns401JsonNotARedirect() throws Exception {
        TestApiClient client = new TestApiClient(port);
        HttpResponse<String> response = client.get("/api/v1/auth/me");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    // --- Regression: static assets remain publicly served, unaffected ----------------------------
    @Test
    void staticAssetsStillServePublicly() throws Exception {
        TestApiClient client = new TestApiClient(port);
        assertThat(client.get("/css/app.css").statusCode()).isEqualTo(200);
        assertThat(client.get("/js/reviews-workspace.js").statusCode()).isEqualTo(200);
    }
}
