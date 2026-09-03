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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idea Review approval, Standard Planning Mode: the resulting Shoot Date/Edit Date - whether the
 * Live-5d/Live-2d default ({@link com.kcpc.mkt.idea.service.IdeaService#approve}) or a manually
 * overridden value in those same Standard-mode fields - must never land before today. The
 * pre-existing BRS-REQ-093 rule only constrains the Planned Live Date itself (must be &gt;= 5 days
 * out under Standard); it does not, on its own, stop an explicit Shoot/Edit Date override from
 * landing in the past even while the Live Date satisfies that floor. This is a real gap a user can
 * hit by manually editing the pre-filled Shoot/Edit Date fields in the Idea Review Planning form.
 *
 * <p>Urgent Planning Mode is deliberately untouched - an urgent plan's explicit dates are the
 * user's own informed call (matches the pre-existing "urgent = already behind schedule" premise),
 * so a past Shoot/Edit Date there must keep succeeding exactly as before.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StandardPlanningPastDateValidationTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String VIDEO_EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createCameraperson(TestApiClient ceo, long unique) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"SPPD Cam\",\"email\":\"sppd-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\","
                        + "\"creationReason\":\"standard planning past date validation test fixture\"}");
        String userId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\","
                        + "\"permission\":\"PERM_18_SHOOT_EXECUTION\",\"scopeType\":\"GLOBAL\","
                        + "\"reason\":\"standard planning past date validation test fixture grant\"}");
        return userId;
    }

    private String createUser(TestApiClient ceo, String label, String roleId, String permissionCode, long unique) throws Exception {
        var response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"SPPD " + label + "\",\"email\":\"sppd-" + label + "-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"" + roleId + "\","
                        + "\"creationReason\":\"standard planning past date validation test fixture\"}");
        String userId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\","
                        + "\"permission\":\"" + permissionCode + "\",\"scopeType\":\"GLOBAL\","
                        + "\"reason\":\"standard planning past date validation test fixture grant\"}");
        return userId;
    }

    private Idea createIdea(TestApiClient ceo, String title) throws Exception {
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        return ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();
    }

    /** Case 1: Standard mode, valid Planned Live Date (>= 5 days out, satisfying the pre-existing
     * BRS-REQ-093 floor), but an EXPLICIT Shoot Date override that lands in the past - must be
     * rejected, and the idea must stay Pending Approval (no half-created Content Plan). */
    @Test
    void explicitShootDateOverrideInThePastIsRejectedUnderStandardMode() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createCameraperson(ceo, unique);
        String title = "SPPD Past Shoot " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(10).toString();
        String pastShootDate = LocalDate.now().minusDays(1).toString();
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", List.of("APPROVE"),
                "cameramanMark", List.of("1.0"),
                "editorMark", List.of("1.0"),
                "modelMark", List.of("1.0"),
                "contentPriority", List.of("MEDIUM"),
                "plannedLiveDate", List.of(liveDate),
                "shootDate", List.of(pastShootDate),
                "folderLink", List.of("https://drive.example.com/sppd-shoot-" + unique),
                "camerapersonUserIds", List.of(camId)));
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).as("No Content Plan left half-created").isEmpty();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Shoot Date cannot be before today");
        // Never silently corrected forward - the rejected past date is still what was submitted,
        // not auto-shifted to today or later.
        assertThat(page).doesNotContain("Review decision recorded.");
    }

    /** Case 2: same shape, but the Edit Date override is the one in the past (Shoot Date left at
     * its valid Standard default). */
    @Test
    void explicitEditDateOverrideInThePastIsRejectedUnderStandardMode() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createCameraperson(ceo, unique);
        String title = "SPPD Past Edit " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(10).toString();
        String pastEditDate = LocalDate.now().minusDays(1).toString();
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", List.of("APPROVE"),
                "cameramanMark", List.of("1.0"),
                "editorMark", List.of("1.0"),
                "modelMark", List.of("1.0"),
                "contentPriority", List.of("MEDIUM"),
                "plannedLiveDate", List.of(liveDate),
                "editDate", List.of(pastEditDate),
                "folderLink", List.of("https://drive.example.com/sppd-edit-" + unique),
                "camerapersonUserIds", List.of(camId)));
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).isEmpty();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Edit Date cannot be before today");
    }

    /** Case 3: valid Standard planning (Live Date comfortably out, both calculated dates land
     * today-or-future) succeeds exactly as before - the new guard never fires for a genuinely
     * valid submission. */
    @Test
    void validStandardPlanningWithFutureCalculatedDatesStillSucceeds() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createCameraperson(ceo, unique);
        String pubId = createUser(ceo, "validpub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Valid Standard " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(10).toString();
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", Map.of(
                "decision", List.of("APPROVE"),
                "cameramanMark", List.of("1.0"),
                "editorMark", List.of("1.0"),
                "modelMark", List.of("1.0"),
                "contentPriority", List.of("MEDIUM"),
                "plannedLiveDate", List.of(liveDate),
                "folderLink", List.of("https://drive.example.com/sppd-valid-" + unique),
                "camerapersonUserIds", List.of(camId),
                "publisherUserIds", List.of(pubId)));
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SA");
        var plan = contentPlanRepository.findByIdea(reloaded).orElseThrow();
        assertThat(plan.getPlannedShootDate()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(plan.getPlannedEditDate()).isEqualTo(LocalDate.now().plusDays(8));

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Review decision recorded.");
    }

    /** Case 4: Urgent Planning Mode is untouched by this fix - an explicit past Shoot Date there
     * still succeeds exactly as before (the urgent-plan premise is that the team is already
     * behind schedule; that decision stays entirely the reviewer's own). */
    @Test
    void urgentPlanningModeWithPastShootDateStillSucceedsUnaffected() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createCameraperson(ceo, unique);
        String pubId = createUser(ceo, "urgentpub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Urgent Unaffected " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(3).toString();
        String pastShootDate = LocalDate.now().minusDays(1).toString();
        String pastEditDate = LocalDate.now().toString();
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("planningMode", List.of("URGENT"));
        params.put("urgencyReason", List.of("standard planning past date validation test fixture - urgent path"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("shootDate", List.of(pastShootDate));
        params.put("editDate", List.of(pastEditDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-urgent-" + unique));
        params.put("camerapersonUserIds", List.of(camId));
        params.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("SA");
        var plan = contentPlanRepository.findByIdea(reloaded).orElseThrow();
        assertThat(plan.getPlannedShootDate()).isEqualTo(LocalDate.now().minusDays(1));

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Review decision recorded.");
    }

    /** Case 5 (Publishing only / a skipped Shoot+Edit): no Shoot/Edit Date is calculated or
     * validated at all, and the BRS-REQ-093 "5 days away" floor - whose entire purpose is
     * protecting the derived Shoot/Edit Date - must not apply either, since neither exists here.
     * A near-term Planned Live Date (well under 5 days out) must succeed under Standard mode. */
    @Test
    void publishingOnlyPlanningSkipsFiveDayFloorAndShootEditValidation() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String pubId = createUser(ceo, "pubonly", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Publishing Only " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(2).toString(); // deliberately fewer than 5 days out
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("stages", List.of("PUBLISHING"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-puballonly-" + unique));
        params.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("RFP");
        var plan = contentPlanRepository.findByIdea(reloaded).orElseThrow();
        assertThat(plan.getPlannedShootDate()).isNull();
        assertThat(plan.getPlannedEditDate()).isNull();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Review decision recorded.");
        assertThat(page).doesNotContain("fewer than 5 days");
    }

    /** Case 6 (Edit selected, Shoot skipped / Direct Edit): the past-date guard must still fire
     * correctly for Edit Date even though Shoot is not part of this pipeline at all - proves the
     * validation is genuinely stage-aware, not just "always check both". */
    @Test
    void directEditWithShootSkippedStillValidatesEditDatePastToday() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String edId = createUser(ceo, "directed", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String pubId = createUser(ceo, "directpub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Direct Edit Past " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(10).toString();
        String pastEditDate = LocalDate.now().minusDays(1).toString();
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("stages", List.of("EDIT", "PUBLISHING"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("editDate", List.of(pastEditDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-directedit-" + unique));
        params.put("editorUserIds", List.of(edId));
        params.put("leadEditorUserId", List.of(edId));
        params.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).isEmpty();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Edit Date cannot be before today");
    }

    /** Case 7 (Edit selected, Shoot skipped, valid dates): the same Direct Edit combination
     * succeeds exactly as before when the calculated/explicit Edit Date is not in the past -
     * proves the new guard never blocks a genuinely valid Direct Edit submission. */
    @Test
    void directEditWithShootSkippedSucceedsWithValidEditDate() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String edId = createUser(ceo, "directedok", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String pubId = createUser(ceo, "directpubok", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Direct Edit Valid " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(10).toString();
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("stages", List.of("EDIT", "PUBLISHING"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-directeditok-" + unique));
        params.put("editorUserIds", List.of(edId));
        params.put("leadEditorUserId", List.of(edId));
        params.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("EA");
        var plan = contentPlanRepository.findByIdea(reloaded).orElseThrow();
        assertThat(plan.getPlannedEditDate()).isEqualTo(LocalDate.now().plusDays(8));
        assertThat(plan.getPlannedShootDate()).isNull();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Review decision recorded.");
    }

    /** Case 8: the BRS-REQ-093 "Planned Live Date too near" floor is sized to whichever derived
     * date the current Stages combination actually has - 5 days when Shoot starts the pipeline
     * (protects Shoot Date = Live - 5d, the stricter of the two), only 2 days for Direct Edit
     * (Shoot skipped - only Edit Date = Live - 2d exists to protect). A Live Date exactly 2 days
     * out is too near for the full pipeline but perfectly valid for Edit + Publishing. */
    @Test
    void directEditAllowsALiveDateOnlyTwoDaysOutUnlikeTheFullPipeline() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String edId = createUser(ceo, "twoday", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String pubId = createUser(ceo, "twodaypub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Direct Edit Two Day Floor " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(2).toString();
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("stages", List.of("EDIT", "PUBLISHING"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-twoday-" + unique));
        params.put("editorUserIds", List.of(edId));
        params.put("leadEditorUserId", List.of(edId));
        params.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("EA");
        var plan = contentPlanRepository.findByIdea(reloaded).orElseThrow();
        assertThat(plan.getPlannedEditDate()).isEqualTo(LocalDate.now());
        assertThat(plan.getPlannedShootDate()).isNull();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("Review decision recorded.");
    }

    /** Case 9: a Live Date only 1 day out is still too near even for Direct Edit's smaller 2-day
     * floor, and the rejection message reflects that combo's own floor (2 days), not the full
     * pipeline's 5. */
    @Test
    void directEditRejectsALiveDateOnlyOneDayOutWithTheTwoDayMessage() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String edId = createUser(ceo, "oneday", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String pubId = createUser(ceo, "onedaypub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);
        String title = "SPPD Direct Edit One Day Too Near " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(1).toString();
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("stages", List.of("EDIT", "PUBLISHING"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-oneday-" + unique));
        params.put("editorUserIds", List.of(edId));
        params.put("leadEditorUserId", List.of(edId));
        params.put("publisherUserIds", List.of(pubId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).isEmpty();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("A Planned Live Date fewer than 2 days away requires Urgent Planning Mode");
    }

    /** Case 10: the full Shoot+Edit+Publishing pipeline keeps its own, stricter 5-day floor
     * unchanged - a Live Date 4 days out (which would be fine for Direct Edit) is still rejected
     * here, with the "5 days" message, proving the fix only widened Direct Edit's own floor and
     * never loosened the full pipeline's. */
    @Test
    void fullPipelineStillRequiresTheFiveDayFloorUnchanged() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        String camId = createCameraperson(ceo, unique);
        String title = "SPPD Full Pipeline Five Day Floor " + unique;
        Idea idea = createIdea(ceo, title);

        String liveDate = LocalDate.now().plusDays(4).toString();
        Map<String, List<String>> params = new HashMap<>();
        params.put("decision", List.of("APPROVE"));
        params.put("cameramanMark", List.of("1.0"));
        params.put("editorMark", List.of("1.0"));
        params.put("modelMark", List.of("1.0"));
        params.put("contentPriority", List.of("MEDIUM"));
        params.put("plannedLiveDate", List.of(liveDate));
        params.put("folderLink", List.of("https://drive.example.com/sppd-fullfloor-" + unique));
        params.put("camerapersonUserIds", List.of(camId));
        HttpResponse<String> response = ceo.postFormMulti("/app/ideas/" + idea.getId() + "/review", params);
        assertThat(response.statusCode()).isEqualTo(302);

        Idea reloaded = ideaRepository.findById(idea.getId()).orElseThrow();
        assertThat(reloaded.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PA");
        assertThat(contentPlanRepository.findByIdea(reloaded)).isEmpty();

        String page = ceo.get("/app/ideas/" + idea.getId()).body();
        assertThat(page).contains("A Planned Live Date fewer than 5 days away requires Urgent Planning Mode");
    }
}
