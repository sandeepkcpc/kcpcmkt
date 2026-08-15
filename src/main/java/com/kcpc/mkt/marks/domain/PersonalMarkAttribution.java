package com.kcpc.mkt.marks.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.workflow.domain.ReviewCycle;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ERD-TBL-016: qualifying-contributor Mark attribution ledger, awarded in full (never split)
 * at Shoot/Edit Approval. Absence of a row is never equivalent to a numeric 0 - no attribution
 * is created on Request Rework, Publishing, or Reposts.
 */
@Entity
@Table(name = "personal_mark_attributions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"recipient_user_id", "review_cycle_id"}))
@AttributeOverride(name = "id", column = @Column(name = "attribution_id"))
public class PersonalMarkAttribution extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType;

    // EAGER: read by self-service DTO mapping outside any open transaction (open-in-view is
    // disabled) - see docs/IMPLEMENTATION_DECISIONS.md ENG-005 for the same pattern on User.businessRole.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_cycle_id", nullable = false)
    private ReviewCycle reviewCycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "predefined_mark_id", nullable = false)
    private PredefinedRoleMarks predefinedMark;

    @Column(name = "attributed_mark_value", nullable = false, precision = 3, scale = 1)
    private BigDecimal attributedMarkValue;

    @CreationTimestamp
    @Column(name = "attributed_at", nullable = false, updatable = false)
    private Instant attributedAt;

    protected PersonalMarkAttribution() {
    }

    public PersonalMarkAttribution(User recipient, RoleType roleType, ContentPlan contentPlan,
                                    ReviewCycle reviewCycle, PredefinedRoleMarks predefinedMark,
                                    BigDecimal attributedMarkValue) {
        this.recipient = recipient;
        this.roleType = roleType;
        this.contentPlan = contentPlan;
        this.reviewCycle = reviewCycle;
        this.predefinedMark = predefinedMark;
        this.attributedMarkValue = attributedMarkValue;
    }

    public User getRecipient() {
        return recipient;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public BigDecimal getAttributedMarkValue() {
        return attributedMarkValue;
    }

    public Instant getAttributedAt() {
        return attributedAt;
    }
}
