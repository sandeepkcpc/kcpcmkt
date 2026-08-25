package com.kcpc.mkt.reporting.dto;

/** Generic label+count row - reused for Content Mix by Type, Platform Distribution, and Channel
 * Distribution (all structurally identical: a dynamic label paired with a real count). */
public class LabelCountRow {

    private final String label;
    private final long count;

    public LabelCountRow(String label, long count) {
        this.label = label;
        this.count = count;
    }

    public String getLabel() {
        return label;
    }

    public long getCount() {
        return count;
    }
}
