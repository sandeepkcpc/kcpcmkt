package com.kcpc.mkt.drive.domain;

import com.kcpc.mkt.common.entity.BaseEntity;
import com.kcpc.mkt.planning.domain.ContentPlan;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One row per {@link ContentPlan}: tracks the real Google Drive folder IDs for the governed
 * per-Content-ID folder structure (root + "01 - Raw Shoot-{CONTENT_ID}" /
 * "02 - Edit-{CONTENT_ID}" / "03 - Final Content-{CONTENT_ID}")
 * and the provisioning status, so automatic Drive folder creation can never silently fail and can
 * always be safely retried without creating duplicates - see DriveProvisioningService.
 *
 * <p>Folder IDs are the canonical identifiers (never URLs - a Drive share URL is only ever
 * derived from an id, on demand, never persisted as the source of truth).
 */
@Entity
@Table(name = "content_drive_provisioning")
@AttributeOverride(name = "id", column = @Column(name = "provisioning_id"))
public class ContentDriveProvisioning extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "content_plan_id", nullable = false, unique = true)
    private ContentPlan contentPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriveProvisioningStatus status = DriveProvisioningStatus.NOT_STARTED;

    @Column(name = "root_folder_id", length = 128)
    private String rootFolderId;

    @Column(name = "raw_shoot_folder_id", length = 128)
    private String rawShootFolderId;

    @Column(name = "edit_folder_id", length = 128)
    private String editFolderId;

    @Column(name = "final_content_folder_id", length = 128)
    private String finalContentFolderId;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContentDriveProvisioning() {
    }

    public ContentDriveProvisioning(ContentPlan contentPlan) {
        this.contentPlan = contentPlan;
    }

    public ContentPlan getContentPlan() {
        return contentPlan;
    }

    public DriveProvisioningStatus getStatus() {
        return status;
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    public void setRootFolderId(String rootFolderId) {
        this.rootFolderId = rootFolderId;
    }

    public String getRawShootFolderId() {
        return rawShootFolderId;
    }

    public void setRawShootFolderId(String rawShootFolderId) {
        this.rawShootFolderId = rawShootFolderId;
    }

    public String getEditFolderId() {
        return editFolderId;
    }

    public void setEditFolderId(String editFolderId) {
        this.editFolderId = editFolderId;
    }

    public String getFinalContentFolderId() {
        return finalContentFolderId;
    }

    public void setFinalContentFolderId(String finalContentFolderId) {
        this.finalContentFolderId = finalContentFolderId;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isFullyProvisioned() {
        return status == DriveProvisioningStatus.SUCCEEDED && rootFolderId != null
                && rawShootFolderId != null && editFolderId != null && finalContentFolderId != null;
    }

    /** Only legal from NOT_STARTED/FAILED - IN_PROGRESS means a provisioning attempt (this one or
     * a concurrent one) is already under way, and SUCCEEDED needs no retry at all. */
    public boolean canRetry() {
        return status == DriveProvisioningStatus.NOT_STARTED || status == DriveProvisioningStatus.FAILED;
    }

    public void markInProgress() {
        this.status = DriveProvisioningStatus.IN_PROGRESS;
        this.lastError = null;
    }

    public void markSucceeded() {
        this.status = DriveProvisioningStatus.SUCCEEDED;
        this.lastError = null;
    }

    public void markFailed(String error) {
        this.status = DriveProvisioningStatus.FAILED;
        this.lastError = error;
    }

    /** Back to an eligible-for-retry state (e.g. after an admin relink corrected the root folder
     * but the 3 subfolders under it aren't all confirmed yet) - equivalent to NOT_STARTED, never a
     * distinct status of its own so {@link #canRetry()} keeps working unchanged. */
    public void markNeedsProvisioning() {
        this.status = DriveProvisioningStatus.NOT_STARTED;
        this.lastError = null;
    }
}
