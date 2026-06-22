package com.wealthbuilder.backend.exceptions;

import com.wealthbuilder.backend.exceptions.asset.AssetInUseException;
import com.wealthbuilder.backend.exceptions.asset.AssetNameAlreadyTakenException;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.exceptions.auth.UsernameAlreadyTakenException;
import com.wealthbuilder.backend.exceptions.holding.HoldingNotFoundException;
import com.wealthbuilder.backend.exceptions.holding.InvalidDateRangeException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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

    /**
     * A path variable or query parameter could not be converted to the target type, e.g. a
     * non-numeric id or an unparseable date. This is bad input, not a server fault.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.info("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has an invalid value.");
    }

    /** Constraint failures on {@code @Validated} request params or path variables. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        log.info("Constraint violation: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
    }

    /** A holdings filter supplied a {@code from} date after its {@code to} date. */
    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException ex) {
        log.info("Invalid date range: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** The request body was missing or not valid JSON, so it could not be deserialized. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.info("Unreadable request body: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed or missing request body.");
    }

    @ExceptionHandler(UsernameAlreadyTakenException.class)
    public ProblemDetail handleDuplicateUsername(UsernameAlreadyTakenException ex) {
        log.warn("Registration conflict: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AssetNameAlreadyTakenException.class)
    public ProblemDetail handleDuplicateAssetName(AssetNameAlreadyTakenException ex) {
        log.warn("Asset conflict: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AssetInUseException.class)
    public ProblemDetail handleAssetInUse(AssetInUseException ex) {
        log.warn("Asset deletion blocked: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * A DB-level constraint (e.g. a uniqueness index) rejected the write — the last-line
     * backstop behind the service-level checks. The raw message can expose schema details,
     * so the client gets a generic conflict instead.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation", ex);

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with the current state of the resource.");
    }

    /**
     * Two writers raced on the same record and the {@code @Version} check rejected the loser.
     * The client should reload to get the current state before retrying.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict", ex);

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "This resource was modified by someone else. Reload and try again.");
    }

    @ExceptionHandler(AssetNotFoundException.class)
    public ProblemDetail handleAssetNotFound(AssetNotFoundException ex) {
        log.info("Asset not found: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(HoldingNotFoundException.class)
    public ProblemDetail handleHoldingNotFound(HoldingNotFoundException ex) {
        log.info("Holding not found: {}", ex.getMessage());

        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
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
