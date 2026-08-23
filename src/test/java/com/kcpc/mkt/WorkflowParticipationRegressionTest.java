package com.kcpc.mkt;

import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Business Role -> Workspace routing/access: the centralized {@code participatesInWorkflow} flag
 * (never a role-name/designation check) must keep the 6 existing role experiences (CEO, Marketing
 * Manager, Camera Person, Video Editor, Model, Publisher) completely unchanged, while every other
 * EMPLOYEE Business Role gets a My Ideas + Submit Idea only workspace, enforced server-side by
 * {@code WorkflowParticipationInterceptor} even on a direct URL - not just hidden nav.
 *
 * {@link TestApiClient}'s underlying {@code HttpClient} defaults to {@code Redirect.NEVER}, so
 * every MVC 302 (both the controller's own role-based landing redirect and the interceptor's
 * deny-by-default redirect) is followed manually here via {@link #followRedirects}, mirroring how
 * a real browser would land on the final page.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WorkflowParticipationRegressionTest {

    @LocalServerPort
    int port;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String MODEL_ROLE_ID = "01926e3e-0001-7000-8000-000000000009";
    private static final String MARKETING_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000002";
    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003";
    private static final String SEO_INTERN_ROLE_ID = "01926e3e-0001-7000-8000-00000000000c";
    private static final String SEO_EXECUTIVE_ROLE_ID = "01926e3e-0001-7000-8000-00000000000b";
    private static final String SALES_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-00000000000e";
    private static final String CRM_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-00000000000f";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String label, String businessRoleId, long unique) throws Exception {
        String email = "e2e-wfp-" + label + "-" + unique + "@kcpcbandhani.local";
        ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"WFP " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"e2e test fixture\"}");
        return email;
    }

    private void setWorkflowParticipation(TestApiClient ceo, String businessRoleId, boolean participates) throws Exception {
        HttpResponse<String> response = ceo.postForm("/app/admin/business-roles/" + businessRoleId + "/workflow-participation",
                java.util.Map.of("participatesInWorkflow", Boolean.toString(participates)));
        assertThat(response.statusCode()).isEqualTo(302);
    }

    /**
     * Manually follows MVC 302s the way a real browser would (see class javadoc). The servlet
     * container always sends an absolute {@code Location} (e.g. {@code http://localhost:PORT/app
     * /ideas}), while {@link TestApiClient#get} takes a context-relative path, so only the path
     * (+query) portion of each {@code Location} is carried forward.
     */
    private HttpResponse<String> followRedirects(TestApiClient client, String path) throws Exception {
        HttpResponse<String> response = client.get(path);
        int hops = 0;
        while (response.statusCode() / 100 == 3 && hops < 5) {
            java.net.URI location = java.net.URI.create(response.headers().firstValue("Location").orElseThrow());
            String next = location.getQuery() == null ? location.getPath() : location.getPath() + "?" + location.getQuery();
            response = client.get(next);
            hops++;
        }
        return response;
    }

    @Test
    void ceoRetainsFullManagementWorkspaceAndAccessClass() throws Exception {
        TestApiClient ceo = ceo();
        HttpResponse<String> home = followRedirects(ceo, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/pipeline");
        String body = home.body();
        // CEO navigation redesign (mirrors the earlier Manager cleanup): Idea Queue is gone -
        // Idea review now lives entirely in Reviews -> Ideas, and "My Ideas" is the CEO's own-
        // submissions history, a distinct concept from Reviews. Order: Content Pipeline, Reviews,
        // Team, Reports, My Ideas, Submit Idea, Administration.
        assertThat(body).contains("Content Pipeline").contains("Reviews").contains("Team").contains("Reports")
                .contains("My Ideas</a>").contains("Submit Idea</a>").contains("Administration");
        assertThat(body).doesNotContain("Idea Queue").doesNotContain("My Work</a>");
    }

    @Test
    void marketingManagerRetainsFullManagementWorkspaceAndAccessClass() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String mmEmail = createUser(ceo, "mm", MARKETING_MANAGER_ROLE_ID, unique);
        TestApiClient mm = new TestApiClient(port);
        mm.login(mmEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(mm, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/pipeline");
        String body = home.body();
        // Manager navigation redesign: Idea Queue is gone (Idea review now lives entirely in
        // Reviews -> Ideas), replaced by "My Ideas" (the Manager's own-submissions history, a
        // distinct concept from Reviews). Order: Content Pipeline, Reviews, Team, Reports,
        // My Ideas, Submit Idea, Administration.
        assertThat(body).contains("Content Pipeline").contains("Reviews").contains("Team").contains("Reports")
                .contains("My Ideas</a>").contains("Submit Idea</a>").contains("Administration");
        assertThat(body).doesNotContain("Idea Queue").doesNotContain("My Work</a>");
    }

    @Test
    void cameraPersonRetainsExistingMyWorkWorkspace() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String camEmail = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        TestApiClient cam = new TestApiClient(port);
        cam.login(camEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(cam, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/my-work");
        String body = home.body();
        assertThat(body).contains("My Work</a>").contains("My Ideas</a>").contains("Submit Idea</a>");
        assertThat(body).doesNotContain("My Shoots</a>");
    }

    @Test
    void videoEditorRetainsExistingMyWorkWorkspace() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String editorEmail = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        TestApiClient editor = new TestApiClient(port);
        editor.login(editorEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(editor, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/my-work");
        assertThat(home.body()).contains("My Work</a>").contains("My Ideas</a>").contains("Submit Idea</a>");
    }

    @Test
    void modelRetainsExistingMyShootsWorkspaceNotMyWork() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String modelEmail = createUser(ceo, "model", MODEL_ROLE_ID, unique);
        TestApiClient model = new TestApiClient(port);
        model.login(modelEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(model, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/my-shoots");
        String body = home.body();
        assertThat(body).contains("My Shoots</a>").contains("My Ideas</a>").contains("Submit Idea</a>");
        assertThat(body).doesNotContain("My Work</a>");
    }

    @Test
    void publisherRetainsExistingMyWorkWorkspace() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String pubEmail = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        TestApiClient pub = new TestApiClient(port);
        pub.login(pubEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(pub, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/my-work");
        assertThat(home.body()).contains("My Work</a>").contains("My Ideas</a>").contains("Submit Idea</a>");
    }

    @Test
    void hrManagerGetsIdeaOnlyWorkspace() throws Exception {
        TestApiClient ceo = ceo();
        setWorkflowParticipation(ceo, HR_MANAGER_ROLE_ID, false);
        long unique = Instant.now().toEpochMilli();
        String hrEmail = createUser(ceo, "hr", HR_MANAGER_ROLE_ID, unique);
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(hr, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/ideas");
        String body = home.body();
        assertThat(body).contains("My Ideas</a>").contains("Submit Idea</a>");
        assertThat(body).doesNotContain("My Work</a>").doesNotContain("My Shoots</a>")
                .doesNotContain("Content Pipeline</a>").doesNotContain("Administration</a>");
    }

    @Test
    void seoInternGetsIdeaOnlyWorkspaceAndDirectMyWorkUrlIsDenied() throws Exception {
        TestApiClient ceo = ceo();
        setWorkflowParticipation(ceo, SEO_INTERN_ROLE_ID, false);
        long unique = Instant.now().toEpochMilli();
        String seoEmail = createUser(ceo, "seo-intern", SEO_INTERN_ROLE_ID, unique);
        TestApiClient seo = new TestApiClient(port);
        seo.login(seoEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(seo, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/ideas");
        assertThat(home.body()).contains("My Ideas</a>").contains("Submit Idea</a>").doesNotContain("My Work</a>");

        // Regression guard for the exact defect the request called out: a direct hit on the
        // workflow/execution URL must be redirected server-side, never rely on hidden nav alone.
        HttpResponse<String> direct = seo.get("/app/my-work");
        assertThat(direct.statusCode()).isEqualTo(302);
        assertThat(direct.headers().firstValue("Location").orElseThrow()).endsWith("/app/ideas");
        HttpResponse<String> landed = followRedirects(seo, "/app/my-work");
        assertThat(landed.uri().getPath()).isEqualTo("/app/ideas");
        assertThat(landed.body()).doesNotContain("Active Shoot Tasks");
    }

    @Test
    void otherNonProductionRolesGetIdeaOnlyWorkspace() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();

        for (var role : new String[][] {
                {"seo-exec", SEO_EXECUTIVE_ROLE_ID},
                {"sales-mgr", SALES_MANAGER_ROLE_ID},
                {"crm-mgr", CRM_MANAGER_ROLE_ID}}) {
            setWorkflowParticipation(ceo, role[1], false);
            String email = createUser(ceo, role[0], role[1], unique);
            TestApiClient client = new TestApiClient(port);
            client.login(email, "Passw0rd!");

            HttpResponse<String> home = followRedirects(client, "/app/home");
            assertThat(home.uri().getPath()).isEqualTo("/app/ideas");
            String body = home.body();
            assertThat(body).contains("My Ideas</a>").contains("Submit Idea</a>").doesNotContain("My Work</a>");
        }
    }

    @Test
    void newBusinessRoleDefaultsToNonProductionWhenNotExplicitlyMarkedAsParticipant() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String roleName = "Test Non-Production Role " + unique;

        // Real admin create-form flow, participatesInWorkflow deliberately omitted - mirrors an
        // unchecked checkbox on the Create Business Role form.
        HttpResponse<String> create = ceo.postForm("/app/admin/business-roles",
                java.util.Map.of("roleName", roleName, "accessClass", "EMPLOYEE"));
        assertThat(create.statusCode()).isEqualTo(302);

        String rolesPage = ceo.get("/app/admin/business-roles").body();
        String row = extractRow(rolesPage, roleName);
        assertThat(row).contains("status-pill status-inactive\">Non-production</span>");

        String newRoleId = extractBusinessRoleId(rolesPage, roleName);
        String userEmail = createUser(ceo, "new-role", newRoleId, unique);
        TestApiClient user = new TestApiClient(port);
        user.login(userEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(user, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/ideas");
        assertThat(home.body()).contains("My Ideas</a>").contains("Submit Idea</a>").doesNotContain("My Work</a>");

        HttpResponse<String> landed = followRedirects(user, "/app/my-work");
        assertThat(landed.uri().getPath()).isEqualTo("/app/ideas");
    }

    @Test
    void explicitlyConfiguredProductionBusinessRoleStillWorksNormally() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String roleName = "Test Production Role " + unique;

        HttpResponse<String> create = ceo.postForm("/app/admin/business-roles",
                java.util.Map.of("roleName", roleName, "accessClass", "EMPLOYEE", "participatesInWorkflow", "true"));
        assertThat(create.statusCode()).isEqualTo(302);

        String rolesPage = ceo.get("/app/admin/business-roles").body();
        String row = extractRow(rolesPage, roleName);
        assertThat(row).contains("status-pill status-active\">Production</span>")
                .doesNotContain("status-pill status-inactive\">Non-production</span>");

        String newRoleId = extractBusinessRoleId(rolesPage, roleName);
        String userEmail = createUser(ceo, "prod-role", newRoleId, unique);
        TestApiClient user = new TestApiClient(port);
        user.login(userEmail, "Passw0rd!");

        HttpResponse<String> home = followRedirects(user, "/app/home");
        assertThat(home.uri().getPath()).isEqualTo("/app/my-work");
        assertThat(home.body()).contains("My Work</a>").contains("My Ideas</a>").contains("Submit Idea</a>");
    }

    private String extractRow(String rolesPage, String roleName) {
        int rowStart = rolesPage.indexOf(roleName);
        assertThat(rowStart).isPositive();
        int rowEnd = rolesPage.indexOf("</tr>", rowStart);
        return rolesPage.substring(rowStart, rowEnd);
    }

    /** The roles table only renders the id inside each row's form action URLs. */
    private String extractBusinessRoleId(String rolesPage, String roleName) {
        int rowStart = rolesPage.indexOf(roleName);
        int formStart = rolesPage.indexOf("/app/admin/business-roles/", rowStart);
        int idStart = formStart + "/app/admin/business-roles/".length();
        int idEnd = rolesPage.indexOf("/", idStart);
        return rolesPage.substring(idStart, idEnd);
    }
}
