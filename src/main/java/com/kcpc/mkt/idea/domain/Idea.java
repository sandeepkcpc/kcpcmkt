package com.kcpc.mkt.idea.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** ERD-TBL-009 ideas: Idea submission record (BRS-REQ-014/015). */
@Entity
@Table(name = "ideas")
@AttributeOverride(name = "id", column = @Column(name = "idea_id"))
public class Idea extends BaseEntity {

    @OneToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false, unique = true)
    private WorkflowInstance workflowInstance;

    @Column(name = "business_idea_code", nullable = false, unique = true, length = 30)
    private String businessIdeaCode;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "reference_link", columnDefinition = "text")
    private String referenceLink;

    @Column(name = "notes_remarks", columnDefinition = "text")
    private String notesRemarks;

    // ENG-060: separate short-form field from notesRemarks (Idea Description / Details).
    @Column(name = "additional_note", columnDefinition = "text")
    private String additionalNote;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "submitted_by_user_id", nullable = false)
    private User submittedBy;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    protected Idea() {
    }

    public Idea(WorkflowInstance workflowInstance, String businessIdeaCode, String title, String referenceLink,
                String notesRemarks, String additionalNote, User submittedBy) {
        this.workflowInstance = workflowInstance;
        this.businessIdeaCode = businessIdeaCode;
        this.title = title;
        this.referenceLink = referenceLink;
        this.notesRemarks = notesRemarks;
        this.additionalNote = additionalNote;
        this.submittedBy = submittedBy;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public String getBusinessIdeaCode() {
        return businessIdeaCode;
    }

    public String getTitle() {
        return title;
    }

    public String getReferenceLink() {
        return referenceLink;
    }

    public String getNotesRemarks() {
        return notesRemarks;
    }

    /** See {@code IdeaService#updateDescription} - the prior value is preserved via
     *  {@link IdeaDescriptionCorrection} before this overwrite, never lost. */
    public void updateNotesRemarks(String notesRemarks) {
        this.notesRemarks = notesRemarks;
    }

    /** See {@code IdeaService#updateReferenceLink} - CEO/Marketing Manager only, validated as a
     *  real http(s) URL before this is ever called. Same Idea record, same Idea ID - never a new
     *  version. */
    public void updateReferenceLink(String referenceLink) {
        this.referenceLink = referenceLink;
    }

    public String getAdditionalNote() {
        return additionalNote;
    }

    public User getSubmittedBy() {
        return submittedBy;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
