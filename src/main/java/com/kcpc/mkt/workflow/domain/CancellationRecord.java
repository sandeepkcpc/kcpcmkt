package com.kcpc.mkt.workflow.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
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

/** ERD-TBL-032: Permission #12, mandatory reason, blocked once ever Completed (ERD-CON-006). */
@Entity
@Table(name = "cancellation_records")
@AttributeOverride(name = "id", column = @Column(name = "cancellation_id"))
public class CancellationRecord extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false, unique = true)
    private WorkflowInstance workflowInstance;

    @Column(name = "mandatory_reason", nullable = false, columnDefinition = "text")
    private String mandatoryReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancelled_by_user_id", nullable = false)
    private User cancelledBy;

    @CreationTimestamp
    @Column(name = "cancelled_at", nullable = false, updatable = false)
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acting_permission_grant_id")
    private PermissionGrant actingPermissionGrant;

    protected CancellationRecord() {
    }

    public CancellationRecord(WorkflowInstance workflowInstance, String mandatoryReason, User cancelledBy,
                               PermissionGrant actingPermissionGrant) {
        this.workflowInstance = workflowInstance;
        this.mandatoryReason = mandatoryReason;
        this.cancelledBy = cancelledBy;
        this.actingPermissionGrant = actingPermissionGrant;
    }
}
