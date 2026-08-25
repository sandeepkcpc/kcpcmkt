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
 * bottom of the Planning tab and references that form via the HTML5 {@code form="planning-details-form"}
 * button attribute (`POST /app/deliverables/{id}/plan-submit`). Planned Outputs / Publication Scope
 * remain their own independent steps in between, each with its own action, unaffected by this.
 * <p>
 * Permission-driven workflow (superseding ENG-045): Shoot Assignment is no longer part of this
 * form or this request at all - it is managed exclusively, and takes effect immediately, from the
 * Shoot tab's own dedicated endpoints ({@code /shooting-assignments}, {@code /shooting-assignments/
 * team}, {@code /shooting-assignments/remove}), so a PERM_04-only delegated employee (who may never
 * hold Planning Execution/PERM_02) has a working entry point too. {@code plan-submit} only saves
 * Planning Details and submits for review; {@link com.kcpc.mkt.planning.service.PlanningService
 * #submitPlanningReview} rejects the submission if the already-persisted Shoot setup is incomplete
 * (at least one active Cameraperson), independently of whatever the Planning form itself carried.
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

        // Shoot Assignment happens first, from its own dedicated Shoot-tab endpoint - no longer
        // bundled into the plan-submit request.
        String cam = createCameraperson(ceo);
        assertThat(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", cam)).statusCode())
                .isEqualTo(302);
        ceo.get(base); // drain that step's own flash message before the assertion below

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
     * A submit-readiness failure (missing Content Priority/Drive Link) still rolls back the
     * Planning Details save too, not just the submit step - unrelated to, and unchanged by, the
     * Shoot Assignment split.
     */
    @Test
    void combinedSubmitRollsBackEverythingWhenSubmissionIsRejected() throws Exception {
        ContentPlan plan = approvedPlan("Save Without Submit");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        // No contentPriority and no folderLink - isReadyForPlanningReview() rejects the submit
        // step, and the whole transaction (including the save) rolls back with it.
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

    /**
     * The new Shoot-setup readiness gate: Planning parameters can be fully complete, but submission
     * is still rejected while zero Camerapersons are actively assigned - the plan is left in
     * Planning (unsubmitted) with a clear message, and the Planning tab renders a "Go to Shoot
     * Setup" link back to the Shoot tab (the single canonical UI for Shoot Assignment now).
     */
    @Test
    void combinedSubmitRejectedWhenNoShootAssignmentExistsYet() throws Exception {
        ContentPlan plan = approvedPlan("No Shoot Setup Yet");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        // Planning parameters are fully ready, but no Cameraperson has ever been assigned.
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "categoryText", "Sarees", "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/no-shoot",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PL"); // never submitted
        assertThat(shootingAssignmentRepository.findByContentPlanAndActiveTrue(reloaded)).isEmpty();

        HttpResponse<String> page = ceo.get(base);
        assertThat(page.body())
                .contains("Nothing was saved:")
                .contains("At least one Cameraperson must be assigned in the Shoot tab before Planning Review can be submitted")
                .contains("content-detail-goto-shoot-tab")
                .contains("Go to Shoot Setup");
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
        // order (Planning Details, then Planned Outputs, then the single final submit, then the
        // Shoot tab's own assignment picker further down the page) via each section's own stable
        // markup instead. Shoot Assignment now renders in the Shoot tab panel, which is DOM-after
        // the Planning tab panel (and its Submit button), not before it as when it lived inline.
        assertThat(body).containsSubsequence("id=\"planning-details-form\"", "id=\"planned-outputs-table\"",
                "form=\"planning-details-form\">Submit for Planning Review</button>", "kcpc-assignment-picker");
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
        // Shoot Assignment happens from its own dedicated Shoot-tab endpoint, independently of the
        // Planning Details submission below.
        assertThat(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", camerapersonId))
                .statusCode()).isEqualTo(302);

        String liveDate = LocalDate.now().plusDays(10).toString();
        assertThat(ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/native-self-review",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate))
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
     * Regression coverage for the permission-driven Shoot Assignment split: {@code plan-submit} no
     * longer accepts (or acts on) Cameraperson/Shoot Lead fields at all, so stale/forged
     * Planning-form request data can never recreate, remove, or overwrite the Shoot Assignment
     * already saved independently through the Shoot tab's own endpoints. This directly replaces the
     * old {@code combinedSubmitReconcilesCamerapersonsToExactlyWhatsChecked} test, which asserted
     * the OPPOSITE (that plan-submit's own cameramanUserIds reconciled/overwrote the active team) -
     * that behavior has been deliberately removed.
     */
    @Test
    void staleCameramanUserIdsInPlanSubmitRequestCannotOverwriteAssignmentsSavedThroughShootTab() throws Exception {
        ContentPlan plan = approvedPlan("Stale Plan Submit Data");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String realCam = createCameraperson(ceo);
        assertThat(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", realCam)).statusCode())
                .isEqualTo(302);
        String staleCam = createCameraperson(ceo); // never actually assigned - only referenced below

        String liveDate = LocalDate.now().plusDays(10).toString();
        // A stale/forged client still sends cameramanUserIds - plan-submit must silently ignore it
        // (the controller no longer even binds this parameter) rather than reconciling/overwriting.
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/stale-data",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate, "cameramanUserIds", staleCam));
        assertThat(response.statusCode()).isEqualTo(302);

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PLRV");

        var active = shootingAssignmentRepository.findByContentPlanAndActiveTrue(reloaded);
        assertThat(active).as("The real Shoot-tab assignment must survive untouched - the stale form field is a no-op")
                .hasSize(1);
        assertThat(active.get(0).getCameraperson().getId().toString()).isEqualTo(realCam);
    }

    /**
     * Shoot Instructions (the shared per-stage Description) is now set exclusively through its own
     * dedicated endpoint ({@code /shooting/description}, available from the Shoot tab), independent
     * of Planning submission - plan-submit no longer accepts or touches this field at all.
     */
    @Test
    void shootInstructionsAreSetIndependentlyOfPlanSubmit() throws Exception {
        ContentPlan plan = approvedPlan("Shoot Instructions");
        String base = "/app/deliverables/" + plan.getId();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        String cam = createCameraperson(ceo);
        assertThat(ceo.postForm(base + "/shooting-assignments", Map.of("cameramanUserId", cam)).statusCode())
                .isEqualTo(302);

        assertThat(ceo.postForm(base + "/shooting/description",
                Map.of("description", "Front, back aur close-up shots lena. Gota work clearly visible ho."))
                .statusCode()).isEqualTo(302);

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postForm(base + "/plan-submit", Map.of(
                "contentPriority", "MEDIUM", "folderLink", "https://drive.example.com/instructions",
                "planningMode", "STANDARD", "plannedLiveDate", liveDate));
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
        String userId = response.get("userId").asText();
        // Candidate eligibility/execution is now permission-driven (OperationalEligibilityService).
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"staged planning flow test fixture grant\"}");
        return userId;
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
