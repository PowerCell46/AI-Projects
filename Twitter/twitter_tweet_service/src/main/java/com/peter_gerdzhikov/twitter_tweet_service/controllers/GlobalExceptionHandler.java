package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
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
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetIdsOutOfRangeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetImageNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetContentTooLongException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetLimitOutOfRangeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNPROCESSABLE_REQUEST_MESSAGE = "The request could not be processed.";

    @ExceptionHandler(InvalidCallerIdentityException.class)
    public ResponseEntity<ErrorResponseDTO> handleInvalidCallerIdentity(InvalidCallerIdentityException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(EmptyTweetException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmptyTweet(EmptyTweetException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TweetContentTooLongException.class)
    public ResponseEntity<ErrorResponseDTO> handleTweetContentTooLong(TweetContentTooLongException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TooManyImagesException.class)
    public ResponseEntity<ErrorResponseDTO> handleTooManyImages(TooManyImagesException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TweetIdsOutOfRangeException.class)
    public ResponseEntity<ErrorResponseDTO> handleTweetIdsOutOfRange(TweetIdsOutOfRangeException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(TweetLimitOutOfRangeException.class)
    public ResponseEntity<ErrorResponseDTO> handleTweetLimitOutOfRange(TweetLimitOutOfRangeException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(NotTweetAuthorException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotTweetAuthor(NotTweetAuthorException e) {
        return errorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(TweetNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTweetNotFound(TweetNotFoundException e) {
        return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(TweetImageNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleTweetImageNotFound(TweetImageNotFoundException e) {
        return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EmptyUploadException.class)
    public ResponseEntity<ErrorResponseDTO> handleEmptyUpload(EmptyUploadException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UnsupportedImageTypeException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnsupportedImageType(UnsupportedImageTypeException e) {
        return errorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<ErrorResponseDTO> handleStorageUnavailable(StorageUnavailableException e) {
        return errorResponse(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    @ExceptionHandler(RequestBodyTooLargeException.class)
    public ResponseEntity<ErrorResponseDTO> handleRequestBodyTooLarge(RequestBodyTooLargeException e) {
        return errorResponse(HttpStatus.CONTENT_TOO_LARGE, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpectedError(Exception e) {
        log.error("Unhandled exception.", e);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<String> messages = e
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::describe)
                .toList();
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, messages));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, List.of("Malformed request body.")));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The default body names the target type and the failing value - both leak internals.
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, List.of("Malformed request parameter.")));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // The inherited headers carry the Allow header RFC 9110 requires on a 405.
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, List.of("This method is not supported for this endpoint.")));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, List.of("The uploaded file is too large.")));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, List.of("This content type is not supported for this endpoint.")));
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        log.warn("No resource found for {} '{}'.", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity
                .status(status)
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
        return ResponseEntity
                .status(status)
                .headers(headers)
                .body(errorBody(status, List.of(UNPROCESSABLE_REQUEST_MESSAGE)));
    }

    private ResponseEntity<ErrorResponseDTO> errorResponse(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(errorBody(status, List.of(message)));
    }

    private String describe(FieldError fieldError) {
        return "%s %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ErrorResponseDTO errorBody(HttpStatusCode status, List<String> messages) {
        return new ErrorResponseDTO(status.value(), messages, Instant.now().toEpochMilli());
    }
}
