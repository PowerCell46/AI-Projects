package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.EmptyUploadException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.FileNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.UnsupportedImageTypeException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.DuplicateEmailException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.DuplicateUsernameException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.EmailNotConfirmedException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidConfirmationTokenException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidCredentialsException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidCursorException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;

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
    void should_return_409_for_a_duplicate_email() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleConflict(new DuplicateEmailException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessages()).containsExactly(DuplicateEmailException.MESSAGE);
    }

    @Test
    void should_return_409_for_a_duplicate_username() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleConflict(new DuplicateUsernameException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessages()).containsExactly(DuplicateUsernameException.MESSAGE);
    }

    @Test
    void should_return_401_for_invalid_credentials() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleInvalidCredentials(new InvalidCredentialsException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessages()).containsExactly(InvalidCredentialsException.MESSAGE);
    }

    @Test
    void should_return_403_for_an_unconfirmed_email() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleEmailNotConfirmed(new EmailNotConfirmedException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessages()).containsExactly(EmailNotConfirmedException.MESSAGE);
    }

    @Test
    void should_return_400_for_an_invalid_confirmation_token() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleInvalidConfirmationToken(new InvalidConfirmationTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(InvalidConfirmationTokenException.MESSAGE);
    }

    @Test
    void should_return_400_for_an_invalid_cursor() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleInvalidPagination(new InvalidCursorException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(InvalidCursorException.MESSAGE);
    }

    @Test
    void should_return_400_for_an_invalid_page_size() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleInvalidPagination(new InvalidPageSizeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(InvalidPageSizeException.MESSAGE);
    }

    @Test
    void should_return_413_for_a_body_that_outgrew_the_cap_while_being_read() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleRequestBodyTooLarge(new RequestBodyTooLargeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getMessages()).containsExactly(RequestBodyTooLargeException.MESSAGE);
    }

    @Test
    void should_return_400_for_an_empty_upload() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleEmptyUpload(new EmptyUploadException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(EmptyUploadException.MESSAGE);
    }

    @Test
    void should_return_404_for_an_unknown_file() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleFileNotFound(new FileNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessages()).containsExactly(FileNotFoundException.MESSAGE);
    }

    @Test
    void should_return_415_for_an_unsupported_image_type() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleUnsupportedImageType(new UnsupportedImageTypeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody().getMessages()).containsExactly(UnsupportedImageTypeException.MESSAGE);
    }

    @Test
    void should_return_502_without_leaking_the_cause_when_storage_is_unavailable() {
        StorageUnavailableException e = new StorageUnavailableException(new IOException("minio:9000 refused"));

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleStorageUnavailable(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getMessages()).containsExactly(StorageUnavailableException.MESSAGE);
    }

    @Test
    void should_return_504_without_leaking_the_cause_when_the_upstream_read_times_out() {
        ResourceAccessException e = new ResourceAccessException("I/O error", new HttpTimeoutException("tweets:8081 request timed out"));

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUpstreamFailure(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().getMessages()).containsExactly("Upstream service timed out.");
    }

    @Test
    void should_return_413_when_the_body_size_cap_trips_while_the_proxy_streams_the_request() {
        ResourceAccessException e = new ResourceAccessException("I/O error", new IOException(new RequestBodyTooLargeException()));

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUpstreamFailure(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getMessages()).containsExactly(RequestBodyTooLargeException.MESSAGE);
    }

    @Test
    void should_return_502_when_the_upstream_connect_times_out() {
        ResourceAccessException e = new ResourceAccessException("I/O error", new HttpConnectTimeoutException("tweets:8081 connect timed out"));

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUpstreamFailure(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getMessages()).containsExactly("Upstream service unavailable.");
    }

    @Test
    void should_return_502_without_leaking_the_cause_when_the_upstream_refuses_the_connection() {
        ResourceAccessException e = new ResourceAccessException("I/O error", new ConnectException("tweets:8081 refused"));

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUpstreamFailure(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getMessages()).containsExactly("Upstream service unavailable.");
    }

    @Test
    void should_return_413_for_a_multipart_upload_over_the_spring_limit() {
        ResponseEntity<Object> response = exceptionHandler.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(1024),
                new HttpHeaders(),
                HttpStatus.CONTENT_TOO_LARGE,
                new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("The uploaded file is too large.");
    }

    @Test
    void should_return_500_with_a_fixed_message_for_an_unexpected_exception() {
        Exception e = new IllegalStateException("org.example.Internal: connection pool exhausted");

        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUnexpectedError(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessages()).containsExactly("An unexpected error occurred.");
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
