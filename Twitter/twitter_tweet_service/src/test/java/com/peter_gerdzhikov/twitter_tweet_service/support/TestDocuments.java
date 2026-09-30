package com.peter_gerdzhikov.twitter_tweet_service.support;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;

/**
 * Documents with fresh random ids and a fresh random author, so a test never sees another test's data.
 */
public final class TestDocuments {

    public static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    private TestDocuments() {
    }

    public static Tweet tweet() {
        return tweetBy(UUID.randomUUID());
    }

    public static Tweet tweetBy(UUID authorId) {
        return Tweet
                .builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .content("hello")
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .build();
    }

    public static TweetImage image() {
        return TweetImage
                .builder()
                .id(UUID.randomUUID())
                .objectKey(UUID.randomUUID().toString())
                .contentType("image/png")
                .sizeBytes(123)
                .build();
    }

    public static OutboxMessage outboxMessage(OutboxStatus status, Instant createdAt) {
        return OutboxMessage
                .builder()
                .id(UUID.randomUUID())
                .topic("test-topic")
                .messageKey(UUID.randomUUID().toString())
                .payload("{}")
                .status(status)
                .createdAt(createdAt.truncatedTo(ChronoUnit.MILLIS))
                .build();
    }

    public static List<TweetImage> images(int count) {
        return Stream
                .generate(TestDocuments::image)
                .limit(count)
                .toList();
    }
}
