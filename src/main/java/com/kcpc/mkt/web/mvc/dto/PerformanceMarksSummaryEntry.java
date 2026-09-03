package com.kcpc.mkt.web.mvc.dto;

import java.math.BigDecimal;

/**
 * "/app/my-performance" Marks Summary row - one (Stage, Role) group of the employee's own marked
 * work within the selected date range (e.g. "Shoot (Model)", "Edit (Editor)"). Plain class, not a
 * record: rendered directly by a JSP (ENG-031).
 */
public class PerformanceMarksSummaryEntry {

    private final String label;
    private final BigDecimal total;
    private final BigDecimal max;
    private final int percent;

    public PerformanceMarksSummaryEntry(String label, BigDecimal total, BigDecimal max, int percent) {
        this.label = label;
        this.total = total;
        this.max = max;
        this.percent = percent;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public BigDecimal getMax() {
        return max;
    }

    public int getPercent() {
        return percent;
    }
}
