package com.kcpc.mkt.identity.dto;

/**
 * User Detail "Permission Summary" card counts - derived from the same
 * {@link PermissionManagementRow} list rendered in the unified table below it, so the two can
 * never disagree. Counts permission CODES, never grant rows (a permission with 2 simultaneously-
 * active scoped grants still counts once - see {@link PermissionManagementRow#isMulti()}).
 * Restricted is a SUBSET of Granted, never added on top of it.
 *
 * <p>Plain class with {@code getX()} accessors, not a {@code record} - see
 * {@link com.kcpc.mkt.reporting.dto.KpiValue}'s class doc for why a record's no-prefix accessors
 * break classic JSP EL property resolution (ENG-031).
 */
public class PermissionSummaryCounts {

    private final int total;
    private final int granted;
    private final int restricted;
    private final int notGranted;

    public PermissionSummaryCounts(int total, int granted, int restricted, int notGranted) {
        this.total = total;
        this.granted = granted;
        this.restricted = restricted;
        this.notGranted = notGranted;
    }

    public int getTotal() {
        return total;
    }

    public int getGranted() {
        return granted;
    }

    public int getRestricted() {
        return restricted;
    }

    public int getNotGranted() {
        return notGranted;
    }
}
