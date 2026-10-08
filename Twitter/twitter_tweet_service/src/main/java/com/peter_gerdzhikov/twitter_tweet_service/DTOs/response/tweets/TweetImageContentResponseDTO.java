package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets;

import java.io.InputStream;

import lombok.Builder;
import lombok.Value;

/**
 * The caller owns {@code content} and must close it, which streaming it as a response body does.
 */
@Value
@Builder
public class TweetImageContentResponseDTO {

    private final long sizeBytes;

    private final String contentType;

    private final InputStream content;
}
