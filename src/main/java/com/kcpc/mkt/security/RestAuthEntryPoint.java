package com.kcpc.mkt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcpc.mkt.common.error.ApiErrorResponse;
import com.kcpc.mkt.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        Object attr = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        ErrorCode code = attr instanceof ErrorCode ec ? ec : ErrorCode.AUTH_TOKEN_MISSING;
        write(response, HttpServletResponse.SC_UNAUTHORIZED, code, "Authentication required", request);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        org.springframework.security.access.AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.PERM_ACCESS_CLASS_DENIED, "Access denied", request);
    }

    private void write(HttpServletResponse response, int status, ErrorCode code, String message, HttpServletRequest request)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiErrorResponse body = ApiErrorResponse.of(code, message, request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
