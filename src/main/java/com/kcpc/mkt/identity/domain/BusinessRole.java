package com.kcpc.mkt.identity.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

/**
 * ERD-TBL-044 business_roles: the expandable Business Role (organizational designation)
 * catalogue. Never itself grants an Operational Permission (ERD-CON-063).
 */
@Entity
@Table(name = "business_roles")
@AttributeOverride(name = "id", column = @Column(name = "business_role_id"))
public class BusinessRole extends BaseEntity {

    @Column(name = "role_name", nullable = false, unique = true, length = 100)
    private String roleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_class_code", nullable = false, length = 30)
    private AccessClass accessClassCode;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BusinessRole() {
    }

    public BusinessRole(String roleName, AccessClass accessClassCode) {
        this.roleName = roleName;
        this.accessClassCode = accessClassCode;
    }

    public void deactivate() {
        this.active = false;
        this.deactivatedAt = Instant.now();
    }

    public String getRoleName() {
        return roleName;
    }

    public AccessClass getAccessClassCode() {
        return accessClassCode;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeactivatedAt() {
        return deactivatedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
