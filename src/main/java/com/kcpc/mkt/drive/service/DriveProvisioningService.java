package com.kcpc.mkt.drive.service;

import com.kcpc.mkt.audit.service.AuditService;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.drive.client.DriveFolderClient;
import com.kcpc.mkt.drive.client.DriveProvisioningException;
import com.kcpc.mkt.drive.config.DriveProperties;
import com.kcpc.mkt.drive.domain.ContentDriveProvisioning;
import com.kcpc.mkt.drive.event.ContentPlanCreatedEvent;
import com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions the governed per-Content-ID Drive folder structure:
 * <pre>
 * CONTENT_ID/
 *   01 - Raw Shoot-CONTENT_ID
 *   02 - Edit-CONTENT_ID
 *   03 - Final Content-CONTENT_ID
 * </pre>
 * The root folder name is exactly the Content ID; each subfolder name carries the Content ID as a
 * suffix (see {@link #rawShootFolderName}/{@link #editFolderName}/{@link #finalContentFolderName})
 * so a subfolder is unambiguous even if viewed outside its parent folder's context.
 *
 * <p>Idempotent by construction: every folder id is persisted the instant that folder is
 * created/confirmed, retry always trusts an already-stored id first, and falls back to a
 * name+parent lookup on Drive itself before ever creating - so no retry path, however many times
 * it runs, can create a duplicate root or subfolder (spec requirement). A folder id already stored
 * from before this naming convention existed is still trusted as-is (see {@link #ensureFolder}) -
 * an already-provisioned subfolder is never renamed or re-created just because the naming
 * convention changed later.
 *
 * <p>Never runs inside the Content ID's own creation transaction (see
 * {@link #initiateProvisioning}) - Drive is a slow external network call, and a Drive failure must
 * never roll back (or duplicate) Content ID creation.
 */
@Service
public class DriveProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(DriveProvisioningService.class);

    private final ContentDriveProvisioningRepository provisioningRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final DriveFolderClient driveFolderClient;
    private final DriveProperties driveProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;
    /** Self-reference through the Spring proxy (not `this`) - required specifically so
     * {@link #onContentPlanCreated} (which runs AFTER_COMMIT, with no transaction of its own) can
     * actually invoke {@link #provision}'s @Transactional boundary; a plain `this.provision(...)`
     * self-invocation would silently bypass the proxy and run with no transaction at all. @Lazy
     * defers resolution past this bean's own construction. */
    private final DriveProvisioningService self;

    public DriveProvisioningService(ContentDriveProvisioningRepository provisioningRepository,
                                     ContentPlanRepository contentPlanRepository,
                                     DriveFolderClient driveFolderClient,
                                     DriveProperties driveProperties,
                                     ApplicationEventPublisher eventPublisher,
                                     AuditService auditService,
                                     @org.springframework.context.annotation.Lazy DriveProvisioningService self) {
        this.provisioningRepository = provisioningRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.driveFolderClient = driveFolderClient;
        this.driveProperties = driveProperties;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.self = self;
    }

    /** Canonical, non-persisted share URL - folder ids are always the stored/canonical identifier;
     * a URL is only ever derived from one, on demand, exactly as the spec requires. */
    public static String folderUrl(String folderId) {
        return "https://drive.google.com/drive/folders/" + folderId;
    }

    // Canonical subfolder-name builders - the single source of truth for naming, used both when
    // creating a subfolder and when searching for an already-existing one (ensureFolder), so
    // creation and idempotent lookup can never drift apart onto two different names.
    static String rawShootFolderName(String contentId) {
        return "01 - Raw Shoot-" + contentId;
    }

    static String editFolderName(String contentId) {
        return "02 - Edit-" + contentId;
    }

    static String finalContentFolderName(String contentId) {
        return "03 - Final Content-" + contentId;
    }

    /** Whether Drive integration is switched on at all - lets callers (e.g. the retry endpoint)
     * give a precise "integration is disabled" message instead of a generic "nothing happened",
     * without each caller needing its own DriveProperties dependency. */
    public boolean isDriveIntegrationEnabled() {
        return driveProperties.isEnabled();
    }

    /**
     * Called from within the SAME transaction that creates the ContentPlan (IdeaService#approve):
     * inserts the NOT_STARTED tracking row (cheap, no network call) and schedules the real Drive
     * calls for after that transaction commits. If Content ID creation itself later rolls back for
     * an unrelated reason, this row and the scheduled event roll back with it - never orphaned.
     */
    @Transactional
    public void initiateProvisioning(ContentPlan plan) {
        if (provisioningRepository.findByContentPlan(plan).isEmpty()) {
            provisioningRepository.save(new ContentDriveProvisioning(plan));
        }
        eventPublisher.publishEvent(new ContentPlanCreatedEvent(plan.getId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onContentPlanCreated(ContentPlanCreatedEvent event) {
        self.provision(event.contentPlanId());
    }

    /**
     * Authorized manual retry (PERM_13_FOLDER_LINK_MANAGE) - same idempotent provision() logic
     * automatic provisioning uses, so "retry" is never a second, different code path that could
     * disagree with what ran automatically.
     */
    @Transactional
    public ContentDriveProvisioning retry(User actor, UUID contentPlanId) {
        // Through `self`, not a bare self-invocation - provision() runs REQUIRES_NEW (see its own
        // javadoc), which a same-bean self-invocation would silently ignore just like the
        // AFTER_COMMIT listener case this same `self` field exists for.
        self.provision(contentPlanId);
        ContentDriveProvisioning row = provisioningRepository.findByContentPlan_Id(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Drive provisioning record not found for content plan: " + contentPlanId));
        auditService.record(actor, java.util.Optional.empty(), "DRIVE_PROVISIONING", "DRIVE_PROVISIONING_RETRIED",
                "content_drive_provisioning", row.getId(), "status=" + row.getStatus());
        return row;
    }

    /**
     * Manual admin repair/relink (PERM_13_FOLDER_LINK_MANAGE): points the structured provisioning
     * record's root folder at an admin-supplied Drive folder (pasted URL or a raw folder id) and
     * resyncs {@code content_plans.folder_link} from it - the canonical structured record is
     * always updated FIRST, folder_link only ever follows it, never the other way round. Works for
     * a ContentPlan that has no provisioning row yet too (legacy content an admin wants to bring
     * under structured tracking for the first time).
     *
     * <p>If the 3 subfolders aren't already known under this (possibly new) root, the row is left
     * eligible for retry so the ordinary "Retry Provisioning" action can create/confirm them under
     * the corrected root - reusing the exact same idempotent logic, never a second relink-specific
     * creation path.
     */
    @Transactional
    public ContentDriveProvisioning relinkRootFolder(User actor, UUID contentPlanId, String newRootFolderIdOrUrl) {
        String newRootFolderId = extractFolderId(newRootFolderIdOrUrl);
        if (newRootFolderId == null || newRootFolderId.isBlank()) {
            throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED, "A Drive folder ID or URL is required");
        }
        ContentPlan plan = contentPlanRepository.findById(contentPlanId)
                .orElseThrow(() -> DomainException.notFound("Content Plan not found: " + contentPlanId));
        ContentDriveProvisioning row = provisioningRepository.findByContentPlan(plan)
                .orElseGet(() -> provisioningRepository.save(new ContentDriveProvisioning(plan)));

        row.setRootFolderId(newRootFolderId);
        if (row.getRawShootFolderId() != null && row.getEditFolderId() != null && row.getFinalContentFolderId() != null) {
            row.markSucceeded();
        } else {
            row.markNeedsProvisioning();
        }
        provisioningRepository.save(row);

        plan.setFolderLink(folderUrl(newRootFolderId));
        contentPlanRepository.save(plan);

        auditService.record(actor, java.util.Optional.empty(), "DRIVE_PROVISIONING", "DRIVE_ROOT_FOLDER_RELINKED",
                "content_drive_provisioning", row.getId(), "rootFolderId=" + newRootFolderId);
        return row;
    }

    /** Accepts either a raw Drive folder id or a full share URL
     * ("https://drive.google.com/drive/folders/{id}[?params]") and returns just the id. */
    static String extractFolderId(String idOrUrl) {
        if (idOrUrl == null) {
            return null;
        }
        String trimmed = idOrUrl.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("/folders/([a-zA-Z0-9_-]+)").matcher(trimmed);
        if (m.find()) {
            return m.group(1);
        }
        return trimmed;
    }

    /**
     * The actual provisioning attempt. A no-op when Drive integration is disabled (no credentials
     * configured - the default in every environment this runs in today) or when the row is not
     * currently eligible for an attempt (already SUCCEEDED, or IN_PROGRESS from a concurrent
     * attempt - the row-level lock below makes that race safe).
     *
     * <p>REQUIRES_NEW: this is always invoked either from an AFTER_COMMIT transactional event
     * listener (no ambient transaction of its own by that point - the default REQUIRED propagation
     * cannot reliably reopen one from that phase) or from {@link #retry}, which must not have this
     * method's row-level pessimistic lock held for the duration of an unrelated outer transaction.
     * Always called through {@link #self}, never a bare self-invocation, so this annotation is
     * actually honored (self-invocation on the same bean silently bypasses the transaction proxy).
     *
     * <p>Runs as one transaction; each successfully created/found folder id is flushed immediately
     * so a later step's DriveProvisioningException (caught below) still commits everything
     * confirmed so far, and only the final markFailed/markSucceeded call determines the commit -
     * an unexpected (non-Drive) exception instead rolls the whole attempt back, which is safe: it
     * simply reverts the row to its pre-attempt state rather than leaving it stuck IN_PROGRESS.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void provision(UUID contentPlanId) {
        if (!driveProperties.isEnabled()) {
            log.debug("Drive provisioning skipped for {} - app.drive.enabled=false", contentPlanId);
            return;
        }
        ContentDriveProvisioning row = provisioningRepository.findByContentPlanIdForUpdate(contentPlanId)
                .orElse(null);
        if (row == null) {
            log.warn("No Drive provisioning row found for content plan {} - nothing to provision", contentPlanId);
            return;
        }
        if (!row.canRetry()) {
            log.debug("Drive provisioning for {} is {} - no action taken", contentPlanId, row.getStatus());
            return;
        }
        ContentPlan plan = row.getContentPlan();
        row.markInProgress();
        provisioningRepository.saveAndFlush(row);

        try {
            String contentId = plan.getContentId();
            String rootId = ensureFolder(row.getRootFolderId(), contentId, driveProperties.getRootFolderId());
            row.setRootFolderId(rootId);
            provisioningRepository.saveAndFlush(row);

            String rawShootId = ensureFolder(row.getRawShootFolderId(), rawShootFolderName(contentId), rootId);
            row.setRawShootFolderId(rawShootId);
            provisioningRepository.saveAndFlush(row);

            String editId = ensureFolder(row.getEditFolderId(), editFolderName(contentId), rootId);
            row.setEditFolderId(editId);
            provisioningRepository.saveAndFlush(row);

            String finalContentId = ensureFolder(row.getFinalContentFolderId(), finalContentFolderName(contentId), rootId);
            row.setFinalContentFolderId(finalContentId);

            row.markSucceeded();
            provisioningRepository.saveAndFlush(row);

            // Keeps every existing "Drive Link" display (Content Detail, Reviews, Pipeline, My
            // Work) working unchanged - PlanningService#updateParameters remains the manual
            // override path (PERM_02, or PERM_13 via the admin repair action) and can still
            // replace this at any time.
            plan.setFolderLink(folderUrl(rootId));
            contentPlanRepository.save(plan);
        } catch (DriveProvisioningException e) {
            log.warn("Drive provisioning failed for content plan {}: {}", contentPlanId, e.getMessage());
            row.markFailed(e.getMessage());
            provisioningRepository.saveAndFlush(row);
        }
    }

    /** Trusts an already-stored id first (no network call); otherwise looks the folder up by
     * exact name+parent before creating, so a retry can never duplicate a folder that already
     * exists on Drive even if our own stored state doesn't yet know its id. */
    private String ensureFolder(String alreadyStoredId, String name, String parentId) throws DriveProvisioningException {
        if (alreadyStoredId != null && !alreadyStoredId.isBlank()) {
            return alreadyStoredId;
        }
        Optional<String> existing = driveFolderClient.findFolder(name, parentId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return driveFolderClient.createFolder(name, parentId);
    }
}
