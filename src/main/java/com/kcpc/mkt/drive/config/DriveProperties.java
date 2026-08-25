package com.kcpc.mkt.drive.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * app.drive.* - every value environment-sourced (see application.yml), never hardcoded and never
 * committed. enabled defaults to false: with no real service-account credentials/Shared
 * Drive/root folder configured, Drive provisioning is inert everywhere this app runs today
 * (local dev, CI, this sandbox) until a real deployment supplies them.
 */
@ConfigurationProperties(prefix = "app.drive")
public class DriveProperties {

    /** Master switch - when false, DriveProvisioningService never calls out to Drive at all. */
    private boolean enabled = false;

    /** Raw service-account JSON key content (not a file path) - sourced from an environment
     * variable/secret store, never checked into source control. */
    private String serviceAccountKey;

    /** The organization Shared Drive folders are created on. Required for Shared Drive
     * operations (supportsAllDrives et al.) to resolve correctly. */
    private String sharedDriveId;

    /** The KCPC company/root folder (inside the Shared Drive) every Content ID's root folder is
     * created directly under. */
    private String rootFolderId;

    /** Optional: a Workspace user to impersonate via domain-wide delegation, if the service
     * account requires it. Left blank when the service account has direct Shared Drive access. */
    private String impersonateUser;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getSharedDriveId() {
        return sharedDriveId;
    }

    public void setSharedDriveId(String sharedDriveId) {
        this.sharedDriveId = sharedDriveId;
    }

    public String getRootFolderId() {
        return rootFolderId;
    }

    public void setRootFolderId(String rootFolderId) {
        this.rootFolderId = rootFolderId;
    }

    public String getImpersonateUser() {
        return impersonateUser;
    }

    public void setImpersonateUser(String impersonateUser) {
        this.impersonateUser = impersonateUser;
    }
}
