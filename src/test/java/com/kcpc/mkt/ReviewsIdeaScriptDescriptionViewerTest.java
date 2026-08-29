package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviews Workspace -> Ideas tab: the selected idea's "Description / Details" field previously
 * rendered ideas.notes_remarks (unlimited length - may hold a full script) inline, which could
 * blow up the compact detail panel. It now shows only a note icon (when a description exists)
 * that opens a read-only modal with the complete text - same script-description-modal.js/CSS
 * component idea-detail.jsp's own Description/Details note icon already uses (see
 * IdeaDetailScriptDescriptionViewerTest), but re-wired after every AJAX region swap by
 * reviews-workspace.js's window.wireScriptDescriptionModal(region) call, since this whole panel's
 * innerHTML is replaced on every idea selection - unlike idea-detail.jsp's one-shot page load.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReviewsIdeaScriptDescriptionViewerTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;

    @Test
    void iconAndModalWithFullDescriptionShowWhenNotesRemarksPresent() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String longScript = "Reviews panel full description line. ".repeat(40) + "END-MARKER-" + unique;
        assertThat(longScript.length()).isGreaterThan(500);

        JsonNode idea = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"Reviews Script Viewer " + unique + "\",\"notesRemarks\":\"" + longScript + "\"}");
        String ideaId = idea.get("ideaId").asText();

        String body = ceo.get("/app/reviews?tab=ideas&selectedId=" + ideaId).body();

        // The field label stays (unlike idea-detail.jsp, which drops it entirely for a header
        // icon) - only its value becomes the icon instead of the raw text.
        assertThat(body).contains(">Description / Details</span>");
        assertThat(body).contains("id=\"scriptDescriptionOpen\"");
        assertThat(body).contains("id=\"scriptDescriptionModalOverlay\"")
                .contains("kcpc-modal-overlay hidden")
                .contains(longScript);
    }

    @Test
    void iconAndModalAlwaysShowEvenWithNoDescription() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String title = "Reviews No Script " + unique;
        assertThat(ceo.postForm("/app/ideas", java.util.Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        String body = ceo.get("/app/reviews?tab=ideas&selectedId=" + idea.getId()).body();

        // Unlike idea-detail.jsp's icon, the Reviews Workspace's "Reviewing Idea" panel shows the
        // icon unconditionally so the reviewer can always open the (possibly empty) description.
        assertThat(body).contains(">Description / Details</span>");
        assertThat(body).contains("id=\"scriptDescriptionOpen\"")
                .contains("id=\"scriptDescriptionModalOverlay\"")
                .contains("No description added yet.");
    }
}
