package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.performance.repository.PerformanceObligationRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Detail Action Center: actions must be derived from Actual Current Workflow Stage +
 * Current Status + User Permission + Existing Backend Action Eligibility - never from permission
 * alone (permission-admin-ui / Action Center stage-aware redesign, spec &sect;1/&sect;20).
 *
 * <p>Drives real ContentPlans through the same golden-path API sequence as
 * {@link GoldenEndToEndFlowTest}, stopping at whichever checkpoint each scenario needs, and
 * asserts on the rendered Content Detail HTML (Action Center button presence/absence via
 * {@link #hasActionButton}, the Reassign form's filtered Task Stage options via
 * {@link #reassignTaskStageSelectHtml}, and the "Current Stage" label) plus one direct
 * backend-rejection assertion proving {@code AdminActionService#reassign} enforces the exact same
 * rule as the UI (never a UI-only check that could diverge from what the backend accepts).
 *
 * <p>Note: a plain {@code data-action-key="X"} substring check is NOT safe on this page - every
 * possible action also has an always-present (but {@code hidden}) form later in the DOM carrying
 * the same {@code data-action-key} attribute, and Reschedule's own "Stage Context" dropdown
 * independently offers {@code SHOOTING}/{@code EDITING} option values regardless of Reassign
 * eligibility. {@link #hasActionButton} and {@link #reassignTaskStageSelectHtml} exist specifically
 * to avoid those two false-positive traps.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ActionCenterEligibilityTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PerformanceObligationRepository obligationRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003";
    private static final String PUBLICATION_TARGET_ID = "01926e3e-000a-7000-8000-000000000001";

    // ================================================================== HTML assertion helpers

    /** The real Action Center button - NOT the always-present hidden form sharing the same
     * data-action-key. Every governed action here has requiresReason=true. */
    private boolean hasActionButton(String page, String actionKey, String label) {
        return page.contains("data-action-key=\"" + actionKey + "\" data-requires-reason=\"true\">" + label + "<");
    }

    /** Just the Reassign form's own Task Stage &lt;select&gt;...&lt;/select&gt; - scoped so its
     * SHOOTING/EDITING option values can never be confused with Reschedule's unrelated Stage
     * Context dropdown, which independently offers the same two enum values unconditionally. */
    private String reassignTaskStageSelectHtml(String page) {
        int marker = page.indexOf("id=\"reassign-task-stage\"");
        assertThat(marker).as("Reassign form's Task Stage select must be present in the DOM").isNotNegative();
        int selectStart = page.lastIndexOf("<select", marker);
        int selectEnd = page.indexOf("</select>", marker);
        return page.substring(selectStart, selectEnd);
    }

    // ================================================================== fixture plumbing

    private static final class Fixture {
        TestApiClient ceo;
        TestApiClient cam;
        TestApiClient ed;
        TestApiClient pub;
        String camId;
        String edId;
        String pubId;
        String contentPlanId;
        String outputId;
    }

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"action-center test fixture\"}");
        return response.get("userId").asText();
    }

    private void grant(TestApiClient ceo, String userId, String permission) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"action-center test grant\"}");
    }

    private String findContentPlanId(String ideaIdText) {
        Idea idea = ideaRepository.findById(UUID.fromString(ideaIdText)).orElseThrow();
        return contentPlanRepository.findByIdea(idea).orElseThrow().getId().toString();
    }

    private String findPlannedOutputId(String contentPlanIdText) {
        ContentPlan plan = contentPlanRepository.findById(UUID.fromString(contentPlanIdText)).orElseThrow();
        return plannedOutputRepository.findByContentPlan(plan).stream()
                .findFirst().map(PlannedOutput::getId).map(UUID::toString).orElseThrow();
    }

    private String findObligationId(String contentPlanIdText) {
        return obligationRepository.findByEvent_ContentPlan_Id(UUID.fromString(contentPlanIdText)).stream()
                .findFirst().map(o -> o.getId().toString()).orElseThrow();
    }

    /** PP -&gt; COMP (scorecard drafted and submitted). */
    private void completePerformance(Fixture f) throws Exception {
        String obligationId = findObligationId(f.contentPlanId);
        f.ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/draft",
                "{\"views3sec\":800,\"plays\":1000,\"averageWatchTimeSeconds\":12.5,\"videoLengthSeconds\":20.0,"
                        + "\"linkClicks\":0,\"clicksIsNa\":true,\"impressions\":5000}");
        f.ceo.postJson("/api/v1/performance-obligations/" + obligationId + "/scorecard/submit", "");
    }

    /** Workflow redesign: Idea Review approval now carries every former Planning field (including
     * the initial Shoot Team and initial Output/Publication Scope) in one call and transitions
     * straight to Shoot Assigned (SA) - there is no more separate Planning/Planning Review resting
     * state to stop at. */
    private Fixture setupPlanning(long unique) throws Exception {
        Fixture f = new Fixture();
        f.ceo = ceo();
        String camEmail = "ace-cam-" + unique + "@kcpcbandhani.local";
        String edEmail = "ace-ed-" + unique + "@kcpcbandhani.local";
        String pubEmail = "ace-pub-" + unique + "@kcpcbandhani.local";
        f.camId = createUser(f.ceo, "ACE Cam " + unique, camEmail, CAMERA_PERSON_ROLE_ID);
        f.edId = createUser(f.ceo, "ACE Ed " + unique, edEmail, VIDEO_EDITOR_ROLE_ID);
        f.pubId = createUser(f.ceo, "ACE Pub " + unique, pubEmail, PUBLISHER_ROLE_ID);
        grant(f.ceo, f.camId, "PERM_18_SHOOT_EXECUTION");
        grant(f.ceo, f.edId, "PERM_19_EDIT_EXECUTION");
        grant(f.ceo, f.pubId, "PERM_08_PUBLISHING_EXECUTION");
        f.cam = new TestApiClient(port);
        f.cam.login(camEmail, "Passw0rd!");
        f.ed = new TestApiClient(port);
        f.ed.login(edEmail, "Passw0rd!");
        f.pub = new TestApiClient(port);
        f.pub.login(pubEmail, "Passw0rd!");

        String liveDate = LocalDate.now().plusDays(10).toString();
        JsonNode idea = f.ceo.postJson("/api/v1/ideas", "{\"title\":\"Action Center Fixture " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        JsonNode approved = f.ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/ace-" + unique + "\","
                        + "\"outputs\":[{\"outputType\":\"POST\","
                        + "\"publicationTargetIds\":[\"" + PUBLICATION_TARGET_ID + "\"]}],"
                        + "\"camerapersonUserIds\":[\"" + f.camId + "\"],"
                        + "\"publisherUserIds\":[\"" + f.pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        f.contentPlanId = findContentPlanId(ideaId);
        f.outputId = findPlannedOutputId(f.contentPlanId);
        return f;
    }

    /** SA -&gt; EA (full Shoot cycle, Shoot Review approved - workflow redesign: Editor team
     * assignment now folds directly into this same Approve call, so the plan lands on EA
     * directly, never resting at SAP - see ShootingService#decideShootReview). */
    private void runShootCycle(Fixture f) throws Exception {
        f.cam.post("/api/v1/content-plans/" + f.contentPlanId + "/shooting/start", "");
        f.cam.post("/api/v1/content-plans/" + f.contentPlanId + "/shooting/review/submit", "");
        f.ceo.postJson("/api/v1/content-plans/" + f.contentPlanId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + f.camId + "\"],"
                        + "\"editorUserIds\":[\"" + f.edId + "\"],\"leadEditorUserId\":\"" + f.edId + "\"}");
    }

    /** EA -&gt; RFP (full Edit cycle, Edit Review approved - workflow redesign: Publisher team
     * assignment now folds directly into this same Approve call - see EditingService#decideEditReview). */
    private void assignAndRunEditCycle(Fixture f) throws Exception {
        f.ed.post("/api/v1/content-plans/" + f.contentPlanId + "/editing/start", "");
        f.ed.post("/api/v1/content-plans/" + f.contentPlanId + "/editing/review/submit", "");
        f.ceo.postJson("/api/v1/content-plans/" + f.contentPlanId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + f.edId + "\"],"
                        + "\"publisherUserIds\":[\"" + f.pubId + "\"]}");
    }

    /** RFP -&gt; PUBG (Publisher already assigned via the Edit Review fold-in above, Publisher started publishing). */
    private void startPublishing(Fixture f) throws Exception {
        f.pub.post("/api/v1/content-plans/" + f.contentPlanId + "/publishing/start", "");
    }

    /** PUBG -&gt; PP (Original publication event recorded). */
    private void recordOriginalPublication(Fixture f, long unique) throws Exception {
        String pastTimestamp = Instant.now().minus(3, ChronoUnit.DAYS).toString();
        f.pub.postJson("/api/v1/content-plans/" + f.contentPlanId + "/publishing/events",
                "{\"plannedOutputId\":\"" + f.outputId + "\",\"publicationTargetId\":\"" + PUBLICATION_TARGET_ID
                        + "\",\"eventType\":\"ORIGINAL\",\"actualPublicationTimestamp\":\"" + pastTimestamp
                        + "\",\"evidenceUrl\":\"https://instagram.com/p/ace-" + unique + "\"}");
    }

    private String contentDetailBody(TestApiClient client, String contentPlanId) throws Exception {
        return client.get("/app/deliverables/" + contentPlanId).body();
    }

    // ================================================================== Shoot Assigned (workflow
    // redesign: Planning is no longer a separate resting stage - Idea Review approval lands
    // directly on Shoot Assigned (SA, canonical stage "Shoot"), which is now the first resting
    // stage where Reschedule/Cancel/Reassign visibility can be observed before Shoot execution
    // itself has started.)

    @Test
    void shootAssigned_rescheduleAndCancelVisible() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        String page = contentDetailBody(f.ceo, f.contentPlanId);

        assertThat(page).as("Current Stage must resolve to the canonical Shoot label").contains(">Shoot<");
        assertThat(hasActionButton(page, "RESCHEDULE", "Reschedule"))
                .as("CEO native authority + not-closed is reschedulable -> Reschedule visible").isTrue();
        assertThat(hasActionButton(page, "CANCEL", "Cancel"))
                .as("CEO native authority + not-closed is cancellable -> Cancel visible").isTrue();
    }

    @Test
    void shootAssigned_reassignVisibleWithActiveShootAssignment_andTaskStageOptionFilteredToShooting() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        String page = contentDetailBody(f.ceo, f.contentPlanId);

        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Active ShootingAssignment made at Idea Review approval -> Reassign visible").isTrue();
        String taskStageSelect = reassignTaskStageSelectHtml(page);
        assertThat(taskStageSelect).as("Only SHOOTING is an eligible Task Stage before Shoot execution starts")
                .contains("<option value=\"SHOOTING\">SHOOTING</option>")
                .doesNotContain("<option value=\"EDITING\">EDITING</option>");
    }

    // ================================================================== Shoot

    @Test
    void shoot_reassignVisibleWithActiveShootAssignment() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        f.cam.post("/api/v1/content-plans/" + f.contentPlanId + "/shooting/start", "");

        String page = contentDetailBody(f.ceo, f.contentPlanId);
        assertThat(page).contains(">Shoot<");
        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Active ShootingAssignment during Shoot In Progress -> Reassign visible").isTrue();
        assertThat(reassignTaskStageSelectHtml(page)).contains("<option value=\"SHOOTING\">SHOOTING</option>");
    }

    // ================================================================== Edit

    @Test
    void edit_reassignVisibleWithActiveEditAssignment_shootTaskStageNoLongerOffered() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        // runShootCycle already folds in the Editor team assignment (lands directly on EA,
        // canonical stage Edit) - stop there, do not run the full Edit cycle, we only need to
        // observe the Edit-canonical window.
        runShootCycle(f);

        String page = contentDetailBody(f.ceo, f.contentPlanId);
        assertThat(page).contains(">Edit<");
        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Active EditingAssignment during Edit Assigned -> Reassign visible").isTrue();
        String taskStageSelect = reassignTaskStageSelectHtml(page);
        assertThat(taskStageSelect).as("Shoot's own assignment is historically finalized once Edit begins - "
                        + "SHOOTING must no longer be an offered Task Stage, only EDITING")
                .contains("<option value=\"EDITING\">EDITING</option>")
                .doesNotContain("<option value=\"SHOOTING\">SHOOTING</option>");
    }

    // ================================================================== Publishing

    @Test
    void publishing_reassignHiddenDespiteStillHavingActiveShootAndEditAssignments() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        runShootCycle(f);
        assignAndRunEditCycle(f);
        startPublishing(f);

        String page = contentDetailBody(f.ceo, f.contentPlanId);
        assertThat(page).contains(">Publishing<");
        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Reassign has no backend concept of a Publishing task stage at all - must never "
                        + "appear on the Publishing stage, permission or not")
                .isFalse();
    }

    // ================================================================== Performance

    @Test
    void performance_noStaleShootOrEditReassignCarriedForward() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        runShootCycle(f);
        assignAndRunEditCycle(f);
        startPublishing(f);
        recordOriginalPublication(f, unique);

        String page = contentDetailBody(f.ceo, f.contentPlanId);
        assertThat(page).contains(">Performance<");
        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Neither the (still-active) Shoot nor Edit assignment may resurface as Reassign here")
                .isFalse();
    }

    // ================================================================== Permissions (state vs permission)

    @Test
    void permissionAbsent_hidesRescheduleEvenWhenPlanningStateAllowsIt() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        String hrEmail = "ace-hr-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(f.ceo, "ACE HR NoGrant " + unique, hrEmail, HR_MANAGER_ROLE_ID);
        // Deliberately no PERM_10 grant - a harmless, Action-Center-irrelevant grant (audit history
        // view) just lets this employee reach the page at all.
        grant(f.ceo, hrId, "PERM_16_AUDIT_HISTORY_VIEW");
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        String page = contentDetailBody(hr, f.contentPlanId);
        assertThat(hasActionButton(page, "RESCHEDULE", "Reschedule"))
                .as("Planning IS reschedulable, but this viewer holds no PERM_10 grant at all").isFalse();
    }

    @Test
    void permissionAlone_doesNotShowReassignOnAnInvalidStage() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        runShootCycle(f);
        assignAndRunEditCycle(f);
        startPublishing(f);

        String hrEmail = "ace-hr-reassign-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(f.ceo, "ACE HR Reassign " + unique, hrEmail, HR_MANAGER_ROLE_ID);
        grant(f.ceo, hrId, "PERM_11_REASSIGN");
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        String page = contentDetailBody(hr, f.contentPlanId);
        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Holding PERM_11 alone must not surface Reassign on the Publishing stage").isFalse();
    }

    // ================================================================== Completed / Reopen

    @Test
    void completed_ordinaryAdminActionsAbsent_reopenForPublishingVisibleWhenEligible() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        runShootCycle(f);
        assignAndRunEditCycle(f);
        startPublishing(f);
        recordOriginalPublication(f, unique);
        completePerformance(f);

        String page = contentDetailBody(f.ceo, f.contentPlanId);
        assertThat(page).contains(">Completed<");
        assertThat(hasActionButton(page, "RESCHEDULE", "Reschedule"))
                .as("Reschedule is closed once Completed").isFalse();
        assertThat(hasActionButton(page, "REASSIGN", "Reassign"))
                .as("Reassign is closed once Completed").isFalse();
        assertThat(hasActionButton(page, "CANCEL", "Cancel"))
                .as("Cancel is closed once Completed").isFalse();
        assertThat(hasActionButton(page, "REOPEN_PUBLISHING", "Reopen for Publishing"))
                .as("CEO holds PERM_08 natively - the governed Reopen for Publishing action must appear").isTrue();
    }

    @Test
    void cancel_stillHiddenAfterReopenBecauseTheDeliverableWasEverCompleted() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        runShootCycle(f);
        assignAndRunEditCycle(f);
        startPublishing(f);
        recordOriginalPublication(f, unique);
        completePerformance(f);
        f.ceo.postFormAjax("/app/deliverables/" + f.contentPlanId + "/reopen-publishing",
                java.util.Map.of("reason", "action-center regression: reopen for cancel check"));

        String page = contentDetailBody(f.ceo, f.contentPlanId);
        assertThat(page).contains(">Publishing<");
        assertThat(hasActionButton(page, "CANCEL", "Cancel"))
                .as("ERD-CON-006: once ever Completed, Cancel stays hidden even though the deliverable "
                        + "is open again (RFP) and otherwise not closed")
                .isFalse();
    }

    // ================================================================== backend enforcement parity

    @Test
    void backendRejectsReassignOnThePublishingStageEvenForNativeAuthority() throws Exception {
        long unique = Instant.now().toEpochMilli();
        Fixture f = setupPlanning(unique);
        runShootCycle(f);
        assignAndRunEditCycle(f);
        startPublishing(f);

        // Same rule the Action Center button visibility uses (AvailableActionService), enforced
        // directly by AdminActionService#reassign - a crafted request cannot succeed just because
        // the button would have been hidden; the backend independently rejects it too.
        assertThat(f.ceo.post("/api/v1/content-plans/" + f.contentPlanId + "/reassign",
                "{\"taskStage\":\"SHOOTING\",\"newAssigneeUserIds\":[\"" + f.camId + "\"],\"reason\":\"should be rejected\"}")
                .statusCode())
                .as("Reassign(SHOOTING) is no longer eligible once the canonical stage is Publishing")
                .isEqualTo(409);
    }
}
