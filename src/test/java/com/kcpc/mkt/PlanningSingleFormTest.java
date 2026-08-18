package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.production.repository.ShootingAssignmentRepository;
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
 * (`POST /app/deliverables/{id}/plan-submit`). Planned Outputs / Publication Scope remain their
 * own independent steps in between, each with its own action, unaffected by this. Cameraperson(s)/
 * Shoot Lead (ENG-045) are different: their checkboxes/select live outside the form's DOM but
 * carry {@code form="planning-details-form"} so they submit together with it too - no separate
 * "Assign Cameraperson(s)" button anymore. One click now saves Planning Details, reconciles Shoot
 * Assignment to exactly what's checked (adds newly-checked, removes newly-unchecked), sets the
 * Shoot Lead, and submits for review, ALL in one transaction - if any step fails, nothing is
 * saved at all, not even the Planning Details fields (a deliberate reversal of the previous
 * two-transaction design, where a submit-readiness failure still left the save in place).
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
    @Autowired
    ShootingAssignmentRepository shootingAssignmentRepository;

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

    /**
     * ENG-045 reversed the previous two-transaction design: a submit-readiness failure now rolls
     * back the save too, not just the submit - "sab ek hi transaction me hona chahiye" was the
     * user's explicit ask, precisely so a failed submit never leaves a half-applied state behind.
     */
    @Test
    void combinedSubmitRollsBackEverythingWhenSubmissionIsRejected() throws Exception {
        ContentPlan plan = approvedPlan("Save Without Submit");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        // No contentPriority and no folderLink - isReadyForPlanningReview() rejects the submit
        // step, and now the whole transaction (including the save) rolls back with it.
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "categoryText", "Sarees", "planningMode", "STANDARD", "plannedLiveDate", liveDate));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getCategoryText()).isNull(); // save was rolled back too, not just the submit
        assertThat(reloaded.getPlannedLiveDate()).isNull();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PL"); // never submitted

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body()).contains("Nothing was saved:");
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
        // Section headings were removed from the UI on user request - assert the same underlying
        // order (Planning Details, then Planned Outputs, then Shoot Assignment, then the single
        // final submit) via each section's own stable markup instead.
        assertThat(body).containsSubsequence("id=\"planning-details-form\"", "id=\"planned-outputs-table\"",
                "planning-shoot-picker", "form=\"planning-details-form\">Submit for Planning Review</button>");
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

        // ENG-045: Cameraperson(s) submit together with the rest of Planning Details in this same
        // POST now - a separate prior /shooting-assignments call would just get reconciled away
        // (removed) by this one, since it only reflects the currently-checked set, not a delta.
        String liveDate = LocalDate.now().plusDays(10).toString();
        assertThat(ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/native-self-review",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate, "cameramanUserIds", camerapersonId))
                .statusCode()).isEqualTo(302);

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body())
                .doesNotContain("the whole decision block (Approve and Request Rework) is disabled")
                .contains(">Planning Review</h2>")
                .contains("action=\"/app/deliverables/" + plan.getId() + "/planning-review/decision\"");

        HttpResponse<String> decision = ceo.postForm(base + "/planning-review/decision", Map.of("approve", "true"));
        assertThat(decision.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SA");
    }

    /**
     * ENG-045's core new capability: unchecking a previously-assigned Cameraperson and resubmitting
     * removes them, not just adds whoever's newly checked - "Submit for Planning Review" reconciles
     * the active Shoot Assignment set to exactly what's checked, since there's no separate remove
     * action anymore (the standalone {@code /shooting-assignments} endpoint used here to seed the
     * stale assignment still works independently, unaffected - only the combined submit's own
     * reconcile behavior is what's under test).
     */
    @Test
    void combinedSubmitReconcilesCamerapersonsToExactlyWhatsChecked() throws Exception {
        ContentPlan plan = approvedPlan("Reconcile Shoot Team");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String staleCam = createCameraperson(ceo);
        assertThat(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", staleCam)).statusCode())
                .isEqualTo(302);
        String freshCam = createCameraperson(ceo);

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/reconcile",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate, "cameramanUserIds", freshCam));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PLRV");

        var active = shootingAssignmentRepository.findByContentPlanAndActiveTrue(reloaded);
        assertThat(active).hasSize(1);
        assertThat(active.get(0).getCameraperson().getId().toString()).isEqualTo(freshCam);
    }

    /**
     * ENG-047: Shoot Instructions (the shared per-stage Description, ENG-046) now enters alongside
     * Cameraperson(s)/Shoot Lead in this same combined submit - "Assignment save hote hi description
     * bhi us stage assignment ke saath save ho."
     */
    @Test
    void combinedSubmitAlsoSavesShootInstructions() throws Exception {
        ContentPlan plan = approvedPlan("Shoot Instructions");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createCameraperson(ceo);

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/instructions",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate, "cameramanUserIds", cam,
                "shootDescription", "Front, back aur close-up shots lena. Gota work clearly visible ho."));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PLRV");
        assertThat(reloaded.getShootDescription()).isEqualTo("Front, back aur close-up shots lena. Gota work clearly visible ho.");

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body()).contains("Shoot Instructions");
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
