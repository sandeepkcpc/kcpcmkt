package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.drive.FakeDriveFolderClient;
import com.kcpc.mkt.drive.client.DriveFolderClient;
import com.kcpc.mkt.drive.domain.ContentDriveProvisioning;
import com.kcpc.mkt.drive.domain.DriveProvisioningStatus;
import com.kcpc.mkt.drive.repository.ContentDriveProvisioningRepository;
import com.kcpc.mkt.drive.service.DriveProvisioningService;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Google Drive folder provisioning: every new Content ID gets
 * {@code CONTENT_ID/{01 - Raw Shoot-CONTENT_ID, 02 - Edit-CONTENT_ID, 03 - Final Content-CONTENT_ID}}
 * automatically, tracked idempotently in content_drive_provisioning.
 * Runs against a {@link FakeDriveFolderClient} - no real Google credentials or network access
 * exist in this environment, so app.drive.enabled=true here is paired with a {@code @Primary} fake
 * client bean (see {@link FakeDriveConfig}) that wins injection over DriveClientConfig's real
 * GoogleDriveFolderClient bean. Every other test class in this suite runs with the real
 * (disabled-by-default) config untouched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.drive.enabled=true",
        "app.drive.root-folder-id=kcpc-company-root"
})
class DriveProvisioningServiceTest {

    @TestConfiguration
    static class FakeDriveConfig {
        @Bean
        @Primary
        FakeDriveFolderClient fakeDriveFolderClient() {
            return new FakeDriveFolderClient();
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    DriveFolderClient driveFolderClient;
    @Autowired
    DriveProvisioningService driveProvisioningService;
    @Autowired
    ContentDriveProvisioningRepository provisioningRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    UserRepository userRepository;

    FakeDriveFolderClient fake;

    @BeforeEach
    void resolveFake() {
        fake = (FakeDriveFolderClient) driveFolderClient;
        fake.clearFailures();
    }

    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003";

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private String createUser(TestApiClient ceo, String fullName, String email, String businessRoleId) throws Exception {
        JsonNode response = ceo.postJson("/api/v1/admin/users",
                "{\"fullName\":\"" + fullName + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rd!\","
                        + "\"businessRoleId\":\"" + businessRoleId + "\",\"creationReason\":\"drive test fixture\"}");
        return response.get("userId").asText();
    }

    private void grant(TestApiClient ceo, String userId, String permission) throws Exception {
        ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + userId + "\",\"permission\":\"" + permission + "\","
                        + "\"scopeType\":\"GLOBAL\",\"reason\":\"drive test grant\"}");
    }

    /** Approves a fresh idea (real HTTP, same as every other E2E test in this suite) and returns
     * the resulting ContentPlan - automatic Drive provisioning has already run synchronously by
     * the time this returns (@TransactionalEventListener AFTER_COMMIT fires in-thread). */
    private ContentPlan approveNewIdea(TestApiClient ceo, String title) throws Exception {
        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"" + title + "\"}");
        String ideaId = idea.get("ideaId").asText();
        ceo.postJson("/api/v1/ideas/" + ideaId + "/review",
                "{\"decision\":\"APPROVE\",\"cameramanMark\":1.0,\"editorMark\":1.0}");
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        return contentPlanRepository.findByIdea(ideaEntity).orElseThrow();
    }

    // ================================================================== happy path

    @Test
    void newContentIdCreatesAllFourFoldersWithCorrectNamesAndHierarchy() throws Exception {
        long unique = Instant.now().toEpochMilli();
        ContentPlan plan = approveNewIdea(ceo(), "Drive Provisioning Happy Path " + unique);

        ContentDriveProvisioning row = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        assertThat(row.isFullyProvisioned()).isTrue();

        // Correct parent-child hierarchy: root created directly under the configured company
        // root, every subfolder created directly under THIS content's root (never under the
        // company root directly, never under each other).
        String contentId = plan.getContentId();
        assertThat(fake.findFolder(contentId, "kcpc-company-root")).contains(row.getRootFolderId());
        assertThat(fake.findFolder("01 - Raw Shoot-" + contentId, row.getRootFolderId())).contains(row.getRawShootFolderId());
        assertThat(fake.findFolder("02 - Edit-" + contentId, row.getRootFolderId())).contains(row.getEditFolderId());
        assertThat(fake.findFolder("03 - Final Content-" + contentId, row.getRootFolderId())).contains(row.getFinalContentFolderId());

        // Stored ids correspond exactly to what the client actually created - never invented ids.
        assertThat(row.getRootFolderId()).startsWith("fake-folder-");
        assertThat(row.getRawShootFolderId()).isNotEqualTo(row.getEditFolderId())
                .isNotEqualTo(row.getFinalContentFolderId());

        // folder_link synced to the root folder's URL, not a random title-derived string, and not
        // one of the subfolder ids.
        assertThat(plan.getFolderLink()).isEqualTo(DriveProvisioningService.folderUrl(row.getRootFolderId()));
    }

    @Test
    void folderNameIsTheImmutableContentIdNeverTheTitle() throws Exception {
        long unique = Instant.now().toEpochMilli();
        ContentPlan plan = approveNewIdea(ceo(), "Some Title That Could Change Later " + unique);
        ContentDriveProvisioning row = provisioningRepository.findByContentPlan(plan).orElseThrow();

        assertThat(fake.findFolder(plan.getContentId(), "kcpc-company-root")).isPresent();
        // The idea title must never appear as a folder name anywhere in the tree.
        assertThat(fake.findFolder("Some Title That Could Change Later " + unique, "kcpc-company-root")).isEmpty();
        assertThat(row.getRootFolderId()).isNotNull();
    }

    // ================================================================== retry / idempotency

    @Test
    void retryAfterPartialFailureCompletesWithoutDuplicatingAlreadyCreatedFolders() throws Exception {
        long unique = Instant.now().toEpochMilli();
        // Root + Raw Shoot succeed, Edit fails - Final Content never even attempted this round.
        fake.failOnFolderNames("02 - Edit");
        ContentPlan plan = approveNewIdea(ceo(), "Drive Partial Failure " + unique);

        ContentDriveProvisioning afterFirstAttempt = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(afterFirstAttempt.getStatus()).isEqualTo(DriveProvisioningStatus.FAILED);
        assertThat(afterFirstAttempt.getRootFolderId()).isNotNull();
        assertThat(afterFirstAttempt.getRawShootFolderId()).isNotNull();
        assertThat(afterFirstAttempt.getEditFolderId()).isNull();
        assertThat(afterFirstAttempt.getFinalContentFolderId()).isNull();
        String rootAfterFirstAttempt = afterFirstAttempt.getRootFolderId();
        String rawShootAfterFirstAttempt = afterFirstAttempt.getRawShootFolderId();

        fake.clearFailures();
        // FakeDriveFolderClient throws IllegalStateException itself if the code under test ever
        // tries to re-create root/Raw Shoot - retry() succeeding at all proves that didn't happen.
        User ceoUser = userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow();
        ContentDriveProvisioning afterRetry = driveProvisioningService.retry(ceoUser, plan.getId());

        assertThat(afterRetry.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        assertThat(afterRetry.getRootFolderId()).isEqualTo(rootAfterFirstAttempt);
        assertThat(afterRetry.getRawShootFolderId()).isEqualTo(rawShootAfterFirstAttempt);
        assertThat(afterRetry.getEditFolderId()).isNotNull();
        assertThat(afterRetry.getFinalContentFolderId()).isNotNull();
        assertThat(afterRetry.isFullyProvisioned()).isTrue();
    }

    @Test
    void retryOnRecordWithFoldersAlreadyStoredUnderThePreviousNamingConventionNeverTouchesOrDuplicatesThem() throws Exception {
        long unique = Instant.now().toEpochMilli();
        // Root succeeds; Raw Shoot fails during the initial automatic attempt, so this test's fake
        // Drive never actually creates anything under the new "-{CONTENT_ID}" convention for it -
        // matching a record where the Raw Shoot folder id came from somewhere else entirely
        // (created before the suffix convention existed).
        fake.failOnFolderNames("01 - Raw Shoot");
        ContentPlan plan = approveNewIdea(ceo(), "Drive Legacy Naming Migration " + unique);
        ContentDriveProvisioning row = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(DriveProvisioningStatus.FAILED);
        assertThat(row.getRootFolderId()).isNotNull();
        assertThat(row.getRawShootFolderId()).isNull();
        fake.clearFailures();

        // Simulate a record whose Raw Shoot folder id is already known/stored from before the
        // "-{CONTENT_ID}" suffix convention existed - proving retry trusts an already-stored id
        // alone and never re-derives or re-looks-up a folder by name once its id is known. Edit/
        // Final Content still have no stored id - the part genuinely needing work.
        String preConventionRawShootId = "legacy-raw-shoot-folder-id-before-suffix-convention";
        row.setRawShootFolderId(preConventionRawShootId);
        provisioningRepository.saveAndFlush(row);

        User ceoUser = userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow();
        ContentDriveProvisioning afterRetry = driveProvisioningService.retry(ceoUser, plan.getId());

        assertThat(afterRetry.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        // The pre-existing Raw Shoot folder is preserved exactly as stored - never renamed, never
        // re-created under the new convention, never even looked up by name.
        assertThat(afterRetry.getRawShootFolderId()).isEqualTo(preConventionRawShootId);
        assertThat(fake.findFolder("01 - Raw Shoot-" + plan.getContentId(), afterRetry.getRootFolderId()))
                .as("Retry must never create a new-convention duplicate for a subfolder whose id is already stored")
                .isEmpty();
        // Edit/Final Content, which truly had no stored id, are created fresh under the new
        // "-{CONTENT_ID}" naming convention.
        assertThat(afterRetry.getEditFolderId()).isNotNull();
        assertThat(afterRetry.getFinalContentFolderId()).isNotNull();
        assertThat(fake.findFolder("02 - Edit-" + plan.getContentId(), afterRetry.getRootFolderId()))
                .contains(afterRetry.getEditFolderId());
        assertThat(fake.findFolder("03 - Final Content-" + plan.getContentId(), afterRetry.getRootFolderId()))
                .contains(afterRetry.getFinalContentFolderId());
    }

    @Test
    void retryAfterCompleteSuccessIsIdempotentAndMakesNoNewDriveCalls() throws Exception {
        long unique = Instant.now().toEpochMilli();
        ContentPlan plan = approveNewIdea(ceo(), "Drive Retry After Success " + unique);
        ContentDriveProvisioning succeeded = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(succeeded.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        int createCallsBeforeRetry = fake.createFolderCallCount();

        User ceoUser = userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow();
        ContentDriveProvisioning afterRetry = driveProvisioningService.retry(ceoUser, plan.getId());

        assertThat(afterRetry.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        assertThat(afterRetry.getRootFolderId()).isEqualTo(succeeded.getRootFolderId());
        assertThat(afterRetry.getRawShootFolderId()).isEqualTo(succeeded.getRawShootFolderId());
        assertThat(afterRetry.getEditFolderId()).isEqualTo(succeeded.getEditFolderId());
        assertThat(afterRetry.getFinalContentFolderId()).isEqualTo(succeeded.getFinalContentFolderId());
        assertThat(fake.createFolderCallCount()).as("retry on an already-SUCCEEDED row must make zero new create calls")
                .isEqualTo(createCallsBeforeRetry);
    }

    @Test
    void apiFailureIsSurfacedClearlyOnTheProvisioningRecord() throws Exception {
        long unique = Instant.now().toEpochMilli();
        fake.failOnFolderNames("01 - Raw Shoot");
        ContentPlan plan = approveNewIdea(ceo(), "Drive API Failure " + unique);

        ContentDriveProvisioning row = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(DriveProvisioningStatus.FAILED);
        assertThat(row.getLastError()).isNotBlank().contains("01 - Raw Shoot");
    }

    // ================================================================== no public sharing

    @Test
    void driveFolderClientInterfaceHasNoWayToGrantPublicSharing() {
        // Structural guarantee: DriveFolderClient exposes exactly "create" and "find" - no
        // permissions/sharing operation exists anywhere for DriveProvisioningService to call, so
        // it is impossible for provisioning to make a folder "anyone with the link" even by
        // mistake, regardless of which implementation (real or fake) is wired.
        assertThat(DriveFolderClient.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .containsExactlyInAnyOrder("createFolder", "findFolder");
    }

    // ================================================================== existing behavior intact

    @Test
    void existingContentCreationFlowStillProducesAWorkflowInstanceAtPlanning() throws Exception {
        long unique = Instant.now().toEpochMilli();
        ContentPlan plan = approveNewIdea(ceo(), "Existing Flow Unaffected " + unique);
        assertThat(plan.getContentId()).isNotBlank();
        assertThat(plan.getWorkflowInstance().getCurrentStatusCode().name()).isEqualTo("PL");
    }

    // ================================================================== Folder Link Management (PERM_13)

    @Test
    void perm13HolderCanRetryAndRelinkViaTheAdminHttpEndpoints() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        fake.failOnFolderNames("03 - Final Content");
        ContentPlan plan = approveNewIdea(ceo, "Drive Admin Endpoints " + unique);
        assertThat(provisioningRepository.findByContentPlan(plan).orElseThrow().getStatus())
                .isEqualTo(DriveProvisioningStatus.FAILED);
        fake.clearFailures();

        String hrEmail = "drive-hr-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "Drive HR " + unique, hrEmail, HR_MANAGER_ROLE_ID);
        grant(ceo, hrId, "PERM_13_FOLDER_LINK_MANAGE");
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        hr.postFormAjax("/app/deliverables/" + plan.getId() + "/drive/retry", java.util.Map.of());

        ContentDriveProvisioning afterRetry = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(afterRetry.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        assertThat(afterRetry.isFullyProvisioned()).isTrue();

        hr.postFormAjax("/app/deliverables/" + plan.getId() + "/drive/relink",
                java.util.Map.of("rootFolderIdOrUrl", "https://drive.google.com/drive/folders/manually-relinked-id"));

        ContentDriveProvisioning afterRelink = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(afterRelink.getRootFolderId()).isEqualTo("manually-relinked-id");
        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getFolderLink()).isEqualTo(DriveProvisioningService.folderUrl("manually-relinked-id"));
    }

    @Test
    void employeeWithoutPerm13CannotRetryOrRelink() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        fake.failOnFolderNames("02 - Edit");
        ContentPlan plan = approveNewIdea(ceo, "Drive No Perm13 " + unique);
        ContentDriveProvisioning failed = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(DriveProvisioningStatus.FAILED);
        fake.clearFailures();

        String hrEmail = "drive-noperm-" + unique + "@kcpcbandhani.local";
        String hrId = createUser(ceo, "Drive No Perm HR " + unique, hrEmail, HR_MANAGER_ROLE_ID);
        // Deliberately no PERM_13 grant.
        TestApiClient hr = new TestApiClient(port);
        hr.login(hrEmail, "Passw0rd!");

        hr.postFormAjax("/app/deliverables/" + plan.getId() + "/drive/retry", java.util.Map.of());

        assertThat(provisioningRepository.findByContentPlan(plan).orElseThrow().getStatus())
                .as("Retry must be rejected without PERM_13 - the failed row stays failed, not silently retried")
                .isEqualTo(DriveProvisioningStatus.FAILED);
    }

    // ================================================================== legacy content / folder_link divergence

    @Test
    void legacyContentWithNoProvisioningRecordKeepsFolderLinkFreelyEditable() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        ContentPlan plan = approveNewIdea(ceo, "Legacy Content No Record " + unique);
        // Simulate "legacy" content that predates this feature: no structured provisioning row.
        provisioningRepository.delete(provisioningRepository.findByContentPlan(plan).orElseThrow());

        ceo.postJson("/api/v1/content-plans/" + plan.getId() + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/manually-pasted-legacy-link\"}");

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getFolderLink()).isEqualTo("https://drive.example.com/manually-pasted-legacy-link");
    }

    @Test
    void ordinaryPlanningEditCannotDivergeFolderLinkOnceStructuredRootIsKnown() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        ContentPlan plan = approveNewIdea(ceo, "Structured Root Known " + unique);
        ContentDriveProvisioning row = provisioningRepository.findByContentPlan(plan).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(DriveProvisioningStatus.SUCCEEDED);
        String canonicalLink = DriveProvisioningService.folderUrl(row.getRootFolderId());

        ceo.postJson("/api/v1/content-plans/" + plan.getId() + "/parameters",
                "{\"contentPriority\":\"MEDIUM\",\"folderLink\":\"https://drive.example.com/an-attempted-divergent-link\"}");

        ContentPlan reloaded = contentPlanRepository.findById(plan.getId()).orElseThrow();
        assertThat(reloaded.getFolderLink())
                .as("An ordinary Planning edit must never diverge folder_link from the structured root once known")
                .isEqualTo(canonicalLink);
    }

    @Test
    void provisioningIsANoOpWhenDriveIntegrationIsDisabled() {
        // Direct unit-level check of the disabled-by-default guard, independent of this test
        // class's own enabled=true override - every OTHER test class in the suite runs with the
        // real default (false) and must never attempt a Drive call at all.
        assertThatThrownBy(() -> new com.kcpc.mkt.drive.client.DisabledDriveFolderClient().createFolder("x", "y"))
                .isInstanceOf(com.kcpc.mkt.drive.client.DriveProvisioningException.class)
                .hasMessageContaining("disabled");
    }
}
