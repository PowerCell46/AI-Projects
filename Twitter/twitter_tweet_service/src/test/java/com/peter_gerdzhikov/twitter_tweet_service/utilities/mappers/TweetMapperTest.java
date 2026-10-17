package com.peter_gerdzhikov.twitter_tweet_service.utilities.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetSummaryResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

class TweetMapperTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00.456Z");

    @Nested
    class ToResponse {

        @Test
        void toResponse_copiesTheTweetFields() {
            Tweet tweet = TestDocuments.tweet();
            tweet.setReplyCount(7);
            tweet.setUpdatedAt(UPDATED_AT);

            TweetResponseDTO response = TweetMapper.toResponse(tweet);

            assertThat(response.getId()).isEqualTo(tweet.getId());
            assertThat(response.getAuthorId()).isEqualTo(tweet.getAuthorId());
            assertThat(response.getContent()).isEqualTo("hello");
            assertThat(response.getReplyCount()).isEqualTo(7);
            assertThat(response.getCreatedAt()).isEqualTo(TestDocuments.CREATED_AT);
            assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
        }

        @Test
        void toResponse_copiesEachImageInOrderWithoutItsObjectKey() {
            TweetImage first = TestDocuments.image();
            TweetImage second = TweetImage
                    .builder()
                    .id(UUID.randomUUID())
                    .objectKey("secret-key")
                    .contentType("image/jpeg")
                    .sizeBytes(456)
                    .build();
            Tweet tweet = TestDocuments.tweet();
            tweet.setImages(List.of(first, second));

            TweetResponseDTO response = TweetMapper.toResponse(tweet);

            assertThat(response.getImages()).hasSize(2);
            assertThat(response.getImages().get(0).getId()).isEqualTo(first.getId());
            assertThat(response.getImages().get(0).getContentType()).isEqualTo("image/png");
            assertThat(response.getImages().get(0).getSizeBytes()).isEqualTo(123);
            assertThat(response.getImages().get(1).getId()).isEqualTo(second.getId());
            assertThat(response.getImages().get(1).getContentType()).isEqualTo("image/jpeg");
            assertThat(response.getImages().get(1).getSizeBytes()).isEqualTo(456);
            assertThat(response.getImages()).noneMatch(image -> image.toString().contains("secret-key"));
        }

        @Test
        void toResponse_returnsNoImagesWhenTheTweetHasNone() {
            assertThat(TweetMapper.toResponse(TestDocuments.tweet()).getImages()).isEmpty();
        }
    }

    @Nested
    class ToSummary {

        @Test
        void toSummary_copiesOnlyTheIdAndTheCreationTime() {
            Tweet tweet = TestDocuments.tweet();

            TweetSummaryResponseDTO summary = TweetMapper.toSummary(tweet);

            assertThat(summary.getId()).isEqualTo(tweet.getId());
            assertThat(summary.getCreatedAt()).isEqualTo(TestDocuments.CREATED_AT);
        }
    }
}
