package com.kcpc.mkt.discussion.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
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
 * ENG-046: one Jira-style discussion thread per (Content Plan, stage). Not in the frozen ERD.
 * Reuses {@link LifecycleStage} for the stage column (restricted to SHOOTING/EDITING/PUBLISHING at
 * the application layer, CHECK-constrained at the DB layer) rather than a parallel enum.
 * ENG-050: no longer fully append-only - the author (only the author, see StageCommentService) may
 * edit the text or soft-delete their own comment; hard DELETE stays DB-forbidden
 * (trg_stage_comments_reject_delete), and every edit/delete is separately audit-logged.
 */
@Entity
@Table(name = "stage_comments")
@AttributeOverride(name = "id", column = @Column(name = "comment_id"))
public class StageComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private LifecycleStage stage;

    // EAGER: rendered directly in deliverable-detail.jsp's Comments thread outside any open
    // transaction (open-in-view is disabled) - see docs/IMPLEMENTATION_DECISIONS.md ENG-005 for
    // the same pattern (ContentPlan.preparedBy et al.).
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "commenter_user_id", nullable = false)
    private User commenter;

    @Column(name = "comment_text", nullable = false, columnDefinition = "text")
    private String commentText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected StageComment() {
    }

    public StageComment(ContentPlan contentPlan, LifecycleStage stage, User commenter, String commentText) {
        this.contentPlan = contentPlan;
        this.stage = stage;
        this.commenter = commenter;
        this.commentText = commentText;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public LifecycleStage getStage() {
        return stage;
    }

    public User getCommenter() {
        return commenter;
    }

    public String getCommentText() {
        return commentText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void editText(String newText) {
        this.commentText = newText;
        this.editedAt = Instant.now();
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}
