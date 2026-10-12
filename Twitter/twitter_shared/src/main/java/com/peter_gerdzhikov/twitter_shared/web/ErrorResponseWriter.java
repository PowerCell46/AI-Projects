package com.peter_gerdzhikov.twitter_shared.web;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the same {@link ErrorResponseDTO} body GlobalExceptionHandler produces - used by the
 * AuthenticationEntryPoint/AccessDeniedHandler beans and the body-size filter, which sit in front of
 * the handler chain and never reach the {@code @RestControllerAdvice}.
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
        write(response, objectMapper, status, message, Clock.systemUTC());
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String message,
            Clock clock
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponseDTO body = new ErrorResponseDTO(status.value(), List.of(message), Instant.now(clock).toEpochMilli());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
