package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviews UI consistency fix: the selected item's Description/Details and Reference Link -
 * already shown in Reviews -> Ideas' "Reviewing Idea" card - must be visible in Reviews -> Shoot
 * and Reviews -> Edit too, since a Shoot/Edit reviewer needs the same context without separately
 * opening the Idea. Backed by the exact SAME Idea entity (ContentPlan#getIdea, never a new/
 * duplicated field) via the exact same shared fragments already used by Reviews -> Ideas
 * (idea-reference-link-edit.jspf / idea-description-modal*.jspf) and re-wired by the exact same
 * pre-existing, unconditional reviews-workspace.js calls (wireScriptDescriptionModal(region)/
 * wireIdeaReferenceLinkEdit(region) already fire after every AJAX tab swap, Shoot/Edit included -
 * no JS change was needed). Rendered read-only here (refLinkEditCanEdit/ideaDescModalCanEdit
 * false) - purely informational, no new edit surface, no workflow change.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReviewsShootEditDescriptionReferenceLinkTest {

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

    private String[] createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "rv-desc-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"RvDesc " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"reviews description test fixture\"}");
        return new String[] {user.get("userId").asText(), email};
    }

    private void grantExecutionPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"reviews description test fixture grant\"}");
    }

    /** Idea (with the given Description/Reference Link, either possibly null) -> approved -> Shoot
     *  started -> Shoot Review submitted, ready to be opened at Reviews -> Shoot. */
    private String[] buildToShootReview(TestApiClient ceo, long unique, String notesRemarks, String referenceLink)
            throws Exception {
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, unique);
        grantExecutionPermission(ceo, cam[0], "PERM_18_SHOOT_EXECUTION");
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, unique);
        grantExecutionPermission(ceo, pub[0], "PERM_08_PUBLISHING_EXECUTION");

        StringBuilder ideaJson = new StringBuilder("{\"title\":\"RvDesc Idea " + unique + "\"");
        if (notesRemarks != null) {
            ideaJson.append(",\"notesRemarks\":\"").append(notesRemarks).append('"');
        }
        if (referenceLink != null) {
            ideaJson.append(",\"referenceLink\":\"").append(referenceLink).append('"');
        }
        ideaJson.append('}');
        JsonNode idea = ceo.postJson("/api/v1/ideas", ideaJson.toString());
        String ideaId = idea.get("ideaId").asText();

        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/rv-desc-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");

        String planId = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow().getId().toString();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");

        return new String[] {planId, cam[0]};
    }

    /** Continues an already-Shoot-Review plan through Shoot Review approval -> Edit started ->
     *  Edit Review submitted, ready to be opened at Reviews -> Edit. */
    private void advanceToEditReview(TestApiClient ceo, String planId, String camId, long unique) throws Exception {
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, unique);
        grantExecutionPermission(ceo, editor[0], "PERM_19_EDIT_EXECUTION");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + camId + "\"],"
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\"}");

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
    }

    @Test
    void shootReviewShowsTheIdeasDescriptionAndReferenceLinkWhenPresent() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String longDescription = "A".repeat(150) + "-tail-" + unique;
        String referenceLink = "https://drive.example.com/rv-desc-shoot-ref-" + unique;
        String[] fx = buildToShootReview(ceo, unique, longDescription, referenceLink);
        String planId = fx[0];

        String body = ceo.get("/app/reviews?tab=shoot&selectedId=" + planId).body();

        assertThat(body).contains(">Description / Details</span>");
        assertThat(body).contains("id=\"scriptDescriptionOpen\"");
        assertThat(body).contains("id=\"scriptDescriptionModalOverlay\"");
        // Inline preview is truncated to 100 chars (same as Reviews -> Ideas) - the full text only
        // lives in the modal body.
        assertThat(body).contains("A".repeat(100) + "&hellip;");
        assertThat(body).contains(longDescription); // full text present in the modal's <pre> body

        assertThat(body).contains(">Reference Link</span>");
        assertThat(body).contains("id=\"refLinkView\"");
        assertThat(body).contains(referenceLink);
    }

    @Test
    void shootReviewShowsTheExistingEmptyStatesWhenDescriptionAndReferenceLinkAreMissing() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToShootReview(ceo, unique, null, null);
        String planId = fx[0];

        String body = ceo.get("/app/reviews?tab=shoot&selectedId=" + planId).body();

        assertThat(body).contains(">Description / Details</span>");
        assertThat(body).contains("id=\"scriptDescriptionOpen\"");
        assertThat(body).contains("No description added yet.");
        assertThat(body).contains(">Reference Link</span>");
        assertThat(body).contains("id=\"refLinkView\"");
    }

    @Test
    void editReviewShowsTheSameIdeasDescriptionAndReferenceLinkWhenPresent() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String longDescription = "B".repeat(150) + "-tail-" + unique;
        String referenceLink = "https://drive.example.com/rv-desc-edit-ref-" + unique;
        String[] fx = buildToShootReview(ceo, unique, longDescription, referenceLink);
        String planId = fx[0];
        String camId = fx[1];
        advanceToEditReview(ceo, planId, camId, unique);

        String body = ceo.get("/app/reviews?tab=edit&selectedId=" + planId).body();

        assertThat(body).contains(">Description / Details</span>");
        assertThat(body).contains("id=\"scriptDescriptionOpen\"");
        assertThat(body).contains("B".repeat(100) + "&hellip;");
        assertThat(body).contains(longDescription);
        assertThat(body).contains(">Reference Link</span>");
        assertThat(body).contains("id=\"refLinkView\"");
        assertThat(body).contains(referenceLink);
    }

    @Test
    void editReviewShowsTheExistingEmptyStatesWhenDescriptionAndReferenceLinkAreMissing() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToShootReview(ceo, unique, null, null);
        String planId = fx[0];
        String camId = fx[1];
        advanceToEditReview(ceo, planId, camId, unique);

        String body = ceo.get("/app/reviews?tab=edit&selectedId=" + planId).body();

        assertThat(body).contains("No description added yet.");
        assertThat(body).contains("id=\"refLinkView\"");
    }

    // --- No new edit surface: Shoot/Edit review panels render these fields read-only, unlike the
    // Ideas tab's own CEO/Marketing-Manager-only pencil-icon edit affordance. ---
    @Test
    void shootAndEditReviewNeverExposeTheDescriptionOrReferenceLinkEditControls() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] fx = buildToShootReview(ceo, unique, "some notes " + unique, "https://drive.example.com/x-" + unique);
        String planId = fx[0];
        String camId = fx[1];

        String shootBody = ceo.get("/app/reviews?tab=shoot&selectedId=" + planId).body();
        assertThat(shootBody).doesNotContain("id=\"scriptDescriptionEditToggle\"");
        assertThat(shootBody).doesNotContain("id=\"refLinkEditToggle\"");

        advanceToEditReview(ceo, planId, camId, unique);
        String editBody = ceo.get("/app/reviews?tab=edit&selectedId=" + planId).body();
        assertThat(editBody).doesNotContain("id=\"scriptDescriptionEditToggle\"");
        assertThat(editBody).doesNotContain("id=\"refLinkEditToggle\"");
    }

    // --- Sanity/regression check: touching reviews-shoot.jspf/reviews-edit.jspf did not disturb
    // the pre-existing Reviews -> Ideas rendering the other two tabs were modeled on. ---
    @Test
    void ideasReviewStillShowsDescriptionAndReferenceLinkUnchanged() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String description = "idea review sanity notes " + unique;
        String referenceLink = "https://drive.example.com/rv-desc-idea-ref-" + unique;
        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"RvDesc Ideas Sanity " + unique + "\",\"notesRemarks\":\"" + description
                        + "\",\"referenceLink\":\"" + referenceLink + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String body = ceo.get("/app/reviews?tab=ideas&selectedId=" + ideaId).body();
        assertThat(body).contains(">Description / Details</span>");
        assertThat(body).contains(description);
        assertThat(body).contains(">Reference Link</span>");
        assertThat(body).contains(referenceLink);
        assertThat(body).contains("id=\"scriptDescriptionEditToggle\""); // CEO retains edit affordance here
        assertThat(body).contains("id=\"refLinkEditToggle\"");
    }
}
