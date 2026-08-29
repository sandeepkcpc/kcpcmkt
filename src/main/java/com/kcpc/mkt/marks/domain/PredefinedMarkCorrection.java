package com.kcpc.mkt.marks.domain;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ERD-TBL-026: append-only correction history for {@link PredefinedRoleMarks} under Permission #1
 * (SAD-DES-009). The original {@code predefined_role_marks} row's active values are updated in
 * the same transaction (API-OP-033); this row preserves the full before/after audit trail.
 */
@Entity
@Table(name = "predefined_mark_corrections")
@AttributeOverride(name = "id", column = @Column(name = "correction_id"))
public class PredefinedMarkCorrection extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "predefined_mark_id", nullable = false)
    private PredefinedRoleMarks predefinedMark;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_correction_id")
    private PredefinedMarkCorrection supersedesCorrection;

    @Column(name = "prior_cameraperson_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal priorCamerapersonMark;

    @Column(name = "prior_editor_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal priorEditorMark;

    @Column(name = "new_cameraperson_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal newCamerapersonMark;

    @Column(name = "new_editor_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal newEditorMark;

    @Column(name = "prior_model_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal priorModelMark;

    @Column(name = "new_model_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal newModelMark;

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

    protected PredefinedMarkCorrection() {
    }

    public PredefinedMarkCorrection(PredefinedRoleMarks predefinedMark, PredefinedMarkCorrection supersedesCorrection,
                                     BigDecimal priorCamerapersonMark, BigDecimal priorEditorMark,
                                     BigDecimal priorModelMark, BigDecimal newCamerapersonMark,
                                     BigDecimal newEditorMark, BigDecimal newModelMark, String correctionReason,
                                     User correctedBy, PermissionGrant actingPermissionGrant) {
        this.predefinedMark = predefinedMark;
        this.supersedesCorrection = supersedesCorrection;
        this.priorCamerapersonMark = priorCamerapersonMark;
        this.priorEditorMark = priorEditorMark;
        this.priorModelMark = priorModelMark;
        this.newCamerapersonMark = newCamerapersonMark;
        this.newEditorMark = newEditorMark;
        this.newModelMark = newModelMark;
        this.correctionReason = correctionReason;
        this.correctedBy = correctedBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }

    public PredefinedRoleMarks getPredefinedMark() {
        return predefinedMark;
    }

    public PredefinedMarkCorrection getSupersedesCorrection() {
        return supersedesCorrection;
    }

    public BigDecimal getPriorCamerapersonMark() {
        return priorCamerapersonMark;
    }

    public BigDecimal getPriorEditorMark() {
        return priorEditorMark;
    }

    public BigDecimal getNewCamerapersonMark() {
        return newCamerapersonMark;
    }

    public BigDecimal getNewEditorMark() {
        return newEditorMark;
    }

    public BigDecimal getPriorModelMark() {
        return priorModelMark;
    }

    public BigDecimal getNewModelMark() {
        return newModelMark;
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
