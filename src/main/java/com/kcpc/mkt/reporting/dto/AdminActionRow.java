package com.kcpc.mkt.reporting.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Administrative Actions report - one real, per-event row (Reassigned/Rescheduled/Hold/Resume/
 * Cancelled/Reopened/Permission Granted-Revoked), sourced directly from {@code system_audit_log}.
 * Read-only management summary - never confused with Audit History's full forensic trail, and
 * never a workflow-mutating view. Additive alongside {@code AdminReportingService
 * #administrativeActionsReport} (the aggregate counts-only method already exposed on the public
 * REST API) - that method and its REST route are untouched; this is a new, MVC-only per-row
 * projection built from the exact same {@code ACTION_TYPE_EVENT_TYPES} category mapping. Plain
 * class with {@code getX()} accessors, not a record: rendered directly by a JSP, whose EL only
 * recognizes getX() JavaBean accessors (ENG-031).
 */
public class AdminActionRow {

    private final String action;
    private final String contentOrUserDisplay;
    private final UUID contentPlanId;
    private final String performedByName;
    private final String performedByRole;
    private final Instant timestamp;
    private final String reason;

    public AdminActionRow(String action, String contentOrUserDisplay, UUID contentPlanId, String performedByName,
                           String performedByRole, Instant timestamp, String reason) {
        this.action = action;
        this.contentOrUserDisplay = contentOrUserDisplay;
        this.contentPlanId = contentPlanId;
        this.performedByName = performedByName;
        this.performedByRole = performedByRole;
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public String getAction() {
        return action;
    }

    public String getContentOrUserDisplay() {
        return contentOrUserDisplay;
    }

    /** Null unless this row's target resolved to a real Content Plan - lets the JSP link to
     * Content Detail only when that link is actually valid, never a guessed URL. */
    public UUID getContentPlanId() {
        return contentPlanId;
    }

    public String getPerformedByName() {
        return performedByName;
    }

    /** Display metadata only - never used to infer authorization (spec requirement). */
    public String getPerformedByRole() {
        return performedByRole;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Null when no reason was captured - the JSP renders "-", this class never fabricates one. */
    public String getReason() {
        return reason;
    }
}
