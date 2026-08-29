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
import java.util.List;

/**
 * ERD-TBL-012: predefined role Marks captured at Idea Approval from the controlled list
 * {@code [0, 0.5, 1.0, 2.0, 3.0]} (BRS-REQ-016). Attributed in full (never split/averaged) to
 * each qualifying final contributor at Shoot/Edit Approval - see marks.domain.PersonalMarkAttribution
 * (added in Phase 5/6).
 */
@Entity
@Table(name = "predefined_role_marks")
@AttributeOverride(name = "id", column = @Column(name = "mark_id"))
public class PredefinedRoleMarks extends BaseEntity {

    public static final List<BigDecimal> CONTROLLED_VALUES = List.of(
            new BigDecimal("0.0"), new BigDecimal("0.5"), new BigDecimal("1.0"),
            new BigDecimal("2.0"), new BigDecimal("3.0"));

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
        requireControlled(cameramanMark, "Cameraperson Mark");
        requireControlled(editorMark, "Editor Mark");
        requireControlled(modelMark, "Model Mark");
        this.contentPlan = contentPlan;
        this.predefinedCameramanMark = cameramanMark;
        this.predefinedEditorMark = editorMark;
        this.predefinedModelMark = modelMark;
        this.setBy = setBy;
    }

    private static void requireControlled(BigDecimal value, String label) {
        if (value == null || CONTROLLED_VALUES.stream().noneMatch(v -> v.compareTo(value) == 0)) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    label + " must be one of [0, 0.5, 1.0, 2.0, 3.0] (ERD-CON-010); blank/missing is not equivalent to 0");
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
        requireControlled(newCamerapersonMark, "Cameraperson Mark");
        requireControlled(newEditorMark, "Editor Mark");
        requireControlled(newModelMark, "Model Mark");
        this.predefinedCameramanMark = newCamerapersonMark;
        this.predefinedEditorMark = newEditorMark;
        this.predefinedModelMark = newModelMark;
    }
}
