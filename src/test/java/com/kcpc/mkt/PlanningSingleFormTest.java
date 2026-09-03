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
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workflow redesign: the old standalone Planning Workspace ({@code /app/deliverables/{id}/plan},
 * {@code /plan-submit}, and the resulting Planning Review gate at {@code /planning-review/decision})
 * is gone entirely - Planning is no longer a separate stage, and none of those endpoints exist any
 * more. This file used to cover that two-step "save Planning Details, then separately submit for
 * Planning Review" UX; it is retired in favor of coverage for the single merged Idea Review
 * approval form ({@code idea-detail.jsp}'s Review Decision card, POSTing to
 * {@code /app/ideas/{ideaId}/review}) that replaced it: one form, one submit button, and atomic
 * all-or-nothing validation (a missing required Planning field rejects the WHOLE approval - no
 * Content Plan is left half-created).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlanningSingleFormTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    /**
     * The Review Decision form is one {@code <form id="idea-review-form">} with no separate
     * "Save Plan" button anywhere - the Planning Details fields live inside the SAME form as the
     * Approve/Reject/Retain decision itself, and the one submit button ("Submit Decision") is what
     * both saves the planning details and decides the idea, in a single atomic request.
     */
    @Test
    void reviewDecisionFormHasNoSeparateSaveButtonAndOnePlanningFieldsBlockInsideIt() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String title = "Single Form Structure " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        HttpResponse<String> page = ceo.get("/app/ideas/" + idea.getId());
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();
        assertThat(body).contains("id=\"idea-review-form\"");
        assertThat(body).doesNotContain(">Save Plan<");
        assertThat(body).contains("id=\"idea-review-submit\"");
        // The Planning Details block sits inside the same form, before the single submit button.
        assertThat(body).containsSubsequence("id=\"idea-review-form\"", "id=\"idea-review-planning-fields\"",
                "id=\"idea-review-submit\"");
    }

    /**
     * Atomic all-or-nothing validation: approving without a Drive Folder Link (mandatory unless
     * Drive auto-provisioning is enabled, which it is not in this test environment) rejects the
     * WHOLE approval - the idea stays Pending Approval and no Content Plan is created at all, not
     * a half-saved one.
     */
    @Test
    void approvalAtomicallyRollsBackWhenRequiredFolderLinkIsMissing() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String camId = createCameraperson(ceo, unique);
        String title = "Atomic Rollback No FolderLink " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", java.util.List.of("APPROVE"),
                "cameramanMark", java.util.List.of("1.0"),
                "editorMark", java.util.List.of("1.0"),
                "modelMark", java.util.List.of("1.0"),
                "contentPriority", java.util.List.of("MEDIUM"),
                "plannedLiveDate", java.util.List.of(liveDate),
                "camerapersonUserIds", java.util.List.of(camId)));
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).as("No Content Plan left half-created").isEmpty();

        HttpResponse<String> page = ceo.get("/app/ideas/" + idea.getId());
        assertThat(page.body()).contains("Drive Folder Link is mandatory");
    }

    /**
     * Same atomicity guarantee for the other newly-mandatory field: approving with zero
     * Camerapersons selected rejects the whole approval, never a Content Plan with an empty Shoot
     * Team waiting to be filled in later.
     */
    @Test
    void approvalAtomicallyRollsBackWhenNoCameraPersonIsSelected() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String title = "Atomic Rollback No Cam " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", java.util.List.of("APPROVE"),
                "cameramanMark", java.util.List.of("1.0"),
                "editorMark", java.util.List.of("1.0"),
                "modelMark", java.util.List.of("1.0"),
                "contentPriority", java.util.List.of("MEDIUM"),
                "plannedLiveDate", java.util.List.of(liveDate),
                "folderLink", java.util.List.of("https://drive.example.com/no-cam-" + unique)));
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).isEmpty();

        HttpResponse<String> page = ceo.get("/app/ideas/" + idea.getId());
        assertThat(page.body()).contains("At least one Cameraperson must be assigned before approval");
    }

    /** A fully-populated merged approval succeeds in one request - the counterpart happy path to
     *  the two rollback cases above, proving the atomic form actually works end to end. */
    @Test
    void fullyPopulatedApprovalSucceedsInOneRequestAndLandsOnShootAssigned() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        long unique = Instant.now().toEpochMilli();
        String camId = createCameraperson(ceo, unique);
        String pubId = createPublisher(ceo, unique);
        String title = "Single Form Happy Path " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", java.util.List.of("APPROVE"),
                "cameramanMark", java.util.List.of("1.0"),
                "editorMark", java.util.List.of("1.0"),
                "modelMark", java.util.List.of("1.0"),
                "contentPriority", java.util.List.of("MEDIUM"),
                "plannedLiveDate", java.util.List.of(liveDate),
                "folderLink", java.util.List.of("https://drive.example.com/happy-" + unique),
                "camerapersonUserIds", java.util.List.of(camId),
                "publisherUserIds", java.util.List.of(pubId)));
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).isPresent();

        HttpResponse<String> page = ceo.get("/app/ideas/" + idea.getId());
        assertThat(page.body()).contains("Review decision recorded.");
    }

    private String createCameraperson(TestApiClient ceo, long unique) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Single Form Camera\",\"email\":\"single-form-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\","
                        + "\"creationReason\":\"single form test fixture\"}");
        String userId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"single form test fixture grant\"}");
        return userId;
    }

    private String createPublisher(TestApiClient ceo, long unique) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Single Form Publisher\",\"email\":\"single-form-pub-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\","
                        + "\"creationReason\":\"single form test fixture\"}");
        String userId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"single form test fixture grant\"}");
        return userId;
    }
}
