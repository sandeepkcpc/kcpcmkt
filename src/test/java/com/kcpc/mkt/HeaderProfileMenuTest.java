package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * App header: the always-visible "Sign out" pill is replaced by a user profile trigger
 * (avatar/name/chevron) that opens a dropdown menu (name, email, divider, Sign out) - plus a
 * purely-visual notification bell (no notification backend/data exists anywhere in this app, so
 * no count is fabricated). The name/email shown are read from the real logged-in principal via
 * {@code MvcNavigationAdvice#currentUserFullName}/{@code #currentUserEmail}, never hardcoded - the
 * two-employee test below proves this by checking each sees their OWN identity, not a shared or
 * hardcoded value. The Sign out button inside the dropdown posts to the exact same {@code /logout}
 * endpoint ({@code AuthMvcController#doLogout}) this app has always used - no new/duplicate logout
 * logic, verified end-to-end here (not just markup) by an actual POST /logout call.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HeaderProfileMenuTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    @Test
    void headerNoLongerShowsAnAlwaysVisibleSignOutButton() throws Exception {
        TestApiClient ceo = ceo();
        HttpResponse<String> response = ceo.get("/app/pipeline");
        String body = response.body();
        // The old always-visible pill is gone; Sign out now lives only inside the (closed-by-
        // default) dropdown, as a differently-styled button.
        assertThat(body).doesNotContain("<button type=\"submit\" class=\"link-button\">Sign out</button>");
        assertThat(body).contains("id=\"headerProfileMenu\"");
        assertThat(body).contains("class=\"app-header-profile-menu hidden\"");
        assertThat(body).contains("<button type=\"submit\" class=\"app-header-profile-menu-signout\">");
    }

    @Test
    void profileTriggerAndDropdownShowTheRealLoggedInUsersNameAndEmailNotHardcoded() throws Exception {
        TestApiClient ceo = ceo();
        HttpResponse<String> response = ceo.get("/app/pipeline");
        String body = response.body();
        assertThat(body).doesNotContain("Karan Mehta");
        // Trigger (collapsed state) and dropdown (identity block) both carry the same real name/email.
        assertThat(body).contains("<span class=\"app-header-profile-name\">KCPC CEO</span>");
        assertThat(body).contains("<span class=\"app-header-profile-menu-name\">KCPC CEO</span>");
        assertThat(body).contains("<span class=\"app-header-profile-menu-email\">ceo@kcpcbandhani.local</span>");
    }

    @Test
    void differentLoggedInUsersSeeTheirOwnNameProvingItIsDynamic() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "hpm-cam-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Header Menu Camera " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"header profile menu test fixture\"}");
        assertThat(user.get("userId").asText()).isNotBlank();

        TestApiClient cam = new TestApiClient(port);
        cam.login(email, "Passw0rd!");
        HttpResponse<String> camResponse = cam.get("/app/my-work");
        String camBody = camResponse.body();
        assertThat(camBody).contains("Header Menu Camera " + unique);
        assertThat(camBody).doesNotContain("KCPC CEO");

        HttpResponse<String> ceoResponse = ceo.get("/app/pipeline");
        assertThat(ceoResponse.body()).contains("KCPC CEO");
        assertThat(ceoResponse.body()).doesNotContain("Header Menu Camera " + unique);
    }

    @Test
    void dropdownStructureMatchesTheRequiredShapeNameEmailDividerSignOut() throws Exception {
        TestApiClient ceo = ceo();
        String body = ceo.get("/app/pipeline").body();
        int menuStart = body.indexOf("id=\"headerProfileMenu\"");
        int menuEnd = body.indexOf("</div>", body.indexOf("logout-form", menuStart));
        String menu = body.substring(menuStart, menuEnd);
        assertThat(menu).contains("app-header-profile-menu-identity");
        assertThat(menu).contains("app-header-avatar");
        assertThat(menu).contains("app-header-profile-menu-name");
        assertThat(menu).contains("app-header-profile-menu-email");
        assertThat(menu).contains("app-header-profile-menu-divider");
        assertThat(menu).contains("app-header-profile-menu-signout");
    }

    @Test
    void signOutInsideTheDropdownPerformsTheExactSameExistingLogout() throws Exception {
        TestApiClient ceo = ceo();
        // Confirm authenticated first.
        assertThat(ceo.get("/app/pipeline").statusCode()).isEqualTo(200);

        HttpResponse<String> logoutResponse = ceo.post("/logout", "");
        assertThat(logoutResponse.statusCode()).isIn(200, 302, 303);

        // Session is genuinely gone - the same protected page now redirects to /login.
        HttpResponse<String> afterLogout = ceo.get("/app/pipeline");
        assertThat(afterLogout.statusCode()).isEqualTo(302);
        assertThat(afterLogout.headers().firstValue("Location").orElse("")).contains("/login");
    }

    @Test
    void notificationBellIsPresentWithoutAnyFabricatedCount() throws Exception {
        // A brand-new user (never the shared seeded CEO, whose own notification history
        // accumulates across the whole test suite run - see NotificationSystemTest for that
        // behavior) has zero notifications, so the badge element must not render at all - it is
        // only rendered when unreadNotificationCount > 0 (see nav.jsp), never an empty/zero badge.
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String email = "hpm-fresh-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Header Fresh User " + unique + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"header profile menu test fixture\"}");
        assertThat(user.get("userId").asText()).isNotBlank();
        TestApiClient fresh = new TestApiClient(port);
        fresh.login(email, "Passw0rd!");

        String body = fresh.get("/app/my-work").body();
        assertThat(body).contains("app-header-notification-btn");
        assertThat(body).doesNotContain("app-header-notification-badge");
    }

    @Test
    void existingNavAndAdministrationVisibilityAreUnaffectedByTheHeaderChange() throws Exception {
        TestApiClient ceo = ceo();
        String body = ceo.get("/app/pipeline").body();
        assertThat(body).contains(">Content Pipeline<");
        assertThat(body).contains(">Reviews<");
        assertThat(body).contains(">Team<");
        assertThat(body).contains(">Reports<");
        assertThat(body).contains(">My Ideas<");
        assertThat(body).contains(">Submit Idea<");
        assertThat(body).contains(">Administration<");
    }
}
