package com.peter_gerdzhikov.twitter_tweet_service.documents;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embedded in its tweet, never a collection of its own. The {@code objectKey} is a random UUID string,
 * never the client's filename.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TweetImage {

    private UUID id;

    private long sizeBytes;

    private String objectKey;

    private String contentType;
}
