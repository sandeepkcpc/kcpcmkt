package com.kcpc.mkt.workflow.domain;

/**
 * ERD-TBL-006 workflow_concepts: the governed 19 workflow concepts (BFD §6.8 Status Catalogue).
 * 14 Active, 1 Dormant (RETAINED), 2 Terminal (REJECTED, CANCELLED), 1 Closed/Reopenable
 * (COMPLETED), 1 Supplementary Flag (DELAYED - never a primary status).
 *
 * <p>Planning is not a separate active-workflow stage: {@code IdeaService#approve} creates a fresh
 * Content Plan already fully planned and transitions it straight {@code PA -> SA}.
 */
public enum WorkflowStatus {
    IS(1, "Idea Submitted", Classification.ACTIVE),
    PA(2, "Pending Approval", Classification.ACTIVE),
    RJ(3, "Rejected", Classification.TERMINAL),
    RET(4, "Retained", Classification.DORMANT),
    SA(8, "Shoot Assigned", Classification.ACTIVE),
    SIP(9, "Shoot In Progress", Classification.ACTIVE),
    SRV(10, "Shoot Review", Classification.ACTIVE),
    SAP(11, "Shoot Approved", Classification.ACTIVE),
    EA(12, "Edit Assigned", Classification.ACTIVE),
    ED(13, "Editing", Classification.ACTIVE),
    ERV(14, "Edit Review", Classification.ACTIVE),
    EAP(15, "Edit Approved", Classification.ACTIVE),
    RFP(16, "Ready for Publishing", Classification.ACTIVE),
    PUBG(17, "Publishing", Classification.ACTIVE),
    PP(18, "Performance Pending", Classification.ACTIVE),
    PFUP(19, "Performance Update", Classification.ACTIVE),
    COMP(20, "Completed", Classification.CLOSED),
    DLY(21, "Delayed", Classification.SUPPLEMENTARY_FLAG),
    CAN(22, "Cancelled", Classification.TERMINAL);

    public enum Classification { ACTIVE, DORMANT, TERMINAL, CLOSED, SUPPLEMENTARY_FLAG }

    private final int conceptNumber;
    private final String statusName;
    private final Classification classification;

    WorkflowStatus(int conceptNumber, String statusName, Classification classification) {
        this.conceptNumber = conceptNumber;
        this.statusName = statusName;
        this.classification = classification;
    }

    public int conceptNumber() {
        return conceptNumber;
    }

    public String statusName() {
        return statusName;
    }

    /** JSP-EL-safe getter alias for {@link #statusName()} - used to display the name, not the code, on screen. */
    public String getStatusName() {
        return statusName;
    }

    public Classification classification() {
        return classification;
    }

    public boolean isPrimaryStatus() {
        return this != DLY;
    }
}
