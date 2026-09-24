package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.ErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Explicit bean because Spring's default access-denied handler bypasses {@code @RestControllerAdvice}
 * entirely, returning an empty 403 body instead of the app's {@code ErrorResponseDTO}.
 */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        ErrorResponseWriter.write(response, objectMapper, HttpStatus.FORBIDDEN, "Access is denied.");
    }
}
