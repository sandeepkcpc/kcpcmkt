package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.identity.domain.LifecycleStage;
import com.kcpc.mkt.identity.domain.OperationalPermission;
import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes {@code accessClass}/{@code businessRoleName}/{@code canSeeAdministration} available to
 * every MVC view so the shared header/nav fragment (fragments/nav.jsp) can gate CEO-only links,
 * branch the nav by Business Role (ENG-067: Model gets "My Shoots" instead of "My Work"), and show
 * the consolidated "Administration" nav entry only to users with real admin access - consistently,
 * regardless of whether the handling controller method also sets them explicitly.
 */
@ControllerAdvice(basePackages = "com.kcpc.mkt.web.mvc")
public class MvcNavigationAdvice {

    private final AuthorizationService authorizationService;

    public MvcNavigationAdvice(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @ModelAttribute("accessClass")
    public AccessClass accessClass(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? null : principal.user().resolvedAccessClass();
    }

    @ModelAttribute("businessRoleName")
    public String businessRoleName(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null || principal.user().getBusinessRole() == null) {
            return null;
        }
        return principal.user().getBusinessRole().getRoleName();
    }

    /**
     * Centralized workflow-participation nav rule (see AuthorizationService#isNonProductionEmployee,
     * the single source of truth this delegates to entirely - never re-derived here from role name
     * or permission grants): true only for an EMPLOYEE whose Business Role does not participate in
     * the Content Production workflow, in which case fragments/nav.jsp shows only My Ideas/Submit
     * Idea. WorkflowParticipationInterceptor enforces the same rule server-side for direct URLs.
     */
    @ModelAttribute("nonProductionEmployee")
    public boolean nonProductionEmployee(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal != null && authorizationService.isNonProductionEmployee(principal.user());
    }

    /**
     * Administration nav visibility: never designation-based. Users/Business Roles/Permissions
     * stay CEO_OWNER-only (see AdminMvcController#isCeo, unchanged); Publishing Catalogue alone
     * also accepts a delegated PERM_17_PLATFORM_CATALOGUE_MANAGE grant (AdminMvcController
     * #catalogue's own existing check, mirrored here exactly, not re-derived) - so a non-CEO/MM
     * Employee holding that one grant still sees "Administration" (landing on Catalogue only),
     * while a Marketing Manager with no delegated grant does not see it at all. The backend
     * re-validates every /app/admin/* route unconditionally regardless of this nav flag.
     */
    @ModelAttribute("canSeeAdministration")
    public boolean canSeeAdministration(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        if (principal == null) {
            return false;
        }
        if (authorizationService.hasNativeAuthority(principal.user())) {
            return true;
        }
        try {
            authorizationService.requireAuthority(principal.user(), OperationalPermission.PERM_17_PLATFORM_CATALOGUE_MANAGE,
                    LifecycleStage.ADMINISTRATIVE, null);
            return true;
        } catch (DomainException e) {
            return false;
        }
    }
}
