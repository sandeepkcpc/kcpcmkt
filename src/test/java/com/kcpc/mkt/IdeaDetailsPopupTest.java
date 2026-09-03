package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
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
 * Idea Details read-only popup on the Employee My Work / Task Detail screens (Shoot/Edit/Publish):
 * a small Note icon in the top-right corner of the "Content &amp; Shoot/Edit/Publishing
 * Information" card header (NOT beside Content Name) opens a native &lt;dialog&gt; showing only
 * the original Idea Submission's Description - Additional Note is deliberately not shown here (or
 * anywhere on these screens) at all, per explicit product decision. Title and Reference Link are
 * shown directly in the Content Information block instead, never inside the popup. Every field
 * reads straight from {@code plan.idea} - the exact same association {@code plan.idea.title}
 * already uses elsewhere on these pages - never a separate copy of the data. No Idea Review/
 * approval/audit control of any kind is exposed, and the popup carries no form - nothing on it is
 * editable. Only reachable from a page the assigned employee's own role/status already gates (see
 * DeliverableMvcController), so no separate permission check is needed for the icon itself. Real
 * HTTP, real Postgres, no mocking - same convention as ShootTaskDetailTest/EditTaskDetailTest.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaDetailsPopupTest {

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

    private String[] createUser(TestApiClient ceo, String label, String roleId, String permission, long unique)
            throws Exception {
        String email = "e2e-ideapopup-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"IdeaPopup " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"idea details popup test fixture\"}");
        String userId = user.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"idea details popup test grant\"}");
        return new String[] {userId, email};
    }

    private String dialogHtml(String page) {
        int dialogStart = page.indexOf("id=\"ideaDetailsDialog\"");
        int dialogEnd = page.indexOf("</dialog>", dialogStart);
        return page.substring(dialogStart, dialogEnd);
    }

    // ================================================================== Shoot Task Detail (full field coverage)

    @Test
    void shootTaskDetailShowsIconInCardHeaderAndPopupWithDescriptionOnly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String[] pub = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);

        String title = "Idea Popup Full " + unique;
        String referenceLink = "https://example.com/reference-" + unique;
        String description = "This is the full idea description text for test " + unique + ".";
        String additionalNote = "This additional note must never appear anywhere " + unique + ".";
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\",\"referenceLink\":\"" + referenceLink
                + "\",\"notesRemarks\":\"" + description + "\",\"additionalNote\":\"" + additionalNote + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ideapopup-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        String page = camClient.get("/app/deliverables/" + plan.getId()).body();

        // Icon lives in the card header, not beside Content Name: it must appear after the card's
        // <h2> heading and before the "Content Information" sub-section (where Content ID/Name
        // rows begin) - never inside the info-list rows themselves.
        int headingIndex = page.indexOf("Content &amp; Shoot Information");
        int iconIndex = page.indexOf("data-idea-details-trigger");
        int contentInfoSubheadingIndex = page.indexOf("Content Information</h3>");
        assertThat(headingIndex).isGreaterThanOrEqualTo(0);
        assertThat(iconIndex).isGreaterThan(headingIndex);
        assertThat(iconIndex).isLessThan(contentInfoSubheadingIndex);
        assertThat(page).contains("id=\"ideaDetailsDialog\"");

        // Title and Reference Link are directly visible in Content Information, never inside the popup.
        assertThat(page).contains(">Content Name<");
        assertThat(page).contains(title);
        assertThat(page).contains(">Reference Link<");
        assertThat(page).contains(referenceLink);

        String dialog = dialogHtml(page);
        assertThat(dialog).doesNotContain("Content Name").doesNotContain(title).doesNotContain(referenceLink);

        // Only Description appears inside the popup - no Title, no Reference Link, no Additional Note.
        assertThat(dialog).contains(">Description<").contains(description);
        assertThat(dialog).doesNotContain("Additional Note").doesNotContain(additionalNote);

        // Additional Note is not shown anywhere at all on this page, per explicit product decision.
        assertThat(page).doesNotContain("Additional Note").doesNotContain(additionalNote);

        // No edit control anywhere inside the dialog - it's a pure view, not a form.
        assertThat(dialog).doesNotContain("<form").doesNotContain("<input").doesNotContain("<textarea")
                .doesNotContain("Approve").doesNotContain("Reject").doesNotContain("Reason *");
    }

    // ================================================================== Shoot Task Detail (empty fields)

    @Test
    void shootTaskDetailShowsIconAndEmDashWhenReferenceLinkAndDescriptionAreEmpty() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] cam = createUser(ceo, "cam2", CAMERA_PERSON_ROLE_ID, "PERM_18_SHOOT_EXECUTION", unique);
        String[] pub = createUser(ceo, "pub2", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);

        // No referenceLink/notesRemarks at all - Idea Submission optional fields.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Idea Popup Empty " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ideapopup-empty-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + cam[0] + "\"],"
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient camClient = new TestApiClient(port);
        camClient.login(cam[1], "Passw0rd!");
        String page = camClient.get("/app/deliverables/" + plan.getId()).body();

        // Icon still appears when Description/Reference Link are empty.
        assertThat(page).contains("data-idea-details-trigger");

        // Reference Link (directly visible, outside the popup) renders as &mdash; too.
        assertThat(page).contains(">Reference Link<");

        String dialog = dialogHtml(page);
        assertThat(dialog).contains(">Description<");
        assertThat(dialog).doesNotContain("Additional Note");
        // JSP source uses the literal &mdash; entity as static template text (never inside an EL
        // string - this app's Jasper setup double-encodes a raw UTF-8 em-dash character embedded
        // that way), so the raw HTTP response body contains the literal entity text.
        int dialogEmDashCount = dialog.split("&mdash;", -1).length - 1;
        assertThat(dialogEmDashCount).as("Description should render as &mdash;").isEqualTo(1);
        int pageEmDashCount = page.split("&mdash;", -1).length - 1;
        assertThat(pageEmDashCount).as("Reference Link (directly visible) adds one more &mdash; on top of the popup's one")
                .isGreaterThanOrEqualTo(2);
    }

    // ================================================================== Edit Task Detail (Direct Edit reaches EA directly)

    @Test
    void editTaskDetailShowsIconInCardHeaderAndPopupWithDescriptionOnly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] editor = createUser(ceo, "editor", VIDEO_EDITOR_ROLE_ID, "PERM_19_EDIT_EXECUTION", unique);
        String[] pub = createUser(ceo, "editor-pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);

        String title = "Idea Popup Edit " + unique;
        String referenceLink = "https://example.com/edit-reference-" + unique;
        String description = "Edit-flow description " + unique;
        String additionalNote = "Edit-flow additional note that must not appear " + unique;
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\",\"referenceLink\":\"" + referenceLink
                + "\",\"notesRemarks\":\"" + description + "\",\"additionalNote\":\"" + additionalNote + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Edit (Stages = Edit + Publishing) reaches EA directly from Idea Review approval.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ideapopup-edit-" + unique + "\","
                        + "\"editorUserIds\":[\"" + editor[0] + "\"],\"leadEditorUserId\":\"" + editor[0] + "\","
                        + "\"publisherUserIds\":[\"" + pub[0] + "\"],"
                        + "\"stages\":[\"EDIT\",\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient editorClient = new TestApiClient(port);
        editorClient.login(editor[1], "Passw0rd!");
        String page = editorClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains("Edit Task");
        int headingIndex = page.indexOf("Content &amp; Edit Information");
        int iconIndex = page.indexOf("data-idea-details-trigger");
        int contentInfoSubheadingIndex = page.indexOf("Content Information</h3>");
        assertThat(iconIndex).isGreaterThan(headingIndex);
        assertThat(iconIndex).isLessThan(contentInfoSubheadingIndex);

        // Title/Reference Link directly visible.
        assertThat(page).contains(title);
        assertThat(page).contains(referenceLink);
        // Additional Note is not shown anywhere on this page at all.
        assertThat(page).doesNotContain("Additional Note").doesNotContain(additionalNote);

        String dialog = dialogHtml(page);
        assertThat(dialog).doesNotContain(title).doesNotContain(referenceLink);
        assertThat(dialog).contains(description);
    }

    // ================================================================== Publish Task Detail (Direct Publishing reaches RFP directly)

    @Test
    void publishTaskDetailShowsIconInCardHeaderAndPopupWithDescriptionOnly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String[] publisher = createUser(ceo, "pub", PUBLISHER_ROLE_ID, "PERM_08_PUBLISHING_EXECUTION", unique);

        String title = "Idea Popup Publish " + unique;
        String referenceLink = "https://example.com/publish-reference-" + unique;
        String description = "Publish-flow description " + unique;
        String additionalNote = "Publish-flow additional note that must not appear " + unique;
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\",\"referenceLink\":\"" + referenceLink
                + "\",\"notesRemarks\":\"" + description + "\",\"additionalNote\":\"" + additionalNote + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Direct Publishing (Stages = Publishing only) reaches RFP directly from Idea Review approval.
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/ideapopup-pub-" + unique + "\","
                        + "\"publisherUserIds\":[\"" + publisher[0] + "\"],\"stages\":[\"PUBLISHING\"]}}");
        ContentPlan plan = contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();

        TestApiClient pubClient = new TestApiClient(port);
        pubClient.login(publisher[1], "Passw0rd!");
        String page = pubClient.get("/app/deliverables/" + plan.getId()).body();

        assertThat(page).contains("Publish");
        int headingIndex = page.indexOf("Content &amp; Publishing Information");
        int iconIndex = page.indexOf("data-idea-details-trigger");
        int contentInfoSubheadingIndex = page.indexOf("Content Information</h3>");
        assertThat(iconIndex).isGreaterThan(headingIndex);
        assertThat(iconIndex).isLessThan(contentInfoSubheadingIndex);

        assertThat(page).contains(title);
        assertThat(page).contains(referenceLink);
        assertThat(page).doesNotContain("Additional Note").doesNotContain(additionalNote);

        String dialog = dialogHtml(page);
        assertThat(dialog).doesNotContain(title).doesNotContain(referenceLink);
        assertThat(dialog).contains(description);
    }
}
