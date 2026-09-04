package com.kcpc.mkt.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

/**
 * Two filter chains sharing the same JWT-cookie identity: /api/v1/** returns JSON 401/403;
 * everything else (/app/**, /login, ...) redirects to the login screen. CSRF is enforced on
 * every unsafe cookie-authenticated request in both chains (section 8 of the build prompt);
 * GET/HEAD remain non-mutating and are Spring Security's default CSRF-exempt safe methods.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthEntryPoint restAuthEntryPoint;
    private final MvcAuthEntryPoint mvcAuthEntryPoint;
    private final CsrfProperties csrfProperties;

    public SecurityConfig(AuthCookieService cookieService, TokenRegistryService tokenRegistryService,
                           RestAuthEntryPoint restAuthEntryPoint, MvcAuthEntryPoint mvcAuthEntryPoint,
                           CsrfProperties csrfProperties) {
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(cookieService, tokenRegistryService);
        this.restAuthEntryPoint = restAuthEntryPoint;
        this.mvcAuthEntryPoint = mvcAuthEntryPoint;
        this.csrfProperties = csrfProperties;
    }

    private void configureCsrf(CsrfConfigurer<HttpSecurity> csrf) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(csrfProperties.getCookieName());
        repository.setHeaderName(csrfProperties.getHeaderName());
        csrf.csrfTokenRepository(repository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler());
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/**")
                .csrf(csrf -> {
                    configureCsrf(csrf);
                    // Login precedes any auth cookie's existence, so there is no ambient
                    // authority for a forged cross-site request to ride on (KCPC-MKT-R3.5-
                    // DEVELOPMENT-HANDOFF.md requires CSRF on cookie-authenticated requests;
                    // login is the one unsafe request that is never cookie-authenticated).
                    csrf.ignoringRequestMatchers("/api/v1/auth/login");
                })
                // Our own per-request JwtAuthenticationFilter means every authenticated request
                // looks like a "new" authentication to SessionManagementFilter. Left enabled
                // (even with SessionCreationPolicy.STATELESS, which only changes whether an
                // HttpSession is opened - the filter itself still runs and still reacts), that
                // triggers CsrfAuthenticationStrategy on every request, which deletes and
                // regenerates the CSRF cookie server-side each time - breaking the intended
                // "prime once, reuse the token" pattern for any client that doesn't re-read the
                // cookie before every single call (our manual curl test scripts happened to
                // dodge this only because each call re-read the cookie jar fresh). CSRF-token
                // rotation on authentication defends against session fixation in session-based
                // auth; it is meaningless here since we have no server session to fixate.
                // Disabling sessionManagement entirely removes SessionManagementFilter (and with
                // it CsrfAuthenticationStrategy's trigger point) from the chain; the
                // "don't touch HttpSession" behaviour that STATELESS would otherwise have
                // configured is preserved explicitly via RequestAttributeSecurityContextRepository.
                .securityContext(sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(AbstractHttpConfigurer::disable)
                // Default HttpSessionRequestCache would call request.getSession(true) on every
                // 401/redirect to stash a SavedRequest, creating an unwanted JSESSIONID - we
                // never use session-based redirect-after-login (MvcAuthEntryPoint just
                // redirects to /login unconditionally), so no request cache is needed.
                .requestCache(rc -> rc.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login", "/api/v1/csrf").permitAll()
                        .requestMatchers("/api/v1/openapi/**", "/api/v1/docs/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthEntryPoint)
                        .accessDeniedHandler(restAuthEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appFilterChain(HttpSecurity http) throws Exception {
        http.csrf(this::configureCsrf)
                // See apiFilterChain for why sessionManagement must be fully disabled (not just
                // set to STATELESS) under per-request JWT authentication + CSRF.
                .securityContext(sc -> sc.securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .sessionManagement(AbstractHttpConfigurer::disable)
                // Default HttpSessionRequestCache would call request.getSession(true) on every
                // 401/redirect to stash a SavedRequest, creating an unwanted JSESSIONID - we
                // never use session-based redirect-after-login (MvcAuthEntryPoint just
                // redirects to /login unconditionally), so no request cache is needed.
                .requestCache(rc -> rc.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/forgot-password", "/reset-password",
                                "/css/**", "/js/**", "/images/**", "/favicon.ico", "/webjars/**", "/swagger-ui/**", "/WEB-INF/**").permitAll()
                        // Deployment healthcheck (Docker Compose / GitHub Actions) - unauthenticated
                        // by necessity, but only ever returns the aggregate UP/DOWN status; see
                        // application.yml's management.endpoint.health.show-details.
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(mvcAuthEntryPoint)
                        .accessDeniedHandler(mvcAuthEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
