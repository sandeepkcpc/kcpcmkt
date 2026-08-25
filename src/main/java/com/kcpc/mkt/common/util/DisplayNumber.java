package com.kcpc.mkt.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * Shared numeric display helpers for report screens (KPI Dashboard formatting pass): singular/
 * plural "day"/"days" duration formatting, and thousands-separated whole-number formatting for
 * averaged count metrics (e.g. Avg Impressions). Centralized here so no JSP fragment duplicates
 * this rounding/pluralization logic; never applied to percentage metrics, which keep their own
 * formatting.
 */
public final class DisplayNumber {

    private DisplayNumber() {
    }

    /** "-" for null (no applicable data - never confused with a genuinely-zero measured duration);
     * otherwise rounded to 1 decimal, "1 day" singular only at exactly 1 (after rounding),
     * "0 days"/"2 days"/"3.4 days" otherwise. */
    public static String days(Number value) {
        if (value == null) {
            return "-";
        }
        BigDecimal rounded = BigDecimal.valueOf(value.doubleValue()).setScale(1, RoundingMode.HALF_UP);
        boolean whole = rounded.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0;
        String numeric = whole ? rounded.setScale(0, RoundingMode.HALF_UP).toPlainString() : rounded.toPlainString();
        String unit = rounded.compareTo(BigDecimal.ONE) == 0 ? "day" : "days";
        return numeric + " " + unit;
    }

    /** Averaged count metric (e.g. Avg Impressions): thousands-separated whole number, "-" for
     * null. Never used for percentage metrics (CTR keeps its own formatting). */
    public static String count(Number value) {
        if (value == null) {
            return "-";
        }
        DecimalFormat format = new DecimalFormat("#,##0");
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(value.doubleValue());
    }
}
