package com.kcpc.mkt.workflow.domain;

/**
 * Content Detail's stage-aware navigation and Action Center both need one shared answer to "what
 * stage is this deliverable actually in right now" - derived purely from the real WorkflowStatus,
 * never from a Pipeline filter tab (e.g. "Needs Attention" is a filter lens, not a stage) and never
 * from whichever Content Detail tab happens to be selected. Distinct from {@link LifecycleStage}
 * (permission-scoping vocabulary - 7 values, no COMPLETED concept, ADMINISTRATIVE is not a content
 * stage) - this is a smaller, display/eligibility-oriented vocabulary matching the tabs Content
 * Detail actually has (Planning/Shoot/Edit/Publishing/Performance) plus the two buckets outside
 * that tab set (Completed, and everything before Planning or terminal-without-Completing).
 */
public enum ContentCanonicalStage {
    PLANNING("Planning"),
    SHOOTING("Shoot"),
    EDITING("Edit"),
    PUBLISHING("Publishing"),
    PERFORMANCE("Performance"),
    COMPLETED("Completed"),
    OTHER("Overview");

    private final String label;

    ContentCanonicalStage(String label) {
        this.label = label;
    }

    /** JSP-EL-safe getter alias - the human-readable label shown as "Current Stage: &lt;label&gt;". */
    public String getLabel() {
        return label;
    }

    /** The one resolver every consumer (Action Center, and any future Pipeline deep-link/Content
     * Detail tab-selection work) must reuse - never a second, parallel Status-&gt;Stage mapping. */
    public static ContentCanonicalStage forStatus(WorkflowStatus status) {
        return switch (status) {
            case PL, PLRV, PLAP -> PLANNING;
            case SA, SIP, SRV, SAP -> SHOOTING;
            case EA, ED, ERV, EAP -> EDITING;
            case RFP, PUBG -> PUBLISHING;
            case PP, PFUP -> PERFORMANCE;
            case COMP -> COMPLETED;
            case IS, PA, RJ, RET, CAN, DLY -> OTHER;
        };
    }
}
