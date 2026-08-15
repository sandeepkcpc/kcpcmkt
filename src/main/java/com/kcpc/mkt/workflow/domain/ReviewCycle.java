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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * ERD-TBL-015 review_cycles: review submission/decision record backing every gate (Idea,
 * Planning, Shoot, Edit Review) and the self-approval-conflict verification trail.
 * Decision fields are immutable once decided (ERD-CON-039, enforced again by DB trigger).
 */
@Entity
@Table(name = "review_cycles",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workflow_instance_id", "gate_type", "cycle_number"}))
@AttributeOverride(name = "id", column = @Column(name = "review_cycle_id"))
public class ReviewCycle extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_type", nullable = false, length = 20)
    private GateType gateType;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedBy;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_user_id")
    private User reviewer;

    @Column(name = "decision", length = 20)
    private String decision;

    @Column(name = "decision_reason", columnDefinition = "text")
    private String decisionReason;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected ReviewCycle() {
    }

    public ReviewCycle(WorkflowInstance workflowInstance, GateType gateType, int cycleNumber, User submittedBy) {
        this.workflowInstance = workflowInstance;
        this.gateType = gateType;
        this.cycleNumber = cycleNumber;
        this.submittedBy = submittedBy;
    }

    /** ERD-CON-039: callable exactly once - decided_at becomes non-null and the DB trigger then locks the row. */
    public void decide(User reviewer, String decision, String decisionReason, PermissionGrant actingPermissionGrant) {
        if (this.decidedAt != null) {
            throw new IllegalStateException("Review cycle decision is immutable once made (ERD-CON-039)");
        }
        this.reviewer = reviewer;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.actingPermissionGrant = actingPermissionGrant;
        this.decidedAt = Instant.now();
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public GateType getGateType() {
        return gateType;
    }

    public int getCycleNumber() {
        return cycleNumber;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public User getReviewer() {
        return reviewer;
    }

    public String getDecision() {
        return decision;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
