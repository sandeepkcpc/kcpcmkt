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

/** ERD-TBL-030: Permission #11 reassignment header; see ReassignmentAssignee for the previous/new detail rows. */
@Entity
@Table(name = "reassignment_records")
@AttributeOverride(name = "id", column = @Column(name = "reassignment_id"))
public class ReassignmentRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_stage", nullable = false, length = 30)
    private TaskStage taskStage;

    @Column(name = "mandatory_reason", nullable = false, columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reassigned_by_user_id", nullable = false)
    private User reassignedBy;

    @CreationTimestamp
    @Column(name = "reassigned_at", nullable = false, updatable = false)
    private Instant reassignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected ReassignmentRecord() {
    }

    public ReassignmentRecord(WorkflowInstance workflowInstance, TaskStage taskStage, String mandatoryReason,
                               User reassignedBy, PermissionGrant actingPermissionGrant) {
        this.workflowInstance = workflowInstance;
        this.taskStage = taskStage;
        this.mandatoryReason = mandatoryReason;
        this.reassignedBy = reassignedBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }

    public TaskStage getTaskStage() {
        return taskStage;
    }
}
