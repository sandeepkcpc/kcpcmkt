package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.security.AuthCookieService;
import com.kcpc.mkt.security.AuthenticationApplicationService;
import com.kcpc.mkt.security.KcpcUserPrincipal;
import com.kcpc.mkt.security.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Same AuthenticationApplicationService/PasswordResetService a future REST caller would use
 * (architecture rule: shared service layer, MVC never re-implements the logic). */
@Controller
public class AuthMvcController {

    private final AuthenticationApplicationService authService;
    private final AuthCookieService cookieService;
    private final PasswordResetService passwordResetService;

    public AuthMvcController(AuthenticationApplicationService authService, AuthCookieService cookieService,
                              PasswordResetService passwordResetService) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.passwordResetService = passwordResetService;
    }

    /** Root URL - never publicly reachable: SecurityConfig's {@code anyRequest().authenticated()}
     *  already covers "/" exactly like every other unmapped path (it is not in the appFilterChain's
     *  permitAll list), so an unauthenticated request is redirected to /login by MvcAuthEntryPoint
     *  before this method is ever invoked - this handler only ever runs for an already-authenticated
     *  user, and simply hands off to the exact same role-appropriate landing target doLogin() itself
     *  redirects to right after a fresh sign-in, so "/" and a completed login always land the user
     *  in the same place via the same, single dispatch path (LandingMvcController#home). */
    @GetMapping("/")
    public String root() {
        return "redirect:/app/home";
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String reason, Model model) {
        if ("denied".equals(reason)) {
            model.addAttribute("errorMessage", "You do not have access to that page.");
        } else if ("auth".equals(reason)) {
            model.addAttribute("errorMessage", "Please sign in to continue.");
        }
        return "login";
    }

    @PostMapping("/login")
    public String doLogin(@RequestParam String email, @RequestParam String password, Model model,
                           HttpServletRequest request, HttpServletResponse response) {
        try {
            var result = authService.login(email, password, request.getRemoteAddr(), request.getHeader("User-Agent"));
            cookieService.setAuthCookie(response, result.jwt(), result.expiresAt());
            return "redirect:/app/home";
        } catch (DomainException e) {
            model.addAttribute("errorMessage", "Invalid email or password.");
            return "login";
        }
    }

    @PostMapping("/logout")
    public String doLogout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookieService.readAuthCookie(request));
        cookieService.clearAuthCookie(response);
        return "redirect:/login";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    /** Always shows the same success message regardless of whether the email matched an account -
     * BR: never reveal whether an email exists. PasswordResetService.requestReset itself never
     * throws for an unknown/inactive email, so there is no error branch to handle here either. */
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, HttpServletRequest request, Model model) {
        passwordResetService.requestReset(email, request.getRemoteAddr());
        model.addAttribute("successMessage",
                "If an account exists for that email address, a password reset link has been sent.");
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String token, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, Model model) {
        model.addAttribute("token", token);
        if (newPassword == null || newPassword.isBlank()) {
            model.addAttribute("errorMessage", "New password is required.");
            return "reset-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "reset-password";
        }
        try {
            passwordResetService.confirmReset(token, newPassword);
        } catch (DomainException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "reset-password";
        }
        model.addAttribute("resetComplete", true);
        model.addAttribute("successMessage", "Your password has been reset. You can now sign in.");
        return "reset-password";
    }

    /** Admin/CEO Password Reset's "Force Change Password" screen - reachable only while
     * authenticated; {@link ForcePasswordChangeInterceptor} redirects every other {@code /app/**}
     * page here whenever {@code User.isPasswordChangeRequired()}, so simply landing on this GET is
     * itself the enforcement, not an extra check repeated here. */
    @GetMapping("/app/change-password")
    public String changePasswordPage() {
        return "change-password";
    }

    @PostMapping("/app/change-password")
    public String changePassword(@RequestParam String newPassword, @RequestParam String confirmPassword,
                                  @AuthenticationPrincipal KcpcUserPrincipal principal, Model model) {
        if (newPassword == null || newPassword.isBlank()) {
            model.addAttribute("errorMessage", "New password is required.");
            return "change-password";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "Passwords do not match.");
            return "change-password";
        }
        passwordResetService.completeForcedPasswordChange(principal.user(), newPassword);
        return "redirect:/app/home";
    }
}
