package com.kcpc.mkt.web.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Primes the CSRF cookie for pure JSON/REST clients. The CookieCsrfTokenRepository only writes
 * the cookie when something actually reads the deferred token (a JSP reading {@code
 * ${_csrf.token}} does this implicitly); a fetch/curl client that never renders a JSP needs an
 * explicit call for that first read. GET is CSRF-exempt by design, and the token is not a secret
 * to the legitimate client (KCPC_CSRF is deliberately JS-readable) - the double-submit
 * protection comes from same-origin policy blocking a forged cross-site page from reading it.
 */
@RestController
@RequestMapping("/api/v1")
public class CsrfRestController {

    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
