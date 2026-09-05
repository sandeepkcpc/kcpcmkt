package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.workflow.service.WorkspaceAccessService;
import jakarta.servlet.SessionTrackingMode;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.resource.ResourceUrlEncodingFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/** Registers {@link WorkflowParticipationInterceptor} for every {@code /app/**} page route. */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthorizationService authorizationService;
    private final WorkspaceAccessService workspaceAccessService;

    public WebMvcConfig(AuthorizationService authorizationService, WorkspaceAccessService workspaceAccessService) {
        this.authorizationService = authorizationService;
        this.workspaceAccessService = workspaceAccessService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registration order matters: a forced password change must win over the (separate)
        // workflow-participation restriction, so it runs first.
        registry.addInterceptor(new ForcePasswordChangeInterceptor())
                .addPathPatterns("/app/**");
        registry.addInterceptor(new WorkflowParticipationInterceptor(authorizationService, workspaceAccessService))
                .addPathPatterns("/app/**");
    }

    /**
     * Makes JSTL's {@code <c:url value="/css/app.css"/>} render the content-hashed asset URL
     * (e.g. {@code /css/app-9f1c...ae.css}) instead of the plain path - the JSP half of the
     * cache-busting configured under {@code spring.web.resources.chain} in application.yml.
     *
     * <p>{@code <c:url>} routes the path through {@code HttpServletResponse.encodeURL()}; this
     * filter wraps the response so that call consults Spring's {@code ResourceUrlProvider} and
     * substitutes the versioned path. Spring Boot 3.3 does NOT auto-register this filter (only
     * the resource-handler half of the chain is auto-configured), so without this bean every
     * {@code <c:url>} would silently emit the unversioned path and the busting would be a no-op.
     *
     * <p>A path the resolver cannot version (anything outside the configured
     * {@code strategy.content.paths}, e.g. {@code /icons/**}) is passed through unchanged, so
     * this is additive - it never breaks a URL it does not recognise.
     */
    @Bean
    public ResourceUrlEncodingFilter resourceUrlEncodingFilter() {
        return new ResourceUrlEncodingFilter();
    }

    /**
     * Restricts session tracking to the cookie only (Tomcat's default otherwise also allows the
     * URL-rewrite fallback). This app never uses {@code HttpSession} for auth - real login state
     * is the JWT cookie, and SecurityConfig disables Spring Security's own session management
     * entirely - so URL-based tracking serves no purpose here, but its default-on fallback would
     * still fire on a brand-new session's first response (before the browser has returned the
     * session cookie), appending {@code ;jsessionid=...} to any {@code encodeURL()}'d link -
     * including every {@code <c:url>} this cache-busting change now uses on css/js/image links
     * app-wide. Left on, that would fragment the browser's cache key per session and leak session
     * ids into the URL (and from there into browser history/cache/proxy logs) - a real, if
     * intermittent, defect this fix removes at the one correct layer instead of working around it
     * at each of the ~140 individual {@code <c:url>} call sites.
     */
    @Bean
    public ServletContextInitializer sessionTrackingModeInitializer() {
        return servletContext -> servletContext.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
    }
}
