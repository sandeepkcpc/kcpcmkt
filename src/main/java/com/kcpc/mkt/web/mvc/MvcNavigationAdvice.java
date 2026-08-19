package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes {@code accessClass}/{@code businessRoleName} available to every MVC view so the shared
 * header/nav fragment (fragments/nav.jsp) can gate CEO-only links and branch the nav by Business
 * Role (ENG-067: Model gets "My Shoots" instead of "My Work") consistently, regardless of whether
 * the handling controller method also sets them explicitly.
 */
@ControllerAdvice(basePackages = "com.kcpc.mkt.web.mvc")
public class MvcNavigationAdvice {

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
}
