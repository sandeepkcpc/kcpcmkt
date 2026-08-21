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

/** ERD-TBL-014: multi-Editor task assignments, initial assignment restricted to post-Shoot-Approval (Permission #6). */
@Entity
@Table(name = "editing_assignments")
@AttributeOverride(name = "id", column = @Column(name = "assignment_id"))
public class EditingAssignment extends BaseEntity {

    // EAGER: read by self-service DTO mapping outside any open transaction - see ENG-005.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "editor_user_id", nullable = false)
    private User editor;

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

    // Not in the frozen ERD - see ENG-036. At most one active row per Content Plan may hold this
    // (DB partial unique index); ending this row (see end()) does not clear it - a since-removed
    // assignment's lead flag is preserved as a historical fact, but is_active=false excludes it
    // from every "current Lead" lookup, so leadership is effectively cleared regardless.
    @Column(name = "is_lead", nullable = false)
    private boolean lead = false;

    protected EditingAssignment() {
    }

    public EditingAssignment(ContentPlan contentPlan, User editor, User assignedBy) {
        this.contentPlan = contentPlan;
        this.editor = editor;
        this.assignedBy = assignedBy;
    }

    public void end() {
        this.active = false;
        this.endedAt = Instant.now();
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public User getEditor() {
        return editor;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isLead() {
        return lead;
    }

    public void setLead(boolean lead) {
        this.lead = lead;
    }
}
