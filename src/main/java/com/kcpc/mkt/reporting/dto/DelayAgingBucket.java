package com.kcpc.mkt.reporting.dto;

/** One bucket of the Delay Aging distribution (0-2 / 3-5 / 6-10 / 11+ days). */
public class DelayAgingBucket {

    private final String label;
    private final long count;

    public DelayAgingBucket(String label, long count) {
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
