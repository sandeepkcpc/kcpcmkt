package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;

/**
 * Idea -&gt; Publish funnel (spec §10, formula locked by ENG-097): Submitted -&gt; Approved (with
 * Retained/Rejected shown separately, never silently merged) -&gt; Planned -&gt; Published.
 * {@code approvalRate} is {@code approved / submitted} - NOT {@code approved / (approved +
 * rejected)}. Submitted is the sole denominator; Retained/Rejected remain visible funnel outcomes
 * only, never separate denominators. {@code null} (never a fabricated {@code 0%}) when submitted
 * is zero.
 */
public class IdeaFunnelDto {

    private final long submitted;
    private final long approved;
    private final long retained;
    private final long rejected;
    private final long planned;
    private final long published;
    private final BigDecimal approvalRate;

    public IdeaFunnelDto(long submitted, long approved, long retained, long rejected, long planned, long published,
                          BigDecimal approvalRate) {
        this.submitted = submitted;
        this.approved = approved;
        this.retained = retained;
        this.rejected = rejected;
        this.planned = planned;
        this.published = published;
        this.approvalRate = approvalRate;
    }

    public long getSubmitted() {
        return submitted;
    }

    public long getApproved() {
        return approved;
    }

    public long getRetained() {
        return retained;
    }

    public long getRejected() {
        return rejected;
    }

    public long getPlanned() {
        return planned;
    }

    public long getPublished() {
        return published;
    }

    public BigDecimal getApprovalRate() {
        return approvalRate;
    }
}
