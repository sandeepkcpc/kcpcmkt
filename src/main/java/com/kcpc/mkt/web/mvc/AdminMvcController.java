package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.audit.domain.SystemAuditLog;
import com.kcpc.mkt.audit.repository.SystemAuditLogRepository;
import com.kcpc.mkt.common.error.ApiErrorResponse;
import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.common.error.ErrorCode;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionGrant;
import com.kcpc.mkt.identity.domain.PermissionGrantStageScope;
import com.kcpc.mkt.identity.domain.PermissionScopeType;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.dto.GrantedPermissionRow;
import com.kcpc.mkt.identity.dto.PermissionManagementRow;
import com.kcpc.mkt.identity.dto.PermissionSummaryCounts;
import com.kcpc.mkt.identity.dto.UserImportBatchResult;
import com.kcpc.mkt.identity.repository.BusinessRoleRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantItemScopeRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantStageScopeRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.service.BusinessRoleAdminService;
import com.kcpc.mkt.identity.service.PermissionGrantAdminService;
import com.kcpc.mkt.identity.service.UserAdminService;
import com.kcpc.mkt.identity.service.UserCsvImportService;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PlatformRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.masterdata.service.MasterCatalogueService;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * UI/UX Design Specification v0.2 §6: Group A Operational Governance (write) - User & Business
 * Role Administration, Operational-Permission Administration, Master-Catalogue Management,
 * Business Role Catalogue Administration. All CEO-exclusive except the Catalogue screen (CEO/MM
 * native, or Employee with Permission #17). Every handler calls the same application/service
 * layer as the equivalent REST controller.
 */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/app/admin")
public class AdminMvcController {

    private final UserRepository userRepository;
    private final BusinessRoleRepository businessRoleRepository;
    private final PermissionGrantRepository permissionGrantRepository;
    private final PermissionGrantStageScopeRepository stageScopeRepository;
    private final PermissionGrantItemScopeRepository itemScopeRepository;
    private final SystemAuditLogRepository systemAuditLogRepository;
    private final ContentPlanRepository contentPlanRepository;
    private final PlatformRepository platformRepository;
    private final CompanyChannelRepository channelRepository;
    private final PublicationTargetRepository targetRepository;
    private final AuthorizationService authorizationService;

    private final UserAdminService userAdminService;
    private final BusinessRoleAdminService businessRoleAdminService;
    private final PermissionGrantAdminService permissionGrantAdminService;
    private final MasterCatalogueService masterCatalogueService;
    private final UserCsvImportService userCsvImportService;

    public AdminMvcController(UserRepository userRepository, BusinessRoleRepository businessRoleRepository,
                               PermissionGrantRepository permissionGrantRepository,
                               PermissionGrantStageScopeRepository stageScopeRepository,
                               PermissionGrantItemScopeRepository itemScopeRepository,
                               SystemAuditLogRepository systemAuditLogRepository, ContentPlanRepository contentPlanRepository,
                               PlatformRepository platformRepository,
                               CompanyChannelRepository channelRepository, PublicationTargetRepository targetRepository,
                               AuthorizationService authorizationService, UserAdminService userAdminService,
                               BusinessRoleAdminService businessRoleAdminService,
                               PermissionGrantAdminService permissionGrantAdminService,
                               MasterCatalogueService masterCatalogueService,
                               UserCsvImportService userCsvImportService) {
        this.userRepository = userRepository;
        this.businessRoleRepository = businessRoleRepository;
        this.permissionGrantRepository = permissionGrantRepository;
        this.stageScopeRepository = stageScopeRepository;
        this.itemScopeRepository = itemScopeRepository;
        this.systemAuditLogRepository = systemAuditLogRepository;
        this.contentPlanRepository = contentPlanRepository;
        this.platformRepository = platformRepository;
        this.channelRepository = channelRepository;
        this.targetRepository = targetRepository;
        this.authorizationService = authorizationService;
        this.userAdminService = userAdminService;
        this.businessRoleAdminService = businessRoleAdminService;
        this.permissionGrantAdminService = permissionGrantAdminService;
        this.masterCatalogueService = masterCatalogueService;
        this.userCsvImportService = userCsvImportService;
    }

    private boolean isCeo(KcpcUserPrincipal principal) {
        return principal.user().resolvedAccessClass() == AccessClass.CEO_OWNER;
    }

    // -------------------------------------------------------------- Users

    @GetMapping("/users")
    public String users(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("businessRoles", businessRoleRepository.findByActiveTrue());
        model.addAttribute("activeAdminTab", "users");
        return "admin-users";
    }

    @PostMapping("/users")
    public String createUser(@RequestParam String fullName, @RequestParam String email, @RequestParam String password,
                              @RequestParam UUID businessRoleId, @RequestParam String creationReason,
                              @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            userAdminService.createUser(principal.user(), fullName, email, password, businessRoleId, creationReason);
            ra.addFlashAttribute("successMessage", "User created.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users";
    }

    // -------------------------------------------------------------- User CSV Import

    private static final String IMPORT_SESSION_BYTES = "userCsvImportBytes";
    private static final String IMPORT_SESSION_FILENAME = "userCsvImportFilename";

    @GetMapping("/users/import")
    public String importUploadForm(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        model.addAttribute("activeAdminTab", "users");
        return "admin-users-import";
    }

    @PostMapping("/users/import/preview")
    public String importPreview(@RequestParam("file") MultipartFile file,
                                 @AuthenticationPrincipal KcpcUserPrincipal principal, HttpSession session,
                                 Model model, RedirectAttributes ra) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMessage", "Please choose a CSV file to upload.");
            return "redirect:/app/admin/users/import";
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            ra.addFlashAttribute("errorMessage", "Could not read the uploaded file.");
            return "redirect:/app/admin/users/import";
        }
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.csv";
        UserImportBatchResult result = userCsvImportService.validate(bytes, filename);
        session.setAttribute(IMPORT_SESSION_BYTES, bytes);
        session.setAttribute(IMPORT_SESSION_FILENAME, filename);
        model.addAttribute("activeAdminTab", "users");
        model.addAttribute("result", result);
        return "admin-users-import-preview";
    }

    @PostMapping("/users/import/confirm")
    public String importConfirm(@AuthenticationPrincipal KcpcUserPrincipal principal, HttpSession session,
                                 Model model, RedirectAttributes ra) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        byte[] bytes = (byte[]) session.getAttribute(IMPORT_SESSION_BYTES);
        String filename = (String) session.getAttribute(IMPORT_SESSION_FILENAME);
        if (bytes == null) {
            ra.addFlashAttribute("errorMessage", "Your import session expired. Please upload the file again.");
            return "redirect:/app/admin/users/import";
        }
        session.removeAttribute(IMPORT_SESSION_BYTES);
        session.removeAttribute(IMPORT_SESSION_FILENAME);
        UserImportBatchResult result = userCsvImportService.importValidated(principal.user(), bytes, filename);
        model.addAttribute("activeAdminTab", "users");
        model.addAttribute("result", result);
        return "admin-users-import-result";
    }

    // -------------------------------------------------------------- Permissions (consolidated)

    /** UI/UX v0.2 §6.2 extended: a single all-users "who holds what" view, not just per-user. */
    @GetMapping("/permissions")
    public String permissions(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        model.addAttribute("grants", permissionGrantAdminService.listActiveGrants(principal.user()));
        model.addAttribute("activeAdminTab", "permissions");
        model.addAttribute("permissionDescriptions", PERMISSION_DESCRIPTIONS);
        return "admin-permissions";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        User user = userRepository.findById(id).orElseThrow(() -> DomainException.notFound("User not found"));
        List<PermissionGrant> allGrants = permissionGrantRepository.findByGrantee(user);

        List<PermissionManagementRow> managementRows = buildManagementRows(allGrants);
        model.addAttribute("targetUser", user);
        model.addAttribute("businessRoles", businessRoleRepository.findByActiveTrue());
        model.addAttribute("managementRows", managementRows);
        model.addAttribute("permissionSummary", buildSummaryCounts(managementRows));
        model.addAttribute("permissions", OperationalPermission.values());
        model.addAttribute("scopeTypes", PermissionScopeType.values());
        model.addAttribute("stages", LifecycleStage.values());
        model.addAttribute("permissionDescriptions", PERMISSION_DESCRIPTIONS);
        return "admin-user-detail";
    }

    /** User Detail unified Permission Management table (permission-admin-ui final redesign): one
     * row per catalogue permission - never hardcoded, iterates {@code OperationalPermission.values()}
     * - resolved to exactly one of Not-Granted / Single-grant / Multi-grant state from this user's
     * real grant history. The multi-grant state is a real, intentionally-preserved possibility (no
     * DB or service constraint prevents 2+ simultaneously-active grants for one user+permission) -
     * it is surfaced explicitly (see {@link PermissionManagementRow#multi}), never silently
     * collapsed into one editable row. */
    private List<PermissionManagementRow> buildManagementRows(List<PermissionGrant> allGrants) {
        Instant now = Instant.now();
        Map<OperationalPermission, List<PermissionGrant>> validByPermission = allGrants.stream()
                .filter(g -> g.isCurrentlyValid(now))
                .collect(Collectors.groupingBy(PermissionGrant::getPermission));

        List<UUID> validGrantIds = validByPermission.values().stream().flatMap(List::stream)
                .map(PermissionGrant::getId).toList();
        // Latest PERMISSION_GRANTED-or-PERMISSION_MODIFIED row per grant id - an inline Update that
        // only changes Expiry/Reason (no scope change) is audited as PERMISSION_MODIFIED, never a
        // second PERMISSION_GRANTED, so looking up only the grant event would keep showing the
        // original reason forever after such an update.
        Map<UUID, String> reasonByGrantId = validGrantIds.isEmpty() ? Map.of()
                : systemAuditLogRepository.findByTargetEntityIdInAndEventTypeIn(validGrantIds,
                        List.of("PERMISSION_GRANTED", "PERMISSION_MODIFIED")).stream()
                .collect(Collectors.toMap(SystemAuditLog::getTargetEntityId, java.util.function.Function.identity(),
                        (a, b) -> a.getEventTimestamp().isAfter(b.getEventTimestamp()) ? a : b))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getActionReason()));

        List<PermissionManagementRow> rows = new ArrayList<>();
        for (OperationalPermission permission : OperationalPermission.values()) {
            String displayName = PERMISSION_DISPLAY_NAMES.get(permission);
            String description = PERMISSION_DESCRIPTIONS.get(permission);
            List<PermissionGrant> validGrants = validByPermission.getOrDefault(permission, List.of());
            if (validGrants.isEmpty()) {
                rows.add(PermissionManagementRow.notGranted(permission, displayName, description));
            } else if (validGrants.size() == 1) {
                PermissionGrant g = validGrants.get(0);
                List<LifecycleStage> stages = g.getScopeType() == PermissionScopeType.STAGE_RESTRICTED
                        ? stageScopeRepository.findByGrant(g).stream().map(PermissionGrantStageScope::getStageNumber).toList()
                        : List.of();
                rows.add(PermissionManagementRow.single(permission, displayName, description, g.getId(), g.getScopeType(),
                        stages, stageLabelFor(g), g.getEffectiveFrom(), g.getEffectiveUntil(),
                        reasonByGrantId.getOrDefault(g.getId(), "-")));
            } else {
                List<GrantedPermissionRow> activeRows = validGrants.stream()
                        .sorted(Comparator.comparing(PermissionGrant::getEffectiveFrom).reversed())
                        .map(g -> new GrantedPermissionRow(g.getId(), g.getPermission(), description, g.getScopeType(),
                                stageLabelFor(g), g.getEffectiveFrom(), g.getEffectiveUntil(),
                                reasonByGrantId.getOrDefault(g.getId(), "-"), true, false))
                        .toList();
                rows.add(PermissionManagementRow.multi(permission, displayName, description, activeRows));
            }
        }
        return rows;
    }

    private String stageLabelFor(PermissionGrant grant) {
        return switch (grant.getScopeType()) {
            case GLOBAL -> "-";
            case STAGE_RESTRICTED -> stageScopeRepository.findByGrant(grant).stream()
                    .map(s -> s.getStageNumber().name())
                    .collect(Collectors.joining(", "));
            case ITEM_SPECIFIC -> itemScopeRepository.findByGrant(grant).size() + " item(s)";
        };
    }

    /** Permission Summary card counts: derived from the same rows the unified table just built -
     * never a separate calculation that could disagree with what's on screen. Restricted is a
     * SUBSET of Granted (never added on top), and a permission with 2+ simultaneous active grants
     * still counts as exactly one permission code either way. */
    private PermissionSummaryCounts buildSummaryCounts(List<PermissionManagementRow> managementRows) {
        int total = managementRows.size();
        int granted = (int) managementRows.stream().filter(PermissionManagementRow::isGranted).count();
        int restricted = (int) managementRows.stream().filter(PermissionManagementRow::isRestricted).count();
        return new PermissionSummaryCounts(total, granted, restricted, total - granted);
    }

    /**
     * Permission-driven multi-function workflow: Administration-facing copy that clearly
     * distinguishes an ASSIGNMENT-management permission (PERM_04/06 - "manage who is assigned,"
     * never hands-on execution) from an EXECUTION permission (PERM_18/19/08 - "eligible for
     * assignment AND execution of your own actively assigned task"), plus the operational
     * consequence of granting each execution permission (spec §26/27) - never implying a
     * permission alone grants access to every Content ID in that stage.
     */
    private static final Map<OperationalPermission, String> PERMISSION_DESCRIPTIONS = buildPermissionDescriptions();

    private static Map<OperationalPermission, String> buildPermissionDescriptions() {
        Map<OperationalPermission, String> d = new EnumMap<>(OperationalPermission.class);
        d.put(OperationalPermission.PERM_01_IDEA_REVIEW, "Evaluate submitted ideas: Approve, Reject, or Retain.");
        d.put(OperationalPermission.PERM_02_PLANNING_EXECUTION,
                "Manage an already-approved Content ID's Outputs, Reel Types, and Publication Scope (Publishing tab). "
                        + "The initial Idea Review approval itself is governed by PERM_01_IDEA_REVIEW alone, not this permission.");
        d.put(OperationalPermission.PERM_04_SHOOT_ASSIGNMENT,
                "ASSIGNMENT MANAGEMENT ONLY: allows the holder to assign/reassign/manage who is on the Shoot "
                        + "team. Does NOT make the holder eligible to execute Shoot work themselves - that requires "
                        + "PERM_18_SHOOT_EXECUTION separately.");
        d.put(OperationalPermission.PERM_05_SHOOT_REVIEW, "Approve or Request Rework on shoot output; attribute Cameraperson Marks.");
        d.put(OperationalPermission.PERM_06_EDIT_ASSIGNMENT,
                "ASSIGNMENT MANAGEMENT ONLY: allows the holder to assign/reassign/manage who is on the Edit "
                        + "team. Does NOT make the holder eligible to execute Edit work themselves - that requires "
                        + "PERM_19_EDIT_EXECUTION separately.");
        d.put(OperationalPermission.PERM_07_EDIT_REVIEW, "Approve or Request Rework on edited output; attribute Editor Marks.");
        d.put(OperationalPermission.PERM_08_PUBLISHING_EXECUTION,
                "EXECUTION PERMISSION: makes the holder eligible for the Publisher assignee/reassignment picker "
                        + "and allows execution (Start Publishing, recording Actual Publication events) ONLY on a "
                        + "Content ID where they hold an active Publishing assignment. Granting this does not by "
                        + "itself grant access to every Publishing Content ID, and does not grant authority to "
                        + "manage who else is assigned as Publisher (that stays native CEO/Marketing Manager "
                        + "authority only).");
        d.put(OperationalPermission.PERM_09_PERFORMANCE_UPDATE, "Enter/submit Creative Performance Scorecard metrics.");
        d.put(OperationalPermission.PERM_10_RESCHEDULE, "Modify approved production dates with mandatory reason.");
        d.put(OperationalPermission.PERM_11_REASSIGN, "Replace existing Shoot/Edit assignees with mandatory reason.");
        d.put(OperationalPermission.PERM_12_CANCEL, "Cancel a pre-completion deliverable with mandatory reason.");
        d.put(OperationalPermission.PERM_13_FOLDER_LINK_MANAGE, "Create/replace the parent asset folder link.");
        d.put(OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW, "View contextual team workload summaries (Team → Workload).");
        d.put(OperationalPermission.PERM_15_TEAM_KPI_VIEW, "View team-level KPI dashboards.");
        d.put(OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW, "View permitted audit-history records.");
        d.put(OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE, "Administer Platform/Company Channel/Publication Target master data (Administration → Catalogue only).");
        d.put(OperationalPermission.PERM_18_SHOOT_EXECUTION,
                "EXECUTION PERMISSION: makes the holder eligible for the Shoot assignee/reassignment picker "
                        + "and allows execution (Start/Continue/Submit Shoot) ONLY on a Content ID where they hold "
                        + "an active Shoot assignment. Granting this will make the user eligible in the Shoot "
                        + "assignee/reassignment pickers and let their assigned Shoot tasks appear in My Work - it "
                        + "does not by itself grant access to every Shoot Content ID, and their Business Role is "
                        + "never changed by granting it.");
        d.put(OperationalPermission.PERM_19_EDIT_EXECUTION,
                "EXECUTION PERMISSION: makes the holder eligible for the Edit assignee/reassignment picker and "
                        + "allows execution (Start/Continue/Submit Edit) ONLY on a Content ID where they hold an "
                        + "active Edit assignment. Granting this will make the user eligible in the Edit "
                        + "assignee/reassignment pickers and let their assigned Edit tasks appear in My Work - it "
                        + "does not by itself grant access to every Edit Content ID, and their Business Role is "
                        + "never changed by granting it.");
        return java.util.Collections.unmodifiableMap(d);
    }

    /** Centralized human-readable names for the unified Permission Management table (permission-
     * admin-ui final redesign spec §5) - the raw {@code PERM_NN_...} code stays visible as a small
     * muted sub-label under it (spec §6, developer/audit traceability), but is never the primary
     * on-screen text. {@link OperationalPermission} itself remains the sole authoritative catalogue -
     * this is presentation metadata only, resolved 1:1 from it. */
    private static final Map<OperationalPermission, String> PERMISSION_DISPLAY_NAMES = buildPermissionDisplayNames();

    private static Map<OperationalPermission, String> buildPermissionDisplayNames() {
        Map<OperationalPermission, String> d = new EnumMap<>(OperationalPermission.class);
        d.put(OperationalPermission.PERM_01_IDEA_REVIEW, "Idea Review");
        d.put(OperationalPermission.PERM_02_PLANNING_EXECUTION, "Planning Execution");
        d.put(OperationalPermission.PERM_04_SHOOT_ASSIGNMENT, "Shoot Assignment Management");
        d.put(OperationalPermission.PERM_05_SHOOT_REVIEW, "Shoot Review");
        d.put(OperationalPermission.PERM_06_EDIT_ASSIGNMENT, "Edit Assignment Management");
        d.put(OperationalPermission.PERM_07_EDIT_REVIEW, "Edit Review");
        d.put(OperationalPermission.PERM_08_PUBLISHING_EXECUTION, "Publishing Execution");
        d.put(OperationalPermission.PERM_09_PERFORMANCE_UPDATE, "Performance Update");
        d.put(OperationalPermission.PERM_10_RESCHEDULE, "Reschedule Content");
        d.put(OperationalPermission.PERM_11_REASSIGN, "Reassign Work");
        d.put(OperationalPermission.PERM_12_CANCEL, "Cancel Content");
        d.put(OperationalPermission.PERM_13_FOLDER_LINK_MANAGE, "Folder Link Management");
        d.put(OperationalPermission.PERM_14_TEAM_WORKLOAD_VIEW, "View Team Workload");
        d.put(OperationalPermission.PERM_15_TEAM_KPI_VIEW, "View KPI Dashboard");
        d.put(OperationalPermission.PERM_16_AUDIT_HISTORY_VIEW, "View Logs / Audit History");
        d.put(OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE, "Manage Publishing Catalogue");
        d.put(OperationalPermission.PERM_18_SHOOT_EXECUTION, "Shoot Execution");
        d.put(OperationalPermission.PERM_19_EDIT_EXECUTION, "Edit Execution");
        return java.util.Collections.unmodifiableMap(d);
    }

    @PostMapping("/users/{id}/business-role")
    public String changeBusinessRole(@PathVariable UUID id, @RequestParam UUID businessRoleId,
                                      @RequestParam String reason, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                      RedirectAttributes ra) {
        try {
            userAdminService.changeBusinessRole(principal.user(), id, businessRoleId, reason);
            ra.addFlashAttribute("successMessage", "Business Role updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + id;
    }

    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable UUID id, @RequestParam String reason,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            userAdminService.activate(principal.user(), id, reason);
            ra.addFlashAttribute("successMessage", "User activated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + id;
    }

    @PostMapping("/users/{id}/deactivate")
    public String deactivateUser(@PathVariable UUID id, @RequestParam String reason,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            userAdminService.deactivate(principal.user(), id, reason);
            ra.addFlashAttribute("successMessage", "User deactivated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + id;
    }

    /** Admin/CEO Password Reset: generates a temporary password and shows it to the CEO exactly
     * once (a flash attribute - gone on the very next real page load, never persisted/logged raw)
     * to copy and share out-of-band. The employee is forced to change it on their next login. */
    @PostMapping("/users/{id}/reset-password")
    public String resetUserPassword(@PathVariable UUID id, @RequestParam String reason,
                                     @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            String temporaryPassword = userAdminService.resetPasswordByAdmin(principal.user(), id, reason);
            ra.addFlashAttribute("temporaryPassword", temporaryPassword);
            ra.addFlashAttribute("successMessage",
                    "Temporary password generated. Copy and share it with the employee now - it will not be shown again.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + id;
    }

    // ------------------------------------------------------ Permission grants

    @PostMapping("/users/{id}/permission-grants")
    public String grantPermission(@PathVariable UUID id, @RequestParam OperationalPermission permission,
                                   @RequestParam PermissionScopeType scopeType,
                                   @RequestParam(required = false) List<LifecycleStage> stages,
                                   @RequestParam(required = false) String effectiveFrom,
                                   @RequestParam(required = false) String effectiveUntil,
                                   @RequestParam String reason, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            Instant from = effectiveFrom != null && !effectiveFrom.isBlank()
                    ? LocalDate.parse(effectiveFrom).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant() : Instant.now();
            Instant until = effectiveUntil != null && !effectiveUntil.isBlank()
                    ? LocalDate.parse(effectiveUntil).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant() : null;
            permissionGrantAdminService.grant(principal.user(), id, permission, scopeType, stages, List.of(), from,
                    until, reason);
            ra.addFlashAttribute("successMessage", "Permission granted.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + id;
    }

    /** User Detail quick-grant checklist (permission-admin-ui spec §6): checking a permission
     * immediately creates a GLOBAL/now/no-expiry/"N/A" grant via {@link
     * PermissionGrantAdminService#quickGrantGlobal} - same CEO-only authorization and audit trail
     * as the advanced form below, just with the 4 fields defaulted instead of asked for. Returns
     * JSON (no redirect) so the page can refresh its own checklist/summary/table regions without a
     * full reload; {@code #grantPermission} above remains the full-page-redirect advanced path. */
    @PostMapping("/users/{id}/permission-grants/quick")
    public ResponseEntity<?> quickGrantPermission(@PathVariable UUID id, @RequestParam OperationalPermission permission,
                                                   @AuthenticationPrincipal KcpcUserPrincipal principal,
                                                   HttpServletRequest request) {
        try {
            permissionGrantAdminService.quickGrantGlobal(principal.user(), id, permission);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (DomainException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiErrorResponse.of(e.getErrorCode(), e.getMessage(), request.getRequestURI()));
        }
    }

    @PostMapping("/permission-grants/{grantId}/modify")
    public String modifyGrant(@PathVariable UUID grantId, @RequestParam UUID userId,
                               @RequestParam(required = false) String newEffectiveUntil, @RequestParam String reason,
                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            Instant until = newEffectiveUntil != null && !newEffectiveUntil.isBlank()
                    ? LocalDate.parse(newEffectiveUntil).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant() : null;
            permissionGrantAdminService.modifyExpiry(principal.user(), grantId, until, reason);
            ra.addFlashAttribute("successMessage", "Grant modified.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + userId;
    }

    @PostMapping("/permission-grants/{grantId}/revoke")
    public String revokeGrant(@PathVariable UUID grantId, @RequestParam UUID userId, @RequestParam String reason,
                               @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            permissionGrantAdminService.revoke(principal.user(), grantId, reason);
            ra.addFlashAttribute("successMessage", "Grant revoked.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + userId;
    }

    /** User Detail unified Permission Management table's single "Update" action (permission-admin-
     * ui final redesign spec §20/§21): saves Scope/Stage(s)/Item/Expires/Reason for one row's
     * currently-active grant in one call - delegates entirely to {@link
     * PermissionGrantAdminService#updateGrant}, which decides internally whether a plain expiry/
     * reason change (grant row preserved) or a controlled revoke+recreate (scope actually changed)
     * is needed; the admin only ever sees one "Update" action either way. ITEM_SPECIFIC resolves
     * the entered Content ID to its WorkflowInstance server-side (spec §16: inline text field
     * rather than a separate picker/modal - safely representable without one). */
    @PostMapping("/permission-grants/{grantId}/update")
    public String updateGrant(@PathVariable UUID grantId, @RequestParam UUID userId,
                               @RequestParam PermissionScopeType scopeType,
                               @RequestParam(required = false) List<LifecycleStage> stages,
                               @RequestParam(required = false) String itemContentId,
                               @RequestParam(required = false) String effectiveUntil,
                               @RequestParam String reason, @AuthenticationPrincipal KcpcUserPrincipal principal,
                               RedirectAttributes ra) {
        try {
            Instant until = effectiveUntil != null && !effectiveUntil.isBlank()
                    ? LocalDate.parse(effectiveUntil).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant() : null;
            List<UUID> workflowInstanceIds = List.of();
            if (scopeType == PermissionScopeType.ITEM_SPECIFIC) {
                if (itemContentId == null || itemContentId.isBlank()) {
                    throw DomainException.badRequest(ErrorCode.VALIDATION_FAILED,
                            "A Content ID is required for an Item-Specific scope");
                }
                ContentPlan plan = contentPlanRepository.findByContentId(itemContentId.trim())
                        .orElseThrow(() -> DomainException.notFound("No Content Plan found for Content ID: " + itemContentId));
                workflowInstanceIds = List.of(plan.getWorkflowInstance().getId());
            }
            permissionGrantAdminService.updateGrant(principal.user(), grantId, scopeType, stages, workflowInstanceIds,
                    until, reason);
            ra.addFlashAttribute("successMessage", "Permission updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/users/" + userId;
    }

    // ------------------------------------------------------- Business Roles

    @GetMapping("/business-roles")
    public String businessRoles(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        model.addAttribute("roles", businessRoleRepository.findAll());
        model.addAttribute("accessClasses", AccessClass.values());
        model.addAttribute("activeAdminTab", "business-roles");
        return "admin-business-roles";
    }

    @PostMapping("/business-roles")
    public String createBusinessRole(@RequestParam String roleName, @RequestParam AccessClass accessClass,
                                      @RequestParam(required = false, defaultValue = "false") boolean participatesInWorkflow,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            businessRoleAdminService.create(principal.user(), roleName, accessClass, participatesInWorkflow);
            ra.addFlashAttribute("successMessage", "Business Role created.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/business-roles";
    }

    @PostMapping("/business-roles/{id}/workflow-participation")
    public String setBusinessRoleWorkflowParticipation(@PathVariable UUID id, @RequestParam boolean participatesInWorkflow,
                                                         @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            businessRoleAdminService.setWorkflowParticipation(principal.user(), id, participatesInWorkflow);
            ra.addFlashAttribute("successMessage", "Business Role workflow participation updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/business-roles";
    }

    @PostMapping("/business-roles/{id}/deactivate")
    public String deactivateBusinessRole(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                          RedirectAttributes ra) {
        try {
            businessRoleAdminService.deactivate(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Business Role deactivated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/business-roles";
    }

    @PostMapping("/business-roles/{id}/activate")
    public String activateBusinessRole(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                        RedirectAttributes ra) {
        try {
            businessRoleAdminService.activate(principal.user(), id);
            ra.addFlashAttribute("successMessage", "Business Role activated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/business-roles";
    }

    // ---------------------------------------------------------- Catalogue

    @GetMapping("/catalogue")
    public String catalogue(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        boolean allowed = authorizationService.hasNativeAuthority(principal.user());
        if (!allowed) {
            try {
                authorizationService.requireAuthority(principal.user(), OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE,
                        LifecycleStage.ADMINISTRATIVE, null);
                allowed = true;
            } catch (DomainException e) {
                allowed = false;
            }
        }
        if (!allowed) {
            return "redirect:/app/home";
        }
        model.addAttribute("platforms", platformRepository.findAll());
        model.addAttribute("channels", channelRepository.findAll());
        model.addAttribute("targets", targetRepository.findAll());
        model.addAttribute("activeAdminTab", "catalogue");
        return "admin-catalogue";
    }

    @PostMapping("/catalogue/platforms")
    public String createPlatform(@RequestParam String platformName, @RequestParam String catalogueReason,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            masterCatalogueService.createPlatform(principal.user(), platformName, catalogueReason);
            ra.addFlashAttribute("successMessage", "Platform created.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/catalogue";
    }

    @PostMapping("/catalogue/platforms/{id}")
    public String updatePlatform(@PathVariable UUID id, @RequestParam(required = false) String platformName,
                                  @RequestParam(required = false) Boolean isActive, @RequestParam String catalogueReason,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            masterCatalogueService.updatePlatform(principal.user(), id, platformName, isActive, catalogueReason);
            ra.addFlashAttribute("successMessage", "Platform updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/catalogue";
    }

    @PostMapping("/catalogue/channels")
    public String createChannel(@RequestParam String channelHandle, @RequestParam String catalogueReason,
                                 @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            masterCatalogueService.createChannel(principal.user(), channelHandle, catalogueReason);
            ra.addFlashAttribute("successMessage", "Channel created.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/catalogue";
    }

    @PostMapping("/catalogue/channels/{id}")
    public String updateChannel(@PathVariable UUID id, @RequestParam(required = false) String channelHandle,
                                 @RequestParam(required = false) Boolean isActive, @RequestParam String catalogueReason,
                                 @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            masterCatalogueService.updateChannel(principal.user(), id, channelHandle, isActive, catalogueReason);
            ra.addFlashAttribute("successMessage", "Channel updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/catalogue";
    }

    @PostMapping("/catalogue/targets")
    public String createTarget(@RequestParam UUID platformId, @RequestParam UUID channelId,
                                @RequestParam String targetName, @RequestParam String catalogueReason,
                                @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            masterCatalogueService.createTarget(principal.user(), platformId, channelId, targetName, catalogueReason);
            ra.addFlashAttribute("successMessage", "Target created.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/catalogue";
    }

    @PostMapping("/catalogue/targets/{id}")
    public String setTargetActive(@PathVariable UUID id, @RequestParam boolean isActive,
                                   @RequestParam String catalogueReason, @AuthenticationPrincipal KcpcUserPrincipal principal,
                                   RedirectAttributes ra) {
        try {
            masterCatalogueService.setTargetActive(principal.user(), id, isActive, catalogueReason);
            ra.addFlashAttribute("successMessage", "Target updated.");
        } catch (DomainException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/app/admin/catalogue";
    }
}
