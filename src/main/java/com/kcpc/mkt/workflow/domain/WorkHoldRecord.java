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
import java.time.LocalDate;

/**
 * ERD-TBL-043: BR-063 In-Progress Work Hold &amp; Resume. Primary workflow status is
 * deliberately untouched by Hold/Resume (ERD-CON-061) - this is a parallel administrative
 * record, not a 23rd workflow status. Permitted while Shoot In Progress, Editing, or Publishing
 * (SIP/ED/PUBG) - the three in-progress execution stages an urgent reassignment can realistically
 * interrupt; Idea Review/Planning Review/Performance/Completed are deliberately excluded.
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

    // EAGER (matching WorkflowTransitionHistory#triggeredBy's own precedent) - heldBy is always
    // displayed directly by non-@Transactional MVC controllers (Task Detail pages, Content Detail's
    // Hold History), which would otherwise hit LazyInitializationException the moment the session
    // that loaded the record is gone.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "held_by_user_id", nullable = false)
    private User heldBy;

    @CreationTimestamp
    @Column(name = "held_at", nullable = false, updatable = false)
    private Instant heldAt;

    @Column(name = "hold_reason", nullable = false, columnDefinition = "text")
    private String holdReason;

    /** Additive, optional (request section 2) - purely informational, never read by any workflow
     * rule; a manager who wants to actually move a planned date still uses Reschedule (section 7). */
    @Column(name = "expected_resume_date")
    private LocalDate expectedResumeDate;

    // Same EAGER reasoning as heldBy above - the Hold History block reads it directly, once set.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resumed_by_user_id")
    private User resumedBy;

    @Column(name = "resumed_at")
    private Instant resumedAt;

    protected WorkHoldRecord() {
    }

    public WorkHoldRecord(WorkflowInstance workflowInstance, WorkflowStatus heldStatusCode, User heldBy,
                           String holdReason, LocalDate expectedResumeDate) {
        if (heldStatusCode != WorkflowStatus.SIP && heldStatusCode != WorkflowStatus.ED
                && heldStatusCode != WorkflowStatus.PUBG) {
            throw new IllegalArgumentException("Hold is only permitted while SIP, ED, or PUBG (ERD-CON-061)");
        }
        this.workflowInstance = workflowInstance;
        this.heldStatusCode = heldStatusCode;
        this.heldBy = heldBy;
        this.holdReason = holdReason;
        this.expectedResumeDate = expectedResumeDate;
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

    public LocalDate getExpectedResumeDate() {
        return expectedResumeDate;
    }

    public Instant getResumedAt() {
        return resumedAt;
    }

    public User getResumedBy() {
        return resumedBy;
    }
}
