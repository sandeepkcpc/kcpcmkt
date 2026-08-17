package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
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
 * Planning Workspace UI: there is no standalone "Save Plan" button. Planning Details
 * (Category/Priority/SKU/Talent/Drive-Link/Schedule) is one {@code <form id="planning-details-form">}
 * with no submit button of its own; the single "Submit for Planning Review" button sits at the
 * very bottom of the page (after Planned Outputs, Publication Scope and Shoot Assignment) and
 * references that form via the HTML5 {@code form="planning-details-form"} button attribute
 * (`POST /app/deliverables/{id}/plan-submit`) - one click saves Planning Details and submits for
 * review together. Planned Outputs / Publication Scope / Shoot Assignment remain their own
 * independent steps in between, each with its own action, unaffected by this.
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
    @Autowired
    PlannedOutputRepository plannedOutputRepository;

    @Test
    void planSaveOnlyEndpointStillSavesParametersAndStandardScheduleTogether() throws Exception {
        ContentPlan plan = approvedPlan("Single Form Standard");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postForm(base + "/plan", Map.of(
                "categoryText", "Reels", "skuReference", "SKU-STD", "folderLink", "https://drive.example.com/std",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getCategoryText()).isEqualTo("Reels");
        assertThat(reloaded.getSkuReference()).isEqualTo("SKU-STD");
        assertThat(reloaded.getFolderLink()).isEqualTo("https://drive.example.com/std");
        assertThat(reloaded.getPlannedLiveDate()).isEqualTo(LocalDate.parse(liveDate));
        assertThat(reloaded.getPlannedShootDate()).isEqualTo(LocalDate.parse(liveDate).minusDays(5));
        assertThat(reloaded.getPlannedEditDate()).isEqualTo(LocalDate.parse(liveDate).minusDays(2));
        assertThat(reloaded.getPlanningMode().name()).isEqualTo("STANDARD");
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PL"); // save-only, no submit
    }

    @Test
    void combinedSubmitButtonSavesThePlanAndAdvancesToPlanningReviewInOneClick() throws Exception {
        ContentPlan plan = approvedPlan("Save And Submit");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "categoryText", "Reels", "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/sas",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getCategoryText()).isEqualTo("Reels");
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PLRV");

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body()).contains("Plan saved and submitted for Planning Review.");
    }

    @Test
    void combinedSubmitStillSavesEnteredFieldsWhenSubmissionIsRejected() throws Exception {
        ContentPlan plan = approvedPlan("Save Without Submit");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        // No contentPriority and no folderLink - isReadyForPlanningReview() will reject the
        // submit step, but the save step (categoryText, plannedLiveDate, ...) must still persist.
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "categoryText", "Sarees", "planningMode", "STANDARD", "plannedLiveDate", liveDate));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getCategoryText()).isEqualTo("Sarees"); // save was not rolled back
        assertThat(reloaded.getPlannedLiveDate()).isEqualTo(LocalDate.parse(liveDate));
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PL"); // never submitted

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body()).contains("Plan saved, but not submitted:");
    }

    @Test
    void planningWorkspaceShowsNoSaveButtonAndOneFinalSubmitReferencingTheTopForm() throws Exception {
        ContentPlan plan = approvedPlan("No Save Button Flow");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        // Steps 2-4 (Planned Outputs, Publication Scope, Shoot Assignment) each stay independent.
        assertThat(ceo.postForm(base + "/outputs", Map.of("outputType", "PHOTOGRAPHY")).statusCode()).isEqualTo(302);
        var output = plannedOutputRepository.findByContentPlan(plan).stream().findFirst().orElseThrow();
        assertThat(ceo.postForm(base + "/outputs/" + output.getReelGroupId() + "/targets",
                Map.of("publicationTargetIds", "01926e3e-000a-7000-8000-000000000001")).statusCode()).isEqualTo(302);
        var camerapersonId = createCameraperson(ceo);
        assertThat(ceo.postForm(base + "/shooting-assignments",
                Map.of("cameramanUserId", camerapersonId)).statusCode()).isEqualTo(302);

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();
        assertThat(body).contains("id=\"planning-details-form\"");
        assertThat(body).doesNotContain(">Save Plan<");
        assertThat(body).contains("form=\"planning-details-form\">Submit for Planning Review</button>");
        assertThat(body).containsSubsequence("1. Planning Details", "2. Planned Outputs",
                "3. Shoot Assignment", "4. Submit for Review",
                "form=\"planning-details-form\">Submit for Planning Review</button>");
    }

    /**
     * The self-review barrier (Permission #3) only applies to authority exercised via an explicit
     * PermissionGrant - CEO/Marketing Manager's native, ungranted authority is exempt (mirrored in
     * PlanningService.decidePlanningReview's {@code actingGrant.isPresent()} check), so the CEO
     * preparing and later deciding their own Planning Review is allowed, both in what the page
     * shows and in what the decision endpoint accepts.
     */
    @Test
    void nativeAuthorityPreparerCanDecideTheirOwnPlanningReview() throws Exception {
        ContentPlan plan = approvedPlan("Native Self Review");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        var camerapersonId = createCameraperson(ceo);
        assertThat(ceo.postForm(base + "/shooting-assignments",
                Map.of("cameramanUserId", camerapersonId)).statusCode()).isEqualTo(302);

        String liveDate = LocalDate.now().plusDays(10).toString();
        assertThat(ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/native-self-review",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate)).statusCode()).isEqualTo(302);

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body())
                .doesNotContain("the whole decision block (Approve and Request Rework) is disabled")
                .contains("Planning Review Decision");

        HttpResponse<String> decision = ceo.postForm(base + "/planning-review/decision", Map.of("approve", "true"));
        assertThat(decision.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SA");
    }

    private String createCameraperson(TestApiClient ceo) throws Exception {
        long unique = Instant.now().toEpochMilli();
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Staged Flow Camera\",\"email\":\"staged-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000004\","
                        + "\"creationReason\":\"staged planning flow test fixture\"}");
        return response.get("userId").asText();
    }

    private ContentPlan approvedPlan(String title) throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String ideaTitle = title + " " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", ideaTitle)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(ideaTitle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/ideas/" + idea.getId() + "/review",
                Map.of("decision", "APPROVE", "cameramanMark", "1.0", "editorMark", "1.0")).statusCode()).isEqualTo(302);
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }
}
