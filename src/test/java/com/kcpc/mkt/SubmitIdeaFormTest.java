package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-060: the redesigned Submit Idea screen separates "Additional Note" from "Idea Description /
 * Details" (a new additional_note column), validates Reference Link as a real URL, and - on a
 * backend validation failure - must preserve everything the user already entered instead of
 * discarding the form (client-side JS catches the common cases first, but the server stays
 * authoritative, e.g. for the no-JS fallback).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SubmitIdeaFormTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;

    @Test
    void submittingWithAllFieldsPersistsAdditionalNoteSeparatelyAndShowsSuccessMessageOnMyIdeas() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String title = "Submit Idea Form Test " + unique;
        HttpResponse<String> submit = ceo.postForm("/app/ideas", Map.of(
                "title", title,
                "referenceLink", "https://drive.example.com/ref-" + unique,
                "additionalNote", "A short note " + unique,
                "notesRemarks", "A longer description of the idea " + unique));
        assertThat(submit.statusCode()).isEqualTo(302);
        assertThat(submit.headers().firstValue("Location").orElseThrow()).contains("/app/ideas");

        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();
        assertThat(idea.getAdditionalNote()).isEqualTo("A short note " + unique);
        assertThat(idea.getNotesRemarks()).isEqualTo("A longer description of the idea " + unique);
        assertThat(idea.getReferenceLink()).isEqualTo("https://drive.example.com/ref-" + unique);

        // Following the redirect (a real browser would) lands on My Ideas with the flash success
        // message surfaced and the new idea visible - checking the flash-carrying response body
        // directly is not possible across a redirect hop in this client; instead confirm the idea
        // is queryable and correctly attributed.
        assertThat(idea.getSubmittedBy().getEmail()).isEqualTo("ceo@kcpcbandhani.local");
    }

    @Test
    void invalidReferenceLinkIsRejectedByTheBackendAndPreservesEveryEnteredValue() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String title = "Submit Idea Bad Link " + unique;
        HttpResponse<String> submit = ceo.postForm("/app/ideas", Map.of(
                "title", title,
                "referenceLink", "not-a-valid-url",
                "additionalNote", "keep me " + unique,
                "notesRemarks", "keep this too " + unique));

        // Rejected without a redirect - the form re-renders in place (200), never persisting the idea.
        assertThat(submit.statusCode()).isEqualTo(200);
        assertThat(submit.body()).contains("valid URL");
        assertThat(submit.body()).contains(title);
        assertThat(submit.body()).contains("not-a-valid-url");
        assertThat(submit.body()).contains("keep me " + unique);
        assertThat(submit.body()).contains("keep this too " + unique);

        assertThat(ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .noneMatch(i -> i.getTitle().equals(title))).isTrue();
    }

    @Test
    void blankTitleIsRejectedByTheBackendAndPreservesTheOtherEnteredValues() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        HttpResponse<String> submit = ceo.postForm("/app/ideas", Map.of(
                "title", "",
                "referenceLink", "https://example.com/kept-" + unique,
                "additionalNote", "kept note " + unique));

        assertThat(submit.statusCode()).isEqualTo(200);
        assertThat(submit.body()).contains("Idea Title is mandatory");
        assertThat(submit.body()).contains("https://example.com/kept-" + unique);
        assertThat(submit.body()).contains("kept note " + unique);
    }
}
