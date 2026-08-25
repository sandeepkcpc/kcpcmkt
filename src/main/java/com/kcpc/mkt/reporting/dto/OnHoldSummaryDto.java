package com.kcpc.mkt.reporting.dto;

/**
 * On Hold summary (spec §14). {@code openCount} is currently-open holds ({@code resumedAt IS
 * NULL}) - resumed historical holds never count as currently on hold. {@code
 * resumedHoldCountInRange} is the explicit, unambiguous availability signal for the duration
 * metrics below it (a plain {@code COUNT(*)}, never inferred from whether an AVG/MAX query
 * happened to come back null) - {@code avgHoldDurationDays}/{@code longestHoldDurationDays} are
 * computed over RESUMED (completed) holds within the selected period only, and are {@code null}
 * whenever {@code resumedHoldCountInRange == 0} (no applicable data - never a fabricated 0 days).
 */
public class OnHoldSummaryDto {

    private final long openCount;
    private final long resumedHoldCountInRange;
    private final Double avgHoldDurationDays;
    private final Double longestHoldDurationDays;

    public OnHoldSummaryDto(long openCount, long resumedHoldCountInRange, Double avgHoldDurationDays,
                             Double longestHoldDurationDays) {
        this.openCount = openCount;
        this.resumedHoldCountInRange = resumedHoldCountInRange;
        this.avgHoldDurationDays = avgHoldDurationDays;
        this.longestHoldDurationDays = longestHoldDurationDays;
    }

    public long getOpenCount() {
        return openCount;
    }

    /** Explicit availability signal for {@link #getAvgHoldDurationDays()}/
     * {@link #getLongestHoldDurationDays()} - 0 means no applicable data (render "-"), never
     * inferred from the duration fields themselves being null. */
    public long getResumedHoldCountInRange() {
        return resumedHoldCountInRange;
    }

    public Double getAvgHoldDurationDays() {
        return avgHoldDurationDays;
    }

    public Double getLongestHoldDurationDays() {
        return longestHoldDurationDays;
    }
}
