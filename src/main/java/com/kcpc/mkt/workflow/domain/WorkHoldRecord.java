package com.kcpc.mkt.workflow.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
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

/**
 * ERD-TBL-043: BR-063 In-Progress Work Hold &amp; Resume. Primary workflow status is
 * deliberately untouched by Hold/Resume (ERD-CON-061) - this is a parallel administrative
 * record, not a 23rd workflow status.
 */
@Entity
@Table(name = "work_hold_records")
@AttributeOverride(name = "id", column = @Column(name = "hold_record_id"))
public class WorkHoldRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "held_status_code", nullable = false, length = 10)
    private WorkflowStatus heldStatusCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "held_by_user_id", nullable = false)
    private User heldBy;

    @CreationTimestamp
    @Column(name = "held_at", nullable = false, updatable = false)
    private Instant heldAt;

    @Column(name = "hold_reason", nullable = false, columnDefinition = "text")
    private String holdReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resumed_by_user_id")
    private User resumedBy;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    protected WorkHoldRecord() {
    }

    public WorkHoldRecord(WorkflowInstance workflowInstance, WorkflowStatus heldStatusCode, User heldBy, String holdReason) {
        if (heldStatusCode != WorkflowStatus.SIP && heldStatusCode != WorkflowStatus.ED) {
            throw new IllegalArgumentException("Hold is only permitted while SIP or ED (ERD-CON-061)");
        }
        this.workflowInstance = workflowInstance;
        this.heldStatusCode = heldStatusCode;
        this.heldBy = heldBy;
        this.holdReason = holdReason;
    }

    /** ERD-CON-062 rules 3/7: resumed_by and resumed_at transition together, exactly once. */
    public void resume(User resumedBy, Instant resumedAt) {
        if (this.resumedAt != null) {
            throw new IllegalStateException("Hold is already resumed and fully immutable (ERD-CON-062 rule 8)");
        }
        this.resumedBy = resumedBy;
        this.resumedAt = resumedAt;
    }

    public boolean isOpen() {
        return resumedAt == null;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public WorkflowStatus getHeldStatusCode() {
        return heldStatusCode;
    }

    public User getHeldBy() {
        return heldBy;
    }

    public Instant getHeldAt() {
        return heldAt;
    }

    public String getHoldReason() {
        return holdReason;
    }

    public Instant getResumedAt() {
        return resumedAt;
    }
}
