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
 * ENG-097 (Overview redesign) - Upcoming Channel Plan. §25 of the implementation spec. Fixtures
 * are built directly through the repository layer for the Planned Output / Publication Target /
 * Actual Publication Event pieces (rather than the full Idea-Approve "Planned Outputs" JSON grid,
 * which is a UI-layer convenience over the same underlying rows) - this is the exact governed
 * counting unit itself (one planned_output_publication_target_mappings row with no matching
 * actual_publication_events row), verified directly against the schema during implementation.
 * Every Planned Live Date used is a synthetic far-future date unique to each test run, so this
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
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + liveDate + "\","
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
        Platform platform = platformRepository.save(new Platform("KpiUpcomingPlatform" + unique + suffix));
        CompanyChannel channel = channelRepository.save(new CompanyChannel(channelHandle + "-" + unique));
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

    // --- multiple targets (channels) on the same content, per the existing governed mapping ---
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
        mapping(plan, targetA); // a second output also targeting channel A -> count 2 for A

        String html = overviewHtml(ceo);
        int dateIdx = html.indexOf(liveDate.toString());
        assertThat(dateIdx).isGreaterThan(-1);
        String window = html.substring(dateIdx, Math.min(html.length(), dateIdx + 600));
        assertThat(window).contains("kpicwoMultiA-" + unique).contains("kpicwoMultiB-" + unique);
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

    // --- empty state (isolated: a channel that never had any outstanding mapping must never appear) ---
    @Test
    void aChannelWithNoOutstandingMappingsNeverAppears() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        target(unique, "Z", "kpicwoNeverUsed"); // created but never mapped to any Planned Output
        assertThat(overviewHtml(ceo)).doesNotContain("kpicwoNeverUsed-" + unique);
    }
}
