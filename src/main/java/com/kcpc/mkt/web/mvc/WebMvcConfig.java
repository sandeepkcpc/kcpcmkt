package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.identity.service.AuthorizationService;
import com.kcpc.mkt.workflow.service.WorkspaceAccessService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
}
