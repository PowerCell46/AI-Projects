package com.peter_gerdzhikov.signal_flow_api_gateway.utilities;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.ErrorResponseDTO;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the same {@link ErrorResponseDTO} body GlobalExceptionHandler produces - used by the
 * AuthenticationEntryPoint/AccessDeniedHandler beans, which sit in front of the filter chain and never
 * reach the {@code @RestControllerAdvice}.
 */
public final class ErrorResponseWriter {

    private ErrorResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDTO body = new ErrorResponseDTO(status.value(), List.of(message), Instant.now().toEpochMilli());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
