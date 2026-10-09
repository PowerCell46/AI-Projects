package com.peter_gerdzhikov.twitter_timeline_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCallerIdentityException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.paging.InvalidCursorException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.paging.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.views.InvalidTweetIdsException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void should_return_400_for_a_missing_or_invalid_caller_identity() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleInvalidCallerIdentity(new InvalidCallerIdentityException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(InvalidCallerIdentityException.MESSAGE);
    }

    @Test
    void should_return_502_without_leaking_the_cause_when_a_downstream_is_unavailable() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUpstreamUnavailable(
                new UpstreamUnavailableException(new IllegalStateException("secret downstream detail")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().getMessages()).containsExactly("Upstream service unavailable.");
    }

    @Test
    void should_return_504_without_leaking_the_cause_when_a_downstream_times_out() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleUpstreamTimeout(
                new UpstreamTimeoutException(new IllegalStateException("secret downstream detail")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody().getMessages()).containsExactly("Upstream service timed out.");
    }

    @Test
    void should_return_404_when_the_author_does_not_exist() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleAuthorNotFound(new AuthorNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessages()).containsExactly("Author not found.");
    }

    @Test
    void should_return_404_when_the_tweet_does_not_exist() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleTweetNotFound(new TweetNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessages()).containsExactly("Tweet not found.");
    }

    @Test
    void should_return_400_for_an_invalid_cursor() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleInvalidPagination(new InvalidCursorException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(InvalidCursorException.MESSAGE);
    }

    @Test
    void should_return_400_for_an_invalid_page_size() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleInvalidPagination(new InvalidPageSizeException(1, 100));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly("Page size must be between 1 and 100.");
    }

    @Test
    void should_return_400_for_invalid_tweet_ids() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleInvalidTweetIds(new InvalidTweetIdsException(50));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly("Between 1 and 50 tweet ids are required, none of them null.");
    }

    @Test
    void should_return_413_for_a_body_that_outgrew_the_cap_while_being_read() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleRequestBodyTooLarge(new RequestBodyTooLargeException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(response.getBody().getMessages()).containsExactly(RequestBodyTooLargeException.MESSAGE);
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
    @ExtendWith(OutputCaptureExtension.class)
    void should_not_log_the_path_when_the_resource_is_unknown(CapturedOutput output) {
        NoResourceFoundException e = new NoResourceFoundException(HttpMethod.GET, "/x\nWARN forged line", "x");

        exceptionHandler.handleNoResourceFoundException(
                e, new HttpHeaders(), HttpStatus.NOT_FOUND, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(output)
                .contains("No resource found for a GET request.")
                .doesNotContain("forged");
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
