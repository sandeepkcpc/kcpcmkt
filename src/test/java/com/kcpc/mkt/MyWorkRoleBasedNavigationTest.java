package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * My Work: the "All" tab has been removed entirely - each employee now sees only the stage tabs
 * they are actually authorized for (Dashboard/Shoot/Edit/Publishing), reusing the exact same
 * pre-existing {@code showShootTab}/{@code showEditTab}/{@code showPublishTab} gates the
 * individual stage panels themselves were already gated by (see {@code LandingMvcController
 * #myWork} - unchanged by this task). No new permission system, no new controller-computed
 * default-tab attribute: {@code my-work-tabs.js} already falls back to the first tab present in
 * the DOM when no button is server-marked "active" (ENG-067, reused as-is by My Shoots), and since
 * every stage-tab button here is itself conditionally rendered, "first in the DOM" is always this
 * employee's own first authorized tab in the fixed Dashboard &gt; Shoot &gt; Edit &gt; Publishing
 * button order - so this file's job is to prove the SERVER outputs the correct authorized-tab/
 * panel set with no hardcoded "active" class anywhere in the group (which would otherwise override
 * that fallback) and the "All" tab never appears for any role; the client-side "first tab becomes
 * active" mechanics themselves are the pre-existing, unmodified my-work-tabs.js behavior and are
 * not re-proven here.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyWorkRoleBasedNavigationTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    @Test
    void theAllTabButtonAndPanelAreNeverRenderedForAnyRole() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser cam = createUser(ceo, "Nav AllGone Cam " + unique, CAMERA_PERSON_ROLE_ID);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");

        String body = loginAs(cam).get("/app/my-work").body();
        assertThat(body).doesNotContain("data-tab=\"all\"").doesNotContain("data-tab-panel=\"all\"");
    }

    @Test
    void cameraPersonOnlySeesOnlyTheShootTab() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser cam = createUser(ceo, "Nav CamOnly " + unique, CAMERA_PERSON_ROLE_ID);
        grantPermission(ceo, cam.id, "PERM_18_SHOOT_EXECUTION");

        String body = loginAs(cam).get("/app/my-work").body();
        String tabBar = tabBarRegion(body);
        assertThat(tabBar).contains("data-tab=\"shoot\"");
        assertThat(tabBar).doesNotContain("data-tab=\"dashboard\"")
                .doesNotContain("data-tab=\"edit\"")
                .doesNotContain("data-tab=\"publish\"");
        assertThat(body).contains("data-tab-panel=\"shoot\"");
        assertThat(body).doesNotContain("data-tab-panel=\"dashboard\"")
                .doesNotContain("data-tab-panel=\"edit\"")
                .doesNotContain("data-tab-panel=\"publish\"");
    }

    @Test
    void editorOnlySeesOnlyTheEditTab() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser editor = createUser(ceo, "Nav EditorOnly " + unique, VIDEO_EDITOR_ROLE_ID);
        grantPermission(ceo, editor.id, "PERM_19_EDIT_EXECUTION");

        String body = loginAs(editor).get("/app/my-work").body();
        String tabBar = tabBarRegion(body);
        assertThat(tabBar).contains("data-tab=\"edit\"");
        assertThat(tabBar).doesNotContain("data-tab=\"dashboard\"")
                .doesNotContain("data-tab=\"shoot\"")
                .doesNotContain("data-tab=\"publish\"");
        assertThat(body).contains("data-tab-panel=\"edit\"");
        assertThat(body).doesNotContain("data-tab-panel=\"dashboard\"")
                .doesNotContain("data-tab-panel=\"shoot\"")
                .doesNotContain("data-tab-panel=\"publish\"");
    }

    @Test
    void publisherOnlySeesExactlyDashboardAndPublishingInThatOrder() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "Nav PubOnly " + unique, PUBLISHER_ROLE_ID);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        String body = loginAs(publisher).get("/app/my-work").body();
        String tabBar = tabBarRegion(body);
        assertThat(tabBar).contains("data-tab=\"dashboard\"").contains("data-tab=\"publish\"");
        assertThat(tabBar).doesNotContain("data-tab=\"shoot\"").doesNotContain("data-tab=\"edit\"");
        assertThat(tabBar.indexOf("data-tab=\"dashboard\""))
                .as("Dashboard must render before Publishing in the tab bar")
                .isLessThan(tabBar.indexOf("data-tab=\"publish\""));
        assertThat(body).contains("data-tab-panel=\"dashboard\"").contains("data-tab-panel=\"publish\"");
        assertThat(body).doesNotContain("data-tab-panel=\"shoot\"").doesNotContain("data-tab-panel=\"edit\"");
    }

    /**
     * Multi-role employee (permission-driven, not Business-Role-driven - ENG-057/058 architecture):
     * holds both PERM_18 and PERM_19 regardless of their own single Business Role, and must see
     * exactly the union of the two matching tabs, nothing else.
     */
    @Test
    void multiRoleEmployeeSeesExactlyTheUnionOfTheirAuthorizedTabs() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser multi = createUser(ceo, "Nav MultiRole " + unique, CAMERA_PERSON_ROLE_ID);
        grantPermission(ceo, multi.id, "PERM_18_SHOOT_EXECUTION");
        grantPermission(ceo, multi.id, "PERM_19_EDIT_EXECUTION");

        String body = loginAs(multi).get("/app/my-work").body();
        String tabBar = tabBarRegion(body);
        assertThat(tabBar).contains("data-tab=\"shoot\"").contains("data-tab=\"edit\"");
        assertThat(tabBar).doesNotContain("data-tab=\"dashboard\"").doesNotContain("data-tab=\"publish\"");
        assertThat(body).contains("data-tab-panel=\"shoot\"").contains("data-tab-panel=\"edit\"");
        assertThat(body).doesNotContain("data-tab-panel=\"dashboard\"").doesNotContain("data-tab-panel=\"publish\"");
    }

    /**
     * No button in the stage-tab group carries a hardcoded "active" class - required so
     * my-work-tabs.js's tabs[0]-fallback (the first tab actually present in the DOM, i.e. this
     * employee's own first authorized tab) is always what selects the default, never a
     * pre-baked/removed choice.
     */
    @Test
    void noStageTabButtonCarriesAHardcodedActiveClass() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        TestUser publisher = createUser(ceo, "Nav NoHardcodeActive " + unique, PUBLISHER_ROLE_ID);
        grantPermission(ceo, publisher.id, "PERM_08_PUBLISHING_EXECUTION");

        String body = loginAs(publisher).get("/app/my-work").body();
        String tabBar = tabBarRegion(body);
        assertThat(tabBar).doesNotContain("my-work-stage-tab active");
    }

    /**
     * Zero-authorized-tabs edge case: an employee with no execution permission and no
     * current/history assignment data in any of the three stages must see a graceful
     * informational message instead of an empty tab bar with nothing below it.
     */
    @Test
    void employeeWithNoAuthorizedStageSeesGracefulEmptyStateAndNoTabBarButtons() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        // Camera Person role, but PERM_18 is deliberately never granted and no assignment is ever
        // created for this user - so showShootTab/showEditTab/showPublishTab are all false.
        TestUser bare = createUser(ceo, "Nav ZeroTabs " + unique, CAMERA_PERSON_ROLE_ID);

        String body = loginAs(bare).get("/app/my-work").body();
        String tabBar = tabBarRegion(body);
        assertThat(tabBar).doesNotContain("data-tab=\"dashboard\"")
                .doesNotContain("data-tab=\"shoot\"")
                .doesNotContain("data-tab=\"edit\"")
                .doesNotContain("data-tab=\"publish\"");
        assertThat(body).contains("You have no assigned work yet.");
    }

    // ------------------------------------------------------------------------------------------

    private record TestUser(String id, String email, String password) {
    }

    private TestUser createUser(TestApiClient ceo, String fullName, String roleId) throws Exception {
        String email = "e2e-" + fullName.toLowerCase().replace(" ", "-") + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return new TestUser(user.get("userId").asText(), email, "Passw0rd!");
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture\"}");
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email, user.password);
        return client;
    }

    /** Isolates just the {@code .my-work-stage-tabs} button bar, before any panel content. */
    private String tabBarRegion(String body) {
        int start = body.indexOf("class=\"my-work-stage-tabs\"");
        int end = body.indexOf("</div>", start);
        assertThat(start).as("stage-tabs bar must be present").isPositive();
        assertThat(end).isGreaterThan(start);
        return body.substring(start, end);
    }
}
