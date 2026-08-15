package com.kcpc.mkt.identity.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * ERD-TBL-034: stage scope mapping for STAGE_RESTRICTED grants.
 * NOTE: LifecycleStage uses LifecycleStageConverter (autoApply), NOT @Enumerated - the
 * stage_number column is INTEGER 1..7, and the enum's number() (not ordinal) is the DB value.
 */
@Entity
@Table(name = "permission_grant_stage_scopes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grant_id", "stage_number"}))
@AttributeOverride(name = "id", column = @Column(name = "scope_id"))
public class PermissionGrantStageScope extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private PermissionGrant grant;

    @Column(name = "stage_number", nullable = false)
    private LifecycleStage stageNumber;

    protected PermissionGrantStageScope() {
    }

    public PermissionGrantStageScope(PermissionGrant grant, LifecycleStage stageNumber) {
        this.grant = grant;
        this.stageNumber = stageNumber;
    }

    public PermissionGrant getGrant() {
        return grant;
    }

    public LifecycleStage getStageNumber() {
        return stageNumber;
    }
}
