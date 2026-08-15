package com.kcpc.mkt.workflow.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.http.HttpStatus;

import java.time.Instant;

/**
 * ERD-TBL-007 workflow_instances: single shared lifecycle instance per deliverable (idea /
 * content plan). {@code currentStatusCode} can never be DLY (ERD-CON-004, a supplementary flag,
 * not a primary status) and direct CRUD status mutation is blocked (ERD-CON-033) - status only
 * changes via {@link #transitionTo}, always paired with a WorkflowTransitionHistory row
 * (ERD-CON-034) written by the calling service in the same transaction.
 */
@Entity
@Table(name = "workflow_instances")
@AttributeOverride(name = "id", column = @Column(name = "workflow_instance_id"))
public class WorkflowInstance extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status_code", nullable = false, length = 10)
    private WorkflowStatus currentStatusCode;

    @Column(name = "first_completed_at")
    private Instant firstCompletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkflowInstance() {
    }

    public WorkflowInstance(WorkflowStatus initialStatus) {
        if (initialStatus == WorkflowStatus.DLY) {
            throw new IllegalArgumentException("DLY is a supplementary flag, never a primary status (ERD-CON-004)");
        }
        this.currentStatusCode = initialStatus;
    }

    /** ERD-CON-033: the only sanctioned way to mutate status; callers must also append transition history. */
    public void transitionTo(WorkflowStatus newStatus) {
        if (newStatus == WorkflowStatus.DLY) {
            throw new DomainException(ErrorCode.WORKFLOW_INVALID_TRANSITION, HttpStatus.CONFLICT,
                    "DLY is a supplementary flag, never assignable as the primary status");
        }
        this.currentStatusCode = newStatus;
    }

    /** ERD-CON-005: once set, permanently immutable - enforced again at the DB trigger layer. */
    public void markFirstCompleted(Instant at) {
        if (this.firstCompletedAt != null) {
            throw new IllegalStateException("first_completed_at is immutable once set (ERD-CON-005)");
        }
        this.firstCompletedAt = at;
    }

    public boolean everCompleted() {
        return firstCompletedAt != null;
    }

    public WorkflowStatus getCurrentStatusCode() {
        return currentStatusCode;
    }

    public Instant getFirstCompletedAt() {
        return firstCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
