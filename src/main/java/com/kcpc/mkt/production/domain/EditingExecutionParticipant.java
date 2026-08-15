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

/** ERD-TBL-039: queryable actual recorded Editor edit participants for self-approval verification. */
@Entity
@Table(name = "editing_execution_participants")
@AttributeOverride(name = "id", column = @Column(name = "participant_id"))
public class EditingExecutionParticipant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "editing_assignment_id")
    private EditingAssignment editingAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false)
    private ContentPlan contentPlan;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "editor_user_id", nullable = false)
    private User editor;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected EditingExecutionParticipant() {
    }

    public EditingExecutionParticipant(EditingAssignment editingAssignment, ContentPlan contentPlan, User editor) {
        this.editingAssignment = editingAssignment;
        this.contentPlan = contentPlan;
        this.editor = editor;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public User getEditor() {
        return editor;
    }
}
