package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "+ Create Idea" button/popup on the Reviews -> Idea Review & Planning screen
 * (fragments/reviews-content.jspf's tab header row) - reuses the exact same Submit Idea form
 * fragment (fragments/idea-submit-form.jspf), fields, validations and backend endpoint/logic
 * (IdeaMvcController#submit / IdeaService#submit) as the standalone /app/ideas/new screen, just
 * submitted over AJAX (idea-submit.js's data-ajax branch) so the Reviews screen never navigates
 * away. Same permission reach as "Submit Idea" itself: IdeaService#submit has no authority check
 * beyond being logged in (see fragments/nav.jsp - the "Submit Idea" link is unconditional for
 * every Access Class), so the button/modal render for every viewer who can reach Reviews at all -
 * there is no separate permission gate to test beyond the existing Reviews-tab reachability rule
 * and plain authentication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaCreateModalTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;

    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003"; // EMPLOYEE access class

    @Test
    void createIdeaButtonAndModalAreVisibleOnTheReviewsIdeasTabForCeo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String body = ceo.get("/app/reviews?tab=ideas").body();

        assertThat(body).contains("id=\"ideaCreateModalOpen\"").contains("+ Create Idea");
        // Clicking the button opens this same popup markup - reuses the shared form fragment
        // verbatim (idea-submit-form.jspf), never a second/parallel submission form.
        assertThat(body).contains("id=\"ideaCreateModalOverlay\"")
                .contains("id=\"idea-submit-form\"")
                .contains("data-ajax=\"true\"");
    }

    @Test
    void blankTitleIsRejectedInsideTheModalsAjaxSubmission() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        Map<String, String> params = new HashMap<>();
        params.put("title", "");
        params.put("referenceLink", "");
        params.put("notesRemarks", "");
        HttpResponse<String> response = ceo.postFormAjax("/app/ideas", params);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Idea Title is mandatory");
    }

    @Test
    void invalidReferenceLinkIsRejectedInsideTheModalsAjaxSubmission() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        HttpResponse<String> response = ceo.postFormAjax("/app/ideas", Map.of(
                "title", "Modal Bad URL " + unique,
                "referenceLink", "not-a-valid-url"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Reference Link must be a valid URL");

        assertThat(ideaRepository.findAll().stream().anyMatch(i -> i.getTitle().equals("Modal Bad URL " + unique))).isFalse();
    }

    @Test
    void successfulAjaxSubmissionCreatesTheIdeaAndItAppearsInPendingReviews() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        int pendingBefore = ideasPendingCount(ceo);

        HttpResponse<String> response = ceo.postFormAjax("/app/ideas", Map.of(
                "title", "Modal Created Idea " + unique,
                "referenceLink", "https://example.com/modal-" + unique,
                "notesRemarks", "Created from the Reviews screen popup"));

        // {"status":"ok"} with a 200 is the exact contract idea-submit.js checks (response.ok) to
        // clear the form, dispatch kcpc:idea-created, and close the popup - see idea-create-modal.js
        // (closes on the event) and reviews-workspace.js (refreshes the list on the same event).
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"ok\"");

        Idea created = ideaRepository.findAll().stream()
                .filter(i -> i.getTitle().equals("Modal Created Idea " + unique))
                .findFirst().orElseThrow();
        assertThat(created.getReferenceLink()).isEqualTo("https://example.com/modal-" + unique);
        assertThat(created.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(WorkflowStatus.PA);

        // The Ideas tab count badge (ideasPendingCount, an un-paginated total) goes up by exactly
        // one - a plain row-count would be unreliable against however many other Pending ideas the
        // rest of the suite has already accumulated in this shared test database.
        assertThat(ideasPendingCount(ceo)).isEqualTo(pendingBefore + 1);

        // Newly submitted Idea appears in Pending Reviews per the existing workflow - searched by
        // its own unique title so the assertion holds regardless of how many other ideas exist.
        String searchBody = ceo.get("/app/reviews?tab=ideas&q=" + java.net.URLEncoder.encode("Modal Created Idea " + unique,
                java.nio.charset.StandardCharsets.UTF_8)).body();
        assertThat(searchBody).contains(created.getBusinessIdeaCode()).contains("Modal Created Idea " + unique);
    }

    @Test
    void unauthenticatedRequestCannotSubmitAnIdeaThroughThisEndpointEither() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient anonymous = new TestApiClient(port);
        anonymous.primeCsrf(); // CSRF token issuance itself is permitAll, no login needed

        HttpResponse<String> response = anonymous.postForm("/app/ideas", Map.of(
                "title", "Should Never Exist " + unique));

        // anyRequest().authenticated() - same rule every other /app/** MVC route enforces, no
        // special exemption for this endpoint just because the popup makes it feel "public".
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/login");

        assertThat(ideaRepository.findAll().stream().anyMatch(i -> i.getTitle().equals("Should Never Exist " + unique))).isFalse();
    }

    @Test
    void employeeWithNoReviewPermissionNeverReachesTheCreateIdeaButtonAtAll() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String hrEmail = "create-idea-hr-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "Create Idea HR", hrEmail, HR_MANAGER_ROLE_ID);
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        // No PERM_01/03/05/07 grant at all - this EMPLOYEE cannot reach Reviews in any tab at all.
        // WorkflowParticipationInterceptor#preHandle redirects them to My Ideas before the request
        // even reaches ReviewsMvcController, so the Create Idea button, which lives inside that
        // screen's shared header, is simply never rendered for them - there is no separate path in.
        HttpResponse<String> response = hr.get("/app/reviews");
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse("")).contains("/app/ideas");
        assertThat(response.body()).doesNotContain("id=\"ideaCreateModalOpen\"");
    }

    /** reviews-content.jspf renders the Ideas tab as
     * {@code Ideas <span class="count-badge">${ideasPendingCount}</span>} - an un-paginated total
     * (ReviewsMvcController#reviews: {@code pendingIdeas.size()}), so parsing it out is a reliable
     * before/after signal regardless of how many Pending ideas already exist in this shared test
     * database from the rest of the suite. */
    private int ideasPendingCount(TestApiClient ceo) throws Exception {
        String body = ceo.get("/app/reviews?tab=ideas").body();
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("Ideas <span class=\"count-badge\">(\\d+)</span>").matcher(body);
        assertThat(matcher.find()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"create idea modal test fixture\"}");
        return response.get("userId").asText();
    }
}
