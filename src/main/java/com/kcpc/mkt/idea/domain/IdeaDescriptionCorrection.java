package com.kcpc.mkt.idea.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.PermissionGrant;
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

/**
 * Append-only correction history for {@link Idea#getNotesRemarks()} (Description/Details), same
 * shape as {@code com.kcpc.mkt.marks.domain.PredefinedMarkCorrection} - the Idea's own field is
 * updated to the new value in the same transaction (see {@code IdeaService#updateDescription});
 * this row preserves the full before/after audit trail, never silently overwritten.
 */
@Entity
@Table(name = "idea_description_corrections")
@AttributeOverride(name = "id", column = @Column(name = "correction_id"))
public class IdeaDescriptionCorrection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idea_id", nullable = false)
    private Idea idea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_correction_id")
    private IdeaDescriptionCorrection supersedesCorrection;

    @Column(name = "prior_description", columnDefinition = "text")
    private String priorDescription;

    @Column(name = "new_description", columnDefinition = "text")
    private String newDescription;

    @Column(name = "correction_reason", nullable = false, columnDefinition = "text")
    private String correctionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "corrected_by_user_id", nullable = false)
    private User correctedBy;

    @CreationTimestamp
    @Column(name = "corrected_at", nullable = false, updatable = false)
    private Instant correctedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected IdeaDescriptionCorrection() {
    }

    public IdeaDescriptionCorrection(Idea idea, IdeaDescriptionCorrection supersedesCorrection,
                                      String priorDescription, String newDescription, String correctionReason,
                                      User correctedBy, PermissionGrant actingPermissionGrant) {
        this.idea = idea;
        this.supersedesCorrection = supersedesCorrection;
        this.priorDescription = priorDescription;
        this.newDescription = newDescription;
        this.correctionReason = correctionReason;
        this.correctedBy = correctedBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }

    public Idea getIdea() {
        return idea;
    }

    public IdeaDescriptionCorrection getSupersedesCorrection() {
        return supersedesCorrection;
    }

    public String getPriorDescription() {
        return priorDescription;
    }

    public String getNewDescription() {
        return newDescription;
    }

    public String getCorrectionReason() {
        return correctionReason;
    }

    public User getCorrectedBy() {
        return correctedBy;
    }

    public Instant getCorrectedAt() {
        return correctedAt;
    }
}
