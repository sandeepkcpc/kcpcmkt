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

/** ERD-TBL-038: queryable actual recorded Cameraperson shoot participants for self-approval verification. */
@Entity
@Table(name = "shooting_execution_participants")
@AttributeOverride(name = "id", column = @Column(name = "participant_id"))
public class ShootingExecutionParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shooting_assignment_id")
    private ShootingAssignment shootingAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "cameraperson_user_id", nullable = false)
    private User cameraperson;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected ShootingExecutionParticipant() {
    }

    public ShootingExecutionParticipant(ShootingAssignment shootingAssignment, ContentPlan contentPlan, User cameraperson) {
        this.shootingAssignment = shootingAssignment;
        this.contentPlan = contentPlan;
        this.cameraperson = cameraperson;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public User getCameraperson() {
        return cameraperson;
    }
}
