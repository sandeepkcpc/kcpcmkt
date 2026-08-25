package com.kcpc.mkt.drive.client;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Real Google Drive API v3 implementation - only wired when {@code app.drive.enabled=true} with
 * valid service-account credentials (see DriveClientConfig). Every call carries the Shared Drive
 * flags Drive requires for Shared Drive folders (supportsAllDrives/includeItemsFromAllDrives, and
 * a driveId-scoped corpora on search when a Shared Drive id is configured) - required per this
 * feature's spec, not optional.
 *
 * <p>Never sets any permission on a created folder: with no explicit permissions call at all, a
 * new folder simply inherits its parent's existing Shared Drive/Workspace sharing - it is never
 * made "anyone with the link" or otherwise public by this client.
 */
public class GoogleDriveFolderClient implements DriveFolderClient {

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    private final Drive drive;
    private final String sharedDriveId;

    public GoogleDriveFolderClient(Drive drive, String sharedDriveId) {
        this.drive = drive;
        this.sharedDriveId = sharedDriveId;
    }

    @Override
    public String createFolder(String name, String parentFolderId) throws DriveProvisioningException {
        File metadata = new File();
        metadata.setName(name);
        metadata.setMimeType(FOLDER_MIME_TYPE);
        metadata.setParents(List.of(parentFolderId));
        try {
            Drive.Files.Create create = drive.files().create(metadata);
            create.setSupportsAllDrives(true);
            create.setFields("id");
            File created = create.execute();
            return created.getId();
        } catch (IOException e) {
            throw new DriveProvisioningException(
                    "Failed to create Drive folder \"" + name + "\" under parent " + parentFolderId, e);
        }
    }

    @Override
    public Optional<String> findFolder(String name, String parentFolderId) throws DriveProvisioningException {
        String escapedName = name.replace("\\", "\\\\").replace("'", "\\'");
        String query = "name = '" + escapedName + "' and '" + parentFolderId + "' in parents "
                + "and mimeType = '" + FOLDER_MIME_TYPE + "' and trashed = false";
        try {
            Drive.Files.List list = drive.files().list();
            list.setQ(query);
            list.setSupportsAllDrives(true);
            list.setIncludeItemsFromAllDrives(true);
            list.setFields("files(id, name)");
            if (sharedDriveId != null && !sharedDriveId.isBlank()) {
                list.setCorpora("drive");
                list.setDriveId(sharedDriveId);
            }
            FileList result = list.execute();
            List<File> files = result.getFiles();
            if (files == null || files.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(files.get(0).getId());
        } catch (IOException e) {
            throw new DriveProvisioningException(
                    "Failed to search for Drive folder \"" + name + "\" under parent " + parentFolderId, e);
        }
    }
}
