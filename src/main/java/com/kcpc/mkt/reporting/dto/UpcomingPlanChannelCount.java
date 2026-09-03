package com.kcpc.mkt.reporting.dto;

import java.util.List;

/** One Channel/Account's still-outstanding planned-publication-target count for one Planned Live
 * Date, inside {@link UpcomingPlanDateGroup}. The counting unit is one
 * {@code planned_output_publication_target_mappings} row that has no matching
 * {@code actual_publication_events} row yet (see {@code KpiDashboardService#upcomingChannelPlan}).
 * {@code contentIds} is the surviving mappings' own Content Plan IDs for this (date, channel) group
 * - captured from data already being iterated to compute {@code count}, never a second query - for
 * the Overview calendar's optional "Content Details" section (KPI Dashboard Overview calendar
 * enhancement). {@code count == contentIds.size()} always. */
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
