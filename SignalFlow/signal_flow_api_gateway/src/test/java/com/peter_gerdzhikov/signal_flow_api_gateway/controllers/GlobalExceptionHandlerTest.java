package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.DuplicateSubscriptionException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.SubscriptionLimitExceededException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.SubscriptionNotFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void should_return_409_for_a_unique_constraint_violation() {
        DataIntegrityViolationException e = new DataIntegrityViolationException("duplicate key value violates unique constraint");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleDataIntegrityViolation(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessages()).containsExactly("The request conflicts with existing data.");
    }

    @Test
    void should_return_409_for_a_duplicate_subscription() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleConflict(new DuplicateSubscriptionException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessages()).containsExactly(DuplicateSubscriptionException.MESSAGE);
    }

    @Test
    void should_return_409_when_the_subscription_limit_is_exceeded() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleConflict(new SubscriptionLimitExceededException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessages()).containsExactly(SubscriptionLimitExceededException.MESSAGE);
    }

    @Test
    void should_return_404_for_a_missing_subscription() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleSubscriptionNotFound(new SubscriptionNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessages()).containsExactly(SubscriptionNotFoundException.MESSAGE);
    }

    @Test
    void should_return_413_for_a_body_that_outgrew_the_cap_while_being_read() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleRequestBodyTooLarge(new RequestBodyTooLargeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getMessages()).containsExactly(RequestBodyTooLargeException.MESSAGE);
    }

    @Test
    void should_return_400_without_leaking_type_names_for_a_type_mismatch() {
        TypeMismatchException e = new TypeMismatchException("not-a-uuid", UUID.class);

        ResponseEntity<Object> response = exceptionHandler.handleTypeMismatch(
                e, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("Malformed request parameter.");
    }

    @Test
    void should_return_405_while_keeping_the_allow_header_for_an_unsupported_method() {
        HttpRequestMethodNotSupportedException e = new HttpRequestMethodNotSupportedException("PUT", List.of("POST"));
        HttpHeaders headers = new HttpHeaders();
        headers.setAllow(Set.of(HttpMethod.POST));

        ResponseEntity<Object> response = exceptionHandler.handleHttpRequestMethodNotSupported(
                e, headers, HttpStatus.METHOD_NOT_ALLOWED, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactly(HttpMethod.POST);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("This method is not supported for this endpoint.");
    }

    @Test
    void should_return_415_for_an_unsupported_content_type() {
        HttpMediaTypeNotSupportedException e =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<Object> response = exceptionHandler.handleHttpMediaTypeNotSupported(
                e, new HttpHeaders(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("This content type is not supported for this endpoint.");
    }

    @Test
    void should_return_404_without_echoing_the_path_for_an_unknown_resource() {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "/admin.php", "admin.php");

        ResponseEntity<Object> response = exceptionHandler.handleNoResourceFoundException(
                e, new HttpHeaders(), HttpStatus.NOT_FOUND, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("No resource found for this path.");
    }

    @Test
    void should_return_a_fixed_message_for_a_spring_mvc_exception_that_is_not_overridden() {
        Exception e = new IllegalStateException("Resolved [org.example.Internal: connection pool exhausted]");

        ResponseEntity<Object> response = exceptionHandler.handleExceptionInternal(
                e, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("The request could not be processed.");
    }
}
