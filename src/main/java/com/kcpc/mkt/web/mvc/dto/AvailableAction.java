package com.kcpc.mkt.web.mvc.dto;

/**
 * ENG-082: one entry in the Content Detail management "Action Center" - built server-side in
 * {@code DeliverableMvcController} from the SAME already-computed {@code canX}/{@code isXActiveAssignee}
 * permission flags and the SAME per-status conditions that used to live as scattered JSTL
 * {@code c:if}s in the old admin-actions bar (e.g. Hold/Resume only at SIP/ED, Reopen only at COMP)
 * - this DTO only consolidates WHICH actions are currently valid into one list to iterate, it never
 * re-derives or duplicates the underlying authorization/workflow-status logic itself. The JSP shows
 * only the actions present in the list (never a disabled button for an unavailable one, per the
 * "prefer hiding over disabling" UX rule) and still renders each action's own actual form fields
 * (which genuinely differ per action - Reschedule needs dates, Reassign needs a picker, a Review
 * decision needs a reason) rather than trying to generically render arbitrary form shapes from this
 * DTO. Plain class, not a record: rendered directly by a JSP, whose EL only recognizes getX()
 * JavaBean accessors (ENG-031).
 */
public class AvailableAction {

    private final String actionKey;
    private final String label;
    private final String style;
    private final String group;
    private final boolean requiresReason;

    public AvailableAction(String actionKey, String label, String style, String group, boolean requiresReason) {
        this.actionKey = actionKey;
        this.label = label;
        this.style = style;
        this.group = group;
        this.requiresReason = requiresReason;
    }

    public String getActionKey() {
        return actionKey;
    }

    public String getLabel() {
        return label;
    }

    public String getStyle() {
        return style;
    }

    public String getGroup() {
        return group;
    }

    public boolean isRequiresReason() {
        return requiresReason;
    }
}
