package com.kcpc.mkt.masterdata.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * ENG-094: admin-configurable catalogue of allowed Planning Category values, replacing the
 * previously-unconstrained free-text Category field. {@code ContentPlan.categoryText} stores the
 * category name as a plain string snapshot (no FK here) - the exact same shape Mark Catalogue
 * already uses for {@code predefined_role_marks}/{@code predefined_mark_corrections} - so renaming,
 * deactivating, or deleting a catalogue entry never touches an already-created Content Plan's
 * recorded category; it only changes what's selectable for new/future Planning submissions.
 *
 * <p>Exactly one row is ever the default ("N/A", seeded by V37): it can never be renamed,
 * deactivated, or deleted (CategoryService enforces this) - Planning must always have a "no
 * specific category" option available.
 */
@Entity
@Table(name = "categories")
@AttributeOverride(name = "id", column = @Column(name = "category_id"))
public class Category extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean defaultCategory = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Category() {
    }

    public Category(String name, boolean defaultCategory) {
        this.name = name;
        this.defaultCategory = defaultCategory;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDefaultCategory() {
        return defaultCategory;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
