package com.kcpc.mkt.reporting.dto;

/**
 * One row of the Overview "Attention Needed" list. {@code linkHref} points to an existing
 * operational screen (Delayed Deliverables / Reviews / Team Workload / Performance) - the KPI
 * Dashboard never duplicates that screen's own detail, only links to it.
 */
public class AttentionItem {

    private final String message;
    private final long count;
    private final String linkHref;

    public AttentionItem(String message, long count, String linkHref) {
        this.message = message;
        this.count = count;
        this.linkHref = linkHref;
    }

    public String getMessage() {
        return message;
    }

    public long getCount() {
        return count;
    }

    public String getLinkHref() {
        return linkHref;
    }
}
