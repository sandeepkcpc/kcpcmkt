package com.kcpc.mkt.reporting.dto;

import java.util.List;

/** One Channel/Account's still-outstanding planned-content count for one Planned Live Date, inside
 * {@link UpcomingPlanDateGroup}. The counting unit is one DISTINCT Content Plan
 * ({@code content_plans.content_id}): a Content Plan targeting Instagram + YouTube + Facebook all
 * under this same Channel/Account counts ONCE, not three times. Eligibility is still decided per
 * {@code planned_output_publication_target_mappings} row - a row counts while it has no matching
 * {@code actual_publication_events} row - so a Content ID stays counted until every one of its
 * targets on this Channel/Account has gone live (see
 * {@code KpiDashboardService#upcomingChannelPlan}).
 *
 * <p>{@code contentIds} is exactly those distinct Content Plan IDs, in first-seen order, captured
 * from the data already being iterated to compute {@code count} - never a second query - and
 * rendered by the Overview calendar's "Content Details" section. {@code count == contentIds.size()}
 * always, and both are the number of distinct pieces of content. Note the same Content ID may still
 * appear under several DIFFERENT Channel/Accounts on the same date; those are separate publication
 * commitments and each is counted once under its own channel. */
public class UpcomingPlanChannelCount {

    private final String channelHandle;
    private final long count;
    private final List<String> contentIds;

    public UpcomingPlanChannelCount(String channelHandle, long count, List<String> contentIds) {
        this.channelHandle = channelHandle;
        this.count = count;
        this.contentIds = contentIds;
    }

    public String getChannelHandle() {
        return channelHandle;
    }

    public long getCount() {
        return count;
    }

    public List<String> getContentIds() {
        return contentIds;
    }
}
