package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.CategoryInUseException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.CategoryNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.DuplicateCategoryNameException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.DuplicateInterestTopicNameException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.InterestTopicNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.RequestBodyTooLargeException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNPROCESSABLE_REQUEST_MESSAGE = "The request could not be processed.";

    @ExceptionHandler({
            DuplicateCategoryNameException.class,
            DuplicateInterestTopicNameException.class,
            CategoryInUseException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(HttpStatus.CONFLICT, List.of(e.getMessage())));
    }

    @ExceptionHandler({
            CategoryNotFoundException.class,
            InterestTopicNotFoundException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleNotFound(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, List.of(e.getMessage())));
    }

    /**
     * A genuine fallback only - each service translates its own constraint into a domain exception, so
     * the message stays neutral rather than naming any one constraint.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Unique constraint violated on save: {}.", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(HttpStatus.CONFLICT, List.of("The request conflicts with existing data.")));
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRequestBodyTooLarge(RequestBodyTooLargeException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(errorBody(HttpStatus.CONTENT_TOO_LARGE, List.of(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpectedError(Exception e) {
        log.error("Unhandled exception.", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, List.of("An unexpected error occurred.")));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> messages = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::describe)
                .toList();
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, messages));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, List.of("Malformed request body.")));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The default body names the target type and the failing value - both leak internals.
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, List.of("Malformed request parameter.")));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The inherited headers carry the Allow header RFC 9110 requires on a 405.
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, List.of("This method is not supported for this endpoint.")));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, List.of("This content type is not supported for this endpoint.")));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("No resource found for {} '{}'.", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, List.of("No resource found for this path.")));
    }

    /**
     * The catch-all for every Spring MVC exception not overridden above. The message is fixed rather
     * than taken from the exception, so an un-anticipated framework message can never reach a client.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Unhandled Spring MVC exception: {}.", e.getMessage());
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, List.of(UNPROCESSABLE_REQUEST_MESSAGE)));
    }

    private String describe(FieldError fieldError) {
        return "%s %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ErrorResponseDTO errorBody(HttpStatusCode status, List<String> messages) {
        return new ErrorResponseDTO(status.value(), messages, Instant.now().toEpochMilli());
    }
}
