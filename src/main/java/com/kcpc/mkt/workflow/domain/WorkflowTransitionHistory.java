package com.kcpc.mkt.workflow.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** ERD-TBL-008: append-only system-generated transition log (ERD-CON-034, ERD-CON-035/058). */
@Entity
@Table(name = "workflow_transition_history")
@AttributeOverride(name = "id", column = @Column(name = "transition_id"))
public class WorkflowTransitionHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status_code", nullable = false, length = 10)
    private WorkflowStatus fromStatusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status_code", nullable = false, length = 10)
    private WorkflowStatus toStatusCode;

    // EAGER: the deliverable timeline is read directly in MVC/JSP views outside any open
    // transaction (open-in-view is disabled) - see docs/IMPLEMENTATION_DECISIONS.md ENG-005.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "triggered_by_user_id", nullable = false)
    private User triggeredBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    @Column(name = "trigger_command", nullable = false, length = 100)
    private String triggerCommand;

    @Column(name = "transition_reason", columnDefinition = "text")
    private String transitionReason;

    @CreationTimestamp
    @Column(name = "transition_timestamp", nullable = false, updatable = false)
    private Instant transitionTimestamp;

    protected WorkflowTransitionHistory() {
    }

    public WorkflowTransitionHistory(WorkflowInstance workflowInstance, WorkflowStatus from, WorkflowStatus to,
                                      User triggeredBy, PermissionGrant actingPermissionGrant,
                                      String triggerCommand, String transitionReason) {
        this.workflowInstance = workflowInstance;
        this.fromStatusCode = from;
        this.toStatusCode = to;
        this.triggeredBy = triggeredBy;
        this.actingPermissionGrant = actingPermissionGrant;
        this.triggerCommand = triggerCommand;
        this.transitionReason = transitionReason;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public WorkflowStatus getFromStatusCode() {
        return fromStatusCode;
    }

    public WorkflowStatus getToStatusCode() {
        return toStatusCode;
    }

    public User getTriggeredBy() {
        return triggeredBy;
    }

    public String getTriggerCommand() {
        return triggerCommand;
    }

    public String getTransitionReason() {
        return transitionReason;
    }

    public Instant getTransitionTimestamp() {
        return transitionTimestamp;
    }
}
