package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.domain.AccessClass;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Makes {@code accessClass} available to every MVC view so the shared header/nav fragment
 * (fragments/nav.jsp) can gate CEO-only links consistently, regardless of whether the handling
 * controller method also sets it explicitly.
 */
@ControllerAdvice(basePackages = "com.kcpc.mkt.web.mvc")
public class MvcNavigationAdvice {

    @ModelAttribute("accessClass")
    public AccessClass accessClass(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return principal == null ? null : principal.user().resolvedAccessClass();
    }
}
