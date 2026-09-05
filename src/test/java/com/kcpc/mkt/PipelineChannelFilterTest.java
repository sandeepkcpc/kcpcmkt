package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.domain.Platform;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PlatformRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Content Pipeline: the inline filter is by Channel/Account (company_channels.channel_handle), not
 * by Platform. A row's Channels value is built de-duplicated by
 * {@code PipelineDashboardService} ({@code .distinct()} over the publication targets' channels), so
 * the two cases that used to be confusing both fall out of it: one Content ID published to
 * Instagram + YouTube + Facebook under ONE Channel/Account matches that channel once, and a Content
 * ID spanning two Channel/Accounts matches under either.
 *
 * <p>Only the filter dimension changed - the Platforms column, sorting, pagination, workflow and
 * status are all untouched, and the separate Publishing screen keeps its own filters.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PipelineChannelFilterTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlatformRepository platformRepository;
    @Autowired
    CompanyChannelRepository channelRepository;
    @Autowired
    PublicationTargetRepository publicationTargetRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PlannedOutputPublicationTargetMappingRepository mappingRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String user(TestApiClient ceo, String name, String email, String roleId) throws Exception {
        return ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"pipeline channel filter fixture\"}")
                .get("userId").asText();
    }

    /** An approved Content Plan carrying a distinctive SKU, so `q=` can scope the pipeline to just
     *  this test's own rows regardless of what else is in the database. */
    private ContentPlan plan(TestApiClient ceo, long unique, String label, String skuTag) throws Exception {
        String camId = user(ceo, "PipeChan Cam " + label + " " + unique,
                "pipechan-cam-" + label + "-" + unique + "@kcpcbandhani.local", CAMERA_PERSON_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline channel filter grant\"}");
        String pubId = user(ceo, "PipeChan Pub " + label + " " + unique,
                "pipechan-pub-" + label + "-" + unique + "@kcpcbandhani.local", PUBLISHER_ROLE_ID);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"pipeline channel filter grant\"}");
        String ideaId = ceo.postJson("/api/v1/ideas",
                "{\"title\":\"PipeChan " + label + " " + unique + "\"}").get("ideaId").asText();
        JsonNode approved = ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(20) + "\","
                        + "\"skuReference\":\"" + skuTag + "\","
                        + "\"folderLink\":\"https://drive.example.com/pipechan-" + label + "-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        assertThat(approved.get("status").asText()).isEqualTo("SA");
        Idea idea = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findByIdea(idea).orElseThrow();
    }

    CompanyChannel channel(long unique, String handle) {
        return channelRepository.save(new CompanyChannel(handle + "-" + unique));
    }

    /** One more Platform on an EXISTING Channel/Account. */
    PublicationTarget targetOn(CompanyChannel channel, long unique, String suffix) {
        Platform platform = platformRepository.save(new Platform("PipeChanPlatform" + unique + suffix));
        return publicationTargetRepository.save(new PublicationTarget(platform, channel, "Target " + unique + suffix));
    }

    void mapping(ContentPlan plan, PublicationTarget pt) {
        PlannedOutput output = plannedOutputRepository.save(new PlannedOutput(plan, OutputType.POST, null, null));
        mappingRepository.save(new PlannedOutputPublicationTargetMapping(output, pt));
    }

    private String pipeline(TestApiClient ceo, String skuTag, String channelHandle) throws Exception {
        String channelParam = channelHandle == null ? ""
                : "&channel=" + URLEncoder.encode(channelHandle, StandardCharsets.UTF_8);
        return ceo.get("/app/pipeline?size=50&q=" + skuTag + channelParam).body();
    }

    private static long countOccurrences(String haystack, String needle) {
        return haystack.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    // --- the filter control itself -------------------------------------------------------------
    @Test
    void theInlineFilterIsByChannelAndNoLongerOffersPlatforms() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "PIPECHAN-UI-" + unique;
        ContentPlan p = plan(ceo, unique, "UI", sku);
        mapping(p, targetOn(channel(unique, "pipechanUiChannel"), unique, "A"));

        String html = pipeline(ceo, sku, null);
        assertThat(html).contains("<select name=\"channel\">");
        assertThat(html).contains("All Channels");
        assertThat(html).as("the Platform filter control is gone").doesNotContain("<select name=\"platform\">");
        assertThat(html).as("no 'All Platforms' option remains").doesNotContain("All Platforms");
        // The dropdown offers the real channel handles present in the data.
        assertThat(html).contains("pipechanUiChannel-" + unique);
        // The Platforms COLUMN is untouched - only the filter dimension changed.
        assertThat(html).contains("data-col=\"platforms\"");
    }

    // --- 1. channel filter returns matching content ---------------------------------------------
    @Test
    void filteringByAChannelReturnsThatChannelsContentAndExcludesOtherChannels() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "PIPECHAN-MATCH-" + unique;
        CompanyChannel bandhani = channel(unique, "pipechanBandhani");
        CompanyChannel sikar = channel(unique, "pipechanSikar");

        ContentPlan onBandhani = plan(ceo, unique, "B", sku);
        mapping(onBandhani, targetOn(bandhani, unique, "B1"));
        ContentPlan onSikar = plan(ceo, unique + 1, "S", sku);
        mapping(onSikar, targetOn(sikar, unique, "S1"));

        String filtered = pipeline(ceo, sku, "pipechanBandhani-" + unique);
        assertThat(filtered).contains(onBandhani.getContentId());
        assertThat(filtered).as("content on another Channel/Account must be excluded")
                .doesNotContain(onSikar.getContentId());
        assertThat(filtered).contains("Showing 1 to 1 of 1 entries");
    }

    // --- 2. same content, several platforms, ONE channel -> appears once -------------------------
    @Test
    void oneContentIdOnThreePlatformsOfOneChannelAppearsExactlyOnce() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "PIPECHAN-MULTI-" + unique;
        CompanyChannel bandhani = channel(unique, "pipechanMultiPlat");
        ContentPlan p = plan(ceo, unique, "MP", sku);
        // Instagram + YouTube + Facebook, all under the SAME Channel/Account.
        mapping(p, targetOn(bandhani, unique, "Insta"));
        mapping(p, targetOn(bandhani, unique, "YouTube"));
        mapping(p, targetOn(bandhani, unique, "Facebook"));

        String filtered = pipeline(ceo, sku, "pipechanMultiPlat-" + unique);
        assertThat(filtered).contains(p.getContentId());
        assertThat(filtered).as("three platforms under one channel is still ONE pipeline row")
                .contains("Showing 1 to 1 of 1 entries");
        assertThat(countOccurrences(filtered, p.getContentId()))
                .as("the Content ID must not be repeated once per platform").isEqualTo(1);
    }

    // --- 3. same content on two channels -> found under either -----------------------------------
    @Test
    void oneContentIdOnTwoChannelsIsFoundUnderEitherChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "PIPECHAN-CROSS-" + unique;
        CompanyChannel bandhani = channel(unique, "pipechanCrossBandhani");
        CompanyChannel sikar = channel(unique, "pipechanCrossSikar");
        ContentPlan p = plan(ceo, unique, "X", sku);
        mapping(p, targetOn(bandhani, unique, "XB1"));
        mapping(p, targetOn(bandhani, unique, "XB2")); // second platform on the first channel
        mapping(p, targetOn(sikar, unique, "XS1"));

        for (String handle : new String[]{"pipechanCrossBandhani-" + unique, "pipechanCrossSikar-" + unique}) {
            String filtered = pipeline(ceo, sku, handle);
            assertThat(filtered).as("must be found when filtering %s", handle).contains(p.getContentId());
            assertThat(filtered).as("%s: still exactly one row", handle).contains("Showing 1 to 1 of 1 entries");
        }
    }

    // --- 4. "All Channels" shows the complete dataset --------------------------------------------
    @Test
    void allChannelsShowsEveryRowIncludingContentWithNoPublicationTarget() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "PIPECHAN-ALL-" + unique;
        ContentPlan a = plan(ceo, unique, "A", sku);
        mapping(a, targetOn(channel(unique, "pipechanAllOne"), unique, "A1"));
        ContentPlan b = plan(ceo, unique + 1, "B", sku);
        mapping(b, targetOn(channel(unique, "pipechanAllTwo"), unique, "B1"));
        // A third plan with no publication target at all - "All Channels" must still show it.
        ContentPlan c = plan(ceo, unique + 2, "C", sku);

        String all = pipeline(ceo, sku, null);
        assertThat(all).contains(a.getContentId()).contains(b.getContentId()).contains(c.getContentId());
        assertThat(all).contains("Showing 1 to 3 of 3 entries");

        // ...and narrowing to one channel is a strict subset of it.
        String narrowed = pipeline(ceo, sku, "pipechanAllOne-" + unique);
        assertThat(narrowed).contains(a.getContentId())
                .doesNotContain(b.getContentId()).doesNotContain(c.getContentId());
    }

    // --- other filters must keep working alongside the new one ------------------------------------
    @Test
    void channelFilterCombinesWithSearchAndDoesNotDisturbPagination() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String sku = "PIPECHAN-COMBO-" + unique;
        CompanyChannel shared = channel(unique, "pipechanCombo");
        ContentPlan a = plan(ceo, unique, "CA", sku);
        mapping(a, targetOn(shared, unique, "CA1"));
        ContentPlan b = plan(ceo, unique + 1, "CB", sku);
        mapping(b, targetOn(shared, unique, "CB1"));

        String handle = "pipechanCombo-" + unique;
        assertThat(pipeline(ceo, sku, handle)).contains("Showing 1 to 2 of 2 entries");

        // Pagination is unchanged by the filter swap.
        String page1 = ceo.get("/app/pipeline?q=" + sku + "&channel="
                + URLEncoder.encode(handle, StandardCharsets.UTF_8) + "&size=1&page=1").body();
        assertThat(page1).contains("Showing 1 to 1 of 2 entries");
        String page2 = ceo.get("/app/pipeline?q=" + sku + "&channel="
                + URLEncoder.encode(handle, StandardCharsets.UTF_8) + "&size=1&page=2").body();
        assertThat(page2).contains("Showing 2 to 2 of 2 entries");
    }
}
