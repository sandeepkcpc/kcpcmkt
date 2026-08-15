package com.kcpc.mkt.publishing.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.masterdata.domain.PublicationTarget;
import com.kcpc.mkt.planning.domain.PlannedOutput;
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

/** ERD-TBL-022: Publication Target N/A exception, append-only supersession chain. */
@Entity
@Table(name = "publication_target_na_records")
@AttributeOverride(name = "id", column = @Column(name = "na_record_id"))
public class PublicationTargetNaRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "planned_output_id", nullable = false)
    private PlannedOutput plannedOutput;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "publication_target_id", nullable = false)
    private PublicationTarget publicationTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private NaActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_na_record_id")
    private PublicationTargetNaRecord supersedes;

    @Column(name = "mandatory_reason", columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @CreationTimestamp
    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    protected PublicationTargetNaRecord() {
    }

    public PublicationTargetNaRecord(PlannedOutput plannedOutput, PublicationTarget publicationTarget,
                                      NaActionType actionType, PublicationTargetNaRecord supersedes,
                                      String mandatoryReason, User actor) {
        if (actionType == NaActionType.DESIGNATED && (mandatoryReason == null || mandatoryReason.isBlank())) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "A mandatory reason is required to designate a target N/A (ERD-CON-040)");
        }
        this.plannedOutput = plannedOutput;
        this.publicationTarget = publicationTarget;
        this.actionType = actionType;
        this.supersedes = supersedes;
        this.mandatoryReason = mandatoryReason;
        this.actor = actor;
    }

    public PlannedOutput getPlannedOutput() {
        return plannedOutput;
    }

    public PublicationTarget getPublicationTarget() {
        return publicationTarget;
    }

    public NaActionType getActionType() {
        return actionType;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
