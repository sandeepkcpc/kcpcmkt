package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Centralized server-side enforcement of the workflow-participation nav/landing rule (see
 * {@link AuthorizationService#isNonProductionEmployee}): a non-production EMPLOYEE is restricted
 * to My Ideas + Submit Idea (the {@code /app/ideas} route family) for every {@code /app/**} page,
 * including a direct URL typed straight into the browser - deny-by-default (only {@code
 * /app/ideas*} is allowlisted) so a future new {@code /app/...} route can never accidentally leak
 * through unchecked. This is also what makes {@code /app/home} land such a user on My Ideas
 * instead of My Work, with zero change needed inside {@link LandingMvcController} itself.
 */
public class WorkflowParticipationInterceptor implements HandlerInterceptor {

    private final AuthorizationService authorizationService;

    public WorkflowParticipationInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws java.io.IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof KcpcUserPrincipal principal)) {
            return true;
        }
        if (!authorizationService.isNonProductionEmployee(principal.user())) {
            return true;
        }
        String allowedPrefix = request.getContextPath() + "/app/ideas";
        if (request.getRequestURI().startsWith(allowedPrefix)) {
            return true;
        }
        response.sendRedirect(request.getContextPath() + "/app/ideas");
        return false;
    }
}
