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
 * idea-detail.jsp (/app/ideas/{id}) -> Review Decision -> Approve: the standalone page's own
 * Planned Outputs section uses the same one-row-per-Output-Type grid as Reviews -> Ideas ->
 * Approve (see ReviewsIdeaOutputFormRenderingTest) - Reel/Story/Post/Long Video, in OutputType's
 * declared order (V31 redesign; PHOTOGRAPHY/VIDEO retired), only two columns (Output Type,
 * Platform/Channel), no Reel Type sub-selector and no Output Description field anywhere. IdeaMvcController#decide reads the
 * grid's serialized state from an {@code outputsJson} form param exactly the way
 * ReviewsMvcController#decideIdea already does - see MvcScreenSmokeTest for the full
 * submit-and-create-PlannedOutput path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdeaDetailOutputFormRenderingTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;

    @Test
    void ideaDetailPlannedOutputsGridRendersOneRowPerOutputTypeWithOnlyTwoColumns() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");

        String title = "Idea Detail Outputs Grid " + unique;
        assertThat(ceo.postForm("/app/ideas", Map.of("title", title)).statusCode()).isEqualTo(302);
        Idea idea = ideaRepository.findAllByOrderBySubmittedAtDesc().stream()
                .filter(i -> i.getTitle().equals(title)).findFirst().orElseThrow();

        String body = ceo.get("/app/ideas/" + idea.getId()).body();

        assertThat(body).doesNotContain("outputTitleDescription\" name=\"outputTitleDescription"); // old single-select field is gone
        assertThat(body).contains("Planned Outputs");
        assertThat(body).contains("Select the type(s) of content you are planning to create and where they will be published.");
        assertThat(body).contains("id=\"ideaOutputsJsonField\"");

        // Only two columns - no Reel Type or Output Description column header any more.
        assertThat(body).contains("<th>Output Type</th>").contains("<th>Platform / Channel</th>");
        assertThat(body).doesNotContain("Reel Type").doesNotContain("Output Description");

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

        assertThat(body).doesNotContain("reviews-output-reeltype-checkbox")
                .doesNotContain("reviews-output-description")
                .doesNotContain("VERY_SHORT").doesNotContain("/ 200 characters");
    }
}
