package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.http.Fault;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class TweetLookupServiceIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    @Autowired
    private TweetLookupService tweetLookupService;

    @Nested
    class FindByIds {

        @Test
        void should_return_the_tweets_by_id_with_their_fields_and_images_when_the_tweet_service_knows_them() {
            UUID tweetId = TestIds.tweetId();
            UUID authorId = TestIds.userId();
            UUID imageId = UUID.randomUUID();
            stubTweets("[{\"id\":\"" + tweetId + "\",\"authorId\":\"" + authorId + "\",\"content\":\"hello\","
                    + "\"createdAt\":\"2026-01-01T00:00:00.123Z\",\"updatedAt\":\"2026-01-02T00:00:00.456Z\","
                    + "\"images\":[{\"id\":\"" + imageId + "\",\"sizeBytes\":1234,\"contentType\":\"image/png\"}]}]");

            Map<UUID, TweetClientDTO> tweets = tweetLookupService.findByIds(List.of(tweetId));

            TweetClientDTO tweet = tweets.get(tweetId);
            assertThat(tweet.getAuthorId()).isEqualTo(authorId);
            assertThat(tweet.getContent()).isEqualTo("hello");
            assertThat(tweet.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123Z"));
            assertThat(tweet.getUpdatedAt()).isEqualTo(Instant.parse("2026-01-02T00:00:00.456Z"));
            assertThat(tweet.getImages()).hasSize(1);
            assertThat(tweet.getImages().getFirst().getId()).isEqualTo(imageId);
            assertThat(tweet.getImages().getFirst().getSizeBytes()).isEqualTo(1234);
            assertThat(tweet.getImages().getFirst().getContentType()).isEqualTo("image/png");
        }

        @Test
        void should_ignore_the_views_field_and_any_other_field_it_does_not_know() {
            UUID tweetId = TestIds.tweetId();
            stubTweets("[{\"id\":\"" + tweetId + "\",\"authorId\":\"" + TestIds.userId() + "\",\"content\":\"hi\","
                    + "\"views\":7,\"surprise\":true,\"createdAt\":\"2026-01-01T00:00:00Z\","
                    + "\"updatedAt\":\"2026-01-01T00:00:00Z\",\"images\":[]}]");

            assertThat(tweetLookupService.findByIds(List.of(tweetId))).containsOnlyKeys(tweetId);
        }

        @Test
        void should_leave_out_an_id_the_tweet_service_does_not_return() {
            UUID known = TestIds.tweetId();
            UUID gone = TestIds.tweetId();
            stubTweets("[{\"id\":\"" + known + "\",\"authorId\":\"" + TestIds.userId() + "\",\"content\":\"hi\","
                    + "\"createdAt\":\"2026-01-01T00:00:00Z\",\"updatedAt\":\"2026-01-01T00:00:00Z\",\"images\":[]}]");

            assertThat(tweetLookupService.findByIds(List.of(known, gone))).containsOnlyKeys(known);
        }

        @Test
        void should_send_the_ids_in_one_comma_separated_parameter() {
            UUID first = TestIds.tweetId();
            UUID second = TestIds.tweetId();
            stubTweets("[]");

            tweetLookupService.findByIds(List.of(first, second));

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH))
                    .withQueryParam("ids", equalTo(first + "," + second)));
        }

        @Test
        void should_never_send_the_gateways_internal_secret_to_the_tweet_service() {
            stubTweets("[]");

            tweetLookupService.findByIds(List.of(TestIds.tweetId()));

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH))
                    .withoutHeader("X-Internal-Secret"));
        }

        @Test
        void should_make_no_call_when_there_are_no_ids() {
            assertThat(tweetLookupService.findByIds(List.of())).isEmpty();

            TWEET_SERVICE_STUB.verifyThat(0, anyRequestedFor(anyUrl()));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_throw_unavailable_when_the_tweet_service_answers_5xx() {
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("secret downstream detail")));

            assertThatThrownBy(() -> tweetLookupService.findByIds(List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessage("Upstream service unavailable.");
        }

        @Test
        void should_throw_unavailable_when_the_tweet_service_answers_400() {
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(TWEETS_PATH)).willReturn(aResponse().withStatus(400)));

            assertThatThrownBy(() -> tweetLookupService.findByIds(List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_connection_is_reset() {
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            assertThatThrownBy(() -> tweetLookupService.findByIds(List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_unavailable_when_the_answer_is_not_a_json_array() {
            stubTweets("{\"unexpected\":true}");

            assertThatThrownBy(() -> tweetLookupService.findByIds(List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_timeout_when_the_tweet_service_is_slower_than_the_read_timeout() {
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            assertThatThrownBy(() -> tweetLookupService.findByIds(List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamTimeoutException.class)
                    .hasMessage("Upstream service timed out.");
        }
    }

    private void stubTweets(String body) {
        TWEET_SERVICE_STUB.register(get(urlPathEqualTo(TWEETS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
