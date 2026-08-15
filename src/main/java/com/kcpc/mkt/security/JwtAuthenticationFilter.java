package com.kcpc.mkt.security;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.identity.domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-request revalidation (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md "Security rules"): every
 * authenticated request re-checks signature, expiry, registry entry, revocation and account
 * state - a purely stateless JWT would silently break logout/deactivation.
 * NOT a @Component: constructed directly by SecurityConfig so Spring Boot's automatic
 * Filter-bean servlet registration does not additionally register it outside the security chain
 * (which would run it twice per request).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "kcpc.auth.error";

    private final AuthCookieService cookieService;
    private final TokenRegistryService tokenRegistryService;

    public JwtAuthenticationFilter(AuthCookieService cookieService, TokenRegistryService tokenRegistryService) {
        this.cookieService = cookieService;
        this.tokenRegistryService = tokenRegistryService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String jwt = cookieService.readAuthCookie(request);
        if (jwt != null && !jwt.isBlank()) {
            try {
                User user = tokenRegistryService.validateAndResolveUser(jwt);
                var principal = new KcpcUserPrincipal(user);
                var authentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (DomainException e) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * {@code OncePerRequestFilter} skips itself on the container's internal forward to
     * {@code /error} by default (e.g. an unmapped URL's 404, or an uncaught exception's 500).
     * Left at the default, that forward carries no authentication (nothing populates
     * {@code SecurityContextHolder} for that dispatch), so {@code anyRequest().authenticated()}
     * fails and the user is bounced to {@code /login?reason=auth} - indistinguishable from being
     * logged out, and masking the real 404/500 as an authentication failure. Re-running this
     * filter on the error dispatch re-validates the same request cookies (still present on a
     * forward) so the real status code reaches the client instead.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
