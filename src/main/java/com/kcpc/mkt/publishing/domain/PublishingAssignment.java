package com.kcpc.mkt.publishing.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Multi-Publisher task assignments for the Publishing stage, mirroring
 * {@link com.kcpc.mkt.production.domain.ShootingAssignment} / {@code EditingAssignment} (ERD-TBL-013/014).
 * Not present in the frozen ERD - see docs/IMPLEMENTATION_DECISIONS.md ENG-035. Purely additive tracking;
 * does not gate Start Publishing, which remains governed solely by PERM_08_PUBLISHING_EXECUTION.
 */
@Entity
@Table(name = "publishing_assignments")
@AttributeOverride(name = "id", column = @Column(name = "assignment_id"))
public class PublishingAssignment extends BaseEntity {

    // EAGER: read by self-service DTO mapping outside any open transaction - see ENG-005.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "publisher_user_id", nullable = false)
    private User publisher;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_user_id", nullable = false)
    private User assignedBy;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "ended_at")
    private Instant endedAt;

    protected PublishingAssignment() {
    }

    public PublishingAssignment(ContentPlan contentPlan, User publisher, User assignedBy) {
        this.contentPlan = contentPlan;
        this.publisher = publisher;
        this.assignedBy = assignedBy;
    }

    public void end() {
        this.active = false;
        this.endedAt = Instant.now();
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public User getPublisher() {
        return publisher;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
