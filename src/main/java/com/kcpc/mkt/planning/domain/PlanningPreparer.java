package com.kcpc.mkt.planning.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** ERD-TBL-037: authoritative queryable Planning preparer provenance for the self-approval-conflict guard. */
@Entity
@Table(name = "planning_preparers")
@AttributeOverride(name = "id", column = @Column(name = "preparer_id"))
public class PlanningPreparer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "preparer_user_id", nullable = false)
    private User preparer;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected PlanningPreparer() {
    }

    public PlanningPreparer(ContentPlan contentPlan, User preparer) {
        this.contentPlan = contentPlan;
        this.preparer = preparer;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public User getPreparer() {
        return preparer;
    }
}
