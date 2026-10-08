package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.lookups;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import com.github.tomakehurst.wiremock.http.Fault;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetPageClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetSummaryClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidCursorException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class TweetLookupServiceIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final int LIMIT = 50;

    private static final UUID AUTHOR_ID = TestIds.userId();

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    private static final String BY_AUTHOR_PATH = "/internal/v1/tweets/by-author/";

    private static final Instant SINCE = Instant.parse("2026-01-01T00:00:00.123456Z");

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
    class FindNewestByAuthor {

        @Test
        void should_return_the_ids_and_times_in_the_order_the_tweet_service_sent_them() {
            UUID newer = TestIds.tweetId();
            UUID older = TestIds.tweetId();
            stubByAuthor("[{\"id\":\"" + newer + "\",\"createdAt\":\"2026-01-02T00:00:00.123456Z\"},"
                    + "{\"id\":\"" + older + "\",\"createdAt\":\"2026-01-01T00:00:00Z\"}]");

            List<TweetSummaryClientDTO> tweets = tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT);

            assertThat(tweets)
                    .extracting(TweetSummaryClientDTO::getId, TweetSummaryClientDTO::getCreatedAt)
                    .containsExactly(
                            tuple(newer, Instant.parse("2026-01-02T00:00:00.123456Z")),
                            tuple(older, Instant.parse("2026-01-01T00:00:00Z")));
        }

        @Test
        void should_return_an_empty_list_when_the_author_has_no_tweets() {
            stubByAuthor("[]");

            assertThat(tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT)).isEmpty();
        }

        @Test
        void should_ignore_any_field_it_does_not_know() {
            stubByAuthor("[{\"id\":\"" + TestIds.tweetId() + "\",\"createdAt\":\"2026-01-01T00:00:00Z\","
                    + "\"content\":\"hello\",\"surprise\":true}]");

            assertThat(tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT)).hasSize(1);
        }

        @Test
        void should_send_the_author_in_the_path_and_the_since_and_the_limit_as_parameters() {
            stubByAuthor("[]");

            tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(BY_AUTHOR_PATH + AUTHOR_ID))
                    .withQueryParam("since", equalTo("2026-01-01T00:00:00.123456Z"))
                    .withQueryParam("limit", equalTo("50")));
        }

        @Test
        void should_never_send_the_gateways_internal_secret_to_the_tweet_service() {
            stubByAuthor("[]");

            tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(BY_AUTHOR_PATH + AUTHOR_ID))
                    .withoutHeader("X-Internal-Secret"));
        }

        @Test
        void should_throw_unavailable_when_the_tweet_service_answers_5xx() {
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(BY_AUTHOR_PATH + AUTHOR_ID))
                    .willReturn(aResponse().withStatus(500).withBody("secret downstream detail")));

            assertThatThrownBy(() -> tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessage("Upstream service unavailable.");
        }

        @Test
        void should_throw_unavailable_when_the_answer_is_not_a_json_array() {
            stubByAuthor("{\"unexpected\":true}");

            assertThatThrownBy(() -> tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_timeout_when_the_tweet_service_is_slower_than_the_read_timeout() {
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(BY_AUTHOR_PATH + AUTHOR_ID))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            assertThatThrownBy(() -> tweetLookupService.findNewestByAuthor(AUTHOR_ID, SINCE, LIMIT))
                    .isInstanceOf(UpstreamTimeoutException.class);
        }
    }

    @Nested
    class FindPageByAuthor {

        @Test
        void should_return_the_cursor_and_the_whole_tweets_in_the_order_the_tweet_service_sent_them() {
            UUID authorId = TestIds.userId();
            UUID first = TestIds.tweetId();
            UUID second = TestIds.tweetId();
            stubPage(authorId, "{\"nextCursor\":\"abc_-\",\"items\":[" + tweetJson(first, authorId) + "," + tweetJson(second, authorId) + "]}");

            TweetPageClientDTO page = tweetLookupService.findPageByAuthor(authorId, null, 2);

            assertThat(page.getNextCursor()).isEqualTo("abc_-");
            assertThat(page.getItems())
                    .extracting(TweetClientDTO::getId)
                    .containsExactly(first, second);
            assertThat(page.getItems().getFirst().getImages()).hasSize(1);
        }

        @Test
        void should_return_no_cursor_when_the_tweet_service_sends_none() {
            UUID authorId = TestIds.userId();
            stubPage(authorId, "{\"nextCursor\":null,\"items\":[]}");

            TweetPageClientDTO page = tweetLookupService.findPageByAuthor(authorId, null, 20);

            assertThat(page.getNextCursor()).isNull();
            assertThat(page.getItems()).isEmpty();
        }

        @Test
        void should_send_the_size_and_no_cursor_when_the_first_page_is_read() {
            UUID authorId = TestIds.userId();
            stubPage(authorId, "{\"nextCursor\":null,\"items\":[]}");

            tweetLookupService.findPageByAuthor(authorId, null, 20);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(pagePath(authorId)))
                    .withQueryParam("size", equalTo("20"))
                    .withoutQueryParam("cursor"));
        }

        @Test
        void should_send_the_cursor_untouched_when_a_later_page_is_read() {
            UUID authorId = TestIds.userId();
            stubPage(authorId, "{\"nextCursor\":null,\"items\":[]}");

            tweetLookupService.findPageByAuthor(authorId, "MTc2NzIyNTYwMDEyMzQ1NjowMTkw", 20);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(pagePath(authorId)))
                    .withQueryParam("cursor", equalTo("MTc2NzIyNTYwMDEyMzQ1NjowMTkw")));
        }

        @Test
        void should_never_send_the_gateways_internal_secret_to_the_tweet_service() {
            UUID authorId = TestIds.userId();
            stubPage(authorId, "{\"nextCursor\":null,\"items\":[]}");

            tweetLookupService.findPageByAuthor(authorId, null, 20);

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(pagePath(authorId)))
                    .withoutHeader("X-Internal-Secret"));
        }

        @Test
        void should_throw_invalid_cursor_when_the_tweet_service_answers_400() {
            UUID authorId = TestIds.userId();
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(pagePath(authorId))).willReturn(aResponse().withStatus(400)));

            assertThatThrownBy(() -> tweetLookupService.findPageByAuthor(authorId, "bad", 20))
                    .isInstanceOf(InvalidCursorException.class);
        }

        @Test
        void should_throw_unavailable_when_the_tweet_service_answers_5xx() {
            UUID authorId = TestIds.userId();
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(pagePath(authorId))).willReturn(aResponse().withStatus(503)));

            assertThatThrownBy(() -> tweetLookupService.findPageByAuthor(authorId, null, 20))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "null",
                "{\"nextCursor\":null}",
                "{\"nextCursor\":null,\"items\":[null]}",
                "{\"nextCursor\":null,\"items\":[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"images\":[]}]}"
        })
        void should_throw_unavailable_when_the_page_or_a_tweet_in_it_is_malformed(String body) {
            UUID authorId = TestIds.userId();
            stubPage(authorId, body);

            assertThatThrownBy(() -> tweetLookupService.findPageByAuthor(authorId, null, 20))
                    .isInstanceOf(UpstreamUnavailableException.class);
        }

        @Test
        void should_throw_timeout_when_the_tweet_service_is_slower_than_the_read_timeout() {
            UUID authorId = TestIds.userId();
            TWEET_SERVICE_STUB.register(get(urlPathEqualTo(pagePath(authorId)))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            assertThatThrownBy(() -> tweetLookupService.findPageByAuthor(authorId, null, 20))
                    .isInstanceOf(UpstreamTimeoutException.class);
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

        @ParameterizedTest
        @ValueSource(strings = {
                "[{\"authorId\":\"00000000-0000-0000-0000-000000000001\",\"images\":[]}]",
                "[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"images\":[]}]",
                "[{\"id\":\"00000000-0000-0000-0000-000000000001\",\"authorId\":\"00000000-0000-0000-0000-000000000002\"}]",
                "[null]",
                "null"
        })
        void should_throw_unavailable_when_a_tweet_lacks_its_id_author_or_images(String body) {
            stubTweets(body);

            assertThatThrownBy(() -> tweetLookupService.findByIds(List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamUnavailableException.class)
                    .hasMessage("Upstream service unavailable.");
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

    private String pagePath(UUID authorId) {
        return BY_AUTHOR_PATH + authorId + "/page";
    }

    private String tweetJson(UUID tweetId, UUID authorId) {
        return "{\"id\":\"" + tweetId + "\",\"authorId\":\"" + authorId + "\",\"content\":\"hi\","
                + "\"createdAt\":\"2026-01-01T00:00:00.123Z\",\"updatedAt\":\"2026-01-01T00:00:00.123Z\","
                + "\"images\":[{\"id\":\"" + UUID.randomUUID() + "\",\"sizeBytes\":1,\"contentType\":\"image/png\"}]}";
    }

    private void stubPage(UUID authorId, String body) {
        TWEET_SERVICE_STUB.register(get(urlPathEqualTo(pagePath(authorId)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubByAuthor(String body) {
        TWEET_SERVICE_STUB.register(get(urlPathEqualTo(BY_AUTHOR_PATH + AUTHOR_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    private void stubTweets(String body) {
        TWEET_SERVICE_STUB.register(get(urlPathEqualTo(TWEETS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}
