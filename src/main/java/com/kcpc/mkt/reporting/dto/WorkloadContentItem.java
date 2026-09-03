package com.kcpc.mkt.reporting.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Team Workload dashboard - Content ID drill-down (Employee -&gt; Stage -&gt; Content IDs): one
 * surviving workload record inside an {@link AssigneeLoadRow}, captured from the SAME
 * {@code ContentPlan} already in scope at the exact point {@link
 * com.kcpc.mkt.reporting.service.TeamWorkloadService} decides that record counts - never a second
 * query, never a fabricated status (every item here is, by construction, one this screen's own
 * active-window filter already let through, so it is always exactly one of Active/Delayed - no
 * third state is possible here). {@code contentPlanId} is the internal UUID {@code
 * DeliverableMvcController}'s existing {@code /app/deliverables/{id}} route takes; {@code
 * contentId} is the business-facing id (e.g. "C-0926-0001") - the UUID is only ever used to build
 * the link's href, never shown as text. Plain class, not a record: rendered directly by a JSP,
 * whose EL only recognizes getX()/isX() JavaBean accessors (ENG-031).
 */
public class WorkloadContentItem {

    private final UUID contentPlanId;
    private final String contentId;
    private final Integer delayDays;
    private final LocalDate applicableDate;

    public WorkloadContentItem(UUID contentPlanId, String contentId, Integer delayDays, LocalDate applicableDate) {
        this.contentPlanId = contentPlanId;
        this.contentId = contentId;
        this.delayDays = delayDays;
        this.applicableDate = applicableDate;
    }

    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getContentId() {
        return contentId;
    }

    public Integer getDelayDays() {
        return delayDays;
    }

    /** {@code true} whenever this specific item is currently delayed - drives the "Active" vs
     * "Delayed - N day(s)" label in the JSP; never a third status, per this class's own javadoc. */
    public boolean isDelayed() {
        return delayDays != null;
    }

    public LocalDate getApplicableDate() {
        return applicableDate;
    }
}
