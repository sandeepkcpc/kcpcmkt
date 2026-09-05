package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.masterdata.domain.CompanyChannel;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * My Work &rarr; Dashboard filter panel (Publisher): Planned Live Date range, Channel and
 * Platform, applied SERVER-SIDE in {@code LandingMvcController#myWork} to both the Dashboard's own
 * Upcoming Tasks list and the Publishing tab's Active Publishing Tasks list.
 *
 * <p>The distinction these tests exist to prove is that a filtered-out row is genuinely absent from
 * the rendered HTML - never merely hidden with CSS by the browser - so every assertion below is a
 * {@code doesNotContain} on the Content ID itself, which only holds if the row was never rendered.
 *
 * <p>Filtering is presentation-only: it changes which of the Publisher's own rows are displayed,
 * never which assignments exist, never any permission, and never any workflow state. The
 * "unfiltered request still shows everything" assertions guard that.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MyWorkDashboardFilterTest {

    @LocalServerPort
    int port;

    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    CompanyChannelRepository companyChannelRepository;
    @Autowired
    PublicationTargetRepository publicationTargetRepository;
    @Autowired
    PermissionGrantRepository permissionGrantRepository;
    @Autowired
    UserRepository userRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String PUBLISHER_ROLE_ID = "01926e3e-0001-7000-8000-000000000008";
    private static final String EDITOR_ROLE_ID = "01926e3e-0001-7000-8000-000000000005";
    private static final String INSTAGRAM_TARGET_KCPCBANDHANI = "01926e3e-000a-7000-8000-000000000001";
    private static final String YOUTUBE_TARGET_KCPCBANDHANI = "01926e3e-000a-7000-8000-000000000002";
    private static final String INSTAGRAM_PLATFORM_ID = "01926e3e-0008-7000-8000-000000000001";
    /** Every seeded publication target maps to this one channel (V8__planning_stage.sql). */
    private static final String SEEDED_CHANNEL_HANDLE = "kcpcbandhani";

    private record TestUser(String id, String email) {
    }

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private TestUser createUser(TestApiClient ceo, String label, String roleId, long unique) throws Exception {
        String email = "mwdf-" + label + "-" + unique + "@kcpcbandhani.local";
        JsonNode user = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"MWDF " + label + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + roleId + "\",\"creationReason\":\"my work dashboard filter test fixture\"}");
        return new TestUser(user.get("userId").asText(), email);
    }

    private void grantPermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permissionCode + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"my work dashboard filter test fixture grant\"}");
    }

    /**
     * Revokes a previously granted operational permission. Needed because assigning a Publisher at
     * Idea Review time requires that Publisher to hold PERM_08, yet the tab-visibility case worth
     * covering is the one where the Publisher has real assignment data but NO live permission.
     */
    private void revokePermission(TestApiClient ceo, String userId, String permissionCode) throws Exception {
        var grantee = userRepository.findById(UUID.fromString(userId)).orElseThrow();
        var grant = permissionGrantRepository.findByGrantee(grantee).stream()
                .filter(g -> g.getPermission().name().equals(permissionCode))
                .findFirst().orElseThrow();
        assertThat(ceo.post("/api/v1/admin/permission-grants/" + grant.getId() + "/revoke",
                "{\"reason\":\"my work dashboard filter test fixture revoke\"}").statusCode()).isEqualTo(200);
    }

    private TestApiClient loginAs(TestUser user) throws Exception {
        TestApiClient client = new TestApiClient(port);
        client.login(user.email(), "Passw0rd!");
        return client;
    }

    /**
     * Approves an Idea into Planning with the Publisher pre-assigned - the row lands in Upcoming.
     *
     * <p>Uses URGENT planning mode whenever the Planned Live Date is fewer than 5 days out.
     * STANDARD mode derives Shoot Date = live-5d and rejects anything closer than that
     * ({@code IdeaService}, BRS-REQ-093), which would make it impossible to build the
     * today/tomorrow fixtures the quick-pick cards exist to filter. Urgent mode takes the Shoot
     * and Edit dates explicitly instead, so a live date of today is legitimate - and the plan
     * still starts at Shoot, so the row is genuinely Upcoming rather than jumping to Publishing.
     * This mirrors how a real near-term task reaches a Publisher's board; it is fixture setup
     * only and exercises no code path this change touches.
     */
    private ContentPlan createUpcomingPlan(TestApiClient ceo, String title, TestUser cam, TestUser publisher,
                                            LocalDate plannedLiveDate, String targetId) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        boolean urgent = plannedLiveDate.isBefore(LocalDate.now().plusDays(5));
        String scheduling = urgent
                ? "\"planningMode\":\"URGENT\",\"urgencyReason\":\"my work dashboard filter near-term fixture\","
                        + "\"shootDate\":\"" + plannedLiveDate + "\",\"editDate\":\"" + plannedLiveDate + "\","
                : "";
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + scheduling
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + plannedLiveDate + "\","
                        + "\"folderLink\":\"https://drive.example.com/" + UUID.randomUUID() + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"publisherUserIds\":[\"" + publisher.id() + "\"],"
                        + "\"outputs\":[{\"outputType\":\"POST\",\"reelTypes\":[],\"publicationTargetIds\":[\""
                        + targetId + "\"]}]}}");
        return resolvePlan(ideaId);
    }

    private ContentPlan resolvePlan(String ideaId) {
        var idea = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findAll().stream()
                .filter(p -> p.getIdea() != null && p.getIdea().getId().equals(idea.getId()))
                .findFirst().orElseThrow();
    }

    /**
     * The Dashboard panel's Upcoming Tasks section only. Assertions MUST be scoped this way rather
     * than run against the whole page: a Content ID the filter correctly excluded from this table
     * still legitimately appears elsewhere in the same document - most notably in the header's
     * notification dropdown (fragments/nav.jsp renders the recipient's latest 5 notifications,
     * which include the assignment notification naming this very Content ID). A whole-body
     * {@code doesNotContain} would therefore fail even when the filter worked perfectly.
     *
     * <p>Cut at the NEXT {@code my-work-stage-panel} rather than at a {@code </table>}: each
     * Platforms cell renders a chip popover containing its own nested {@code <table>}, so the
     * first closing table tag is not the Upcoming table's own.
     */
    private static String upcomingSection(String body) {
        int start = body.indexOf("Upcoming Tasks");
        assertThat(start).as("Upcoming Tasks section is rendered").isPositive();
        int end = body.indexOf("my-work-stage-panel", start);
        return end > start ? body.substring(start, end) : body.substring(start);
    }

    /**
     * The count rendered on the quick-pick card whose date line is {@code date}. Reads the card's
     * own markup rather than trusting a page-wide count, so a card can never appear to agree with
     * the table by coincidence.
     */
    private static int countOnCard(String body, LocalDate date) {
        // The pills are single-line now, so the date is carried in the title attribute rather
        // than a second visible line - that title is what identifies which card this is.
        String dateMarker = "title=\"Planned Live Date " + date + " ";
        int at = body.indexOf(dateMarker);
        assertThat(at).as("card for " + date + " is rendered").isPositive();
        String countMarker = "<span class=\"mw-date-card-count\">";
        int countAt = body.indexOf(countMarker, at);
        assertThat(countAt).as("count on the " + date + " card").isPositive();
        int from = countAt + countMarker.length();
        return Integer.parseInt(body.substring(from, body.indexOf("</span>", from)).trim());
    }

    /** The class attribute of the quick-pick card carrying {@code label} ("Today"/"Tomorrow"). */
    private static String cardClasses(String body, String label) {
        String labelMarker = "<span class=\"mw-date-card-label\">" + label + "</span>";
        int labelAt = body.indexOf(labelMarker);
        assertThat(labelAt).as(label + " card is rendered").isPositive();
        // Walk back to this card's own opening tag and return just its class attribute.
        int classAt = body.lastIndexOf("class=\"mw-date-card", labelAt);
        assertThat(classAt).as(label + " card opening tag").isPositive();
        int from = classAt + "class=\"".length();
        return body.substring(from, body.indexOf('"', from));
    }

    /** The Publishing tab's Active Publishing Tasks section only - same scoping rationale. */
    private static String activePublishingSection(String body) {
        int start = body.indexOf("Active Publishing Tasks");
        assertThat(start).as("Active Publishing Tasks section is rendered").isPositive();
        int end = body.indexOf("my-work-tab-panel", start);
        return end > start ? body.substring(start, end) : body.substring(start);
    }

    // ---------------------------------------------------------------------------------------
    // Planned Live Date: Today / Tomorrow quick-pick cards and the single-date picker
    // ---------------------------------------------------------------------------------------

    @Test
    void todayTomorrowAndPickedDateEachKeepOnlyThatExactDateAndClearingRestoresAll() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "datecam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "date", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate later = today.plusDays(30);
        ContentPlan todayPlan = createUpcomingPlan(ceo, "MWDF Today " + unique, cam, publisherUser, today,
                INSTAGRAM_TARGET_KCPCBANDHANI);
        ContentPlan tomorrowPlan = createUpcomingPlan(ceo, "MWDF Tomorrow " + unique, cam, publisherUser, tomorrow,
                INSTAGRAM_TARGET_KCPCBANDHANI);
        ContentPlan laterPlan = createUpcomingPlan(ceo, "MWDF Later " + unique, cam, publisherUser, later,
                INSTAGRAM_TARGET_KCPCBANDHANI);

        TestApiClient publisher = loginAs(publisherUser);

        // No date filter - all three rows.
        assertThat(upcomingSection(publisher.get("/app/my-work").body()))
                .contains(todayPlan.getContentId())
                .contains(tomorrowPlan.getContentId())
                .contains(laterPlan.getContentId());

        // Today: an EXACT date match, so tomorrow's row is excluded too (a range filter would
        // have kept it - this is the assertion that pins the equality semantics).
        String todayOnly = publisher.get("/app/my-work?dashDate=" + today).body();
        assertThat(upcomingSection(todayOnly)).contains(todayPlan.getContentId());
        assertThat(upcomingSection(todayOnly)).doesNotContain(tomorrowPlan.getContentId());
        assertThat(upcomingSection(todayOnly)).doesNotContain(laterPlan.getContentId());

        String tomorrowOnly = publisher.get("/app/my-work?dashDate=" + tomorrow).body();
        assertThat(upcomingSection(tomorrowOnly)).contains(tomorrowPlan.getContentId());
        assertThat(upcomingSection(tomorrowOnly)).doesNotContain(todayPlan.getContentId());
        assertThat(upcomingSection(tomorrowOnly)).doesNotContain(laterPlan.getContentId());

        // "Select Date": an arbitrary date, driven by the same single parameter.
        String pickedOnly = publisher.get("/app/my-work?dashDate=" + later).body();
        assertThat(upcomingSection(pickedOnly)).contains(laterPlan.getContentId());
        assertThat(upcomingSection(pickedOnly)).doesNotContain(todayPlan.getContentId());
        assertThat(upcomingSection(pickedOnly)).doesNotContain(tomorrowPlan.getContentId());

        // A date with no tasks at all is simply empty, not an error.
        assertThat(upcomingSection(publisher.get("/app/my-work?dashDate=" + today.plusYears(3)).body()))
                .doesNotContain(todayPlan.getContentId())
                .doesNotContain(tomorrowPlan.getContentId())
                .doesNotContain(laterPlan.getContentId());

        // Clear restores everything.
        assertThat(upcomingSection(publisher.get("/app/my-work").body()))
                .contains(todayPlan.getContentId())
                .contains(tomorrowPlan.getContentId())
                .contains(laterPlan.getContentId());
    }

    @Test
    void quickPickCardsShowCountsHighlightTheSelectedOneAndCarryTheOtherFiltersForward() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "cardcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "card", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        // Two Instagram rows today, one YouTube row tomorrow.
        createUpcomingPlan(ceo, "MWDF CardA " + unique, cam, publisherUser, today, INSTAGRAM_TARGET_KCPCBANDHANI);
        createUpcomingPlan(ceo, "MWDF CardB " + unique, cam, publisherUser, today, INSTAGRAM_TARGET_KCPCBANDHANI);
        createUpcomingPlan(ceo, "MWDF CardC " + unique, cam, publisherUser, tomorrow, YOUTUBE_TARGET_KCPCBANDHANI);

        TestApiClient publisher = loginAs(publisherUser);
        String body = publisher.get("/app/my-work").body();

        // Both cards render with their own date and a count.
        assertThat(body).contains(">Today<").contains(">Tomorrow<").contains(">Select Date<");
        assertThat(body).contains("title=\"Planned Live Date " + today + " ");
        assertThat(body).contains("title=\"Planned Live Date " + tomorrow + " ");
        assertThat(countOnCard(body, today)).as("today card count").isEqualTo(2);
        assertThat(countOnCard(body, tomorrow)).as("tomorrow card count").isEqualTo(1);

        // Nothing selected yet -> no card is highlighted.
        assertThat(body).doesNotContain("mw-date-card active");

        // Selecting Today highlights exactly that card.
        String todaySelected = publisher.get("/app/my-work?dashDate=" + today).body();
        assertThat(todaySelected).contains("mw-date-card active");
        assertThat(cardClasses(todaySelected, "Today")).contains("active");
        assertThat(cardClasses(todaySelected, "Tomorrow")).doesNotContain("active");

        String tomorrowSelected = publisher.get("/app/my-work?dashDate=" + tomorrow).body();
        assertThat(cardClasses(tomorrowSelected, "Tomorrow")).contains("active");
        assertThat(cardClasses(tomorrowSelected, "Today")).doesNotContain("active");

        // Card counts honour the OTHER filters, so a card's number is exactly what clicking it
        // will show - here the YouTube-only selection empties Today and leaves Tomorrow at 1.
        String youtubeFiltered = publisher.get("/app/my-work?dashPlatform=YouTube").body();
        assertThat(countOnCard(youtubeFiltered, today)).as("today count under a YouTube filter").isEqualTo(0);
        assertThat(countOnCard(youtubeFiltered, tomorrow)).as("tomorrow count under a YouTube filter").isEqualTo(1);

        // And each card link carries that platform selection forward, so clicking a card narrows
        // the date without silently dropping the Channel/Platform filters already applied.
        assertThat(youtubeFiltered).contains("dashDate=" + today + "&amp;dashPlatform=YouTube");
        assertThat(youtubeFiltered).contains("dashDate=" + tomorrow + "&amp;dashPlatform=YouTube");
    }

    // ---------------------------------------------------------------------------------------
    // Platform (multi-select) and Channel, and their AND combination
    // ---------------------------------------------------------------------------------------

    @Test
    void platformFilterAcceptsMultipleSelectionsAndCombinesWithChannelUsingAndLogic() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "platcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "plat", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate live = LocalDate.now().plusDays(12);
        ContentPlan instagramPlan = createUpcomingPlan(ceo, "MWDF Insta " + unique, cam, publisherUser, live,
                INSTAGRAM_TARGET_KCPCBANDHANI);
        ContentPlan youtubePlan = createUpcomingPlan(ceo, "MWDF Tube " + unique, cam, publisherUser, live,
                YOUTUBE_TARGET_KCPCBANDHANI);

        TestApiClient publisher = loginAs(publisherUser);

        String unfiltered = publisher.get("/app/my-work").body();
        assertThat(upcomingSection(unfiltered)).contains(instagramPlan.getContentId()).contains(youtubePlan.getContentId());

        // Single platform.
        String instaOnly = publisher.get("/app/my-work?dashPlatform=Instagram").body();
        assertThat(upcomingSection(instaOnly)).contains(instagramPlan.getContentId());
        assertThat(upcomingSection(instaOnly)).doesNotContain(youtubePlan.getContentId());

        // Multi-select: repeated "platform" parameters, exactly what the checkbox dropdown submits.
        // Both rows come back - this is the assertion that a second selection genuinely widens
        // the result rather than replacing or intersecting the first.
        String bothPlatforms = publisher.get("/app/my-work?dashPlatform=Instagram&dashPlatform=YouTube").body();
        assertThat(upcomingSection(bothPlatforms)).contains(instagramPlan.getContentId()).contains(youtubePlan.getContentId());

        // Channel: every seeded target maps to the one seeded channel, so both rows match it.
        String seededChannel = publisher.get("/app/my-work?dashChannel=" + SEEDED_CHANNEL_HANDLE).body();
        assertThat(upcomingSection(seededChannel)).contains(instagramPlan.getContentId()).contains(youtubePlan.getContentId());

        // AND logic: same channel, narrowed to one platform -> only that platform's row survives.
        String andCombined = publisher.get("/app/my-work?dashChannel=" + SEEDED_CHANNEL_HANDLE
                + "&dashPlatform=Instagram").body();
        assertThat(upcomingSection(andCombined)).contains(instagramPlan.getContentId());
        assertThat(upcomingSection(andCombined)).doesNotContain(youtubePlan.getContentId());

        // AND logic, unsatisfiable combination: a real channel that carries no target for this
        // content -> zero rows, even though the platform half of the criteria matches.
        String contradictory = publisher.get("/app/my-work?dashChannel=kcpclegacy&dashPlatform=Instagram").body();
        assertThat(upcomingSection(contradictory)).doesNotContain(instagramPlan.getContentId());
        assertThat(upcomingSection(contradictory)).doesNotContain(youtubePlan.getContentId());
    }

    @Test
    void channelFilterDiscriminatesBetweenTwoRealChannelsOnTheSamePlatform() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "chcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "ch", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        // A second Instagram channel, so the two rows differ ONLY by channel - proving the channel
        // criterion is doing the work rather than the platform criterion incidentally matching.
        String otherHandle = "mwdf-channel-" + unique;
        assertThat(ceo.postForm("/app/admin/catalogue/channels",
                Map.of("channelHandle", otherHandle, "catalogueReason", "my work dashboard filter test fixture"))
                .statusCode()).isEqualTo(302);
        CompanyChannel otherChannel = companyChannelRepository.findAll().stream()
                .filter(c -> c.getChannelHandle().equals(otherHandle)).findFirst().orElseThrow();
        assertThat(ceo.postForm("/app/admin/catalogue/targets",
                Map.of("platformId", INSTAGRAM_PLATFORM_ID, "channelId", otherChannel.getId().toString(),
                        "targetName", "MWDF Target " + unique,
                        "catalogueReason", "my work dashboard filter test fixture")).statusCode()).isEqualTo(302);
        var otherTarget = publicationTargetRepository.findAll().stream()
                .filter(t -> t.getChannel().getId().equals(otherChannel.getId())).findFirst().orElseThrow();

        LocalDate live = LocalDate.now().plusDays(9);
        ContentPlan seededChannelPlan = createUpcomingPlan(ceo, "MWDF ChSeeded " + unique, cam, publisherUser, live,
                INSTAGRAM_TARGET_KCPCBANDHANI);
        ContentPlan otherChannelPlan = createUpcomingPlan(ceo, "MWDF ChOther " + unique, cam, publisherUser, live,
                otherTarget.getId().toString());

        TestApiClient publisher = loginAs(publisherUser);

        String unfiltered = publisher.get("/app/my-work").body();
        assertThat(upcomingSection(unfiltered)).contains(seededChannelPlan.getContentId()).contains(otherChannelPlan.getContentId());

        // Both plans are on Instagram, so only the channel can tell them apart.
        String seededOnly = publisher.get("/app/my-work?dashChannel=" + SEEDED_CHANNEL_HANDLE).body();
        assertThat(upcomingSection(seededOnly)).contains(seededChannelPlan.getContentId());
        assertThat(upcomingSection(seededOnly)).doesNotContain(otherChannelPlan.getContentId());

        String otherOnly = publisher.get("/app/my-work?dashChannel=" + otherHandle).body();
        assertThat(upcomingSection(otherOnly)).contains(otherChannelPlan.getContentId());
        assertThat(upcomingSection(otherOnly)).doesNotContain(seededChannelPlan.getContentId());

        // Both channels' handles are offered as options, sourced from the unfiltered row set.
        assertThat(unfiltered).contains("<option value=\"" + otherHandle + "\"");
        assertThat(unfiltered).contains("All Channels");
    }

    // ---------------------------------------------------------------------------------------
    // The SAME filter also applies to the Publishing tab's Active Publishing Tasks list
    // ---------------------------------------------------------------------------------------

    /**
     * The filter panel lives on the Dashboard tab but the requirement covers both Publishing
     * lists, so this drives one plan all the way into the Publishing active window (RFP) and
     * proves the very same query parameters filter that second table too.
     */
    @Test
    void filtersAlsoApplyToTheActivePublishingTasksList() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "actcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser editor = createUser(ceo, "acted", EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id(), "PERM_19_EDIT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "act", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate live = LocalDate.now().plusDays(25);
        ContentPlan plan = createUpcomingPlan(ceo, "MWDF Active " + unique, cam, publisherUser, live,
                INSTAGRAM_TARGET_KCPCBANDHANI);
        String planId = plan.getId().toString();

        // Shoot -> Edit -> RFP, which is the Publishing ACTIVE window.
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode editApproved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + publisherUser.id() + "\"]}");
        assertThat(editApproved.get("status").asText()).isEqualTo("RFP");

        TestApiClient publisher = loginAs(publisherUser);

        // Unfiltered: the row is in Active Publishing (and no longer Upcoming).
        String unfiltered = publisher.get("/app/my-work").body();
        assertThat(activePublishingSection(unfiltered)).contains(plan.getContentId());

        // A different date -> gone from Active Publishing.
        String outOfRange = publisher.get("/app/my-work?pubDate=" + live.minusDays(1)).body();
        assertThat(activePublishingSection(outOfRange)).doesNotContain(plan.getContentId());

        // Its own date -> back.
        String inRange = publisher.get("/app/my-work?pubDate=" + live).body();
        assertThat(activePublishingSection(inRange)).contains(plan.getContentId());

        // Platform: the plan is Instagram-only, so a YouTube-only selection excludes it.
        assertThat(activePublishingSection(publisher.get("/app/my-work?pubPlatform=Instagram").body()))
                .contains(plan.getContentId());
        assertThat(activePublishingSection(publisher.get("/app/my-work?pubPlatform=YouTube").body()))
                .doesNotContain(plan.getContentId());

        // Channel, and channel AND platform together.
        assertThat(activePublishingSection(publisher.get("/app/my-work?pubChannel=" + SEEDED_CHANNEL_HANDLE).body()))
                .contains(plan.getContentId());
        assertThat(activePublishingSection(publisher.get("/app/my-work?pubChannel=kcpclegacy").body()))
                .doesNotContain(plan.getContentId());

        // The Active Publishing table says so when it is being filtered, so missing rows are never
        // mistaken for lost work while the user is looking at a different tab from the filter.
        assertThat(outOfRange).contains("this tab's filters applied.");
    }

    /**
     * The Active Publishing Tasks table's Publisher(s) and Targets columns were replaced by a
     * Platforms column that reuses the Dashboard's existing chip component verbatim - same
     * fragment, same PipelinePlatformSummary data, same markup - rather than a second
     * implementation. Asserted by comparing the chip markup the two tables actually emit.
     */
    @Test
    void activePublishingTableRendersTheSamePlatformChipComponentAsTheDashboard() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "colcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser editor = createUser(ceo, "coled", EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id(), "PERM_19_EDIT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "col", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        // One plan driven into Publishing (Active), with TWO platforms so the per-platform chips
        // and their counts are both meaningful.
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"MWDF Cols " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":0.5,\"modelMark\":0.1,\"planning\":{"
                        + "\"contentPriority\":\"MEDIUM\",\"plannedLiveDate\":\"" + LocalDate.now().plusDays(18) + "\","
                        + "\"folderLink\":\"https://drive.example.com/" + UUID.randomUUID() + "\","
                        + "\"camerapersonUserIds\":[\"" + cam.id() + "\"],\"publisherUserIds\":[\"" + publisherUser.id() + "\"],"
                        + "\"outputs\":[{\"outputType\":\"POST\",\"reelTypes\":[],\"publicationTargetIds\":[\""
                        + INSTAGRAM_TARGET_KCPCBANDHANI + "\",\"" + YOUTUBE_TARGET_KCPCBANDHANI + "\"]}]}}");
        ContentPlan plan = resolvePlan(ideaId);
        String planId = plan.getId().toString();

        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + publisherUser.id() + "\"]}");

        TestApiClient publisher = loginAs(publisherUser);
        String body = publisher.get("/app/my-work").body();
        String activeSection = activePublishingSection(body);

        // Header: Platforms in, Publisher(s)/Targets out.
        int theadEnd = body.indexOf("</thead>", body.indexOf("Active Publishing Tasks"));
        String headerRow = body.substring(body.indexOf("Active Publishing Tasks"), theadEnd);
        assertThat(headerRow).contains("<th>Platforms</th>");
        assertThat(headerRow).doesNotContain(">Publisher(s)<").doesNotContain(">Targets<");
        // Column order is unchanged around the swap.
        assertThat(headerRow.indexOf("<th>Planned Live Date</th>")).isLessThan(headerRow.indexOf("<th>Platforms</th>"));
        assertThat(headerRow.indexOf("<th>Platforms</th>")).isLessThan(headerRow.indexOf("<th>Status</th>"));

        // The chip component itself - identical markup to the Dashboard's Upcoming Tasks column.
        assertThat(activeSection).contains("class=\"pipeline-platform-chips\"");
        assertThat(activeSection).contains("class=\"pipeline-platform-chip");
        assertThat(activeSection).contains("<span class=\"pipeline-platform-count\">&times;1</span>");
        assertThat(activeSection).contains("aria-label=\"Instagram: 0 of 1 published\"");
        assertThat(activeSection).contains("aria-label=\"YouTube: 0 of 1 published\"");
        assertThat(activeSection).contains("/icons/platforms/instagram.svg");
        assertThat(activeSection).contains("/icons/platforms/youtube.svg");
        assertThat(activeSection).contains("data-popup-target=");
        assertThat(activeSection.split("pipeline-platform-chip ").length - 1).as("one chip per platform").isEqualTo(2);

        // Popover ids are distinct from the Upcoming table's, so both tables can render chips on
        // the same page without colliding - both sets are portalized to <body> at page load.
        assertThat(activeSection).contains("active-platform-popover-");
        assertThat(activeSection).doesNotContain("upcoming-platform-popover-");

        // The click contract platform-chip-popover.js depends on: every chip's data-popup-target
        // must resolve to a popover element that actually exists, carrying the same detail table
        // (Type / Channel / Status / Link) the Upcoming Tasks popup shows. Without this linkage a
        // chip would be wired but open nothing. The wiring half - that this panel is passed to
        // PlatformChipPopover.wireClicks() - is covered by
        // src/test/js/my-work-platform-popover-wiring.test.js.
        Matcher targets = Pattern.compile("data-popup-target=\"(active-platform-popover-[^\"]+)\"")
                .matcher(activeSection);
        int chipsChecked = 0;
        while (targets.find()) {
            String popoverId = targets.group(1);
            assertThat(activeSection)
                    .as("popover element for chip target " + popoverId)
                    .contains("id=\"" + popoverId + "\"");
            chipsChecked++;
        }
        assertThat(chipsChecked).as("both chips carry a resolvable popover target").isEqualTo(2);
        assertThat(activeSection).contains("class=\"pipeline-platform-popover");
        assertThat(activeSection).contains("<th>Type</th>").contains("<th>Channel</th>")
                .contains("<th>Status</th>").contains("<th>Link</th>");

        // Everything else in the row is untouched.
        assertThat(activeSection).contains(plan.getContentId());
        assertThat(activeSection).contains("class=\"status-pill");
        assertThat(activeSection).contains("class=\"drive-link\"");
        assertThat(activeSection).contains("class=\"priority-pill");
    }

    /**
     * The Platforms control is a collapsed dropdown, not an expanded list box. This pins the three
     * things that regressed when it was a {@code <select multiple size="4">}: every option was
     * visible at once, the default state was not stated anywhere, and the control did not match
     * the Channel dropdown next to it.
     */
    @Test
    void platformsControlIsACollapsedDropdownThatSummarisesItsSelection() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "ddcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "dd", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate live = LocalDate.now().plusDays(11);
        createUpcomingPlan(ceo, "MWDF Dd1 " + unique, cam, publisherUser, live, INSTAGRAM_TARGET_KCPCBANDHANI);
        createUpcomingPlan(ceo, "MWDF Dd2 " + unique, cam, publisherUser, live, YOUTUBE_TARGET_KCPCBANDHANI);

        TestApiClient publisher = loginAs(publisherUser);

        // Default: collapsed, summarised as "All Platforms", and NOT a multi-select list box.
        String body = dashboardPanel(publisher.get("/app/my-work").body());
        assertThat(body).doesNotContain("multiple");
        assertThat(body).doesNotContain("<option value=\"Instagram\"");
        assertThat(body).contains("<details class=\"mw-platform-picker\">");
        assertThat(body).contains("All Platforms");
        // A <details> with no "open" attribute is closed - the options are in the DOM but the
        // browser does not render them until the user opens the dropdown.
        assertThat(body).doesNotContain("<details class=\"mw-platform-picker\" open");

        // One checkbox per available platform, all unchecked by default.
        assertThat(body).contains("type=\"checkbox\" name=\"dashPlatform\" value=\"Instagram\"");
        assertThat(body).contains("type=\"checkbox\" name=\"dashPlatform\" value=\"YouTube\"");
        assertThat(platformCheckboxIsChecked(body, "Instagram")).isFalse();
        assertThat(platformCheckboxIsChecked(body, "YouTube")).isFalse();

        // One selection: summarised as a chip naming it, and that box comes back checked. Scoped
        // to the Dashboard panel - the Publishing tab's own panel is independent and correctly
        // still reads "All Platforms", since only the dash* filter was set.
        String oneSelected = dashboardPanel(publisher.get("/app/my-work?dashPlatform=Instagram").body());
        assertThat(oneSelected).doesNotContain("All Platforms");
        assertThat(oneSelected).contains("<span class=\"mw-platform-chip\">Instagram</span>");
        assertThat(platformCheckboxIsChecked(oneSelected, "Instagram")).isTrue();
        assertThat(platformCheckboxIsChecked(oneSelected, "YouTube")).isFalse();

        // Two selections: both chips, both checked.
        String twoSelected = dashboardPanel(
                publisher.get("/app/my-work?dashPlatform=Instagram&dashPlatform=YouTube").body());
        assertThat(twoSelected).contains("<span class=\"mw-platform-chip\">Instagram</span>");
        assertThat(twoSelected).contains("<span class=\"mw-platform-chip\">YouTube</span>");
        assertThat(platformCheckboxIsChecked(twoSelected, "Instagram")).isTrue();
        assertThat(platformCheckboxIsChecked(twoSelected, "YouTube")).isTrue();

        // Channel keeps its own <select> - the two controls sit side by side and must not have
        // been accidentally converted along with the platform one.
        assertThat(body).contains("<select name=\"dashChannel\">").contains("All Channels");

        // Apply/Clear are grouped so the layout can push them to the far right of the row.
        assertThat(body).contains("class=\"mw-filter-actions\"");
    }

    /** The Dashboard tab's filter panel only - the Publishing tab has its own, independent copy. */
    private static String dashboardPanel(String body) {
        return panelBetween(body, "id=\"publishDashboardFilterForm\"", "Upcoming Tasks");
    }

    /** Whether the Platforms dropdown's checkbox for {@code platform} is rendered checked. */
    private static boolean platformCheckboxIsChecked(String body, String platform) {
        String marker = "type=\"checkbox\" name=\"dashPlatform\" value=\"" + platform + "\"";
        int at = body.indexOf(marker);
        assertThat(at).as(platform + " checkbox is rendered").isPositive();
        int tagEnd = body.indexOf(">", at);
        return body.substring(at, tagEnd).contains("checked");
    }

    // ---------------------------------------------------------------------------------------
    // The two tabs filter independently, from different data sources
    // ---------------------------------------------------------------------------------------

    /**
     * Dashboard counts come from Upcoming Tasks; Publishing counts come from Active Publishing
     * Tasks. Built so the two numbers CANNOT coincide: one upcoming row today, two active rows
     * today. A shared/page-wide count would report 3 on both cards.
     */
    @Test
    void todayTomorrowCountsAreCalculatedPerTabFromThatTabsOwnList() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "cntcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser editor = createUser(ceo, "cnted", EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id(), "PERM_19_EDIT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "cnt", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // One UPCOMING row today (stays in Shoot), and one upcoming row tomorrow.
        createUpcomingPlan(ceo, "MWDF CntUpToday " + unique, cam, publisherUser, today, INSTAGRAM_TARGET_KCPCBANDHANI);
        createUpcomingPlan(ceo, "MWDF CntUpTom " + unique, cam, publisherUser, tomorrow, INSTAGRAM_TARGET_KCPCBANDHANI);
        // Two ACTIVE rows today (driven through to RFP), and none tomorrow.
        driveToPublishing(ceo, cam, editor, publisherUser, "MWDF CntActA " + unique, today);
        driveToPublishing(ceo, cam, editor, publisherUser, "MWDF CntActB " + unique, today);

        TestApiClient publisher = loginAs(publisherUser);
        String body = publisher.get("/app/my-work").body();

        String dashPanel = panelBetween(body, "id=\"publishDashboardFilterForm\"", "Upcoming Tasks");
        String pubPanel = panelBetween(body, "id=\"publishTabFilterForm\"", "Active Publishing Tasks");

        // Dashboard: Upcoming only -> 1 today, 1 tomorrow.
        assertThat(countOnCard(dashPanel, today)).as("Dashboard Today = upcoming rows today").isEqualTo(1);
        assertThat(countOnCard(dashPanel, tomorrow)).as("Dashboard Tomorrow = upcoming rows tomorrow").isEqualTo(1);

        // Publishing: Active only -> 2 today, 0 tomorrow. Different numbers from the Dashboard's,
        // which is the whole point: neither tab reuses the other's count.
        assertThat(countOnCard(pubPanel, today)).as("Publishing Today = active rows today").isEqualTo(2);
        assertThat(countOnCard(pubPanel, tomorrow)).as("Publishing Tomorrow = active rows tomorrow").isEqualTo(0);
    }

    /**
     * Filtering one tab must leave the other completely alone - its rows, its counts and its own
     * selection. The two panels submit different parameter names and each round-trips the other's.
     */
    @Test
    void filteringOneTabLeavesTheOtherTabUntouched() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "indcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser editor = createUser(ceo, "inded", EDITOR_ROLE_ID, unique);
        grantPermission(ceo, editor.id(), "PERM_19_EDIT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "ind", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        LocalDate today = LocalDate.now();
        LocalDate far = today.plusDays(21);
        ContentPlan upcomingPlan = createUpcomingPlan(ceo, "MWDF IndUp " + unique, cam, publisherUser, far,
                INSTAGRAM_TARGET_KCPCBANDHANI);
        ContentPlan activePlan = driveToPublishing(ceo, cam, editor, publisherUser, "MWDF IndAct " + unique, far);

        TestApiClient publisher = loginAs(publisherUser);

        // Filter the DASHBOARD to a date neither row has -> Upcoming empties, Active is untouched.
        String dashFiltered = publisher.get("/app/my-work?dashDate=" + today).body();
        assertThat(upcomingSection(dashFiltered)).doesNotContain(upcomingPlan.getContentId());
        assertThat(activePublishingSection(dashFiltered)).as("Publishing tab unaffected by a Dashboard filter")
                .contains(activePlan.getContentId());

        // Filter the PUBLISHING tab instead -> Active empties, Upcoming is untouched.
        String pubFiltered = publisher.get("/app/my-work?pubDate=" + today).body();
        assertThat(activePublishingSection(pubFiltered)).doesNotContain(activePlan.getContentId());
        assertThat(upcomingSection(pubFiltered)).as("Dashboard tab unaffected by a Publishing filter")
                .contains(upcomingPlan.getContentId());

        // Both at once - each applies only to its own table.
        String bothFiltered = publisher.get("/app/my-work?dashDate=" + far + "&pubDate=" + today).body();
        assertThat(upcomingSection(bothFiltered)).contains(upcomingPlan.getContentId());
        assertThat(activePublishingSection(bothFiltered)).doesNotContain(activePlan.getContentId());

        // Each panel re-submits the other's filter as hidden inputs, so applying one does not
        // clear the other on the round trip.
        assertThat(bothFiltered).contains("<input type=\"hidden\" name=\"pubDate\" value=\"" + today + "\">");
        assertThat(bothFiltered).contains("<input type=\"hidden\" name=\"dashDate\" value=\"" + far + "\">");

        // And Clear on one tab keeps the other tab's filter rather than wiping both.
        String pubPanel = panelBetween(bothFiltered, "id=\"publishTabFilterForm\"", "Active Publishing Tasks");
        assertThat(pubPanel).as("Publishing Clear preserves the Dashboard filter")
                .contains("dashDate=" + far);
    }

    /** Drives one plan from Idea approval through to RFP - the Publishing ACTIVE window. */
    private ContentPlan driveToPublishing(TestApiClient ceo, TestUser cam, TestUser editor, TestUser publisher,
                                          String title, LocalDate plannedLiveDate) throws Exception {
        ContentPlan plan = createUpcomingPlan(ceo, title, cam, publisher, plannedLiveDate, INSTAGRAM_TARGET_KCPCBANDHANI);
        String planId = plan.getId().toString();
        TestApiClient camClient = loginAs(cam);
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/start", "");
        camClient.post("/api/v1/content-plans/" + planId + "/shooting/review/submit", "");
        ceo.postJson("/api/v1/content-plans/" + planId + "/shooting/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + cam.id() + "\"],"
                        + "\"editorUserIds\":[\"" + editor.id() + "\"],\"leadEditorUserId\":\"" + editor.id() + "\"}");
        TestApiClient editorClient = loginAs(editor);
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/start", "");
        editorClient.post("/api/v1/content-plans/" + planId + "/editing/review/submit", "");
        JsonNode approved = ceo.postJson("/api/v1/content-plans/" + planId + "/editing/review/decision",
                "{\"approve\":true,\"qualifyingRecipientUserIds\":[\"" + editor.id() + "\"],"
                        + "\"publisherUserIds\":[\"" + publisher.id() + "\"]}");
        assertThat(approved.get("status").asText()).isEqualTo("RFP");
        return plan;
    }

    /** One filter panel's markup: from its form id up to the table heading it sits above. */
    private static String panelBetween(String body, String formIdMarker, String tableHeading) {
        int start = body.indexOf(formIdMarker);
        assertThat(start).as("panel " + formIdMarker).isPositive();
        int end = body.indexOf(tableHeading, start);
        assertThat(end).as("table after " + formIdMarker).isGreaterThan(start);
        return body.substring(start, end);
    }

    // ---------------------------------------------------------------------------------------
    // Panel rendering and the guards around it
    // ---------------------------------------------------------------------------------------

    @Test
    void filterPanelRendersAboveUpcomingTasksWithAllControlsAndDefaults() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisherUser = createUser(ceo, "panel", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestApiClient publisher = loginAs(publisherUser);

        String body = publisher.get("/app/my-work").body();

        int filterFormAt = body.indexOf("id=\"publishDashboardFilterForm\"");
        int upcomingAt = body.indexOf("Upcoming Tasks");
        assertThat(filterFormAt).as("filter panel is rendered").isPositive();
        assertThat(filterFormAt).as("filter panel sits ABOVE the Upcoming Tasks section").isLessThan(upcomingAt);

        // All three filters, with their required defaults.
        assertThat(body).doesNotContain("name=\"liveFrom\"").doesNotContain("name=\"liveTo\"");
        assertThat(body).contains(">Today<").contains(">Tomorrow<").contains(">Select Date<");
        assertThat(body).contains("name=\"dashDate\"");
        assertThat(body).contains("name=\"dashChannel\"").contains(">All Channels<");
        // A collapsed dropdown, not an expanded list box: a <details> picker whose summary shows
        // the default "All Platforms", with one checkbox per platform inside it.
        assertThat(body).doesNotContain("multiple");
        assertThat(body).contains("class=\"mw-platform-picker\"").contains("mw-platform-picker-toggle");
        assertThat(body).contains("All Platforms");
        // This fixture Publisher has no assigned content, so there are legitimately no platform
        // options to check - the dropdown states that rather than rendering an empty box. The
        // populated case (one checkbox per platform) is covered by
        // platformsControlIsACollapsedDropdownThatSummarisesItsSelection.
        assertThat(body).contains("No platforms available");
        assertThat(body).contains("Apply Filters").contains(">Clear<");
        // Reuses the Content Pipeline filter bar's own classes - no bespoke filter styling.
        assertThat(body).contains("class=\"pipeline-filter-bar\"");
    }

    /**
     * The Dashboard tab is what carries the filter panel, so its visibility must never depend on
     * the filter's own result - otherwise a filter matching nothing would hide the only control
     * capable of clearing it, stranding the user on an empty page.
     */
    @Test
    void dashboardTabAndFilterPanelSurviveAFilterThatMatchesNoRows() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "emptycam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "empty", PUBLISHER_ROLE_ID, unique);
        // Granted only so the assignment itself is permitted at approval time, then revoked: what
        // this test needs is a Publisher with real assignment data but NO live execution
        // permission, because that is the case where showPublishTab rests purely on the work
        // lists - exactly what a filtered-to-zero list could otherwise have made disappear.
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");
        ContentPlan plan = createUpcomingPlan(ceo, "MWDF Empty " + unique, cam, publisherUser,
                LocalDate.now().plusDays(7), INSTAGRAM_TARGET_KCPCBANDHANI);
        revokePermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        TestApiClient publisher = loginAs(publisherUser);
        assertThat(upcomingSection(publisher.get("/app/my-work").body())).contains(plan.getContentId());

        // A date window that excludes the only row this Publisher has.
        String noMatches = publisher.get("/app/my-work?dashDate=" + LocalDate.now().plusYears(5)).body();
        assertThat(upcomingSection(noMatches)).doesNotContain(plan.getContentId());
        assertThat(noMatches).as("Dashboard tab still rendered").contains("data-tab=\"dashboard\"");
        assertThat(noMatches).as("filter panel still rendered").contains("id=\"publishDashboardFilterForm\"");
        assertThat(noMatches).as("empty state explains the filter, not lost work")
                .contains("No upcoming publishing tasks match this tab's filters");
    }

    /**
     * The Publishing tab carries its own copy of the identical filter panel, so a Publisher
     * working there never has to switch tabs to filter. Both copies are the same fragment driving
     * the same request parameters, so this checks the second copy is present, distinctly
     * identified, and round-trips the tab.
     */
    @Test
    void publishingTabRendersItsOwnFilterPanelAboveTheActivePublishingTable() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser publisherUser = createUser(ceo, "tabpanel", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");
        TestApiClient publisher = loginAs(publisherUser);

        String body = publisher.get("/app/my-work").body();

        // Both panels exist, with distinct DOM ids (duplicate ids would be invalid HTML and would
        // make any first-match lookup silently target the wrong panel).
        int dashboardPanelAt = body.indexOf("id=\"publishDashboardFilterForm\"");
        int publishPanelAt = body.indexOf("id=\"publishTabFilterForm\"");
        assertThat(dashboardPanelAt).as("Dashboard tab's filter panel").isPositive();
        assertThat(publishPanelAt).as("Publishing tab's filter panel").isPositive();
        assertThat(publishPanelAt).as("the two panels are distinct").isNotEqualTo(dashboardPanelAt);

        // The Publishing copy sits ABOVE that tab's own table.
        int activeTableAt = body.indexOf("Active Publishing Tasks");
        assertThat(publishPanelAt).as("panel is above the Active Publishing table").isLessThan(activeTableAt);

        // Same three filters, same card controls, in this second copy too.
        String publishPanel = body.substring(publishPanelAt, activeTableAt);
        assertThat(publishPanel).contains(">Today<").contains(">Tomorrow<").contains(">Select Date<");
        assertThat(publishPanel).contains("name=\"pubChannel\"").contains("All Channels");
        assertThat(publishPanel).doesNotContain("multiple");
        assertThat(publishPanel).contains("class=\"mw-platform-picker\"").contains("All Platforms");
        assertThat(publishPanel).contains("Apply Filters").contains(">Clear<");
        // Round-trips its own tab so Apply/Clear return here rather than to the Dashboard tab.
        assertThat(publishPanel).contains("name=\"tab\" value=\"publish\"");
        assertThat(publishPanel).contains("?tab=publish");
    }

    /**
     * The tab parameter is a view concern only - it pre-selects a stage tab so applying a filter
     * from the Publishing tab does not bounce the user back to Dashboard.
     */
    @Test
    void tabParameterPreSelectsTheStageTabWithoutAffectingWhichRowsAreShown() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "tabcam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "tabsel", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");
        ContentPlan plan = createUpcomingPlan(ceo, "MWDF Tab " + unique, cam, publisherUser,
                LocalDate.now().plusDays(4), INSTAGRAM_TARGET_KCPCBANDHANI);

        TestApiClient publisher = loginAs(publisherUser);

        // Without the parameter no stage tab is pre-marked - my-work-tabs.js falls back to the
        // first one, exactly as before this change.
        String noTab = publisher.get("/app/my-work").body();
        assertThat(noTab).doesNotContain("my-work-stage-tab active");

        String publishTab = publisher.get("/app/my-work?tab=publish").body();
        assertThat(publishTab).contains("my-work-stage-tab active\" data-tab=\"publish\"");
        assertThat(publishTab).doesNotContain("my-work-stage-tab active\" data-tab=\"dashboard\"");

        String dashboardTab = publisher.get("/app/my-work?tab=dashboard").body();
        assertThat(dashboardTab).contains("my-work-stage-tab active\" data-tab=\"dashboard\"");

        // An unknown value simply falls back - never an error, never a hidden tab.
        String bogusTab = publisher.get("/app/my-work?tab=not-a-tab").body();
        assertThat(bogusTab).doesNotContain("my-work-stage-tab active");

        // In every case the same rows are shown: tab selection filters nothing.
        for (String body : new String[]{noTab, publishTab, dashboardTab, bogusTab}) {
            assertThat(upcomingSection(body)).contains(plan.getContentId());
        }
    }

    /**
     * Filtering is display-only. A Publisher's assignments, permissions and workflow state are
     * untouched by any filter value, including one that hides every row.
     */
    @Test
    void filtersNeverChangeAssignmentsPermissionsOrWorkflowState() throws Exception {
        TestApiClient ceo = ceo();
        long unique = Instant.now().toEpochMilli();
        TestUser cam = createUser(ceo, "safecam", CAMERA_PERSON_ROLE_ID, unique);
        grantPermission(ceo, cam.id(), "PERM_18_SHOOT_EXECUTION");
        TestUser publisherUser = createUser(ceo, "safe", PUBLISHER_ROLE_ID, unique);
        grantPermission(ceo, publisherUser.id(), "PERM_08_PUBLISHING_EXECUTION");

        ContentPlan plan = createUpcomingPlan(ceo, "MWDF Safe " + unique, cam, publisherUser,
                LocalDate.now().plusDays(20), INSTAGRAM_TARGET_KCPCBANDHANI);
        var before = contentPlanRepository.findById(plan.getId()).orElseThrow();
        var statusBefore = before.getWorkflowInstance().getCurrentStatusCode();

        TestApiClient publisher = loginAs(publisherUser);
        publisher.get("/app/my-work?liveDate=" + LocalDate.now().plusYears(9)
                + "&channel=kcpclegacy&platform=Instagram&platform=YouTube");

        var after = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(after.getWorkflowInstance().getCurrentStatusCode()).isEqualTo(statusBefore);
        assertThat(after.getPlannedLiveDate()).isEqualTo(before.getPlannedLiveDate());

        // The row itself is untouched: clearing the filter brings it straight back.
        assertThat(upcomingSection(publisher.get("/app/my-work").body())).contains(plan.getContentId());
    }
}
