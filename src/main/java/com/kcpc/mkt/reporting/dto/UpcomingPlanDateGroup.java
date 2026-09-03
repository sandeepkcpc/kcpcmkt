package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;
import java.util.List;

/** One Planned Live Date's worth of still-outstanding planned publications, grouped by
 * Channel/Account (spec: date-grouped presentation, never Platform as the grouping dimension).
 * Sorted ascending by {@code plannedLiveDate}; {@code channels} sorted by Channel/Account name. */
public class UpcomingPlanDateGroup {

    private final LocalDate plannedLiveDate;
    private final List<UpcomingPlanChannelCount> channels;

    public UpcomingPlanDateGroup(LocalDate plannedLiveDate, List<UpcomingPlanChannelCount> channels) {
        this.plannedLiveDate = plannedLiveDate;
        this.channels = channels;
    }

    public LocalDate getPlannedLiveDate() {
        return plannedLiveDate;
    }

    public List<UpcomingPlanChannelCount> getChannels() {
        return channels;
    }
}
