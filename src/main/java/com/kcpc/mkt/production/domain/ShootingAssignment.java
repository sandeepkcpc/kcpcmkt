package com.kcpc.mkt.production.domain;

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
 * ERD-TBL-013: multi-Cameraperson shooting task assignments. Initial assignment during Planning
 * (Permission #4); later replacement is Reassign only (Permission #11) - see build-prompt §18.
 */
@Entity
@Table(name = "shooting_assignments")
@AttributeOverride(name = "id", column = @Column(name = "assignment_id"))
public class ShootingAssignment extends BaseEntity {

    // EAGER: read by self-service DTO mapping outside any open transaction - see ENG-005.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cameraperson_user_id", nullable = false)
    private User cameraperson;

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

    protected ShootingAssignment() {
    }

    public ShootingAssignment(ContentPlan contentPlan, User cameraperson, User assignedBy) {
        this.contentPlan = contentPlan;
        this.cameraperson = cameraperson;
        this.assignedBy = assignedBy;
    }

    public void end() {
        this.active = false;
        this.endedAt = Instant.now();
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public User getCameraperson() {
        return cameraperson;
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
