package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.ErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Explicit bean because Spring's default entry point bypasses {@code @RestControllerAdvice} entirely,
 * returning an empty 401 body instead of the app's {@code ErrorResponseDTO} (PLAN.md step 4).
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ErrorResponseWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication is required.");
    }
}
