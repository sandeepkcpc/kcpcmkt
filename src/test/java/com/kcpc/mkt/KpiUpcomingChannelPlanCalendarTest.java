package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ENG-097 Overview redesign - Upcoming Channel Plan calendar UX (a presentation-only enhancement;
 * see reports-kpi-calendar.js/ReportingMvcController#buildUpcomingChannelPlanJson). These tests
 * cover exactly what a JS-only harness cannot: the calendar's JSON data source
 * (#kpiUpcomingPlanData) actually reaches the page, is exactly what the existing list already
 * shows, and continues to obey Upcoming Channel Plan's existing business rules (exit-on-actual-
 * publication, current-state/not-date-ranged) - neither of which the distinct-Content-ID counting
 * change touched. The counting unit itself IS asserted here, because the calendar must show the
 * same distinct-Content-ID numbers the list does: see
 * {@link #calendarJsonContentIdsAreUniqueWithinAChannel()}. Grid rendering,
 * highlighting, month navigation, and the click-to-detail interaction are covered separately in
 * src/test/js/reports-kpi-calendar.test.js. Fixtures mirror KpiOverviewUpcomingChannelPlanTest's
 * exact pattern (repository-level Planned Output/Publication Target/Actual Publication Event rows).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class KpiUpcomingChannelPlanCalendarTest {

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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern PLAN_DATA_PATTERN =
            Pattern.compile("id=\"kpiUpcomingPlanData\">(.*?)</script>", Pattern.DOTALL);

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createIdeaAndUser(TestApiClient ceo, long unique) throws Exception {
        String email = "kpi-cal-cam-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"KpiCal cam\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"KPI calendar test fixture\"}");
        return user.get("userId").asText();
    }

    private ContentPlan approvePlan(TestApiClient ceo, long unique, LocalDate liveDate) throws Exception {
        String camId = createIdeaAndUser(ceo, unique);
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI calendar test fixture grant\"}");
        String pubEmail = "kpi-cal-pub-" + unique + "@kcpcbandhani.local";
        JsonNode pubUser = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"KpiCal pub\",\"email\":\"" + pubEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"KPI calendar test fixture\"}");
        String pubId = pubUser.get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"KPI calendar test fixture grant\"}");
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"KpiCal Idea " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/kpi-cal-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    PublicationTarget target(long unique, String suffix, String channelHandle) {
        return targetOn(channel(unique, channelHandle), unique, suffix);
    }

    /** A Channel/Account of its own, reusable across several Publication Targets. */
    CompanyChannel channel(long unique, String channelHandle) {
        return channelRepository.save(new CompanyChannel(channelHandle + "-" + unique));
    }

    /** One more Platform on an EXISTING Channel/Account - the "same content, several platforms,
     *  one channel" shape the distinct-Content-ID rule collapses to a single entry. */
    PublicationTarget targetOn(CompanyChannel channel, long unique, String suffix) {
        Platform platform = platformRepository.save(new Platform("KpiCalPlatform" + unique + suffix));
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

    private String overviewHtml(TestApiClient ceo, String queryString) throws Exception {
        return ceo.get("/app/reports/kpis?view=overview" + (queryString == null ? "" : queryString)).body();
    }

    private JsonNode planDataJson(String html) throws Exception {
        Matcher m = PLAN_DATA_PATTERN.matcher(html);
        assertThat(m.find()).as("kpiUpcomingPlanData JSON block must be present in the Overview response").isTrue();
        return OBJECT_MAPPER.readTree(m.group(1));
    }

    // --- 1: calendar icon is rendered, with the required accessible label -------------------------
    @Test
    void calendarIconIsRenderedWithTheRequiredAccessibleLabel() throws Exception {
        TestApiClient ceo = ceo();
        String html = overviewHtml(ceo, null);
        assertThat(html).contains("id=\"kpiUpcomingCalendarOpen\"");
        assertThat(html).contains("aria-label=\"Open planning calendar\"");
        assertThat(html).contains("id=\"kpiUpcomingCalendarOverlay\"");
        assertThat(html).contains("id=\"kpiCalendarGrid\"");
    }

    // --- 2: the existing Upcoming Channel Plan list remains rendered, unchanged, alongside the
    // new calendar markup for the same fixture -----------------------------------------------------
    @Test
    void existingListStillRendersAlongsideTheNewCalendarMarkup() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(unique % 300);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        mapping(plan, target(unique, "A", "kpicalListA"));

        String html = overviewHtml(ceo, null);
        // The pre-existing date-grouped list markup (untouched by this task).
        assertThat(html).contains("kpi-upcoming-date-group").contains("kpi-upcoming-channel-list");
        assertThat(html).contains(liveDate.toString());
        assertThat(html).contains("kpicalListA-" + unique);
        // The new calendar markup, present alongside it.
        assertThat(html).contains("id=\"kpiUpcomingCalendarOpen\"");
    }

    // --- 3: the planned date + channel/count are available to the calendar via its JSON data -----
    @Test
    void plannedDateAndChannelCountAreAvailableInTheCalendarJsonData() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(10 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        mapping(plan, target(unique, "B", "kpicalJsonB"));

        JsonNode groups = planDataJson(overviewHtml(ceo, null));
        JsonNode match = null;
        for (JsonNode g : groups) {
            if (g.get("plannedLiveDate").asText().equals(liveDate.toString())) {
                match = g;
                break;
            }
        }
        assertThat(match).as("the planned date should be present in the JSON data").isNotNull();
        boolean found = false;
        for (JsonNode ch : match.get("channels")) {
            if (ch.get("channelHandle").asText().equals("kpicalJsonB-" + unique)) {
                assertThat(ch.get("count").asLong()).isEqualTo(1);
                found = true;
            }
        }
        assertThat(found).isTrue();
    }

    // --- 6: multiple channels on the same date are each present, separately, in the JSON ----------
    @Test
    void multipleChannelsOnTheSameDateAreEachPresentSeparatelyInTheJson() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(20 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        mapping(plan, target(unique, "C", "kpicalMultiC"));
        mapping(plan, target(unique, "D", "kpicalMultiD"));

        JsonNode groups = planDataJson(overviewHtml(ceo, null));
        JsonNode match = null;
        for (JsonNode g : groups) {
            if (g.get("plannedLiveDate").asText().equals(liveDate.toString())) {
                match = g;
            }
        }
        assertThat(match).isNotNull();
        boolean hasC = false;
        boolean hasD = false;
        for (JsonNode ch : match.get("channels")) {
            if (ch.get("channelHandle").asText().equals("kpicalMultiC-" + unique)) { hasC = true; }
            if (ch.get("channelHandle").asText().equals("kpicalMultiD-" + unique)) { hasD = true; }
        }
        assertThat(hasC).isTrue();
        assertThat(hasD).isTrue();
    }

    // --- 9: the existing actual-publication exit rule is still respected in the JSON data ---------
    @Test
    void actualPublicationExitRuleIsStillRespectedInTheCalendarJsonData() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(30 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        var mapping = mapping(plan, target(unique, "E", "kpicalExitE"));

        assertThat(planDataAsString(overviewHtml(ceo, null))).contains("kpicalExitE-" + unique);

        User publisher = userRepository.findAll().stream().findFirst().orElseThrow();
        recordActualPublication(plan, mapping, publisher, Instant.now());

        // Exits the JSON immediately, exactly like the existing list already does - same
        // outstanding-mapping definition, never a second one for the calendar.
        assertThat(planDataAsString(overviewHtml(ceo, null))).doesNotContain("kpicalExitE-" + unique);
    }

    // --- 10: the calendar's JSON data is NOT filtered by the selected KPI Date Range - a future
    // outstanding date remains present even when the selected range ends well before it -----------
    @Test
    void calendarJsonDataIgnoresTheSelectedKpiDateRangeCurrentStateUnaffected() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(40 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        mapping(plan, target(unique, "F", "kpicalRangeF"));

        // A narrow, already-past Date Range that does not remotely cover liveDate.
        String narrowRange = "&startDate=" + LocalDate.now().minusDays(2) + "&endDate=" + LocalDate.now().minusDays(1);
        String html = overviewHtml(ceo, narrowRange);

        assertThat(planDataAsString(html)).contains("kpicalRangeF-" + unique);
        JsonNode groups = planDataJson(html);
        boolean found = false;
        for (JsonNode g : groups) {
            if (g.get("plannedLiveDate").asText().equals(liveDate.toString())) { found = true; }
        }
        assertThat(found).as("Upcoming Channel Plan is current-state - the selected historical Date Range must not remove it").isTrue();
    }

    // --- Content Details enhancement: contentIds are captured from the SAME already-iterated
    // mapping rows (no new query) - count always equals contentIds.size(). ------------------------
    @Test
    void contentIdsAreExposedInTheJsonDataMatchingTheExistingCount() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(50 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        mapping(plan, target(unique, "G", "kpicalContentG"));

        JsonNode groups = planDataJson(overviewHtml(ceo, null));
        JsonNode channel = findChannel(groups, liveDate, "kpicalContentG-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong()).isEqualTo(1);
        assertThat(channel.get("contentIds")).isNotNull();
        assertThat(channel.get("contentIds").size()).isEqualTo(1);
        assertThat(channel.get("contentIds").get(0).asText()).isEqualTo(plan.getContentId());
    }

    // --- Two plans, same date, same channel: both Content Plan IDs captured, count matches. -------
    @Test
    void multiplePlansOnTheSameDateAndChannelEachContributeTheirOwnContentId() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(60 + unique % 200);
        PublicationTarget sharedTarget = target(unique, "H", "kpicalMultiPlanH");
        ContentPlan planOne = approvePlan(ceo, unique, liveDate);
        mapping(planOne, sharedTarget);
        ContentPlan planTwo = approvePlan(ceo, unique + 1, liveDate);
        mapping(planTwo, sharedTarget);

        JsonNode groups = planDataJson(overviewHtml(ceo, null));
        JsonNode channel = findChannel(groups, liveDate, "kpicalMultiPlanH-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong()).isEqualTo(2);
        java.util.List<String> ids = new java.util.ArrayList<>();
        channel.get("contentIds").forEach(n -> ids.add(n.asText()));
        assertThat(ids).containsExactlyInAnyOrder(planOne.getContentId(), planTwo.getContentId());
    }

    /** Distinct-Content-ID counting, as the calendar sees it: one Content ID on three Platforms of
     *  ONE Channel/Account is count 1 and a single-entry Content Details list - so the calendar
     *  detail pane can never render the same Content ID three times. */
    @Test
    void calendarJsonContentIdsAreUniqueWithinAChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(unique % 150);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel chan = channel(unique, "kpicalUniqueChan");
        mapping(plan, targetOn(chan, unique, "UniInsta"));
        mapping(plan, targetOn(chan, unique, "UniYouTube"));
        mapping(plan, targetOn(chan, unique, "UniFacebook"));

        JsonNode channel = findChannel(planDataJson(overviewHtml(ceo, null)), liveDate,
                "kpicalUniqueChan-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong())
                .as("3 platforms of one Channel/Account, one Content ID -> 1").isEqualTo(1);
        assertThat(channel.get("contentIds").size())
                .as("Content Details must list the Content ID once, not once per platform").isEqualTo(1);
        assertThat(channel.get("contentIds").get(0).asText()).isEqualTo(plan.getContentId());
    }

    /** The same Content ID on two different Channel/Accounts stays two separate entries in the JSON
     *  - one under each channel - so the calendar's per-channel counts remain independent. */
    @Test
    void calendarJsonKeepsTheSameContentIdSeparatePerChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(200 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "kpicalCrossBandhani");
        CompanyChannel sikar = channel(unique, "kpicalCrossSikar");
        mapping(plan, targetOn(bandhani, unique, "CrossA"));
        mapping(plan, targetOn(bandhani, unique, "CrossB")); // second platform of the same channel
        mapping(plan, targetOn(sikar, unique, "CrossC"));

        JsonNode groups = planDataJson(overviewHtml(ceo, null));
        JsonNode first = findChannel(groups, liveDate, "kpicalCrossBandhani-" + unique);
        JsonNode second = findChannel(groups, liveDate, "kpicalCrossSikar-" + unique);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.get("count").asLong()).isEqualTo(1);
        assertThat(first.get("contentIds").size()).isEqualTo(1);
        assertThat(second.get("count").asLong()).isEqualTo(1);
        assertThat(second.get("contentIds").size()).isEqualTo(1);
        assertThat(first.get("contentIds").get(0).asText())
                .as("it is genuinely the same content, counted once under each Channel/Account")
                .isEqualTo(second.get("contentIds").get(0).asText());
    }

    // --- 12: adding Content IDs to the JSON does not alter the existing list's own rendered data --
    @Test
    void calendarDoesNotAlterTheExistingListData() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(70 + unique % 200);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        mapping(plan, target(unique, "I", "kpicalUnalteredI"));

        String html = overviewHtml(ceo, null);
        // Existing list still shows exactly "1" for this channel - the same count the pre-existing
        // aggregation always produced - not a size influenced by the new contentIds collection.
        assertThat(html).contains("kpicalUnalteredI-" + unique);
        JsonNode channel = findChannel(planDataJson(html), liveDate, "kpicalUnalteredI-" + unique);
        assertThat(channel.get("count").asLong()).isEqualTo(1);
    }

    // --- Legend markup (Planned date / Selected date) is rendered inside the calendar popover -----
    @Test
    void calendarLegendIsRendered() throws Exception {
        TestApiClient ceo = ceo();
        String html = overviewHtml(ceo, null);
        assertThat(html).contains("kpi-calendar-legend");
        assertThat(html).contains("Planned date");
        assertThat(html).contains("Selected date");
    }

    // --- Month navigation controls exist and are real, keyboard-focusable <button> elements --------
    @Test
    void monthNavigationAndCloseControlsAreRealButtonsForKeyboardAccessibility() throws Exception {
        TestApiClient ceo = ceo();
        String html = overviewHtml(ceo, null);
        assertThat(html).contains("<button type=\"button\" class=\"btn-outline kpi-calendar-prev\" aria-label=\"Previous month\">");
        assertThat(html).contains("<button type=\"button\" class=\"btn-outline kpi-calendar-next\" aria-label=\"Next month\">");
        assertThat(html).contains("id=\"kpiUpcomingCalendarClose\"").contains("aria-label=\"Close\"");
    }

    private JsonNode findChannel(JsonNode groups, LocalDate liveDate, String channelHandle) {
        for (JsonNode g : groups) {
            if (!g.get("plannedLiveDate").asText().equals(liveDate.toString())) {
                continue;
            }
            for (JsonNode ch : g.get("channels")) {
                if (ch.get("channelHandle").asText().equals(channelHandle)) {
                    return ch;
                }
            }
        }
        return null;
    }

    private String planDataAsString(String html) {
        Matcher m = PLAN_DATA_PATTERN.matcher(html);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
