package com.kcpc.mkt;

import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviews Workspace -> Ideas tab -> Approve: the Planned Outputs grid (V31 redesign) - one fixed
 * row per Output Type (Reel/Story/Post/Long Video, in OutputType's declared order; PHOTOGRAPHY/
 * VIDEO retired) instead of the old arbitrary Add/Edit/Delete staging table. Only two columns - Output Type and
 * Platform/Channel - there is no Reel Type sub-selector and no Output Description field anywhere
 * in this grid any more. Every row starts unchecked/disabled by default.
 *
 * This is a pure UI/JS redesign (see reviews-workspace.js's syncReviewsOutputRowState/
 * collectIdeaApproveParams) - the submitted outputsJson shape is unchanged (reelTypes/
 * outputTitleDescription are just always sent empty now), so no backend/DTO test needed here;
 * what's assertable at the HTTP level is the server-rendered default markup, not the live
 * checkbox-driven show/hide/chip/popover behaviour itself (no browser-JS test harness exists in
 * this codebase for that).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReviewsIdeaOutputFormRenderingTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;

    @Test
    void plannedOutputsGridRendersOneRowPerOutputTypeWithOnlyTwoColumns() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String title = "Outputs Grid Rendering " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        String body = ceo.get("/app/reviews?tab=ideas&selectedId=" + idea.getId()).body();

        assertThat(body).doesNotContain("â"); // no source-encoding mojibake anywhere on the page
        assertThat(body).contains("3</span> Planned Outputs");
        assertThat(body).contains("Select the type(s) of content you are planning to create and where they will be published.");

        // Only two columns - no Reel Type or Output Description column header any more.
        assertThat(body).contains("<th>Output Type</th>").contains("<th>Platform / Channel</th>");
        assertThat(body).doesNotContain("Reel Type").doesNotContain("Output Description");

        // Photography/Video are fully retired - one row per Output Type, in enum order (REEL,
        // STORY, POST, LONG_VIDEO - OutputType's declared order, the single source of truth every
        // Planned Outputs UI iterates via OutputType.values() rather than each keeping its own
        // hardcoded row order), every one starting unchecked/disabled.
        assertThat(body).doesNotContain("PHOTOGRAPHY").doesNotContain("Photography")
                .doesNotContain("data-output-type=\"VIDEO\"").doesNotContain(">Video<");
        assertThat(body).contains("data-output-type=\"STORY\"");
        assertThat(body).contains("data-output-type=\"POST\"");
        assertThat(body).contains("data-output-type=\"REEL\"");
        assertThat(body).contains("data-output-type=\"LONG_VIDEO\"");
        int storyIdx = body.indexOf("data-output-type=\"STORY\"");
        int postIdx = body.indexOf("data-output-type=\"POST\"");
        int reelIdx = body.indexOf("data-output-type=\"REEL\"");
        int longVideoIdx = body.indexOf("data-output-type=\"LONG_VIDEO\"");
        assertThat(reelIdx).isLessThan(storyIdx);
        assertThat(storyIdx).isLessThan(postIdx);
        assertThat(postIdx).isLessThan(longVideoIdx);
        assertThat(body.split("reviews-output-row reviews-output-row-disabled", -1).length - 1).isEqualTo(4);

        // Type labels and their descriptions.
        assertThat(body).contains("<strong>Story</strong>").contains("Short vertical stories for engagement");
        assertThat(body).contains("<strong>Post</strong>").contains("Images / graphics / carousels for feeds");
        assertThat(body).contains("<strong>Reel</strong>").contains("Short vertical videos for social platforms");
        assertThat(body).contains("<strong>Long Video</strong>").contains("Long-form or horizontal videos");

        // No Reel Type checklist or Output Description textarea/char-counter anywhere in the grid,
        // including within the Reel row itself.
        assertThat(body).doesNotContain("reviews-output-reeltype-checkbox")
                .doesNotContain("reviews-output-description")
                .doesNotContain("VERY_SHORT").doesNotContain("/ 200 characters");
    }
}
