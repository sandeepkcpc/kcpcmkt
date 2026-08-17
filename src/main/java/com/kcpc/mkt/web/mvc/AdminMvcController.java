package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.BusinessRole;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.domain.PermissionScopeType;
import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.BusinessRoleRepository;
import com.kcpc.mkt.identity.repository.PermissionGrantRepository;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.identity.service.BusinessRoleAdminService;
import com.kcpc.mkt.identity.service.PermissionGrantAdminService;
import com.kcpc.mkt.identity.service.UserAdminService;
import com.kcpc.mkt.masterdata.repository.CompanyChannelRepository;
import com.kcpc.mkt.masterdata.repository.PlatformRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.masterdata.service.MasterCatalogueService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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
    private final PlatformRepository platformRepository;
    private final CompanyChannelRepository channelRepository;
    private final PublicationTargetRepository targetRepository;
    private final AuthorizationService authorizationService;

    private final UserAdminService userAdminService;
    private final BusinessRoleAdminService businessRoleAdminService;
    private final PermissionGrantAdminService permissionGrantAdminService;
    private final MasterCatalogueService masterCatalogueService;

    public AdminMvcController(UserRepository userRepository, BusinessRoleRepository businessRoleRepository,
                               PermissionGrantRepository permissionGrantRepository, PlatformRepository platformRepository,
                               CompanyChannelRepository channelRepository, PublicationTargetRepository targetRepository,
                               AuthorizationService authorizationService, UserAdminService userAdminService,
                               BusinessRoleAdminService businessRoleAdminService,
                               PermissionGrantAdminService permissionGrantAdminService,
                               MasterCatalogueService masterCatalogueService) {
        this.userRepository = userRepository;
        this.businessRoleRepository = businessRoleRepository;
        this.permissionGrantRepository = permissionGrantRepository;
        this.platformRepository = platformRepository;
        this.channelRepository = channelRepository;
        this.targetRepository = targetRepository;
        this.authorizationService = authorizationService;
        this.userAdminService = userAdminService;
        this.businessRoleAdminService = businessRoleAdminService;
        this.permissionGrantAdminService = permissionGrantAdminService;
        this.masterCatalogueService = masterCatalogueService;
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

    // -------------------------------------------------------------- Permissions (consolidated)

    /** UI/UX v0.2 §6.2 extended: a single all-users "who holds what" view, not just per-user. */
    @GetMapping("/permissions")
    public String permissions(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        model.addAttribute("grants", permissionGrantAdminService.listActiveGrants(principal.user()));
        return "admin-permissions";
    }

    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable UUID id, @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        User user = userRepository.findById(id).orElseThrow(() -> DomainException.notFound("User not found"));
        model.addAttribute("targetUser", user);
        model.addAttribute("businessRoles", businessRoleRepository.findByActiveTrue());
        model.addAttribute("grants", permissionGrantRepository.findByGrantee(user));
        model.addAttribute("permissions", OperationalPermission.values());
        model.addAttribute("scopeTypes", PermissionScopeType.values());
        model.addAttribute("stages", LifecycleStage.values());
        return "admin-user-detail";
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

    // ------------------------------------------------------- Business Roles

    @GetMapping("/business-roles")
    public String businessRoles(@AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (!isCeo(principal)) {
            return "redirect:/app/home";
        }
        model.addAttribute("roles", businessRoleRepository.findAll());
        model.addAttribute("accessClasses", AccessClass.values());
        return "admin-business-roles";
    }

    @PostMapping("/business-roles")
    public String createBusinessRole(@RequestParam String roleName, @RequestParam AccessClass accessClass,
                                      @AuthenticationPrincipal KcpcUserPrincipal principal, RedirectAttributes ra) {
        try {
            businessRoleAdminService.create(principal.user(), roleName, accessClass);
            ra.addFlashAttribute("successMessage", "Business Role created.");
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
