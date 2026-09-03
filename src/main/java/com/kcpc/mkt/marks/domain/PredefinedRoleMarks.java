package com.kcpc.mkt.marks.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ERD-TBL-012: predefined role Marks captured at Idea Approval (BRS-REQ-016). Attributed in full
 * (never split/averaged) to each qualifying final contributor at Shoot/Edit Approval - see
 * marks.domain.PersonalMarkAttribution (added in Phase 5/6).
 *
 * <p>ENG-092: which values are actually allowed is no longer hardcoded here - it's the
 * admin-configurable Mark Catalogue ({@code MarkCatalogueEntry}). The caller (IdeaService)
 * validates against the live catalogue via {@code MarkCatalogueService#requireActiveValue} BEFORE
 * constructing/correcting this entity; this class only guards against a null/missing value, which
 * is never valid regardless of what the catalogue currently allows.
 */
@Entity
@Table(name = "predefined_role_marks")
@AttributeOverride(name = "id", column = @Column(name = "mark_id"))
public class PredefinedRoleMarks extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false, unique = true)
    private ContentPlan contentPlan;

    @Column(name = "predefined_cameraperson_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal predefinedCameramanMark;

    @Column(name = "predefined_editor_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal predefinedEditorMark;

    @Column(name = "predefined_model_mark", nullable = false, precision = 3, scale = 1)
    private BigDecimal predefinedModelMark;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_by_user_id", nullable = false)
    private User setBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PredefinedRoleMarks() {
    }

    public PredefinedRoleMarks(ContentPlan contentPlan, BigDecimal cameramanMark, BigDecimal editorMark,
                                BigDecimal modelMark, User setBy) {
        requireNotNull(cameramanMark, "Cameraperson Mark");
        requireNotNull(editorMark, "Editor Mark");
        requireNotNull(modelMark, "Model Mark");
        this.contentPlan = contentPlan;
        this.predefinedCameramanMark = cameramanMark;
        this.predefinedEditorMark = editorMark;
        this.predefinedModelMark = modelMark;
        this.setBy = setBy;
    }

    private static void requireNotNull(BigDecimal value, String label) {
        if (value == null) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    label + " is mandatory; blank/missing is not equivalent to 0");
        }
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public BigDecimal getPredefinedCameramanMark() {
        return predefinedCameramanMark;
    }

    public BigDecimal getPredefinedEditorMark() {
        return predefinedEditorMark;
    }

    public BigDecimal getPredefinedModelMark() {
        return predefinedModelMark;
    }

    public User getSetBy() {
        return setBy;
    }

    /** API-OP-033: updates the active values in place; the correction ledger preserves the history. */
    public void applyCorrection(BigDecimal newCamerapersonMark, BigDecimal newEditorMark, BigDecimal newModelMark) {
        requireNotNull(newCamerapersonMark, "Cameraperson Mark");
        requireNotNull(newEditorMark, "Editor Mark");
        requireNotNull(newModelMark, "Model Mark");
        this.predefinedCameramanMark = newCamerapersonMark;
        this.predefinedEditorMark = newEditorMark;
        this.predefinedModelMark = newModelMark;
    }
}
