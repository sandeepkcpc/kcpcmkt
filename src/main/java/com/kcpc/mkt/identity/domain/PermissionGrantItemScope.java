package com.kcpc.mkt.identity.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** ERD-TBL-035: item-specific scope mapping for ITEM_SPECIFIC grants (Idea ID / Content ID restricted). */
@Entity
@Table(name = "permission_grant_item_scopes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grant_id", "workflow_instance_id"}))
@AttributeOverride(name = "id", column = @Column(name = "scope_id"))
public class PermissionGrantItemScope extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grant_id", nullable = false)
    private PermissionGrant grant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    protected PermissionGrantItemScope() {
    }

    public PermissionGrantItemScope(PermissionGrant grant, WorkflowInstance workflowInstance) {
        this.grant = grant;
        this.workflowInstance = workflowInstance;
    }

    public PermissionGrant getGrant() {
        return grant;
    }

    public WorkflowInstance getWorkflowInstance() {
        return workflowInstance;
    }
}
