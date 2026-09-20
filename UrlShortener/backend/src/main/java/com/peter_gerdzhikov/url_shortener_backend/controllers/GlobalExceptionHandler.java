package com.peter_gerdzhikov.url_shortener_backend.controllers;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.peter_gerdzhikov.url_shortener_backend.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.url_shortener_backend.exceptions.ShortUrlNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ShortUrlNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleShortUrlNotFound(ShortUrlNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(HttpStatus.NOT_FOUND, List.of(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpectedError(Exception e) {
        log.error("Unhandled exception.", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody(HttpStatus.INTERNAL_SERVER_ERROR, List.of("An unexpected error occurred")));
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
                .body(errorBody(status, List.of("Malformed request body")));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("Unhandled Spring MVC exception: {}.", e.getMessage());
        List<String> messages = List.of(Objects.requireNonNullElse(e.getMessage(), "Request could not be processed"));
        return ResponseEntity.status(status)
                .headers(headers)
                .body(errorBody(status, messages));
    }

    private String describe(FieldError fieldError) {
        return "%s %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ErrorResponseDTO errorBody(HttpStatusCode status, List<String> messages) {
        return new ErrorResponseDTO(status.value(), messages, Instant.now().toEpochMilli());
    }
}
