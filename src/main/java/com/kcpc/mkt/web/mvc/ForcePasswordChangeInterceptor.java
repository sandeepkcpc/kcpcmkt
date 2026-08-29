package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Admin/CEO Password Reset: once a temporary password has been issued
 * ({@code User.isPasswordChangeRequired()}), every {@code /app/**} page - including a direct URL
 * typed straight into the browser - redirects to the Change Password screen until the employee
 * sets their own new password, exactly like {@link WorkflowParticipationInterceptor}'s own
 * deny-by-default pattern for workspace reachability (registered ahead of it in
 * {@link WebMvcConfig} so a forced password change always wins over that separate restriction).
 * The account itself stays fully active throughout - this only gates navigation, never login or
 * any REST API authorization check.
 */
public class ForcePasswordChangeInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof KcpcUserPrincipal principal)) {
            return true;
        }
        User user = principal.user();
        if (!user.isPasswordChangeRequired()) {
            return true;
        }

        String contextPath = request.getContextPath();
        String uri = request.getRequestURI();
        if (uri.startsWith(contextPath + "/app/change-password")) {
            return true;
        }

        response.sendRedirect(contextPath + "/app/change-password");
        return false;
    }
}
