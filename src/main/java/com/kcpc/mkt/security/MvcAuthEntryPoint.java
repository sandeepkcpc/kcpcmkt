package com.kcpc.mkt.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Browser (/app/**) unauthenticated/forbidden handling: redirect to the login screen. */
@Component
public class MvcAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/login?reason=auth");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        response.sendRedirect(request.getContextPath() + "/login?reason=denied");
    }
}
