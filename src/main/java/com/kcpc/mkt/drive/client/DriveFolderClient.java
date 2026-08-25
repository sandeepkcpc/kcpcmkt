package com.kcpc.mkt.drive.client;

import java.util.Optional;

/**
 * Thin abstraction over "create a folder" / "find a folder by exact name under a parent" on
 * Google Drive - the only two Drive operations Drive folder provisioning needs. Kept separate
 * from {@link com.kcpc.mkt.drive.service.DriveProvisioningService}'s idempotency/retry
 * orchestration so that logic is testable against a fake implementation, never against the real
 * Google API (no real credentials or network access to Google exist in automated tests).
 *
 * <p>Implementations must never make a folder publicly accessible - a created folder inherits
 * sharing from its parent (the organization's existing Shared Drive/Workspace access model) and
 * this interface has no "set permissions" operation at all, deliberately.
 */
public interface DriveFolderClient {

    /** Creates a folder named exactly {@code name} directly under {@code parentFolderId} and
     * returns its Drive-assigned folder id. Must apply the Shared Drive options required for
     * Shared Drive parents (supportsAllDrives, etc.) - implementations decide based on
     * configuration, not the caller. */
    String createFolder(String name, String parentFolderId) throws DriveProvisioningException;

    /** Looks up a folder named exactly {@code name} directly under {@code parentFolderId}, for
     * idempotent retry (never create a duplicate if one already exists on Drive even though our
     * own stored state doesn't know about it). Empty if no such folder exists. */
    Optional<String> findFolder(String name, String parentFolderId) throws DriveProvisioningException;
}
