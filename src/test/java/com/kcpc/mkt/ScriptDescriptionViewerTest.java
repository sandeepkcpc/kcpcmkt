package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idea submission's "Idea Description / Details" (ideas.notes_remarks) must support unlimited-
 * length script content (no server-side truncation), and Content Detail -> Overview must never
 * render that full text inline - only a note-icon button, whose click opens a read-only modal
 * containing the complete, untruncated text. Covers: (1) a description well over the old 500-char
 * UI cap round-trips completely from submission through to the rendered modal, and (2) the icon/
 * modal are entirely absent when there is no description to show.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ScriptDescriptionViewerTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;

    @Test
    void overviewShowsNoteIconAndModalWithFullUnlimitedLengthDescriptionInsteadOfInlineText() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String longScript = "Scene description line for the shoot script. ".repeat(40) + "END-MARKER-" + unique;
        assertThat(longScript.length()).isGreaterThan(500);

        String camId = createCameraperson(ceo, unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Script Viewer " + unique + "\",\"notesRemarks\":\"" + longScript + "\"}");
        String ideaId = idea.get("ideaId").asText();
        // Workflow redesign: approval carries every former Planning field and transitions straight
        // to Shoot Assigned (SA), never PL/PLRV/PLAP.
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/script-viewer-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        String contentPlanId = findContentPlanId(ideaId);

        HttpResponse<String> page = ceo.get("/app/deliverables/" + contentPlanId);
        assertThat(page.statusCode()).isEqualTo(200);
        String body = page.body();

        assertThat(body).contains("id=\"scriptDescriptionOpen\"")
                .contains("id=\"scriptDescriptionModalOverlay\"")
                .contains("Script Description")
                .contains(longScript); // full text present, untruncated - proves no 500-char cutoff anywhere
    }

    @Test
    void overviewHidesNoteIconAndModalWhenThereIsNoDescription() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String camId = createCameraperson(ceo, unique);
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"No Script Viewer " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + java.time.LocalDate.now().plusDays(10) + "\","
                        + "\"folderLink\":\"https://drive.example.com/no-script-viewer-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"]}}");
        String contentPlanId = findContentPlanId(ideaId);

        HttpResponse<String> page = ceo.get("/app/deliverables/" + contentPlanId);
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).doesNotContain("id=\"scriptDescriptionOpen\"")
                .doesNotContain("id=\"scriptDescriptionModalOverlay\"");
    }

    private String findContentPlanId(String ideaIdText) {
        UUID ideaId = UUID.fromString(ideaIdText);
        Idea idea = ideaRepository.findById(ideaId).orElseThrow();
        ContentPlan plan = contentPlanRepository.findByIdea(idea).orElseThrow();
        return plan.getId().toString();
    }

    private String createCameraperson(TestApiClient ceo, long unique) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"Script Viewer Cam\",\"email\":\"script-viewer-cam-" + unique + "@kcpcbandhani.local\","
                        + "\"password\":\"Passw0rd!\",\"businessRoleId\":\"01926e3e-0001-7000-8000-000000000004\","
                        + "\"creationReason\":\"script viewer test fixture\"}");
        String userId = response.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"script viewer test fixture grant\"}");
        return userId;
    }
}
