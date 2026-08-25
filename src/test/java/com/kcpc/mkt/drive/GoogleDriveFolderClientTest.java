package com.kcpc.mkt.drive;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.kcpc.mkt.drive.client.DriveProvisioningException;
import com.kcpc.mkt.drive.client.GoogleDriveFolderClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure-unit (no Spring context, no network) coverage of the exact Google Drive API v3 call shape
 * GoogleDriveFolderClient produces - specifically the Shared Drive flags the spec requires
 * (supportsAllDrives / includeItemsFromAllDrives / driveId+corpora) and the structural guarantee
 * that no permissions/sharing call is ever made.
 */
class GoogleDriveFolderClientTest {

    @Test
    void createFolderSetsSupportsAllDrivesForSharedDriveCompatibility() throws Exception {
        Drive drive = mock(Drive.class);
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.Create create = mock(Drive.Files.Create.class);
        when(drive.files()).thenReturn(files);
        when(files.create(any(File.class))).thenReturn(create);
        when(create.setSupportsAllDrives(true)).thenReturn(create);
        when(create.setFields(any())).thenReturn(create);
        File created = new File().setId("new-folder-id");
        when(create.execute()).thenReturn(created);

        GoogleDriveFolderClient client = new GoogleDriveFolderClient(drive, "shared-drive-1");
        String id = client.createFolder("01 - Raw Shoot", "parent-folder-id");

        assertThat(id).isEqualTo("new-folder-id");
        verify(create).setSupportsAllDrives(true);
        verify(files).create(argThatFolderMetadata("01 - Raw Shoot", "parent-folder-id"));
    }

    @Test
    void findFolderAppliesSharedDriveSearchFlagsAndScopesToTheConfiguredDrive() throws Exception {
        Drive drive = mock(Drive.class);
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.List list = mock(Drive.Files.List.class);
        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(any())).thenReturn(list);
        when(list.setSupportsAllDrives(true)).thenReturn(list);
        when(list.setIncludeItemsFromAllDrives(true)).thenReturn(list);
        when(list.setFields(any())).thenReturn(list);
        when(list.setCorpora(any())).thenReturn(list);
        when(list.setDriveId(any())).thenReturn(list);
        FileList result = new FileList().setFiles(List.of(new File().setId("found-id").setName("02 - Edit")));
        when(list.execute()).thenReturn(result);

        GoogleDriveFolderClient client = new GoogleDriveFolderClient(drive, "shared-drive-1");
        var found = client.findFolder("02 - Edit", "parent-folder-id");

        assertThat(found).contains("found-id");
        verify(list).setSupportsAllDrives(true);
        verify(list).setIncludeItemsFromAllDrives(true);
        verify(list).setCorpora("drive");
        verify(list).setDriveId("shared-drive-1");
    }

    @Test
    void findFolderSkipsDriveIdScopingWhenNoSharedDriveIsConfigured() throws Exception {
        Drive drive = mock(Drive.class);
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.List list = mock(Drive.Files.List.class);
        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(list);
        when(list.setQ(any())).thenReturn(list);
        when(list.setSupportsAllDrives(true)).thenReturn(list);
        when(list.setIncludeItemsFromAllDrives(true)).thenReturn(list);
        when(list.setFields(any())).thenReturn(list);
        when(list.execute()).thenReturn(new FileList().setFiles(List.of()));

        GoogleDriveFolderClient client = new GoogleDriveFolderClient(drive, null);
        assertThat(client.findFolder("02 - Edit", "parent-folder-id")).isEmpty();

        verify(list, org.mockito.Mockito.never()).setCorpora(any());
        verify(list, org.mockito.Mockito.never()).setDriveId(any());
    }

    @Test
    void wrapsAnUnderlyingApiFailureIntoDriveProvisioningException() throws Exception {
        Drive drive = mock(Drive.class);
        Drive.Files files = mock(Drive.Files.class);
        Drive.Files.Create create = mock(Drive.Files.Create.class);
        when(drive.files()).thenReturn(files);
        when(files.create(any(File.class))).thenReturn(create);
        when(create.setSupportsAllDrives(true)).thenReturn(create);
        when(create.setFields(any())).thenReturn(create);
        when(create.execute()).thenThrow(new java.io.IOException("simulated network failure"));

        GoogleDriveFolderClient client = new GoogleDriveFolderClient(drive, null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.createFolder("01 - Raw Shoot", "parent-id"))
                .isInstanceOf(DriveProvisioningException.class)
                .hasMessageContaining("01 - Raw Shoot")
                .hasCauseInstanceOf(java.io.IOException.class);
    }

    private static File argThatFolderMetadata(String expectedName, String expectedParent) {
        return org.mockito.ArgumentMatchers.argThat(f -> f != null
                && expectedName.equals(f.getName())
                && f.getParents() != null && f.getParents().contains(expectedParent)
                && "application/vnd.google-apps.folder".equals(f.getMimeType()));
    }
}
