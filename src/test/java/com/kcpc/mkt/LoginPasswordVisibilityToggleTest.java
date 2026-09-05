package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Login screen Password visibility toggle - a purely client-side (HTML/CSS/JS) enhancement, so
 * this only proves the SERVER-RENDERED markup carries the expected structure/wiring
 * ({@code .password-field}/{@code .password-toggle}/{@code data-toggle-password}, the exact same
 * mechanism {@code admin-users.jsp}'s Create User Initial Password field already ships, reused
 * here verbatim - see {@code login-password-toggle.js} and the {@code .auth-card .password-toggle}
 * CSS in app.css) and that nothing about the actual login mechanics (fields, CSRF token, form
 * action, real authentication) changed. The toggle's own show/hide/no-submit behavior is a
 * client-side interaction with nothing to assert against on the server - verified manually via a
 * headless-browser walkthrough (masked by default, reveals/re-masks on click without altering the
 * field's value, and the toggle button never submits the form).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LoginPasswordVisibilityToggleTest {

    @LocalServerPort
    int port;

    @Test
    void loginPageRendersThePasswordFieldWithTheEyeToggleWiredUp() throws Exception {
        TestApiClient client = new TestApiClient(port);
        HttpResponse<String> response = client.get("/login");
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();

        // Password input itself: unchanged id/name/required-ness, masked (type="password") by default.
        assertThat(body).contains("<input type=\"password\" id=\"password\" name=\"password\" required>");

        // The new toggle: a non-submitting button positioned inside the same field wrapper, wired
        // to that exact input id via data-toggle-password - never a second, independent input.
        assertThat(body).contains("<div class=\"password-field\">");
        assertThat(body).contains("<button type=\"button\" class=\"password-toggle\" data-toggle-password=\"password\"");
        assertThat(body).contains("aria-label=\"Show password\"");

        // Existing login fields/mechanics are all still present, unmoved.
        assertThat(body).contains("name=\"email\"");
        assertThat(body).contains("type=\"hidden\"");
        assertThat(body).contains("<form method=\"post\" action=\"");
        assertThat(body).contains("/login\">");
        assertThat(body).contains("<button type=\"submit\">Sign In</button>");

        // Cache-busting rewrites this to a content-hashed path (see BrandLogoTest's own identical
        // note) - matched loosely so this survives the hash changing whenever the script's bytes do.
        assertThat(body).containsPattern("/js/login-password-toggle(-[0-9a-f]{32})?\\.js");
    }

    @Test
    void loginPasswordToggleScriptIsServedSuccessfully() throws Exception {
        TestApiClient client = new TestApiClient(port);
        HttpResponse<String> response = client.get("/js/login-password-toggle.js");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("data-toggle-password");
    }

    @Test
    void realLoginStillSucceedsAfterWrappingThePasswordFieldForTheToggle() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        // TestApiClient#login posts the real form fields through the real /login endpoint - if
        // wrapping the <input> in .password-field had broken field name/submission in any way,
        // this would fail exactly like it would for any other regression test in this suite.
        var response = ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        assertThat(response).isNotNull();
        HttpResponse<String> pipeline = ceo.get("/app/pipeline");
        assertThat(pipeline.statusCode()).isEqualTo(200);
    }
}
