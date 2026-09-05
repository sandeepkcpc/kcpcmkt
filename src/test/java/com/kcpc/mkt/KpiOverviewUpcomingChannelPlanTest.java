package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
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
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.support.TestApiClient;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.repository.WorkflowInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-097 (Overview redesign) - Upcoming Channel Plan. §25 of the implementation spec. Fixtures
 * are built directly through the repository layer for the Planned Output / Publication Target /
 * Actual Publication Event pieces (rather than the full Idea-Approve "Planned Outputs" JSON grid,
 * which is a UI-layer convenience over the same underlying rows) - these are the exact rows the
 * metric is computed from, verified directly against the schema during implementation.
 *
 * <p>Counting unit under test: DISTINCT content_plans.content_id per (Planned Live Date,
 * Channel/Account). Eligibility is still per planned_output_publication_target_mappings row with no
 * matching actual_publication_events row; only the aggregation over the surviving rows is
 * by distinct Content ID (it was previously a raw row count, which double-counted one piece of
 * content planned on several platforms of the same Channel/Account).
 *
 * <p>Every Planned Live Date used is a synthetic far-future date unique to each test run, so this
 * suite's own fixtures can never collide with other tests' or be confused with real data.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KpiOverviewUpcomingChannelPlanTest {

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
    @Autowired
    ActualPublicationEventRepository eventRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    WorkflowInstanceRepository workflowInstanceRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createIdeaAndUser(TestApiClient ceo, long unique) throws Exception {
        String email = "kpi-upcoming-cam-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"KpiUpcoming cam\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"KPI upcoming plan test fixture\"}");
        return user.get("userId").asText();
    }

    /** Approved Content Plan with the given Planned Live Date - Planned Outputs/mappings are added
     *  separately, directly through the repository layer (see class javadoc). */
    private ContentPlan approvePlan(TestApiClient ceo, long unique, LocalDate liveDate) throws Exception {
        String camId = createIdeaAndUser(ceo, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI upcoming plan test fixture grant\"}");
        String pubEmail = "kpi-upcoming-pub-" + unique + "@kcpcbandhani.local";
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"KpiUpcoming pub\",\"email\":\"" + pubEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"KPI upcoming plan test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI upcoming plan test fixture grant\"}");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiUpcoming Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-upcoming-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    // Each repository .save() call below is already its own atomic transaction (Spring Data JPA's
    // SimpleJpaRepository wraps every CRUD method) - no method-level @Transactional needed (and it
    // would be a no-op here regardless: self-invoked instance methods on the test class are never
    // proxied by Spring's transaction interceptor).
    PublicationTarget target(long unique, String suffix, String channelHandle) {
        return targetOn(channel(unique, channelHandle), unique, suffix);
    }

    /** A Channel/Account of its own, reusable across several Publication Targets - the fixture the
     *  distinct-Content-ID tests need, since "same channel, several platforms" is exactly the shape
     *  that used to be over-counted. */
    CompanyChannel channel(long unique, String channelHandle) {
        return channelRepository.save(new CompanyChannel(channelHandle + "-" + unique));
    }

    /** One more Platform on an EXISTING Channel/Account - i.e. one more Publication Target the same
     *  content can be planned on without becoming a second channel. */
    PublicationTarget targetOn(CompanyChannel channel, long unique, String suffix) {
        Platform platform = platformRepository.save(new Platform("KpiUpcomingPlatform" + unique + suffix));
        return publicationTargetRepository.save(new PublicationTarget(platform, channel, "Target " + unique + suffix));
    }

    PlannedOutputPublicationTargetMapping mapping(ContentPlan plan, PublicationTarget pt) {
        PlannedOutput output = plannedOutputRepository.save(new PlannedOutput(plan, OutputType.POST, null, null));
        return mappingRepository.save(new PlannedOutputPublicationTargetMapping(output, pt));
    }

    void recordActualPublication(ContentPlan plan, PlannedOutputPublicationTargetMapping mapping, User publisher,
                                  Instant actualAt) {
        eventRepository.save(new ActualPublicationEvent(plan, mapping.getPlannedOutput(), mapping.getPublicationTarget(),
                PublicationEventType.ORIGINAL, actualAt, "https://evidence.example.com/x", publisher));
    }

    private String overviewHtml(TestApiClient ceo) throws Exception {
        return ceo.get("/app/reports/kpis?view=overview").body();
    }

    /** The number the Upcoming Channel Plan list itself renders for one Channel/Account - read back
     *  out of the real rendered markup (reports-kpi-overview.jspf's channel-name span immediately
     *  followed by its count span), so these assertions are about what a CEO actually sees on the
     *  screen, not only about what the DTO happens to hold. Fails loudly if the channel is absent. */
    private long renderedCount(String html, String channelHandle) {
        Matcher m = Pattern.compile(Pattern.quote(channelHandle)
                + "</span>\\s*<span class=\"kpi-upcoming-channel-count\">(\\d+)</span>").matcher(html);
        assertThat(m.find()).as("Channel/Account '%s' must be present in the rendered Upcoming Channel Plan list",
                channelHandle).isTrue();
        return Long.parseLong(m.group(1));
    }

    // --- grouping by Planned Live Date + Channel/Account ---
    @Test
    void groupsOutstandingTargetsByPlannedLiveDateThenChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(unique % 300);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        PublicationTarget targetA = target(unique, "A", "kpicwoChannelA");
        PublicationTarget targetB = target(unique, "B", "kpicwoChannelB");
        mapping(plan, targetA);
        mapping(plan, targetB);

        String html = overviewHtml(ceo);
        assertThat(html).contains(liveDate.toString());
        assertThat(html).contains("kpicwoChannelA-" + unique);
        assertThat(html).contains("kpicwoChannelB-" + unique);
        // Never grouped by Platform - the platform name must not appear as a group label.
        assertThat(html).doesNotContain("KpiUpcomingPlatform" + unique + "A");
    }

    // --- multiple channels on the same content, per the existing governed mapping. Distinct-
    // Content-ID counting: the second Planned Output also targeting channel A is the SAME piece of
    // content, so channel A is 1, not 2 (it was 2 under the retired mapping-row counting unit). ---
    @Test
    void multipleChannelsOnSameContentEachCountedUnderTheirOwnChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(50 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        PublicationTarget targetA = target(unique, "A", "kpicwoMultiA");
        PublicationTarget targetB = target(unique, "B", "kpicwoMultiB");
        mapping(plan, targetA);
        mapping(plan, targetB);
        mapping(plan, targetA); // a second output also targeting channel A -> still ONE Content ID

        String html = overviewHtml(ceo);
        int dateIdx = html.indexOf(liveDate.toString());
        assertThat(dateIdx).isGreaterThan(-1);
        String window = html.substring(dateIdx, Math.min(html.length(), dateIdx + 600));
        assertThat(window).contains("kpicwoMultiA-" + unique).contains("kpicwoMultiB-" + unique);
        assertThat(renderedCount(html, "kpicwoMultiA-" + unique))
                .as("two Planned Outputs of ONE Content ID on one Channel/Account is 1 piece of content").isEqualTo(1);
        assertThat(renderedCount(html, "kpicwoMultiB-" + unique)).isEqualTo(1);
    }

    // --- actual publication before Planned Live Date removes the item immediately ---
    @Test
    void actualPublicationBeforePlannedLiveDateRemovesItImmediately() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(100 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        PublicationTarget target = target(unique, "X", "kpicwoEarly");
        var mapping = mapping(plan, target);

        assertThat(overviewHtml(ceo)).contains("kpicwoEarly-" + unique);

        User publisher = userRepository.findAll().stream().findFirst().orElseThrow();
        recordActualPublication(plan, mapping, publisher, Instant.now()); // published now, long before liveDate

        // Exits immediately - never waits until the Planned Live Date itself.
        assertThat(overviewHtml(ceo)).doesNotContain("kpicwoEarly-" + unique);
    }

    // --- unpublished future target remains ---
    @Test
    void unpublishedFutureTargetRemainsListed() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(150 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        PublicationTarget target = target(unique, "Y", "kpicwoRemains");
        mapping(plan, target);

        assertThat(overviewHtml(ceo)).contains("kpicwoRemains-" + unique);
    }

    // --- ordering by date ascending ---
    @Test
    void groupsAreOrderedByPlannedLiveDateAscending() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate laterDate = LocalDate.now().plusYears(4).plusDays(unique % 200);
        LocalDate earlierDate = laterDate.minusDays(5);
        ContentPlan laterPlan = approvePlan(ceo, unique, laterDate);
        ContentPlan earlierPlan = approvePlan(ceo, unique + 1, earlierDate);
        mapping(laterPlan, target(unique, "L", "kpicwoLater"));
        mapping(earlierPlan, target(unique, "E", "kpicwoEarlier"));

        String html = overviewHtml(ceo);
        int earlierIdx = html.indexOf(earlierDate.toString());
        int laterIdx = html.indexOf(laterDate.toString());
        assertThat(earlierIdx).isGreaterThan(-1);
        assertThat(laterIdx).isGreaterThan(-1);
        assertThat(earlierIdx).isLessThan(laterIdx);
    }

    // ==================================================================================================
    // Distinct-Content-ID counting. The counting unit is DISTINCT content_plans.content_id per
    // (Planned Live Date, Channel/Account) - NOT the number of surviving
    // planned_output_publication_target_mappings rows, which double-counted multi-platform content.
    // ==================================================================================================

    /** The headline rule: one Content ID on Instagram + YouTube + Facebook, all under the SAME
     *  Channel/Account, is 1 - never 3. */
    @Test
    void sameContentIdAcrossMultiplePlatformsOfOneChannelCountsOnce() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(unique % 150);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "kpicwoOneChan");
        // Three different Platforms, one single Channel/Account -> three mapping rows, one content.
        mapping(plan, targetOn(bandhani, unique, "Insta"));
        mapping(plan, targetOn(bandhani, unique, "YouTube"));
        mapping(plan, targetOn(bandhani, unique, "Facebook"));

        assertThat(renderedCount(overviewHtml(ceo), "kpicwoOneChan-" + unique))
                .as("3 platforms of one Channel/Account carrying ONE Content ID must render 1, not 3")
                .isEqualTo(1);
    }

    /** The same Content ID planned on two different Channel/Accounts is two genuinely separate
     *  publication commitments - one count under each, never collapsed into a single global 1. */
    @Test
    void sameContentIdOnDifferentChannelsCountsOnceUnderEachChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(200 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "kpicwoSplitBandhani");
        CompanyChannel sikar = channel(unique, "kpicwoSplitSikar");
        mapping(plan, targetOn(bandhani, unique, "SplitA"));
        mapping(plan, targetOn(bandhani, unique, "SplitB")); // second platform, same channel
        mapping(plan, targetOn(sikar, unique, "SplitC"));

        String html = overviewHtml(ceo);
        assertThat(renderedCount(html, "kpicwoSplitBandhani-" + unique)).isEqualTo(1);
        assertThat(renderedCount(html, "kpicwoSplitSikar-" + unique)).isEqualTo(1);
    }

    /** Distinctness must not flatten genuinely different content: three separate Content IDs on one
     *  Channel/Account and one date are 3. */
    @Test
    void differentContentIdsOnTheSameChannelEachCountSeparately() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(unique % 100);
        CompanyChannel shared = channel(unique, "kpicwoThreeIds");
        PublicationTarget sharedTarget = targetOn(shared, unique, "Shared");
        mapping(approvePlan(ceo, unique, liveDate), sharedTarget);
        mapping(approvePlan(ceo, unique + 1, liveDate), sharedTarget);
        mapping(approvePlan(ceo, unique + 2, liveDate), sharedTarget);

        assertThat(renderedCount(overviewHtml(ceo), "kpicwoThreeIds-" + unique))
                .as("three distinct Content IDs on one Channel/Account must render 3").isEqualTo(3);
    }

    /** Eligibility is still per mapping row, but the count is per Content ID - so publishing ONE of
     *  a Content ID's platforms leaves the content still outstanding on that Channel/Account (still
     *  1), and only publishing ALL of them removes it. The documented consequence of the rule. */
    @Test
    void contentStaysCountedUntilEveryTargetOnThatChannelIsPublished() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(120 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel chan = channel(unique, "kpicwoPartial");
        var first = mapping(plan, targetOn(chan, unique, "PartOne"));
        var second = mapping(plan, targetOn(chan, unique, "PartTwo"));

        assertThat(renderedCount(overviewHtml(ceo), "kpicwoPartial-" + unique)).isEqualTo(1);

        User publisher = userRepository.findAll().stream().findFirst().orElseThrow();
        recordActualPublication(plan, first, publisher, Instant.now());
        assertThat(renderedCount(overviewHtml(ceo), "kpicwoPartial-" + unique))
                .as("one platform published, one still pending -> the content is still outstanding, still 1")
                .isEqualTo(1);

        recordActualPublication(plan, second, publisher, Instant.now());
        assertThat(overviewHtml(ceo))
                .as("every target published -> the Channel/Account leaves the list entirely")
                .doesNotContain("kpicwoPartial-" + unique);
    }

    /** Terminal-status exclusion is unchanged by the counting-unit change: a Cancelled plan's
     *  obligations are void. Cancelled through the real governed admin action, not by poking state. */
    @Test
    void cancelledContentIsStillExcluded() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(220 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel chan = channel(unique, "kpicwoCancelled");
        mapping(plan, targetOn(chan, unique, "CanA"));
        mapping(plan, targetOn(chan, unique, "CanB"));

        assertThat(renderedCount(overviewHtml(ceo), "kpicwoCancelled-" + unique)).isEqualTo(1);

        ceo.post("/api/v1/content-plans/" + plan.getId() + "/cancel",
                "{\"reason\":\"KPI upcoming plan test: cancelled-exclusion regression\"}");

        assertThat(overviewHtml(ceo)).doesNotContain("kpicwoCancelled-" + unique);
    }

    /** Same for a Completed plan - nothing is left "still to go live". Driven straight through
     *  WorkflowInstance#transitionTo (the sanctioned mutator) rather than replaying the whole
     *  Shoot -> Edit -> Publish chain: what is under test here is this KPI's own status filter. */
    @Test
    void completedContentIsStillExcluded() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(320 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel chan = channel(unique, "kpicwoCompleted");
        mapping(plan, targetOn(chan, unique, "CompA"));
        mapping(plan, targetOn(chan, unique, "CompB"));

        assertThat(renderedCount(overviewHtml(ceo), "kpicwoCompleted-" + unique)).isEqualTo(1);

        WorkflowInstance instance = workflowInstanceRepository.findById(plan.getWorkflowInstance().getId())
                .orElseThrow();
        instance.transitionTo(WorkflowStatus.COMP);
        workflowInstanceRepository.save(instance);

        assertThat(overviewHtml(ceo)).doesNotContain("kpicwoCompleted-" + unique);
    }

    // --- empty state (isolated: a channel that never had any outstanding mapping must never appear) ---
    @Test
    void aChannelWithNoOutstandingMappingsNeverAppears() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        target(unique, "Z", "kpicwoNeverUsed"); // created but never mapped to any Planned Output
        assertThat(overviewHtml(ceo)).doesNotContain("kpicwoNeverUsed-" + unique);
    }
}
