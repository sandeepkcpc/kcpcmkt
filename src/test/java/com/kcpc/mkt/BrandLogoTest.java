package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KCPC Bandhani logo: the official uploaded logo asset (unmodified pixels, format-transcoded from
 * the source .avif to .png for universal browser support) replaces the old plain-text "KCPC
 * Bandhani" branding in both the app header ({@code nav.jsp}'s {@code .brand}) and the Login page
 * ({@code login.jsp}). Served from {@code /images/kcpc-logo.png}, added to
 * {@code SecurityConfig}'s public permitAll list alongside {@code /css/**}/{@code /js/**} - the
 * Login page itself is unauthenticated, so the image must be reachable without a session or it
 * would silently fail to load there.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrandLogoTest {

    @LocalServerPort
    int port;

    @Test
    void logoAssetIsPubliclyServedWithoutAuthentication() throws Exception {
        TestApiClient client = new TestApiClient(port);
        HttpResponse<String> response = client.get("/images/kcpc-logo.png");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("image/png");
    }

    @Test
    void loginPageRendersTheLogoInPlaceOfThePlainTextHeading() throws Exception {
        TestApiClient client = new TestApiClient(port);
        HttpResponse<String> response = client.get("/login");
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("<img src=\"/images/kcpc-logo.png\" alt=\"KCPC Bandhani\" class=\"auth-logo\">");
        assertThat(body).doesNotContain("<h1>KCPC Bandhani</h1>");
        // Subtitle and existing login fields/mechanics are all still present, unmoved.
        assertThat(body).contains("Content Production Lifecycle");
        assertThat(body).contains("name=\"email\"");
        assertThat(body).contains("<button type=\"submit\">Sign In</button>");
    }

    @Test
    void authenticatedHeaderRendersTheLogoDirectlyOnTheDarkHeader() throws Exception {
        // The logo asset is a genuinely transparent-background PNG (verified: corner/edge pixels
        // alpha=0), so it renders straight on the dark .app-header with no white "badge" chip
        // wrapper needed around it (an earlier revision used one, back when the source artwork
        // still had an opaque white background).
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        HttpResponse<String> response = ceo.get("/app/pipeline");
        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(body).contains("<img src=\"/images/kcpc-logo.png\" alt=\"KCPC Bandhani\" class=\"brand-logo\">");
        assertThat(body).doesNotContain("brand-logo-badge");
        assertThat(body).doesNotContain("<span class=\"brand\">KCPC Bandhani</span>");
        // Logout form/action itself (see HeaderProfileMenuTest for the moved-into-dropdown Sign
        // out button and the rest of the profile menu) - unchanged.
        assertThat(body).contains("class=\"logout-form\"");
        assertThat(body).contains("action=\"/logout\"");
    }

    @Test
    void logoAssetHasAGenuinelyTransparentBackground() throws Exception {
        // Guards against silently regressing back to an opaque-background export (which would
        // need the old white chip wrapper again) - reads real pixel bytes, not just trusting the
        // file was replaced correctly.
        TestApiClient client = new TestApiClient(port);
        HttpResponse<byte[]> response = client.getBytes("/images/kcpc-logo.png");
        assertThat(response.statusCode()).isEqualTo(200);
        javax.imageio.ImageIO.setUseCache(false);
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        int corner = image.getRGB(0, 0);
        assertThat(corner >>> 24).as("corner pixel alpha channel").isZero();
    }

    @Test
    void realLoginStillSucceedsAfterTheLogoSwap() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        var loginResult = ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        assertThat(loginResult).isNotNull();
        HttpResponse<String> pipeline = ceo.get("/app/pipeline");
        assertThat(pipeline.statusCode()).isEqualTo(200);
    }
}
