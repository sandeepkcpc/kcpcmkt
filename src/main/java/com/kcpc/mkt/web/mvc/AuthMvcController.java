package com.kcpc.mkt.web.mvc;

import com.kcpc.mkt.common.error.DomainException;
import com.kcpc.mkt.security.AuthCookieService;
import com.kcpc.mkt.security.AuthenticationApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Same AuthenticationApplicationService as the REST controller (architecture rule: shared service layer). */
@Controller
public class AuthMvcController {

    private final AuthenticationApplicationService authService;
    private final AuthCookieService cookieService;

    public AuthMvcController(AuthenticationApplicationService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
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
}
