package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.exceptions.UsernameAlreadyTakenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;


/**
 * Translates exceptions into RFC-7807 {@link ProblemDetail} responses so the SPA always
 * receives a consistent, body-typed error — never a stack trace or servlet error page.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Bean-validation failures on request bodies. Each rejected field is reported under
     * the {@code errors} extension so the form can highlight inputs individually.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        final ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setProperty("errors", fieldErrorsOf(ex));

        return problem;
    }

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ProblemDetail handleDuplicateUsername(UsernameAlreadyTakenException ex) {
        log.warn("Registration conflict: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        log.info("Authentication failed: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You are not allowed to perform this action.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unhandled error handling request", ex);

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    private Map<String, String> fieldErrorsOf(MethodArgumentNotValidException ex) {
        final Map<String, String> errors = new HashMap<>();

        for (final FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        return errors;
    }
}
