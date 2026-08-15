package com.kcpc.mkt.web.rest;

import com.kcpc.mkt.identity.dto.LoginRequest;
import com.kcpc.mkt.identity.dto.UserProfileResponse;
import com.kcpc.mkt.security.AuthCookieService;
import com.kcpc.mkt.security.AuthenticationApplicationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-OP-001-class operations: authentication. Delegates entirely to the shared application service. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthRestController {

    private final AuthenticationApplicationService authService;
    private final AuthCookieService cookieService;

    public AuthRestController(AuthenticationApplicationService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<UserProfileResponse> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        var result = authService.login(request.email(), request.password(), httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));
        cookieService.setAuthCookie(httpResponse, result.jwt(), result.expiresAt());
        return ResponseEntity.ok(UserProfileResponse.from(result.user()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String jwt = cookieService.readAuthCookie(httpRequest);
        authService.logout(jwt);
        cookieService.clearAuthCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal KcpcUserPrincipal principal) {
        return ResponseEntity.ok(UserProfileResponse.from(principal.user()));
    }
}
