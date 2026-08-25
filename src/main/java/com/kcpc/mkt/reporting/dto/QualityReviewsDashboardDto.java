package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;
import java.util.List;

/** KPI Dashboard -&gt; Quality &amp; Reviews (spec §28-32). */
public class QualityReviewsDashboardDto {

    private final BigDecimal firstPassApprovalRatePercent;
    private final BigDecimal overallReworkRatePercent;
    private final Double avgReviewTurnaroundDays;
    private final long pendingReviews;
    private final BigDecimal ideaRejectionRatePercent;
    private final long evidenceCorrectionCount;
    private final List<ReviewStageRow> reworkByStage;
    private final List<ReviewStageRow> stageWisePerformance;

    public QualityReviewsDashboardDto(BigDecimal firstPassApprovalRatePercent, BigDecimal overallReworkRatePercent,
                                       Double avgReviewTurnaroundDays, long pendingReviews,
                                       BigDecimal ideaRejectionRatePercent, long evidenceCorrectionCount,
                                       List<ReviewStageRow> reworkByStage, List<ReviewStageRow> stageWisePerformance) {
        this.firstPassApprovalRatePercent = firstPassApprovalRatePercent;
        this.overallReworkRatePercent = overallReworkRatePercent;
        this.avgReviewTurnaroundDays = avgReviewTurnaroundDays;
        this.pendingReviews = pendingReviews;
        this.ideaRejectionRatePercent = ideaRejectionRatePercent;
        this.evidenceCorrectionCount = evidenceCorrectionCount;
        this.reworkByStage = reworkByStage;
        this.stageWisePerformance = stageWisePerformance;
    }

    public BigDecimal getFirstPassApprovalRatePercent() {
        return firstPassApprovalRatePercent;
    }

    public BigDecimal getOverallReworkRatePercent() {
        return overallReworkRatePercent;
    }

    public Double getAvgReviewTurnaroundDays() {
        return avgReviewTurnaroundDays;
    }

    public long getPendingReviews() {
        return pendingReviews;
    }

    public BigDecimal getIdeaRejectionRatePercent() {
        return ideaRejectionRatePercent;
    }

    public long getEvidenceCorrectionCount() {
        return evidenceCorrectionCount;
    }

    public List<ReviewStageRow> getReworkByStage() {
        return reworkByStage;
    }

    public List<ReviewStageRow> getStageWisePerformance() {
        return stageWisePerformance;
    }
}
