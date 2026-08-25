package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;

/**
 * Publishing Target Completion (spec §21/§25, denominator locked): {@code publishedCount} /
 * {@code (publishedCount + pendingCount)} - N/A-designated (PlannedOutput, PublicationTarget)
 * mappings are excluded from both numerator and denominator entirely (never pending, never
 * complete), reusing the exact same designated-N/A exclusion {@code
 * DeliverableMvcController#buildPublishingChecklist} already applies. {@code completionPercent} is
 * {@code null} only when there are zero non-N/A mappings at all.
 */
public class TargetCompletionDto {

    private final long publishedCount;
    private final long pendingCount;
    private final long naCount;
    private final BigDecimal completionPercent;

    public TargetCompletionDto(long publishedCount, long pendingCount, long naCount, BigDecimal completionPercent) {
        this.publishedCount = publishedCount;
        this.pendingCount = pendingCount;
        this.naCount = naCount;
        this.completionPercent = completionPercent;
    }

    public long getPublishedCount() {
        return publishedCount;
    }

    public long getPendingCount() {
        return pendingCount;
    }

    public long getNaCount() {
        return naCount;
    }

    public BigDecimal getCompletionPercent() {
        return completionPercent;
    }
}
