package com.kcpc.mkt.drive.client;

/**
 * Wired in when {@code app.drive.enabled=false} (the default - no real Google service-account
 * credentials configured). {@link com.kcpc.mkt.drive.service.DriveProvisioningService} checks the
 * flag itself before ever calling this bean, so in normal operation these methods are never
 * reached; they throw rather than silently no-op, so a wiring mistake fails loudly instead of
 * quietly skipping provisioning forever.
 */
public class DisabledDriveFolderClient implements DriveFolderClient {

    @Override
    public String createFolder(String name, String parentFolderId) throws DriveProvisioningException {
        throw new DriveProvisioningException(
                "Google Drive integration is disabled (app.drive.enabled=false) - cannot create folder \"" + name + "\"");
    }

    @Override
    public java.util.Optional<String> findFolder(String name, String parentFolderId) throws DriveProvisioningException {
        throw new DriveProvisioningException(
                "Google Drive integration is disabled (app.drive.enabled=false) - cannot look up folder \"" + name + "\"");
    }
}
