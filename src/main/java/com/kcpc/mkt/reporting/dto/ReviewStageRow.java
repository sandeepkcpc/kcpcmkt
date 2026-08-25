package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;

/** Stage-wise Review Performance row (Planning/Shoot/Edit only - Publishing has no ReviewCycle
 * gate, Idea Review is reported separately since its decision semantics differ). */
public class ReviewStageRow {

    private final String stage;
    private final long totalReviews;
    private final long firstCycleReviews;
    private final long firstPassApproved;
    private final long rework;
    private final Double avgReviewTimeDays;
    private final BigDecimal firstPassApprovedPercent;
    private final BigDecimal reworkPercent;

    public ReviewStageRow(String stage, long totalReviews, long firstCycleReviews, long firstPassApproved, long rework,
                           Double avgReviewTimeDays, BigDecimal firstPassApprovedPercent, BigDecimal reworkPercent) {
        this.stage = stage;
        this.totalReviews = totalReviews;
        this.firstCycleReviews = firstCycleReviews;
        this.firstPassApproved = firstPassApproved;
        this.rework = rework;
        this.avgReviewTimeDays = avgReviewTimeDays;
        this.firstPassApprovedPercent = firstPassApprovedPercent;
        this.reworkPercent = reworkPercent;
    }

    public String getStage() {
        return stage;
    }

    public long getTotalReviews() {
        return totalReviews;
    }

    /** Denominator for {@link #getFirstPassApprovedPercent()} - cycle_number=1 decided reviews only,
     * never all-cycles {@link #getTotalReviews()} (a stage with rework has more total decided cycles
     * than first-cycle ones). */
    public long getFirstCycleReviews() {
        return firstCycleReviews;
    }

    public long getFirstPassApproved() {
        return firstPassApproved;
    }

    public long getRework() {
        return rework;
    }

    public Double getAvgReviewTimeDays() {
        return avgReviewTimeDays;
    }

    public BigDecimal getFirstPassApprovedPercent() {
        return firstPassApprovedPercent;
    }

    public BigDecimal getReworkPercent() {
        return reworkPercent;
    }
}
