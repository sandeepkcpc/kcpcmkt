package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-059: the "My Ideas" screen (/app/ideas for an EMPLOYEE-class user) must show only the
 * logged-in employee's own submitted ideas, with KPI counts that always match the table, and an
 * employee must never be able to view another employee's idea detail page by guessing/changing
 * the URL - this must be enforced server-side, not just hidden from the table UI.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyIdeasVisibilityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003"; // EMPLOYEE access class

    @Test
    void myIdeasShowsOnlyOwnIdeasWithMatchingKpiCountsAndBlocksDirectUrlAccessToAnotherEmployeesIdea() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String empAEmail = "e2e-myideas-a-" + unique + "@kcpcbandhani.local";
        String empAId = createUser(ceo, "MyIdeas Employee A", empAEmail, HR_MANAGER_ROLE_ID);
        String empBEmail = "e2e-myideas-b-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "MyIdeas Employee B", empBEmail, HR_MANAGER_ROLE_ID);

        TestApiClient empA = new TestApiClient(port);
        empA.login(empAEmail, "Passw0rd!");
        TestApiClient empB = new TestApiClient(port);
        empB.login(empBEmail, "Passw0rd!");

        String ownTitle = "MyIdeas Own Idea " + unique;
        assertThat(empA.postForm("/app/ideas", Map.of("title", ownTitle)).statusCode()).isEqualTo(302);
        String otherTitle = "MyIdeas Other Employee's Idea " + unique;
        assertThat(empB.postForm("/app/ideas", Map.of("title", otherTitle)).statusCode()).isEqualTo(302);

        Idea ownIdea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ownTitle)).findFirst().orElseThrow();
        Idea otherIdea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(otherTitle)).findFirst().orElseThrow();

        // Own My Ideas listing: sees only the own idea, never the other employee's, and the KPI
        // count reads from the exact same underlying list the table renders (both freshly
        // submitted ideas sit at PA -> "Under Review").
        String myIdeas = empA.get("/app/ideas").body();
        assertThat(myIdeas).contains(ownTitle);
        assertThat(myIdeas).doesNotContain(otherTitle);
        assertThat(myIdeas).contains("Total Ideas</span><span class=\"kpi-card-count\">1</span>");
        assertThat(myIdeas).contains("Under Review</span><span class=\"kpi-card-count\">1</span>");
        assertThat(myIdeas).doesNotContain("Category");

        // Own idea's detail page renders normally.
        HttpResponse<String> ownDetail = empA.get("/app/ideas/" + ownIdea.getId());
        assertThat(ownDetail.statusCode()).isEqualTo(200);
        assertThat(ownDetail.body()).contains("Idea Details").contains(ownTitle);

        // Direct URL/ID guess at another employee's idea must NOT leak content and must NOT
        // surface as an uncaught-exception 500 - it is redirected away exactly like a genuinely
        // nonexistent idea would be, with a clean flash error message.
        HttpResponse<String> otherAttempt = empA.get("/app/ideas/" + otherIdea.getId());
        assertThat(otherAttempt.statusCode()).isEqualTo(302);
        assertThat(otherAttempt.headers().firstValue("Location").orElseThrow()).contains("/app/ideas");
        assertThat(otherAttempt.body()).doesNotContain(otherTitle);

        // Following the redirect (a real browser would) lands back on My Ideas with the error
        // surfaced, still without ever having rendered the other employee's idea content.
        String afterRedirect = empA.get("/app/ideas").body();
        assertThat(afterRedirect).doesNotContain(otherTitle);

        // CEO/Manager Idea Queue removal: the CEO no longer has a separate company-wide Idea
        // Queue at /app/ideas (that route is now "My Ideas" - own submissions only - for every
        // AccessClass, same as EMPLOYEE already had). Idea review for the CEO now lives entirely
        // in Reviews -> Ideas, so verify both employees' ideas are visible there instead. Search on
        // the shared unique suffix (present in both freshly-created titles) to find both rows
        // regardless of how many other ideas are pending. Assert on the Idea ID (businessIdeaCode)
        // rather than the raw title text - the table correctly HTML-escapes title output (e.g.
        // otherTitle's apostrophe renders as &#039;), so a literal-apostrophe substring match
        // against escaped markup would fail even though the row is rendering correctly.
        String ceoReviewsIdeas = ceo.get("/app/reviews?tab=ideas&q=" + unique).body();
        assertThat(ceoReviewsIdeas).contains(ownIdea.getBusinessIdeaCode()).contains(otherIdea.getBusinessIdeaCode());

        // CEO's own /app/ideas is now their own-submissions-only "My Ideas" - neither employee's
        // idea (CEO submitted nothing here) shows up there.
        String ceoMyIdeas = ceo.get("/app/ideas?q=" + unique).body();
        assertThat(ceoMyIdeas).doesNotContain(ownIdea.getBusinessIdeaCode()).doesNotContain(otherIdea.getBusinessIdeaCode());
    }

    /**
     * ENG-061: the Idea lifecycle is separate from Planning - once approved, the Idea Status must
     * read "Approved" forever, never a downstream Planning workflow state name, even after the
     * resulting content plan progresses well past Planning itself. A Retained idea reads
     * "Retained" (not the old "Needs Changes"); reopening it is a distinct "Reopened" status, not
     * indistinguishable from a brand-new "Under Review" submission, even though both sit at the
     * same underlying Pending Approval workflow status.
     */
    @Test
    void ideaStatusStaysApprovedThroughPlanningAndReopenedIsDistinguishedFromUnderReview() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String empEmail = "e2e-myideas-lifecycle-" + unique + "@kcpcbandhani.local";
        createUser(ceo, "MyIdeas Lifecycle Employee", empEmail, HR_MANAGER_ROLE_ID);
        TestApiClient employee = new TestApiClient(port);
        employee.login(empEmail, "Passw0rd!");

        // --- Idea A: approved, then the resulting plan progresses well past Planning - the Idea
        // Status must stay "Approved" throughout, never a raw Planning/Shoot status name.
        String titleA = "MyIdeas Lifecycle Approved " + unique;
        assertThat(employee.postForm("/app/ideas", Map.of("title", titleA)).statusCode()).isEqualTo(302);
        Idea ideaA = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(titleA)).findFirst().orElseThrow();
        ceo.postJson("/api/v1/ideas/" + ideaA.getId() + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");

        String myIdeasAfterApprove = employee.get("/app/ideas").body();
        assertThat(myIdeasAfterApprove).contains(titleA);
        int rowStart = myIdeasAfterApprove.indexOf(titleA);
        String rowSnippet = myIdeasAfterApprove.substring(rowStart, Math.min(rowStart + 800, myIdeasAfterApprove.length()));
        assertThat(rowSnippet).contains(">Approved<");
        assertThat(rowSnippet).doesNotContain("In Planning");

        HttpResponse<String> detailAfterApprove = employee.get("/app/ideas/" + ideaA.getId());
        assertThat(detailAfterApprove.statusCode()).isEqualTo(200);
        assertThat(detailAfterApprove.body()).contains("Current Idea Status").contains(">Approved<");
        assertThat(detailAfterApprove.body()).contains("This approved idea has moved to Planning.");
        // Employee view: informational note only, no link into the operational deliverable page.
        assertThat(detailAfterApprove.body()).doesNotContain("Open the deliverable");

        // CEO viewing the SAME idea detail page DOES get the operational link (management, not employee).
        HttpResponse<String> ceoDetailAfterApprove = ceo.get("/app/ideas/" + ideaA.getId());
        assertThat(ceoDetailAfterApprove.body()).contains("Open the deliverable");

        // Progress the resulting plan well past Planning (Shoot Assigned) - the Idea Status must
        // still read "Approved", never "Planning"/"Shoot Assigned"/any Planning workflow state name.
        String planId = contentPlanRepository.findByIdea(ideaA).orElseThrow().getId().toString();
        String camId = createUser(ceo, "MyIdeas Lifecycle Cam " + unique,
                "e2e-myideas-lifecycle-cam-" + unique + "@kcpcbandhani.local", "01926e3e-0001-7000-8000-000000000004");
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"e2e test fixture execution grant\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/shooting-assignments", "{\"cameramanUserId\":\"" + camId + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/schedule/standard",
                "{\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\"}");
        ceo.postJson("/api/v1/content-plans/" + planId + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/myideas-lifecycle-" + unique + "\"}");
        ceo.post("/api/v1/content-plans/" + planId + "/planning-review/submit", "");
        var decision = ceo.postJson("/api/v1/content-plans/" + planId + "/planning-review/decision", "{\"approve\":true}");
        assertThat(decision.get("status").asText()).isEqualTo("SA");

        String detailAfterShootAssigned = employee.get("/app/ideas/" + ideaA.getId()).body();
        assertThat(detailAfterShootAssigned).contains("Current Idea Status").contains(">Approved<");
        assertThat(detailAfterShootAssigned).doesNotContain("Shoot Assigned");
        String myIdeasAfterShootAssigned = employee.get("/app/ideas").body();
        assertThat(myIdeasAfterShootAssigned).doesNotContain("Shoot Assigned").doesNotContain("In Planning");

        // --- Idea B: retained, then reopened - a distinct "Reopened" status, not "Under Review".
        String titleB = "MyIdeas Lifecycle Retained " + unique;
        assertThat(employee.postForm("/app/ideas", Map.of("title", titleB)).statusCode()).isEqualTo(302);
        Idea ideaB = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(titleB)).findFirst().orElseThrow();
        String retainReason = "Good idea. Keep for the right time. " + unique;
        ceo.postJson("/api/v1/ideas/" + ideaB.getId() + "/review",
                "{\"decision\":\"RETAIN\",\"reason\":\"" + retainReason + "\"}");

        String myIdeasAfterRetain = employee.get("/app/ideas").body();
        int retainRowStart = myIdeasAfterRetain.indexOf(titleB);
        String retainRowSnippet = myIdeasAfterRetain.substring(retainRowStart, Math.min(retainRowStart + 800, myIdeasAfterRetain.length()));
        assertThat(retainRowSnippet).contains(">Retained<").contains(retainReason);

        ceo.post("/api/v1/ideas/" + ideaB.getId() + "/reopen", "");

        String myIdeasAfterReopen = employee.get("/app/ideas").body();
        int reopenRowStart = myIdeasAfterReopen.indexOf(titleB);
        String reopenRowSnippet = myIdeasAfterReopen.substring(reopenRowStart, Math.min(reopenRowStart + 800, myIdeasAfterReopen.length()));
        assertThat(reopenRowSnippet).contains(">Reopened<");
        // Still awaiting a fresh decision - the last decided feedback (the earlier Retain reason) stays visible.
        assertThat(reopenRowSnippet).contains(retainReason);

        HttpResponse<String> detailAfterReopen = employee.get("/app/ideas/" + ideaB.getId());
        assertThat(detailAfterReopen.body()).contains("Current Idea Status").contains(">Reopened<");
        assertThat(detailAfterReopen.body()).contains("Idea Decision History");
        // Idea Detail's redesigned Decision History is a real table (Date & Time | Action | By |
        // Remarks columns), not inline "<action> by <actor>" text - the Reopened action and its
        // actor now live in separate cells, so assert both are present in that table's markup
        // rather than requiring the old concatenated phrase.
        int historyStart = detailAfterReopen.body().indexOf("Idea Decision History");
        String historySnippet = detailAfterReopen.body().substring(historyStart,
                Math.min(historyStart + 1200, detailAfterReopen.body().length()));
        assertThat(historySnippet).contains("idea-action-badge-reopened\">Reopened<").contains("KCPC CEO");

        // Reopened is folded into the "Under Review" KPI bucket (still filterable/displayed as its
        // own distinct row status), so Under Review count includes both the Reopened idea and any
        // freshly-submitted-and-not-yet-decided idea.
        assertThat(myIdeasAfterReopen).contains("Under Review</span><span class=\"kpi-card-count\">1</span>");
        assertThat(myIdeasAfterReopen).contains("Approved</span><span class=\"kpi-card-count\">1</span>");
        assertThat(myIdeasAfterReopen).contains("Retained</span><span class=\"kpi-card-count\">0</span>");
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"my ideas test fixture\"}");
        return response.get("userId").asText();
    }
}
