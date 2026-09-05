package com.kcpc.mkt.reporting.service;

import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.domain.PlannedOutputPublicationTargetMapping;
import com.kcpc.mkt.planning.repository.PlannedOutputPublicationTargetMappingRepository;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.reporting.dto.UpcomingPlanChannelCount;
import com.kcpc.mkt.reporting.dto.UpcomingPlanDateGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * THE single definition of "Upcoming Channel Plan" - how much planned work is still outstanding,
 * grouped by a planned date then Channel/Account (never Platform - spec explicit).
 *
 * <p>Extracted out of {@code KpiDashboardService} so that every screen that shows this computes it
 * from ONE implementation rather than several that could drift apart:
 * <ul>
 *   <li>{@link #upcomingChannelPlan()} - Planned LIVE Date. The KPI Dashboard Overview's Upcoming
 *       Channel Plan, and the Idea Review &amp; Planning screen's Planned Live Date calendar.</li>
 *   <li>{@link #plannedShootPlan()} - Planned SHOOT Date. The Shoot Date calendar.</li>
 *   <li>{@link #plannedEditPlan()} - Planned EDIT Date. The Edit Date calendar.</li>
 * </ul>
 * All three share {@link #groupBy} verbatim and differ ONLY in which {@code content_plans} date
 * column buckets the rows. {@code KpiDashboardService#overview} delegates here; its behaviour, its
 * permission gate and its DTOs are unchanged.
 *
 * <p><strong>Counting unit:</strong> one DISTINCT Content Plan ({@code content_plans.content_id})
 * per (planned date, Channel/Account) pair - "how many distinct pieces of content are planned on
 * this Channel/Account on this date". A Content Plan targeting Instagram + YouTube +
 * Facebook all under the SAME Channel/Account is three
 * {@code planned_output_publication_target_mappings} rows but only ONE piece of content, and counts
 * as 1. Distinctness is per (date, channel), so the same Content ID planned on two different
 * Channel/Accounts still counts once under each - those are two genuinely separate publication
 * commitments. Never platform-wise, never publication-target-wise.
 *
 * <p><strong>Eligibility</strong> is evaluated per mapping row: a row is "outstanding" precisely
 * when it has no matching {@code ActualPublicationEvent} yet, keyed on
 * (plannedOutput + publicationTarget) - the exact same key
 * {@code ActualPublicationEventRepository}'s own existence checks use. Only the AGGREGATION over
 * the surviving rows is by distinct Content ID. A consequence of that split, and the intended
 * behaviour: publishing ONE of a Content ID's targets on a channel does not decrement the count -
 * the content is still outstanding on that channel until EVERY one of its targets there has gone
 * live, at which point it disappears entirely. Publication before the planned date still removes it
 * immediately, from the same per-row check, with no separate state.
 *
 * <p>Terminal Content Plans (Cancelled/Completed/Rejected) are excluded: a cancelled plan's
 * obligations are void, and a completed plan has nothing left "still to go live". NOT incorporated:
 * {@code publication_target_na_records} (a target explicitly marked Not Applicable) - the exit rule
 * names only the actual-publication-event source, so an N/A'd target with no actual publication
 * event still shows as outstanding. Carried over unchanged from the original implementation rather
 * than silently resolved either way during this extraction.
 *
 * <p>Deliberately a CURRENT-STATE view, never date-ranged - it represents "still-outstanding
 * planned publication work right now", not a historical window.
 */
@Service
public class UpcomingChannelPlanService {

    /** TEMPORARY DIAGNOSTIC support - see {@link #logPlan}. Remove with it. */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UpcomingChannelPlanService.class);

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");
    private static final Set<String> NON_TERMINAL_EXCLUSIONS = Set.of("COMP", "CAN", "RJ");

    private final PlannedOutputPublicationTargetMappingRepository mappingRepository;
    private final ActualPublicationEventRepository eventRepository;

    public UpcomingChannelPlanService(PlannedOutputPublicationTargetMappingRepository mappingRepository,
                                       ActualPublicationEventRepository eventRepository) {
        this.mappingRepository = mappingRepository;
        this.eventRepository = eventRepository;
    }

    /** Every still-outstanding Planned Live Date, ascending, each with its Channel/Accounts sorted
     *  by handle. This is what the KPI Dashboard Overview renders. */
    @Transactional(readOnly = true)
    public List<UpcomingPlanDateGroup> upcomingChannelPlan() {
        return groupBy(ContentPlan::getPlannedLiveDate);
    }

    /**
     * The same aggregation, grouped on the Planned SHOOT Date instead - "how many distinct pieces of
     * content are already scheduled to be shot on this date, per Channel/Account", for the Idea
     * Review &amp; Planning screen's Shoot Date calendar.
     *
     * <p>Identical counting unit (distinct Content ID per date + Channel/Account) and identical
     * eligibility to {@link #upcomingChannelPlan()} - the ONLY difference is which
     * {@code content_plans} date column the rows are bucketed by. Reusing the eligibility verbatim
     * rather than inventing a shoot-specific one keeps this a single code path; its one visible
     * consequence is that a plan whose targets have all already been published drops out of the
     * shoot view too. That is immaterial in practice, because this is only ever surfaced for today
     * and later (see {@link #fromToday}) and future-dated work is not yet published.
     */
    @Transactional(readOnly = true)
    public List<UpcomingPlanDateGroup> plannedShootPlan() {
        return groupBy(ContentPlan::getPlannedShootDate);
    }

    /** As {@link #plannedShootPlan()}, grouped on the Planned EDIT Date - the Edit Date calendar. */
    @Transactional(readOnly = true)
    public List<UpcomingPlanDateGroup> plannedEditPlan() {
        return groupBy(ContentPlan::getPlannedEditDate);
    }

    /**
     * The one aggregation, parameterised only by which planned date column groups the rows. Every
     * caller above shares this body, so the distinct-Content-ID rule, the terminal-status exclusion
     * and the published-target exit rule each exist exactly once.
     */
    private List<UpcomingPlanDateGroup> groupBy(java.util.function.Function<ContentPlan, LocalDate> dateOf) {
        List<PlannedOutputPublicationTargetMapping> allMappings = mappingRepository.findAll();
        if (allMappings.isEmpty()) {
            return List.of();
        }
        Set<UUID> contentPlanIds = allMappings.stream()
                .map(m -> m.getPlannedOutput().getContentPlan().getId()).collect(Collectors.toSet());
        Set<String> publishedKeys = eventRepository.findByContentPlan_IdIn(contentPlanIds).stream()
                .map(e -> e.getPlannedOutput().getId() + "|" + e.getPublicationTarget().getId())
                .collect(Collectors.toSet());

        // Distinct Content IDs per (date, channel) group. LinkedHashSet does both jobs at once: it
        // IS the de-duplication that makes this a distinct-Content-ID count, and its insertion
        // order is the first-seen ordering the calendar's detail panel renders. So the same Content
        // ID arriving again from a second Planned Output or a second Publication Target under this
        // same Channel/Account (Instagram + YouTube + Facebook; two Reels for one plan) collapses
        // into the single entry it already has. count is always contentIds.size(), and both are the
        // number of distinct pieces of content.
        Map<LocalDate, Map<String, LinkedHashSet<String>>> byDateThenChannel = new TreeMap<>();
        for (PlannedOutputPublicationTargetMapping mapping : allMappings) {
            PlannedOutput output = mapping.getPlannedOutput();
            ContentPlan plan = output.getContentPlan();
            if (NON_TERMINAL_EXCLUSIONS.contains(plan.getWorkflowInstance().getCurrentStatusCode().name())) {
                continue;
            }
            LocalDate groupDate = dateOf.apply(plan);
            if (groupDate == null) {
                continue;
            }
            String key = output.getId() + "|" + mapping.getPublicationTarget().getId();
            if (publishedKeys.contains(key)) {
                continue; // already actually published - exits immediately, regardless of the date
            }
            String channelHandle = mapping.getPublicationTarget().getChannel().getChannelHandle();
            byDateThenChannel.computeIfAbsent(groupDate, d -> new TreeMap<>())
                    .computeIfAbsent(channelHandle, c -> new LinkedHashSet<>())
                    .add(plan.getContentId());
        }
        List<UpcomingPlanDateGroup> groups = new ArrayList<>();
        for (var dateEntry : byDateThenChannel.entrySet()) {
            // List.copyOf preserves the LinkedHashSet's first-seen iteration order and hands the
            // DTO an immutable snapshot (content_id is NOT NULL, so it can never reject an element).
            List<UpcomingPlanChannelCount> channels = dateEntry.getValue().entrySet().stream()
                    .map(e -> new UpcomingPlanChannelCount(e.getKey(), e.getValue().size(),
                            List.copyOf(e.getValue())))
                    .toList();
            groups.add(new UpcomingPlanDateGroup(dateEntry.getKey(), channels));
        }
        logPlan(groups);
        return groups;
    }

    /**
     * Restricted to today and later - what the Idea Review &amp; Planning screen's calendars
     * highlight, since a planner can only ever pick today or a future date. Purely a presentation
     * filter applied AFTER the aggregation has computed everything: no second query, no second
     * counting rule, and the KPI Dashboard's own list is untouched by it (that screen deliberately
     * still shows every outstanding date, past included).
     */
    private List<UpcomingPlanDateGroup> fromToday(List<UpcomingPlanDateGroup> groups) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return groups.stream().filter(g -> !g.getPlannedLiveDate().isBefore(today)).toList();
    }

    /** Planned Live Date calendar data: today onwards. */
    @Transactional(readOnly = true)
    public List<UpcomingPlanDateGroup> upcomingChannelPlanFromToday() {
        return fromToday(upcomingChannelPlan());
    }

    /** Shoot Date calendar data: today onwards. */
    @Transactional(readOnly = true)
    public List<UpcomingPlanDateGroup> plannedShootPlanFromToday() {
        return fromToday(plannedShootPlan());
    }

    /** Edit Date calendar data: today onwards. */
    @Transactional(readOnly = true)
    public List<UpcomingPlanDateGroup> plannedEditPlanFromToday() {
        return fromToday(plannedEditPlan());
    }

    // TEMPORARY DIAGNOSTIC - remove once the distinct-Content-ID rollout has been confirmed.
    // Prints exactly what this method hands the KPI list, the KPI calendar and the Planned Live
    // Date calendar, so a rendered screen can be compared against the aggregation line by line.
    // Emitted at DEBUG, so it is silent under the default INFO level; enable with:
    //   logging.level.com.kcpc.mkt.reporting.service.UpcomingChannelPlanService=DEBUG
    private void logPlan(List<UpcomingPlanDateGroup> groups) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("[UpcomingChannelPlan] counting unit = DISTINCT contentPlan.contentId per (date, channel)");
        log.debug("[UpcomingChannelPlan] date | channel | count | uniqueContentIds");
        for (UpcomingPlanDateGroup group : groups) {
            for (UpcomingPlanChannelCount channel : group.getChannels()) {
                log.debug("[UpcomingChannelPlan] {} | {} | {} | {}", group.getPlannedLiveDate(),
                        channel.getChannelHandle(), channel.getCount(), channel.getContentIds());
                if (channel.getCount() != channel.getContentIds().size()) {
                    log.warn("[UpcomingChannelPlan] MISMATCH date={} channel={} count={} contentIds.size={}",
                            group.getPlannedLiveDate(), channel.getChannelHandle(), channel.getCount(),
                            channel.getContentIds().size());
                }
            }
        }
    }
}
