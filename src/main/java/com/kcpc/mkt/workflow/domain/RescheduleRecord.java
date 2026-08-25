package com.kcpc.mkt.workflow.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
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
import java.time.LocalDate;

/** ERD-TBL-029: Permission #10, mandatory reason, preserves old/new approved dates; never touches Performance Due Date. */
@Entity
@Table(name = "reschedule_records")
@AttributeOverride(name = "id", column = @Column(name = "reschedule_id"))
public class RescheduleRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_context", nullable = false, length = 30)
    private StageContext stageContext;

    @Column(name = "prior_planned_shoot_date")
    private LocalDate priorPlannedShootDate;

    @Column(name = "prior_planned_edit_date")
    private LocalDate priorPlannedEditDate;

    @Column(name = "prior_planned_live_date")
    private LocalDate priorPlannedLiveDate;

    @Column(name = "new_planned_shoot_date")
    private LocalDate newPlannedShootDate;

    @Column(name = "new_planned_edit_date")
    private LocalDate newPlannedEditDate;

    @Column(name = "new_planned_live_date")
    private LocalDate newPlannedLiveDate;

    @Column(name = "mandatory_reason", nullable = false, columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rescheduled_by_user_id", nullable = false)
    private User rescheduledBy;

    @CreationTimestamp
    @Column(name = "rescheduled_at", nullable = false, updatable = false)
    private Instant rescheduledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected RescheduleRecord() {
    }

    public RescheduleRecord(WorkflowInstance workflowInstance, StageContext stageContext,
                             LocalDate priorShoot, LocalDate priorEdit, LocalDate priorLive,
                             LocalDate newShoot, LocalDate newEdit, LocalDate newLive,
                             String mandatoryReason, User rescheduledBy, PermissionGrant actingPermissionGrant) {
        this.workflowInstance = workflowInstance;
        this.stageContext = stageContext;
        this.priorPlannedShootDate = priorShoot;
        this.priorPlannedEditDate = priorEdit;
        this.priorPlannedLiveDate = priorLive;
        this.newPlannedShootDate = newShoot;
        this.newPlannedEditDate = newEdit;
        this.newPlannedLiveDate = newLive;
        this.mandatoryReason = mandatoryReason;
        this.rescheduledBy = rescheduledBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }

    public StageContext getStageContext() {
        return stageContext;
    }

    public LocalDate getPriorPlannedShootDate() {
        return priorPlannedShootDate;
    }

    public LocalDate getPriorPlannedEditDate() {
        return priorPlannedEditDate;
    }

    public LocalDate getPriorPlannedLiveDate() {
        return priorPlannedLiveDate;
    }

    public LocalDate getNewPlannedShootDate() {
        return newPlannedShootDate;
    }

    public LocalDate getNewPlannedEditDate() {
        return newPlannedEditDate;
    }

    public LocalDate getNewPlannedLiveDate() {
        return newPlannedLiveDate;
    }

    public String getMandatoryReason() {
        return mandatoryReason;
    }

    public User getRescheduledBy() {
        return rescheduledBy;
    }

    public Instant getRescheduledAt() {
        return rescheduledAt;
    }
}
