package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.InvalidCallerIdentityException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.RequestBodyTooLargeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.EmptyUploadException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.UnsupportedImageTypeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.EmptyTweetException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.NotTweetAuthorException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TooManyImagesException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetImageNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetContentTooLongException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException;

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
    void should_return_400_for_a_tweet_without_text_or_image() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleEmptyTweet(new EmptyTweetException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly("A tweet needs text or an image.");
    }

    @Test
    void should_return_400_for_tweet_content_that_is_too_long() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleTweetContentTooLong(new TweetContentTooLongException(280));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly("A tweet can be at most 280 characters.");
    }

    @Test
    void should_return_400_for_a_tweet_with_too_many_images() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleTooManyImages(new TooManyImagesException(4));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly("A tweet can have at most 4 images.");
    }

    @Test
    void should_return_403_when_the_caller_is_not_the_tweet_author() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleNotTweetAuthor(new NotTweetAuthorException(NotTweetAuthorException.EDIT_MESSAGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessages()).containsExactly("You can only edit your own tweets.");
    }

    @Test
    void should_return_403_with_the_delete_wording_when_a_stranger_deletes() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleNotTweetAuthor(
                new NotTweetAuthorException(NotTweetAuthorException.DELETE_MESSAGE));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getMessages()).containsExactly("You can only delete your own tweets.");
    }

    @Test
    void should_return_404_for_a_tweet_that_does_not_exist() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleTweetNotFound(new TweetNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessages()).containsExactly("Tweet not found.");
    }

    @Test
    void should_return_404_for_an_image_that_does_not_exist() {
        ResponseEntity<ErrorResponseDTO> response =
                exceptionHandler.handleTweetImageNotFound(new TweetImageNotFoundException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessages()).containsExactly("Image not found.");
    }

    @Test
    void should_return_400_for_an_empty_upload() {
        ResponseEntity<ErrorResponseDTO> response = exceptionHandler.handleEmptyUpload(new EmptyUploadException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessages()).containsExactly(EmptyUploadException.MESSAGE);
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
    void should_return_a_fixed_message_for_a_spring_mvc_exception_that_is_not_overridden() {
        Exception e = new IllegalStateException("Resolved [org.example.Internal: connection pool exhausted]");

        ResponseEntity<Object> response = exceptionHandler.handleExceptionInternal(
                e, null, new HttpHeaders(), HttpStatus.BAD_REQUEST, new ServletWebRequest(new MockHttpServletRequest()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ErrorResponseDTO) response.getBody()).getMessages())
                .containsExactly("The request could not be processed.");
    }
}
