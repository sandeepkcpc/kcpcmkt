package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Idea Review &amp; Planning -&gt; Schedule -&gt; Planned Live Date calendar.
 *
 * <p>The calendar highlights dates that already have planned content and shows a per-channel
 * breakdown when one is clicked. Its numbers are NOT computed for it: the Planning screen embeds the
 * very same {@code List<UpcomingPlanDateGroup>} that {@code UpcomingChannelPlanService} produces for
 * the KPI Dashboard's Upcoming Channel Plan, so the two screens can never disagree. That shared
 * counting unit is DISTINCT {@code content_plans.content_id} per (Planned Live Date,
 * Channel/Account) - never platform-wise, never publication-target-wise.
 *
 * <p>These tests assert what a JS-only harness cannot: that the JSON actually reaches the rendered
 * Planning screen, and that the numbers in it are byte-for-byte the ones the KPI Dashboard shows for
 * the same fixture. Grid rendering, highlighting, the detail panel and the past-date rule are
 * covered in src/test/js/kcpc-date-picker*.test.js.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlannedLiveDateCalendarTest {

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
    private static final Pattern PLAN_DATA = Pattern.compile(
            "id=\"kcpcPlannedLiveDatePlanData\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern KPI_PLAN_DATA = Pattern.compile(
            "id=\"kpiUpcomingPlanData\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern SHOOT_PLAN_DATA = Pattern.compile(
            "id=\"kcpcPlannedShootPlanData\">(.*?)</script>", Pattern.DOTALL);
    private static final Pattern EDIT_PLAN_DATA = Pattern.compile(
            "id=\"kcpcPlannedEditPlanData\">(.*?)</script>", Pattern.DOTALL);

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    /** An Idea left at Pending Approval, so the Reviews Ideas tab renders its Planning form (and
     *  therefore the calendar's data block) for it. */
    private String pendingIdea(TestApiClient ceo, long unique) throws Exception {
        return ceo.postJson("/api/v1/ideas", "{\"title\":\"PlannedLiveDateCal Idea " + unique + "\"}")
                .get("ideaId").asText();
    }

    private ContentPlan approvePlan(TestApiClient ceo, long unique, LocalDate liveDate) throws Exception {
        String camEmail = "plcal-cam-" + unique + "@kcpcbandhani.local";
        String camId = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"PlCal cam\",\"email\":\"" + camEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + CAMERA_PERSON_ROLE_ID + "\",\"creationReason\":\"Planned Live Date calendar fixture\"}")
                .get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + camId + "\",\"permission\":\"PERM_18_SHOOT_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"Planned Live Date calendar fixture grant\"}");
        String pubEmail = "plcal-pub-" + unique + "@kcpcbandhani.local";
        String pubId = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"PlCal pub\",\"email\":\"" + pubEmail + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + PUBLISHER_ROLE_ID + "\",\"creationReason\":\"Planned Live Date calendar fixture\"}")
                .get("userId").asText();
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + pubId + "\",\"permission\":\"PERM_08_PUBLISHING_EXECUTION\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"Planned Live Date calendar fixture grant\"}");
        String ideaId = pendingIdea(ceo, unique);
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0,\"modelMark\":1.0,\"planning\":{"
                        + "\"contentPriority\":\"HIGH\",\"plannedLiveDate\":\"" + liveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/plcal-" + unique + "\","
                        + "\"camerapersonUserIds\":[\"" + camId + "\"],\"publisherUserIds\":[\"" + pubId + "\"]}}");
        return contentPlanRepository.findByIdea(ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow())
                .orElseThrow();
    }

    CompanyChannel channel(long unique, String handle) {
        return channelRepository.save(new CompanyChannel(handle + "-" + unique));
    }

    /** One more Platform on an EXISTING Channel/Account - the "same content, several platforms, one
     *  channel" shape the distinct-Content-ID rule collapses to a single entry. */
    PublicationTarget targetOn(CompanyChannel channel, long unique, String suffix) {
        Platform platform = platformRepository.save(new Platform("PlCalPlatform" + unique + suffix));
        return publicationTargetRepository.save(new PublicationTarget(platform, channel, "Target " + unique + suffix));
    }

    void mapping(ContentPlan plan, PublicationTarget pt) {
        PlannedOutput output = plannedOutputRepository.save(new PlannedOutput(plan, OutputType.POST, null, null));
        mappingRepository.save(new PlannedOutputPublicationTargetMapping(output, pt));
    }

    /** The Reviews Ideas tab, rendering the Planning form for a Pending-Approval idea. */
    private String planningScreenHtml(TestApiClient ceo, String ideaId) throws Exception {
        return ceo.get("/app/reviews?tab=ideas&ideaId=" + ideaId).body();
    }

    private JsonNode planData(String html) throws Exception {
        Matcher m = PLAN_DATA.matcher(html);
        assertThat(m.find()).as("the Planned Live Date calendar's JSON data block must be present").isTrue();
        return OBJECT_MAPPER.readTree(m.group(1).replace("\\u003c", "<"));
    }

    private JsonNode blockData(String html, Pattern pattern, String what) throws Exception {
        Matcher m = pattern.matcher(html);
        assertThat(m.find()).as("the %s calendar's JSON data block must be present", what).isTrue();
        return OBJECT_MAPPER.readTree(m.group(1).replace("\\u003c", "<"));
    }

    private JsonNode findChannel(JsonNode groups, LocalDate date, String handle) {
        for (JsonNode g : groups) {
            if (!g.get("plannedLiveDate").asText().equals(date.toString())) {
                continue;
            }
            for (JsonNode ch : g.get("channels")) {
                if (ch.get("channelHandle").asText().equals(handle)) {
                    return ch;
                }
            }
        }
        return null;
    }

    // --- the data block actually reaches the Planning screen ---------------------------------------
    /** All three Schedule date fields opt in, each naming its OWN dataset - and nothing else on the
     *  Planning form does. The kind name is what routes a field to its data block and its wording,
     *  so a mix-up here would silently show one calendar's numbers under another's label. */
    @Test
    void planningScreenRendersTheCalendarDataBlockAndOptsTheScheduleFieldsIn() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String html = planningScreenHtml(ceo, pendingIdea(ceo, unique));

        assertThat(html).contains("id=\"kcpcPlannedLiveDatePlanData\"");
        assertFieldOptsIn(html, "reviewsIdeaPlannedLiveDate", "live");
        assertFieldOptsIn(html, "reviewsIdeaShootDate", "shoot");
        assertFieldOptsIn(html, "reviewsIdeaEditDate", "edit");

        // The opt-in stays confined to the Schedule section's three date fields - the Idea-queue
        // filter date inputs on the same page must never pick up a planning calendar.
        for (String other : new String[]{"rvIdeaDateFrom", "rvIdeaDateTo"}) {
            Matcher m = Pattern.compile("id=\"" + other + "\"[^>]*>").matcher(html);
            if (m.find()) {
                assertThat(m.group()).as("%s must NOT be opted in", other).doesNotContain("data-kcpc-calendar");
            }
        }
    }

    private void assertFieldOptsIn(String html, String fieldId, String expectedKind) {
        Matcher m = Pattern.compile("id=\"" + fieldId + "\"[^>]*>").matcher(html);
        assertThat(m.find()).as("%s must be rendered", fieldId).isTrue();
        assertThat(m.group()).as("%s must opt into the '%s' calendar", fieldId, expectedKind)
                .contains("data-kcpc-calendar=\"" + expectedKind + "\"");
        assertThat(m.group()).as("%s must keep its past-date restriction", fieldId).contains("min=");
    }

    // --- same Content ID, multiple platforms, ONE channel -> 1 -------------------------------------
    @Test
    void sameContentIdAcrossPlatformsOfOneChannelCountsOnce() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(unique % 150);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalOneChan");
        mapping(plan, targetOn(bandhani, unique, "Insta"));
        mapping(plan, targetOn(bandhani, unique, "YouTube"));
        mapping(plan, targetOn(bandhani, unique, "Facebook"));

        JsonNode channel = findChannel(planData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 1))),
                liveDate, "plcalOneChan-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong())
                .as("Instagram + YouTube + Facebook under one Channel/Account is ONE piece of content")
                .isEqualTo(1);
        assertThat(channel.get("contentIds").size()).isEqualTo(1);
        assertThat(channel.get("contentIds").get(0).asText()).isEqualTo(plan.getContentId());
    }

    // --- same Content ID, different channels -> counted separately ---------------------------------
    @Test
    void sameContentIdOnDifferentChannelsCountsSeparatelyUnderEach() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(3).plusDays(200 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalSplitBandhani");
        CompanyChannel sikar = channel(unique, "plcalSplitSikar");
        mapping(plan, targetOn(bandhani, unique, "SplitA"));
        mapping(plan, targetOn(bandhani, unique, "SplitB")); // second platform, same channel
        mapping(plan, targetOn(sikar, unique, "SplitC"));

        JsonNode groups = planData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 1)));
        JsonNode first = findChannel(groups, liveDate, "plcalSplitBandhani-" + unique);
        JsonNode second = findChannel(groups, liveDate, "plcalSplitSikar-" + unique);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.get("count").asLong()).isEqualTo(1);
        assertThat(second.get("count").asLong()).isEqualTo(1);
    }

    // --- distinctness must not flatten genuinely different content ---------------------------------
    @Test
    void differentContentIdsOnOneChannelEachCount() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(unique % 100);
        CompanyChannel shared = channel(unique, "plcalThreeIds");
        PublicationTarget target = targetOn(shared, unique, "Shared");
        mapping(approvePlan(ceo, unique, liveDate), target);
        mapping(approvePlan(ceo, unique + 1, liveDate), target);
        mapping(approvePlan(ceo, unique + 2, liveDate), target);

        JsonNode channel = findChannel(planData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 3))),
                liveDate, "plcalThreeIds-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong()).isEqualTo(3);
    }

    // --- the whole point of the extraction: the two screens must agree exactly ---------------------
    @Test
    void calendarDataMatchesTheUpcomingChannelPlanCalculationExactly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(4).plusDays(300 + unique % 60);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalParityBandhani");
        CompanyChannel sikar = channel(unique, "plcalParitySikar");
        mapping(plan, targetOn(bandhani, unique, "PA"));
        mapping(plan, targetOn(bandhani, unique, "PB"));
        mapping(plan, targetOn(sikar, unique, "PC"));

        JsonNode planningGroups = planData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 1)));

        // The KPI Dashboard Overview's own embedded calendar data, for the same fixture.
        Matcher m = KPI_PLAN_DATA.matcher(ceo.get("/app/reports/kpis?view=overview").body());
        assertThat(m.find()).isTrue();
        JsonNode kpiGroups = OBJECT_MAPPER.readTree(m.group(1).replace("\\u003c", "<"));

        for (String handle : new String[]{"plcalParityBandhani-" + unique, "plcalParitySikar-" + unique}) {
            JsonNode fromPlanning = findChannel(planningGroups, liveDate, handle);
            JsonNode fromKpi = findChannel(kpiGroups, liveDate, handle);
            assertThat(fromPlanning).as("%s must appear on the Planning calendar", handle).isNotNull();
            assertThat(fromKpi).as("%s must appear on the KPI calendar", handle).isNotNull();
            assertThat(fromPlanning.get("count").asLong())
                    .as("%s: Planning calendar and Upcoming Channel Plan must agree", handle)
                    .isEqualTo(fromKpi.get("count").asLong());
            assertThat(fromPlanning.get("contentIds")).isEqualTo(fromKpi.get("contentIds"));
        }
    }

    // --- the calendar only ever offers today onwards ------------------------------------------------
    @Test
    void pastPlannedDatesAreNeverSentToThePlanningCalendar() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        JsonNode groups = planData(planningScreenHtml(ceo, pendingIdea(ceo, unique)));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        for (JsonNode g : groups) {
            assertThat(LocalDate.parse(g.get("plannedLiveDate").asText()))
                    .as("a planner can only pick today or later, so no past date may be sent")
                    .isAfterOrEqualTo(today);
        }
    }

    // ================================================================ Shoot Date / Edit Date calendars
    // Same aggregation (UpcomingChannelPlanService#groupBy), grouped on planned_shoot_date /
    // planned_edit_date instead. Standard planning mode derives them from the Live Date itself
    // (Shoot = Live - 5, Edit = Live - 2), which is exactly what these fixtures rely on - the dates
    // under test are the ones the existing, untouched calculation produced.

    @Test
    void shootAndEditCalendarDataBlocksReachThePlanningScreenAndOptTheFieldsIn() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String html = planningScreenHtml(ceo, pendingIdea(ceo, unique));

        assertThat(html).contains("id=\"kcpcPlannedShootPlanData\"");
        assertThat(html).contains("id=\"kcpcPlannedEditPlanData\"");
        assertThat(html).contains("id=\"reviewsIdeaShootDate\" min=\"" + LocalDate.now(
                java.time.ZoneId.of("Asia/Kolkata")) + "\" data-kcpc-calendar=\"shoot\"");
        assertThat(html).contains("data-kcpc-calendar=\"edit\"");
    }

    /** Requirement: same Content ID, several platforms, ONE channel -> 1 on the Shoot calendar. */
    @Test
    void shootCalendarCountsOneContentIdAcrossPlatformsOfOneChannelOnce() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(5).plusDays(unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalShootOne");
        mapping(plan, targetOn(bandhani, unique, "SInsta"));
        mapping(plan, targetOn(bandhani, unique, "SYouTube"));
        mapping(plan, targetOn(bandhani, unique, "SFacebook"));

        LocalDate shootDate = contentPlanRepository.findById(plan.getId()).orElseThrow().getPlannedShootDate();
        assertThat(shootDate).as("Standard mode still derives Shoot = Live - 5").isEqualTo(liveDate.minusDays(5));

        JsonNode groups = blockData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 1)),
                SHOOT_PLAN_DATA, "Shoot Date");
        JsonNode channel = findChannel(groups, shootDate, "plcalShootOne-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong())
                .as("Instagram + YouTube + Facebook under one Channel/Account is ONE shoot").isEqualTo(1);
        assertThat(channel.get("contentIds").size()).isEqualTo(1);
    }

    /** Requirement: the same Content ID on different channels counts separately. */
    @Test
    void shootCalendarCountsTheSameContentIdSeparatelyPerChannel() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(5).plusDays(150 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalShootBandhani");
        CompanyChannel sikar = channel(unique, "plcalShootSikar");
        mapping(plan, targetOn(bandhani, unique, "ShA"));
        mapping(plan, targetOn(bandhani, unique, "ShB"));
        mapping(plan, targetOn(sikar, unique, "ShC"));

        LocalDate shootDate = liveDate.minusDays(5);
        JsonNode groups = blockData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 1)),
                SHOOT_PLAN_DATA, "Shoot Date");
        assertThat(findChannel(groups, shootDate, "plcalShootBandhani-" + unique).get("count").asLong()).isEqualTo(1);
        assertThat(findChannel(groups, shootDate, "plcalShootSikar-" + unique).get("count").asLong()).isEqualTo(1);
    }

    @Test
    void editCalendarCountsOneContentIdAcrossPlatformsOfOneChannelOnce() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(5).plusDays(300 + unique % 100);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalEditOne");
        mapping(plan, targetOn(bandhani, unique, "EInsta"));
        mapping(plan, targetOn(bandhani, unique, "EYouTube"));

        LocalDate editDate = contentPlanRepository.findById(plan.getId()).orElseThrow().getPlannedEditDate();
        assertThat(editDate).as("Standard mode still derives Edit = Live - 2").isEqualTo(liveDate.minusDays(2));

        JsonNode channel = findChannel(blockData(planningScreenHtml(ceo, pendingIdea(ceo, unique + 1)),
                EDIT_PLAN_DATA, "Edit Date"), editDate, "plcalEditOne-" + unique);
        assertThat(channel).isNotNull();
        assertThat(channel.get("count").asLong()).isEqualTo(1);
    }

    /** The three calendars must be grouped on three DIFFERENT date columns - a shoot date must not
     *  appear in the live dataset, and vice versa. */
    @Test
    void theThreeCalendarsAreGroupedOnTheirOwnDateColumns() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(6).plusDays(unique % 90);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel chan = channel(unique, "plcalThreeCols");
        mapping(plan, targetOn(chan, unique, "TC"));
        String handle = "plcalThreeCols-" + unique;

        String html = planningScreenHtml(ceo, pendingIdea(ceo, unique + 1));
        JsonNode live = blockData(html, PLAN_DATA, "Planned Live Date");
        JsonNode shoot = blockData(html, SHOOT_PLAN_DATA, "Shoot Date");
        JsonNode edit = blockData(html, EDIT_PLAN_DATA, "Edit Date");

        assertThat(findChannel(live, liveDate, handle)).as("live dataset holds the Live Date").isNotNull();
        assertThat(findChannel(shoot, liveDate.minusDays(5), handle)).as("shoot dataset holds Live - 5").isNotNull();
        assertThat(findChannel(edit, liveDate.minusDays(2), handle)).as("edit dataset holds Live - 2").isNotNull();

        assertThat(findChannel(shoot, liveDate, handle)).as("the Live Date must not appear as a shoot date").isNull();
        assertThat(findChannel(live, liveDate.minusDays(5), handle)).as("a shoot date must not appear in the live dataset").isNull();
    }

    /** The Shoot/Edit calendars use the SAME counting rule as Upcoming Channel Plan: for a fixture
     *  whose live/shoot/edit dates all fall on distinct days, each channel's number is identical
     *  across the three datasets, because only the grouping column differs. */
    @Test
    void shootAndEditCountsMatchTheUpcomingChannelPlanRuleExactly() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        LocalDate liveDate = LocalDate.now().plusYears(6).plusDays(200 + unique % 90);
        ContentPlan plan = approvePlan(ceo, unique, liveDate);
        CompanyChannel bandhani = channel(unique, "plcalRuleBandhani");
        CompanyChannel sikar = channel(unique, "plcalRuleSikar");
        mapping(plan, targetOn(bandhani, unique, "RA"));
        mapping(plan, targetOn(bandhani, unique, "RB"));  // 2nd platform, same channel -> still 1
        mapping(plan, targetOn(sikar, unique, "RC"));

        String html = planningScreenHtml(ceo, pendingIdea(ceo, unique + 1));
        JsonNode live = blockData(html, PLAN_DATA, "Planned Live Date");
        JsonNode shoot = blockData(html, SHOOT_PLAN_DATA, "Shoot Date");
        JsonNode edit = blockData(html, EDIT_PLAN_DATA, "Edit Date");

        for (String handle : new String[]{"plcalRuleBandhani-" + unique, "plcalRuleSikar-" + unique}) {
            JsonNode l = findChannel(live, liveDate, handle);
            JsonNode s = findChannel(shoot, liveDate.minusDays(5), handle);
            JsonNode e = findChannel(edit, liveDate.minusDays(2), handle);
            assertThat(l).isNotNull();
            assertThat(s).as("%s must appear on the Shoot calendar", handle).isNotNull();
            assertThat(e).as("%s must appear on the Edit calendar", handle).isNotNull();
            assertThat(s.get("count").asLong())
                    .as("%s: the Shoot calendar must use the same distinct-Content-ID rule", handle)
                    .isEqualTo(l.get("count").asLong());
            assertThat(e.get("count").asLong())
                    .as("%s: the Edit calendar must use the same distinct-Content-ID rule", handle)
                    .isEqualTo(l.get("count").asLong());
            assertThat(s.get("contentIds")).isEqualTo(l.get("contentIds"));
            assertThat(e.get("contentIds")).isEqualTo(l.get("contentIds"));
        }
    }

    @Test
    void pastPlannedShootAndEditDatesAreNeverSentToThePlanningCalendars() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        String html = planningScreenHtml(ceo, pendingIdea(ceo, unique));
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        for (Pattern p : new Pattern[]{SHOOT_PLAN_DATA, EDIT_PLAN_DATA}) {
            for (JsonNode g : blockData(html, p, "planning")) {
                assertThat(LocalDate.parse(g.get("plannedLiveDate").asText())).isAfterOrEqualTo(today);
            }
        }
    }
}
