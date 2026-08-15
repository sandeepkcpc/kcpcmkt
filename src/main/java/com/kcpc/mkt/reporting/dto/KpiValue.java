package com.kcpc.mkt.reporting.dto;

import java.util.Map;

/**
 * One governed KPI's computed value (BFD §7, KPI-001..030). {@code breakdown} is populated only
 * for the distribution-shaped KPIs (006, 015, 016, 029); scalar KPIs leave it null.
 * {@code displayValue} is pre-formatted (a count, "12.34%", "5.2 days", or "N/A" when the
 * denominator is zero/not-yet-measurable - never a fabricated 0).
 *
 * <p>Deliberately a plain class with {@code getX()} accessors, not a {@code record}: this object
 * is read directly by JSP EL (the 30-KPI Console screen), and classic JSP EL's bean resolver does
 * not recognize a record's no-prefix accessor methods (`displayValue()`) as JavaBean properties -
 * only `getDisplayValue()` - so a record here would throw `PropertyNotFoundException` at render
 * time (found by actually running the screen, not by compiling; see ENG-031).
 */
public class KpiValue {

    private final String code;
    private final String label;
    private final String category;
    private final String displayValue;
    private final Map<String, Long> breakdown;

    public KpiValue(String code, String label, String category, String displayValue, Map<String, Long> breakdown) {
        this.code = code;
        this.label = label;
        this.category = category;
        this.displayValue = displayValue;
        this.breakdown = breakdown;
    }

    public static KpiValue scalar(String code, String label, String category, String displayValue) {
        return new KpiValue(code, label, category, displayValue, null);
    }

    public static KpiValue distribution(String code, String label, String category, Map<String, Long> breakdown) {
        long total = breakdown.values().stream().mapToLong(Long::longValue).sum();
        return new KpiValue(code, label, category, String.valueOf(total), breakdown);
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getCategory() {
        return category;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public Map<String, Long> getBreakdown() {
        return breakdown;
    }
}
