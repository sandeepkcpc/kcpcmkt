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

/** ERD-TBL-033: Reopen history for Retained ideas (Permission #1) and Completed deliverables (Permission #8/#9). */
@Entity
@Table(name = "reopen_records")
@AttributeOverride(name = "id", column = @Column(name = "reopen_id"))
public class ReopenRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status_code", nullable = false, length = 10)
    private WorkflowStatus fromStatusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status_code", nullable = false, length = 10)
    private WorkflowStatus toStatusCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reopen_purpose", nullable = false, length = 50)
    private ReopenPurpose reopenPurpose;

    @Column(name = "mandatory_reason", columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reopened_by_user_id", nullable = false)
    private User reopenedBy;

    @CreationTimestamp
    @Column(name = "reopened_at", nullable = false, updatable = false)
    private Instant reopenedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected ReopenRecord() {
    }

    public ReopenRecord(WorkflowInstance workflowInstance, WorkflowStatus fromStatusCode, WorkflowStatus toStatusCode,
                         ReopenPurpose reopenPurpose, String mandatoryReason, User reopenedBy,
                         PermissionGrant actingPermissionGrant) {
        this.workflowInstance = workflowInstance;
        this.fromStatusCode = fromStatusCode;
        this.toStatusCode = toStatusCode;
        this.reopenPurpose = reopenPurpose;
        this.mandatoryReason = mandatoryReason;
        this.reopenedBy = reopenedBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }
}
