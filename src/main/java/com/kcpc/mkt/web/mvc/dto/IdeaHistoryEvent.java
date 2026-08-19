package com.kcpc.mkt.web.mvc.dto;

import java.time.Instant;

/**
 * One idea-lifecycle-only event (Submitted/Approved/Retained/Rejected/Reopened) for the "Idea
 * Decision History" panel (ENG-061) - deliberately re-labeled from the raw {@code WorkflowStatus}
 * from/to codes so the panel never leaks a downstream Planning/Shoot/Edit/Publishing status name
 * (e.g. "Planning") into what is meant to be Idea-domain-only history. Plain class, not a record:
 * rendered directly by a JSP, whose EL only recognizes getX() JavaBean accessors (ENG-031).
 */
public class IdeaHistoryEvent {

    private final String eventLabel;
    private final Instant timestamp;
    private final String triggeredByName;
    private final String reason;

    public IdeaHistoryEvent(String eventLabel, Instant timestamp, String triggeredByName, String reason) {
        this.eventLabel = eventLabel;
        this.timestamp = timestamp;
        this.triggeredByName = triggeredByName;
        this.reason = reason;
    }

    public String getEventLabel() {
        return eventLabel;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTriggeredByName() {
        return triggeredByName;
    }

    public String getReason() {
        return reason;
    }
}
