package com.kcpc.mkt.reporting.dto;

import java.math.BigDecimal;

/** Generic label+decimal-value row - reused for "Top by Avg CTR" rankings (Content Type/Platform/
 * Channel) and for ORIGINAL vs REPOST comparable-metric rows. {@code value} is never a fabricated
 * number - callers must exclude N/A scorecards before averaging (spec §35/§36). */
public class LabelValueRow {

    private final String label;
    private final BigDecimal value;
    private final Long sampleSize;

    /** Two-arg form for callers with no meaningful sample size (e.g. ORIGINAL vs REPOST comparison
     * rows, which are themselves already an average over a set never shown as a count). */
    public LabelValueRow(String label, BigDecimal value) {
        this(label, value, null);
    }

    public LabelValueRow(String label, BigDecimal value, Long sampleSize) {
        this.label = label;
        this.value = value;
        this.sampleSize = sampleSize;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getValue() {
        return value;
    }

    /** Eligible (non-N/A) scorecard count behind {@link #getValue()} - null where not applicable. */
    public Long getSampleSize() {
        return sampleSize;
    }
}
