package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idea Details screen (/app/ideas/{ideaId}, idea-detail.jsp): the "Idea Description / Details"
 * text (ideas.notes_remarks, unlimited length - may hold a full script) is never rendered inline
 * in the Idea Details card - only a note icon in the card header (top-right, aligned with the
 * "Idea Details" title) shown exclusively when a description exists, opening a read-only modal
 * with the complete, untruncated text (same script-description-modal.js/CSS component reused from
 * Content Detail -> Overview's Script Description viewer). No permission change: visibility
 * follows this page's existing access to view the idea, unchanged.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaDetailScriptDescriptionViewerTest {

    @LocalServerPort
    int port;

    @Test
    void iconAndModalWithFullDescriptionShowWhenNotesRemarksPresent() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String longScript = "Full shoot script line for this idea. ".repeat(40) + "END-MARKER-" + unique;
        assertThat(longScript.length()).isGreaterThan(500);

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Idea Detail Script Viewer " + unique + "\",\"notesRemarks\":\"" + longScript + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> page = ceo.get("/app/ideas/" + ideaId);
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();

        // 1. Icon visible when description exists.
        assertThat(body).contains("id=\"scriptDescriptionOpen\"");
        // Icon sits in the card header, not as an inline field row - the old field label must be gone.
        assertThat(body).doesNotContain(">Idea Description / Details</span>");

        // 2. Modal opens with the full, untruncated description.
        assertThat(body).contains("id=\"scriptDescriptionModalOverlay\"")
                .contains("kcpc-modal-overlay hidden")
                .contains(longScript);
    }

    @Test
    void iconAndModalStillShowAfterIdeaIsApproved() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String longScript = "Approved-idea script line. ".repeat(40) + "END-MARKER-" + unique;
        JsonNode camUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Idea Detail Script Cam\",\"email\":\"idea-detail-script-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000004\","
                        + "\"creationReason\":\"idea detail script viewer test fixture\"}");
        String camId = camUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"idea detail script viewer test fixture grant\"}");
        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Approved Idea Script Viewer " + unique + "\",\"notesRemarks\":\"" + longScript + "\"}");
        String ideaId = idea.get("ideaId").asText();

        // Workflow redesign: approval carries every former Planning field and transitions straight
        // to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/idea-detail-script-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");

        HttpResponse<String> page = ceo.get("/app/ideas/" + ideaId);
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();

        assertThat(body).contains("id=\"scriptDescriptionOpen\"")
                .contains("id=\"scriptDescriptionModalOverlay\"")
                .contains(longScript);
    }

    @Test
    void iconAndModalAreAbsentWhenNoDescription() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"Idea Detail No Script " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();

        HttpResponse<String> page = ceo.get("/app/ideas/" + ideaId);
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();

        // 3. Icon hidden (entirely absent from the DOM, not just CSS-hidden) when there is no description.
        assertThat(body).doesNotContain("id=\"scriptDescriptionOpen\"")
                .doesNotContain("id=\"scriptDescriptionModalOverlay\"");
    }
}
