package com.kcpc.mkt.marks.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ENG-092: admin-configurable catalogue of allowed Cameraperson/Editor/Model Mark values, replacing
 * the previously-hardcoded {@code [0, 0.5, 1.0, 2.0, 3.0]} list. One row per (roleType, markValue)
 * pair. Historical {@code predefined_role_marks}/{@code predefined_mark_corrections} rows store the
 * mark value directly (no FK here), so deleting or deactivating a catalogue entry never affects
 * already-recorded marks - it only changes what's selectable going forward.
 */
@Entity
@Table(name = "mark_catalogue_entries")
@AttributeOverride(name = "id", column = @Column(name = "mark_catalogue_entry_id"))
public class MarkCatalogueEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 20)
    private RoleType roleType;

    @Column(name = "mark_value", nullable = false, precision = 3, scale = 1)
    private BigDecimal markValue;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MarkCatalogueEntry() {
    }

    public MarkCatalogueEntry(RoleType roleType, BigDecimal markValue) {
        this.roleType = roleType;
        this.markValue = markValue;
    }

    public void changeValue(BigDecimal markValue) {
        this.markValue = markValue;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public BigDecimal getMarkValue() {
        return markValue;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
