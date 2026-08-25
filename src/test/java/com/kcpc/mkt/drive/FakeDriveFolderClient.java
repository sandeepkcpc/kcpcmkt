package com.kcpc.mkt.drive;

import com.kcpc.mkt.drive.client.DriveFolderClient;
import com.kcpc.mkt.drive.client.DriveProvisioningException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory stand-in for the real Google Drive API - no real credentials or network access exist
 * in automated tests, so every DriveProvisioningService test exercises this instead. Tracks every
 * (parentId, name) folder ever created so a test can assert exact hierarchy/naming, and can be
 * told to fail the Nth createFolder call to simulate a partial-provisioning failure.
 */
public class FakeDriveFolderClient implements DriveFolderClient {

    /** key: parentId + "/" + name -> generated folder id. */
    private final Map<String, String> foldersByParentAndName = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    private final AtomicInteger createCallCount = new AtomicInteger(0);
    private volatile int failOnCreateCallNumber = -1;
    private volatile Set<String> failOnFolderNamePrefixes = Set.of();

    private static String key(String parentId, String name) {
        return parentId + "/" + name;
    }

    @Override
    public synchronized String createFolder(String name, String parentFolderId) throws DriveProvisioningException {
        int callNumber = createCallCount.incrementAndGet();
        boolean prefixMatch = failOnFolderNamePrefixes.stream().anyMatch(name::startsWith);
        if (callNumber == failOnCreateCallNumber || prefixMatch) {
            throw new DriveProvisioningException("Simulated Drive API failure creating folder \"" + name + "\"");
        }
        String key = key(parentFolderId, name);
        String existing = foldersByParentAndName.get(key);
        if (existing != null) {
            // A real Drive API would happily create a second same-named folder - this fake
            // deliberately does NOT, so any test path that would produce a duplicate call fails
            // loudly via an assertion elsewhere rather than silently succeeding twice.
            throw new IllegalStateException("Test bug: createFolder called again for already-created \"" + name
                    + "\" under parent " + parentFolderId + " - the provisioning logic under test should never do this");
        }
        String id = "fake-folder-" + idSequence.getAndIncrement();
        foldersByParentAndName.put(key, id);
        return id;
    }

    @Override
    public Optional<String> findFolder(String name, String parentFolderId) {
        return Optional.ofNullable(foldersByParentAndName.get(key(parentFolderId, name)));
    }

    /** Simulates the createFolder call number N failing (1-indexed, across the whole client's
     * lifetime) - used to test "partial failure, then retry completes the rest". */
    public void failOnCreateCallNumber(int callNumber) {
        this.failOnCreateCallNumber = callNumber;
    }

    /** Simulates any createFolder call for a name starting with one of these prefixes failing,
     * every time - used to test "the failure is surfaced clearly" without depending on exact call
     * ordering or the Content-ID suffix each real subfolder name now carries (e.g.
     * {@code failOnFolderNames("02 - Edit")} matches "02 - Edit-C-0826-0057" regardless of the
     * actual Content ID allocated for that test). */
    public void failOnFolderNames(String... namePrefixes) {
        this.failOnFolderNamePrefixes = Set.of(namePrefixes);
    }

    public void clearFailures() {
        this.failOnCreateCallNumber = -1;
        this.failOnFolderNamePrefixes = Set.of();
    }

    public int createFolderCallCount() {
        return createCallCount.get();
    }

    /** Directly seeds a folder as already existing on "Drive" - used to test the defense-in-depth
     * findFolder-before-create idempotency path (our own stored id is unknown, but Drive itself
     * already has the folder). */
    public String seedExistingFolder(String name, String parentFolderId) {
        String id = "fake-folder-" + idSequence.getAndIncrement();
        foldersByParentAndName.put(key(parentFolderId, name), id);
        return id;
    }
}
