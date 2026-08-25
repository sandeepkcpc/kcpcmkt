package com.kcpc.mkt;

import com.fasterxml.jackson.databind.JsonNode;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.support.TestApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * User Detail "Grant Permissions" quick-grant checklist redesign - driven through the real HTTP
 * endpoints (AJAX quick-grant, the still-unchanged Modify/Revoke/advanced-Grant form posts, and
 * the page's own rendered HTML for checklist/summary assertions), never calling
 * PermissionGrantAdminService directly, so this exercises the exact same path the browser does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PermissionQuickGrantTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;
    @Autowired
    PermissionGrantRepository permissionGrantRepository;
    @Autowired
    IdeaRepository ideaRepository;

    private static final String CAMERA_PERSON_ROLE_ID = "01926e3e-0001-7000-8000-000000000004";
    private static final String HR_MANAGER_ROLE_ID = "01926e3e-0001-7000-8000-000000000003";

    @Test
    void quickGrantCreatesGlobalActiveGrantWithGovernedDefaults() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "QuickGrant Target", HR_MANAGER_ROLE_ID);

        Instant before = Instant.now();
        HttpResponse<String> resp = ceo.postFormAjax(
                "/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_18_SHOOT_EXECUTION"));
        assertThat(resp.statusCode()).isEqualTo(200);
        Instant after = Instant.now();

        List<PermissionGrant> grants = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_18_SHOOT_EXECUTION);
        assertThat(grants).hasSize(1);
        PermissionGrant grant = grants.get(0);
        assertThat(grant.getScopeType().name()).isEqualTo("GLOBAL");
        assertThat(grant.getEffectiveFrom()).isBetween(before, after);
        assertThat(grant.getEffectiveUntil()).isNull();
        User ceoUser = userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow();
        assertThat(grant.getGrantor().getId()).isEqualTo(ceoUser.getId());
        assertThat(grant.isCurrentlyValid(Instant.now())).isTrue();

        // Reason "N/A" must be persisted (audit trail), not merely displayed - read back via the
        // same page the browser renders, exactly as a real admin would verify it.
        HttpResponse<String> detailPage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(detailPage.body(), "PERM_18_SHOOT_EXECUTION")).isEqualTo("perm-status-global");
    }

    @Test
    void duplicateActiveGlobalGrantIsNeverCreated() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Duplicate Target", HR_MANAGER_ROLE_ID);

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_19_EDIT_EXECUTION")).statusCode()).isEqualTo(200);
        long countAfterFirst = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_19_EDIT_EXECUTION).size();

        // Checking an already-granted permission again (e.g. a second click before the checkbox
        // was disabled client-side, or a direct re-POST) must not insert a second row.
        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_19_EDIT_EXECUTION")).statusCode()).isEqualTo(200);
        long countAfterSecond = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_19_EDIT_EXECUTION).size();

        assertThat(countAfterFirst).isEqualTo(1);
        assertThat(countAfterSecond).isEqualTo(1);
    }

    @Test
    void restrictedGrantShowsGrantedRestrictedNotGlobal() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Restricted Target", HR_MANAGER_ROLE_ID);

        HttpResponse<String> advancedGrant = ceo.postFormMulti(
                "/app/admin/users/" + target.getId() + "/permission-grants",
                Map.of("permission", List.of("PERM_05_SHOOT_REVIEW"), "scopeType", List.of("STAGE_RESTRICTED"),
                        "stages", List.of("SHOOTING"), "reason", List.of("restricted quick-grant test")));
        assertThat(advancedGrant.statusCode()).isEqualTo(302);

        HttpResponse<String> detailPage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(detailPage.body(), "PERM_05_SHOOT_REVIEW")).isEqualTo("perm-status-restricted");
    }

    /** ITEM_SPECIFIC grants have no field for {@code workflowInstanceIds} on the MVC "Advanced"
     * form (it only ever carries {@code stages}) - they are created through the REST API in
     * practice, so this test uses that endpoint directly, exactly as an ITEM_SPECIFIC grant would
     * really be created, then verifies the checklist/summary read it back correctly. */
    @Test
    void itemSpecificGrantShowsGrantedRestricted() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "ItemSpecific Target", HR_MANAGER_ROLE_ID);

        JsonNode idea = ceo.postJson("/api/v1/ideas", "{\"title\":\"QuickGrant ItemSpecific Flow " + unique + "\"}");
        String ideaId = idea.get("ideaId").asText();
        Idea ideaEntity = ideaRepository.findById(UUID.fromString(ideaId)).orElseThrow();
        UUID workflowInstanceId = ideaEntity.getWorkflowInstance().getId();

        HttpResponse<String> restGrant = ceo.post("/api/v1/admin/permission-grants",
                "{\"granteeUserId\":\"" + target.getId() + "\",\"permission\":\"PERM_01_IDEA_REVIEW\","
                        + "\"scopeType\":\"ITEM_SPECIFIC\",\"workflowInstanceIds\":[\"" + workflowInstanceId + "\"],"
                        + "\"reason\":\"item-specific quick-grant test\"}");
        assertThat(restGrant.statusCode()).isEqualTo(201);

        HttpResponse<String> detailPage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(detailPage.body(), "PERM_01_IDEA_REVIEW")).isEqualTo("perm-status-restricted");
    }

    @Test
    void summaryCountsDedupePerPermissionAndExcludeRevokedAndExpired() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Summary Target", HR_MANAGER_ROLE_ID);

        HttpResponse<String> before = ceo.get("/app/admin/users/" + target.getId());
        int grantedBefore = extractGrantedCount(before.body());

        // Grant, then grant a SECOND (restricted) coverage for the SAME permission code - summary
        // must still only count this permission once.
        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_04_SHOOT_ASSIGNMENT")).statusCode()).isEqualTo(200);
        assertThat(ceo.postFormMulti("/app/admin/users/" + target.getId() + "/permission-grants",
                Map.of("permission", List.of("PERM_04_SHOOT_ASSIGNMENT"), "scopeType", List.of("STAGE_RESTRICTED"),
                        "stages", List.of("SHOOTING"), "reason", List.of("dedupe test")))
                .statusCode()).isEqualTo(302);

        HttpResponse<String> afterBothGrants = ceo.get("/app/admin/users/" + target.getId());
        int grantedAfterBoth = extractGrantedCount(afterBothGrants.body());
        assertThat(grantedAfterBoth).isEqualTo(grantedBefore + 1); // +1 permission code, not +2 grant rows

        // Revoke the GLOBAL one - the STAGE_RESTRICTED one is still active, so it must still count
        // as Granted (Restricted), not drop out of the summary.
        PermissionGrant globalGrant = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                        userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_04_SHOOT_ASSIGNMENT).stream()
                .filter(g -> g.getScopeType().name().equals("GLOBAL")).findFirst().orElseThrow();
        assertThat(ceo.postFormMulti("/app/admin/permission-grants/" + globalGrant.getId() + "/revoke",
                Map.of("userId", List.of(target.getId().toString()), "reason", List.of("dedupe test revoke")))
                .statusCode()).isEqualTo(302);

        HttpResponse<String> afterRevoke = ceo.get("/app/admin/users/" + target.getId());
        int grantedAfterRevoke = extractGrantedCount(afterRevoke.body());
        assertThat(grantedAfterRevoke).isEqualTo(grantedBefore + 1); // still granted, via the restricted one
        assertThat(statusClassForPermission(afterRevoke.body(), "PERM_04_SHOOT_ASSIGNMENT")).isEqualTo("perm-status-restricted");
    }

    @Test
    void unauthorizedUserCannotUseQuickGrantEndpoint() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Auth Target", HR_MANAGER_ROLE_ID);

        String nonCeoEmail = "quickgrant-noauth-" + unique + "@kcpcbandhani.local";
        User nonCeoUser = createUser(ceo, unique, "NonCeo Actor", CAMERA_PERSON_ROLE_ID, nonCeoEmail);
        assertThat(nonCeoUser.resolvedAccessClass()).isNotEqualTo(AccessClass.CEO_OWNER);
        TestApiClient nonCeo = new TestApiClient(port);
        nonCeo.login(nonCeoEmail, "Passw0rd!");

        HttpResponse<String> resp = nonCeo.postFormAjax(
                "/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_18_SHOOT_EXECUTION"));
        assertThat(resp.statusCode()).isEqualTo(403);
        assertThat(permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_18_SHOOT_EXECUTION)).isEmpty();
    }

    @Test
    void executionPermissionsAppearDynamicallyAndBusinessRoleNeverChanges() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "HR Multi Target", HR_MANAGER_ROLE_ID);

        HttpResponse<String> beforePage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(beforePage.body()).contains("PERM_18_SHOOT_EXECUTION").contains("PERM_19_EDIT_EXECUTION");

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_18_SHOOT_EXECUTION")).statusCode()).isEqualTo(200);

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getBusinessRole().getId().toString()).isEqualTo(HR_MANAGER_ROLE_ID);
    }

    @Test
    void revokePreservesHistoricalGrantRowAndMetadata() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Revoke Target", HR_MANAGER_ROLE_ID);

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_09_PERFORMANCE_UPDATE")).statusCode()).isEqualTo(200);
        PermissionGrant grant = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_09_PERFORMANCE_UPDATE).get(0);

        assertThat(ceo.postFormMulti("/app/admin/permission-grants/" + grant.getId() + "/revoke",
                Map.of("userId", List.of(target.getId().toString()), "reason", List.of("no longer needed")))
                .statusCode()).isEqualTo(302);

        PermissionGrant revoked = permissionGrantRepository.findById(grant.getId()).orElseThrow();
        assertThat(revoked.isActive()).isFalse();
        assertThat(revoked.getRevokedAt()).isNotNull();
        assertThat(revoked.isCurrentlyValid(Instant.now())).isFalse();
        // Row preserved, not deleted.
        assertThat(permissionGrantRepository.findById(grant.getId())).isPresent();
    }

    @Test
    void revokeViaTheCheckboxFormReturnsRowToNotGrantedState() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "CheckboxRevoke Target", HR_MANAGER_ROLE_ID);

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_02_PLANNING_EXECUTION")).statusCode()).isEqualTo(200);
        PermissionGrant grant = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_02_PLANNING_EXECUTION).get(0);

        // Exactly what the row's checkbox form posts when unchecked: reason hardcoded "N/A", no
        // confirmation dialog, immediate audited revoke.
        HttpResponse<String> revoke = ceo.postFormMulti("/app/admin/permission-grants/" + grant.getId() + "/revoke",
                Map.of("userId", List.of(target.getId().toString()), "reason", List.of("N/A")));
        assertThat(revoke.statusCode()).isEqualTo(302);

        PermissionGrant reloaded = permissionGrantRepository.findById(grant.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getRevokedAt()).isNotNull();

        HttpResponse<String> page = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(page.body(), "PERM_02_PLANNING_EXECUTION")).isEqualTo("perm-status-none");
    }

    @Test
    void inlineUpdateChangesExpiryAndReasonWithoutReplacingGrant() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Update Target", HR_MANAGER_ROLE_ID);

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_10_RESCHEDULE")).statusCode()).isEqualTo(200);
        PermissionGrant original = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_10_RESCHEDULE).get(0);

        LocalDate future = LocalDate.now().plusDays(30);
        HttpResponse<String> update = ceo.postFormMulti("/app/admin/permission-grants/" + original.getId() + "/update",
                Map.of("userId", List.of(target.getId().toString()), "scopeType", List.of("GLOBAL"),
                        "effectiveUntil", List.of(future.toString()), "reason", List.of("Temporary responsibility")));
        assertThat(update.statusCode()).isEqualTo(302);

        // Scope unchanged -> Update must delegate to modifyExpiry (same grant row preserved), never
        // a revoke+recreate replacement.
        PermissionGrant reloaded = permissionGrantRepository.findById(original.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isTrue();
        assertThat(reloaded.getEffectiveUntil()).isNotNull();
        List<PermissionGrant> stillActive = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_10_RESCHEDULE);
        assertThat(stillActive).hasSize(1);
        assertThat(stillActive.get(0).getId()).isEqualTo(original.getId());

        // The updated reason must actually display - proves the Reason column reads the LATEST
        // PERMISSION_GRANTED-or-PERMISSION_MODIFIED audit row, not only the original grant event.
        HttpResponse<String> page = ceo.get("/app/admin/users/" + target.getId());
        assertThat(page.body()).contains("Temporary responsibility");
    }

    @Test
    void inlineUpdateGlobalToStageRestrictedReplacesGrantSafelyWithoutLeavingTwoActive() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "ScopeChange Target", HR_MANAGER_ROLE_ID);

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_11_REASSIGN")).statusCode()).isEqualTo(200);
        PermissionGrant original = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_11_REASSIGN).get(0);

        HttpResponse<String> update = ceo.postFormMulti("/app/admin/permission-grants/" + original.getId() + "/update",
                Map.of("userId", List.of(target.getId().toString()), "scopeType", List.of("STAGE_RESTRICTED"),
                        "stages", List.of("SHOOTING"), "reason", List.of("narrow to Shoot only")));
        assertThat(update.statusCode()).isEqualTo(302);

        PermissionGrant oldReloaded = permissionGrantRepository.findById(original.getId()).orElseThrow();
        assertThat(oldReloaded.isActive()).isFalse(); // controlled replacement: old grant revoked, not mutated in place
        assertThat(oldReloaded.getRevokedAt()).isNotNull();

        List<PermissionGrant> nowActive = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_11_REASSIGN);
        assertThat(nowActive).hasSize(1); // never simultaneously 2 active as a result of Update
        assertThat(nowActive.get(0).getId()).isNotEqualTo(original.getId());
        assertThat(nowActive.get(0).getScopeType().name()).isEqualTo("STAGE_RESTRICTED");

        User businessRoleCheck = userRepository.findById(target.getId()).orElseThrow();
        assertThat(businessRoleCheck.getBusinessRole().getId().toString()).isEqualTo(HR_MANAGER_ROLE_ID);

        HttpResponse<String> page = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(page.body(), "PERM_11_REASSIGN")).isEqualTo("perm-status-restricted");
    }

    @Test
    void inlineUpdateStageRestrictedToGlobalClearsStageRestriction() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "ScopeBack Target", HR_MANAGER_ROLE_ID);

        HttpResponse<String> advancedGrant = ceo.postFormMulti("/app/admin/users/" + target.getId() + "/permission-grants",
                Map.of("permission", List.of("PERM_12_CANCEL"), "scopeType", List.of("STAGE_RESTRICTED"),
                        "stages", List.of("EDITING"), "reason", List.of("initial restricted grant")));
        assertThat(advancedGrant.statusCode()).isEqualTo(302);
        PermissionGrant original = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_12_CANCEL).get(0);

        HttpResponse<String> update = ceo.postFormMulti("/app/admin/permission-grants/" + original.getId() + "/update",
                Map.of("userId", List.of(target.getId().toString()), "scopeType", List.of("GLOBAL"),
                        "reason", List.of("broaden to Global")));
        assertThat(update.statusCode()).isEqualTo(302);

        List<PermissionGrant> nowActive = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_12_CANCEL);
        assertThat(nowActive).hasSize(1);
        assertThat(nowActive.get(0).getScopeType().name()).isEqualTo("GLOBAL");

        HttpResponse<String> page = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(page.body(), "PERM_12_CANCEL")).isEqualTo("perm-status-global");
    }

    @Test
    void notGrantedRowHasDisabledControlsAndUncheckedCheckbox() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Disabled Target", HR_MANAGER_ROLE_ID);

        HttpResponse<String> page = ceo.get("/app/admin/users/" + target.getId());
        String rowHtml = rowHtml(page.body(), "PERM_13_FOLDER_LINK_MANAGE");
        assertThat(rowHtml).contains("disabled");
        assertThat(rowHtml).doesNotContain("checked");
        assertThat(statusClassForPermission(page.body(), "PERM_13_FOLDER_LINK_MANAGE")).isEqualTo("perm-status-none");
    }

    /** The mandatory §13/§44 check this whole redesign was gated on: the schema allows 2+
     * simultaneously-active grants for one user+permission (no unique constraint) - this proves the
     * unified table never merges, hides, or bulk-revokes them, and correctly auto-collapses back to
     * normal single-row mode once only one remains. */
    @Test
    void multiGrantRowNeverMergesOrBulkRevokesAndAutoCollapsesWhenDownToOne() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "Multi Target", HR_MANAGER_ROLE_ID);

        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_05_SHOOT_REVIEW")).statusCode()).isEqualTo(200);
        assertThat(ceo.postFormMulti("/app/admin/users/" + target.getId() + "/permission-grants",
                Map.of("permission", List.of("PERM_05_SHOOT_REVIEW"), "scopeType", List.of("STAGE_RESTRICTED"),
                        "stages", List.of("SHOOTING"), "reason", List.of("multi-grant test")))
                .statusCode()).isEqualTo(302);

        List<PermissionGrant> bothActive = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_05_SHOOT_REVIEW);
        assertThat(bothActive).hasSize(2);

        HttpResponse<String> multiPage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(multiPage.body(), "PERM_05_SHOOT_REVIEW")).isEqualTo("perm-status-multi");
        String multiRowHtml = rowHtml(multiPage.body(), "PERM_05_SHOOT_REVIEW");
        assertThat(multiRowHtml).contains("checked").contains("disabled"); // summary indicator only, never a live toggle
        assertThat(multiPage.body()).contains("multi-row-PERM_05_SHOOT_REVIEW"); // both grants individually listed, not hidden

        // Revoke ONE of the two individually - exactly what the sub-table's own Revoke button does.
        PermissionGrant globalOne = bothActive.stream()
                .filter(g -> g.getScopeType().name().equals("GLOBAL")).findFirst().orElseThrow();
        assertThat(ceo.postFormMulti("/app/admin/permission-grants/" + globalOne.getId() + "/revoke",
                Map.of("userId", List.of(target.getId().toString()), "reason", List.of("N/A")))
                .statusCode()).isEqualTo(302);

        List<PermissionGrant> oneLeft = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_05_SHOOT_REVIEW);
        assertThat(oneLeft).hasSize(1); // the OTHER grant is untouched, never bulk-revoked
        assertThat(oneLeft.get(0).getScopeType().name()).isEqualTo("STAGE_RESTRICTED");

        // Auto-collapses back to normal single-row inline-edit mode.
        HttpResponse<String> singlePage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(singlePage.body(), "PERM_05_SHOOT_REVIEW")).isEqualTo("perm-status-restricted");

        // Revoke the last remaining grant - row returns to Not Granted.
        assertThat(ceo.postFormMulti("/app/admin/permission-grants/" + oneLeft.get(0).getId() + "/revoke",
                Map.of("userId", List.of(target.getId().toString()), "reason", List.of("N/A")))
                .statusCode()).isEqualTo(302);
        HttpResponse<String> notGrantedPage = ceo.get("/app/admin/users/" + target.getId());
        assertThat(statusClassForPermission(notGrantedPage.body(), "PERM_05_SHOOT_REVIEW")).isEqualTo("perm-status-none");
    }

    @Test
    void unauthorizedUserCannotUseUpdateEndpoint() throws Exception {
        long unique = Instant.now().toEpochMilli();
        TestApiClient ceo = ceo();
        User target = createUser(ceo, unique, "UpdateAuth Target", HR_MANAGER_ROLE_ID);
        assertThat(ceo.postFormAjax("/app/admin/users/" + target.getId() + "/permission-grants/quick",
                Map.of("permission", "PERM_14_TEAM_WORKLOAD_VIEW")).statusCode()).isEqualTo(200);
        PermissionGrant grant = permissionGrantRepository.findByGranteeAndPermissionAndActiveTrue(
                userRepository.findById(target.getId()).orElseThrow(), OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW).get(0);

        String nonCeoEmail = "update-noauth-" + unique + "@kcpcbandhani.local";
        createUser(ceo, unique, "NonCeo Updater", CAMERA_PERSON_ROLE_ID, nonCeoEmail);
        TestApiClient nonCeo = new TestApiClient(port);
        nonCeo.login(nonCeoEmail, "Passw0rd!");

        // This MVC endpoint always redirects (catches DomainException into a flash error, same
        // pattern as grant/modify/revoke) - the real authorization guarantee is that the grant's
        // state never actually changes, not the HTTP status.
        HttpResponse<String> resp = nonCeo.postFormMulti("/app/admin/permission-grants/" + grant.getId() + "/update",
                Map.of("userId", List.of(target.getId().toString()), "scopeType", List.of("GLOBAL"),
                        "reason", List.of("unauthorized attempt")));
        assertThat(resp.statusCode()).isEqualTo(302);
        PermissionGrant unchanged = permissionGrantRepository.findById(grant.getId()).orElseThrow();
        assertThat(unchanged.isActive()).isTrue();
        assertThat(unchanged.getEffectiveUntil()).isNull();
    }

    private String rowHtml(String pageBody, String permissionCode) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<tr class=\"perm-row\"[^>]*data-permission=\"" + permissionCode + "\"[^>]*>[\\s\\S]*?</tr>")
                .matcher(pageBody);
        assertThat(m.find()).as("row html for " + permissionCode).isTrue();
        return m.group();
    }

    private int extractGrantedCount(String pageBody) {
        // "Granted</span>\n<span class=\"perm-summary-count\">N</span>" - extract N from the
        // second perm-summary-count span (Total is first, Granted is second, in that DOM order).
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("perm-icon-granted.*?perm-summary-count\">(\\d+)</span>", java.util.regex.Pattern.DOTALL)
                .matcher(pageBody);
        assertThat(m.find()).as("Granted summary count must be present in the page").isTrue();
        return Integer.parseInt(m.group(1));
    }

    /** Reads the checklist row's status badge CSS class for one permission code - deliberately
     * class-based (ASCII-only: perm-status-global/perm-status-restricted/perm-status-none) rather
     * than matching the visible "Granted · Global" text, which contains a non-ASCII middle dot
     * that would make this assertion depend on the test source file's compile-time encoding. */
    private String statusClassForPermission(String pageBody, String permissionCode) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("data-permission=\"" + permissionCode + "\"[\\s\\S]*?perm-status-badge (perm-status-\\S+)\"")
                .matcher(pageBody);
        assertThat(m.find()).as("permission management row for " + permissionCode + " must be present").isTrue();
        return m.group(1);
    }

    private TestApiClient ceo() throws Exception {
        TestApiClient ceo = new TestApiClient(port);
        ceo.login("ceo@kcpcbandhani.local", "ChangeMe123!");
        return ceo;
    }

    private User createUser(TestApiClient ceo, long unique, String namePrefix, String businessRoleId) throws Exception {
        return createUser(ceo, unique, namePrefix, businessRoleId,
                "quickgrant-" + namePrefix.replace(" ", "").toLowerCase() + "-" + unique + "@kcpcbandhani.local");
    }

    private User createUser(TestApiClient ceo, long unique, String namePrefix, String businessRoleId, String email)
            throws Exception {
        HttpResponse<String> created = ceo.postForm("/app/admin/users",
                Map.of("fullName", namePrefix + " " + unique, "email", email, "password", "Passw0rd!",
                        "businessRoleId", businessRoleId, "creationReason", "permission quick-grant test fixture"));
        assertThat(created.statusCode()).isEqualTo(302);
        return userRepository.findByEmailIgnoreCase(email).orElseThrow();
    }
}
