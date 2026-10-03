package com.peter_gerdzhikov.twitter_timeline_service.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class ViewControllerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String VIEWS_PATH = "/api/v1/views";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USERS_PATH = "/internal/v1/users";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int MAX_REPORTED_TWEETS = 50;

    private static final int MAX_READ_TWEETS = 100;

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> tweetsHeldByTheTweetService = new ArrayList<>();

    @Nested
    class Identity {

        @Test
        void should_return_400_when_the_user_id_header_is_missing_on_report() throws Exception {
            mockMvc
                    .perform(post(VIEWS_PATH).contentType(MediaType.APPLICATION_JSON).content(body(TestIds.tweetId())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_user_id_header_is_missing_on_read() throws Exception {
            mockMvc
                    .perform(get(VIEWS_PATH).param("tweetIds", TestIds.tweetId().toString()))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", ""})
        void should_return_400_when_the_user_id_header_is_not_a_uuid(String header) throws Exception {
            mockMvc
                    .perform(post(VIEWS_PATH)
                            .header(USER_ID_HEADER, header)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(TestIds.tweetId())))
                    .andExpect(status().isBadRequest());
            mockMvc
                    .perform(get(VIEWS_PATH).header(USER_ID_HEADER, header).param("tweetIds", TestIds.tweetId().toString()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Report {

        @Test
        void should_return_204_and_count_one_view_when_a_viewer_reports_a_tweet_for_the_first_time() throws Exception {
            UUID viewer = TestIds.userId();
            UUID tweetId = existingTweet();
            stubTweets();

            report(viewer, List.of(tweetId), status().isNoContent());

            assertThat(viewsOf(tweetId)).isEqualTo(1);
            assertThat(viewersOf(tweetId)).containsExactly(viewer);
        }

        @Test
        void should_keep_the_count_at_one_when_the_same_viewer_reports_the_tweet_again() throws Exception {
            UUID viewer = TestIds.userId();
            UUID tweetId = existingTweet();
            stubTweets();
            report(viewer, List.of(tweetId), status().isNoContent());

            report(viewer, List.of(tweetId), status().isNoContent());

            assertThat(viewsOf(tweetId)).isEqualTo(1);
            assertThat(viewersOf(tweetId)).containsExactly(viewer);
        }

        @Test
        void should_count_two_when_a_second_viewer_reports_the_tweet() throws Exception {
            UUID tweetId = existingTweet();
            stubTweets();
            report(TestIds.userId(), List.of(tweetId), status().isNoContent());

            report(TestIds.userId(), List.of(tweetId), status().isNoContent());

            assertThat(viewsOf(tweetId)).isEqualTo(2);
        }

        @Test
        void should_count_the_view_when_the_author_reports_their_own_tweet() throws Exception {
            UUID author = TestIds.userId();
            UUID tweetId = existingTweet();
            stubTweets();

            report(author, List.of(tweetId), status().isNoContent());

            assertThat(viewsOf(tweetId)).isEqualTo(1);
            assertThat(viewersOf(tweetId)).containsExactly(author);
        }

        @Test
        void should_count_each_tweet_once_when_one_batch_holds_several_tweets() throws Exception {
            List<UUID> tweetIds = List.of(existingTweet(), existingTweet(), existingTweet());
            stubTweets();

            report(TestIds.userId(), tweetIds, status().isNoContent());

            tweetIds.forEach(tweetId -> assertThat(viewsOf(tweetId)).isEqualTo(1));
        }

        @Test
        void should_count_the_tweet_once_when_one_batch_repeats_its_id() throws Exception {
            UUID tweetId = existingTweet();
            stubTweets();

            report(TestIds.userId(), List.of(tweetId, tweetId, tweetId), status().isNoContent());

            assertThat(viewsOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_drop_the_unknown_ids_and_count_the_known_ones_when_a_batch_mixes_them() throws Exception {
            UUID knownTweetId = existingTweet();
            UUID unknownTweetId = TestIds.tweetId();
            stubTweets();

            report(TestIds.userId(), List.of(unknownTweetId, knownTweetId), status().isNoContent());

            assertThat(viewsOf(knownTweetId)).isEqualTo(1);
            assertThat(viewsOf(unknownTweetId)).isZero();
            assertThat(viewersOf(unknownTweetId)).isEmpty();
        }

        @Test
        void should_return_204_and_record_nothing_when_every_id_is_unknown() throws Exception {
            UUID viewer = TestIds.userId();
            UUID unknownTweetId = TestIds.tweetId();
            stubTweets();

            report(viewer, List.of(unknownTweetId), status().isNoContent());

            assertThat(viewersOf(unknownTweetId)).isEmpty();
            assertThat(tweetViewCountRepository.existsById(unknownTweetId)).isFalse();
        }

        @Test
        void should_return_204_when_the_batch_holds_exactly_50_ids() throws Exception {
            List<UUID> tweetIds = IntStream
                    .range(0, MAX_REPORTED_TWEETS)
                    .mapToObj(i -> existingTweet())
                    .toList();
            stubTweets();

            report(TestIds.userId(), tweetIds, status().isNoContent());

            tweetIds.forEach(tweetId -> assertThat(viewsOf(tweetId)).isEqualTo(1));
        }

        @Test
        void should_count_only_the_new_pairs_when_a_batch_mixes_seen_and_unseen_tweets() throws Exception {
            UUID viewer = TestIds.userId();
            UUID seenTweetId = existingTweet();
            UUID unseenTweetId = existingTweet();
            stubTweets();
            report(viewer, List.of(seenTweetId), status().isNoContent());

            report(viewer, List.of(seenTweetId, unseenTweetId), status().isNoContent());

            assertThat(viewsOf(seenTweetId)).isEqualTo(1);
            assertThat(viewsOf(unseenTweetId)).isEqualTo(1);
        }
    }

    @Nested
    class ReportValidation {

        @Test
        void should_return_400_when_the_ids_list_is_empty() throws Exception {
            reportRaw(TestIds.userId(), "{\"tweetIds\":[]}", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_batch_holds_51_ids() throws Exception {
            List<UUID> tweetIds = IntStream
                    .range(0, MAX_REPORTED_TWEETS + 1)
                    .mapToObj(i -> TestIds.tweetId())
                    .toList();

            report(TestIds.userId(), tweetIds, status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_batch_holds_51_entries_that_collapse_to_fewer_ids() throws Exception {
            UUID tweetId = existingTweet();
            stubTweets();
            List<UUID> entries = IntStream
                    .range(0, MAX_REPORTED_TWEETS + 1)
                    .mapToObj(i -> tweetId)
                    .toList();

            report(TestIds.userId(), entries, status().isBadRequest());

            assertThat(viewsOf(tweetId)).isZero();
        }

        @Test
        void should_return_400_when_an_id_is_malformed() throws Exception {
            reportRaw(TestIds.userId(), "{\"tweetIds\":[\"not-a-uuid\"]}", status().isBadRequest());
        }

        @Test
        void should_return_400_when_an_id_is_null() throws Exception {
            reportRaw(TestIds.userId(), "{\"tweetIds\":[null]}", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_ids_field_is_missing() throws Exception {
            reportRaw(TestIds.userId(), "{}", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_body_is_missing_or_not_json() throws Exception {
            UUID viewer = TestIds.userId();

            mockMvc
                    .perform(post(VIEWS_PATH).header(USER_ID_HEADER, viewer.toString()).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
            reportRaw(viewer, "not json", status().isBadRequest());
        }

        @Test
        void should_record_nothing_when_the_batch_is_rejected() throws Exception {
            UUID tweetId = existingTweet();
            stubTweets();
            List<UUID> tooMany = Stream
                    .concat(Stream.of(tweetId), IntStream.range(0, MAX_REPORTED_TWEETS).mapToObj(i -> TestIds.tweetId()))
                    .toList();

            report(TestIds.userId(), tooMany, status().isBadRequest());

            assertThat(viewersOf(tweetId)).isEmpty();
            assertThat(tweetViewCountRepository.existsById(tweetId)).isFalse();
        }
    }

    @Nested
    class ReportCalls {

        @Test
        void should_make_one_tweet_call_with_the_distinct_ids_and_no_user_call_when_views_are_reported() throws Exception {
            UUID firstTweetId = existingTweet();
            UUID secondTweetId = existingTweet();
            stubTweets();

            report(TestIds.userId(), List.of(firstTweetId, secondTweetId, firstTweetId), status().isNoContent());

            assertThat(requestedIds()).containsExactlyInAnyOrder(firstTweetId.toString(), secondTweetId.toString());
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }

        @Test
        void should_make_no_tweet_call_when_the_batch_is_rejected() throws Exception {
            stubTweets();

            reportRaw(TestIds.userId(), "{\"tweetIds\":[]}", status().isBadRequest());

            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
        }
    }

    @Nested
    class ReportFailures {

        @Test
        void should_return_502_and_record_nothing_when_the_tweet_service_is_down() throws Exception {
            UUID tweetId = TestIds.tweetId();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            MvcResult result = report(TestIds.userId(), List.of(tweetId), status().isBadGateway());

            assertThat(result.getResponse().getContentAsString()).contains("Upstream service unavailable.");
            assertThat(viewersOf(tweetId)).isEmpty();
        }

        @Test
        void should_return_502_and_record_nothing_when_the_tweet_service_answers_5xx() throws Exception {
            UUID tweetId = TestIds.tweetId();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(503)));

            report(TestIds.userId(), List.of(tweetId), status().isBadGateway());

            assertThat(viewersOf(tweetId)).isEmpty();
        }

        @Test
        void should_return_504_and_record_nothing_when_the_tweet_service_is_slower_than_the_read_timeout() throws Exception {
            UUID tweetId = TestIds.tweetId();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            MvcResult result = report(TestIds.userId(), List.of(tweetId), status().isGatewayTimeout());

            assertThat(result.getResponse().getContentAsString()).contains("Upstream service timed out.");
            assertThat(viewersOf(tweetId)).isEmpty();
        }

        @Test
        void should_not_leak_the_downstream_body_when_the_tweet_service_fails() throws Exception {
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("secret downstream detail")));

            MvcResult result = report(TestIds.userId(), List.of(TestIds.tweetId()), status().isBadGateway());

            assertThat(result.getResponse().getContentAsString()).doesNotContain("secret downstream detail");
        }
    }

    @Nested
    class Read {

        @Test
        void should_return_the_counts_of_known_tweets_when_they_have_views() throws Exception {
            UUID firstTweetId = viewedTweet(3);
            UUID secondTweetId = viewedTweet(1);

            JsonNode counts = read(TestIds.userId(), firstTweetId + "," + secondTweetId, status().isOk());

            assertThat(counts.get(firstTweetId.toString()).asLong()).isEqualTo(3);
            assertThat(counts.get(secondTweetId.toString()).asLong()).isEqualTo(1);
        }

        @Test
        void should_return_zero_for_a_tweet_nobody_viewed() throws Exception {
            UUID tweetId = TestIds.tweetId();

            JsonNode counts = read(TestIds.userId(), tweetId.toString(), status().isOk());

            assertThat(counts.get(tweetId.toString()).asLong()).isZero();
        }

        @Test
        void should_return_one_entry_per_requested_id_when_known_and_unknown_ids_are_mixed() throws Exception {
            UUID viewedTweetId = viewedTweet(2);
            UUID unknownTweetId = TestIds.tweetId();

            JsonNode counts = read(TestIds.userId(), viewedTweetId + "," + unknownTweetId, status().isOk());

            assertThat(counts.propertyNames()).containsExactlyInAnyOrder(viewedTweetId.toString(), unknownTweetId.toString());
            assertThat(counts.get(viewedTweetId.toString()).asLong()).isEqualTo(2);
            assertThat(counts.get(unknownTweetId.toString()).asLong()).isZero();
        }

        @Test
        void should_return_one_entry_when_the_same_id_is_requested_twice() throws Exception {
            UUID tweetId = viewedTweet(1);

            JsonNode counts = read(TestIds.userId(), tweetId + "," + tweetId, status().isOk());

            assertThat(counts.propertyNames()).containsExactly(tweetId.toString());
        }

        @Test
        void should_return_the_same_counts_to_every_caller_when_different_users_read() throws Exception {
            UUID tweetId = viewedTweet(2);

            JsonNode first = read(TestIds.userId(), tweetId.toString(), status().isOk());
            JsonNode second = read(TestIds.userId(), tweetId.toString(), status().isOk());

            assertThat(first).isEqualTo(second);
            assertThat(first.get(tweetId.toString()).asLong()).isEqualTo(2);
        }

        @Test
        void should_return_200_when_exactly_100_ids_are_requested() throws Exception {
            String ids = commaSeparated(IntStream
                    .range(0, MAX_READ_TWEETS)
                    .mapToObj(i -> TestIds.tweetId())
                    .toList());

            JsonNode counts = read(TestIds.userId(), ids, status().isOk());

            assertThat(counts.size()).isEqualTo(MAX_READ_TWEETS);
        }

        @Test
        void should_create_nothing_when_unknown_ids_are_read() throws Exception {
            UUID unknownTweetId = TestIds.tweetId();

            read(TestIds.userId(), unknownTweetId.toString(), status().isOk());

            assertThat(tweetViewCountRepository.existsById(unknownTweetId)).isFalse();
            assertThat(viewersOf(unknownTweetId)).isEmpty();
        }

        @Test
        void should_make_no_downstream_call_when_counts_are_read() throws Exception {
            stubTweets();

            read(TestIds.userId(), viewedTweet(1).toString(), status().isOk());

            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }
    }

    @Nested
    class ReadValidation {

        @Test
        void should_return_400_when_the_tweet_ids_parameter_is_missing() throws Exception {
            mockMvc
                    .perform(get(VIEWS_PATH).header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_tweet_ids_parameter_is_empty() throws Exception {
            read(TestIds.userId(), "", status().isBadRequest());
        }

        @Test
        void should_return_400_when_101_ids_are_requested() throws Exception {
            String ids = commaSeparated(IntStream
                    .range(0, MAX_READ_TWEETS + 1)
                    .mapToObj(i -> TestIds.tweetId())
                    .toList());

            read(TestIds.userId(), ids, status().isBadRequest());
        }

        @Test
        void should_return_400_when_an_id_is_malformed() throws Exception {
            read(TestIds.userId(), TestIds.tweetId() + ",not-a-uuid", status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_list_holds_an_empty_element() throws Exception {
            read(TestIds.userId(), TestIds.tweetId() + ",," + TestIds.tweetId(), status().isBadRequest());
        }
    }

    private MvcResult report(UUID viewerId, List<UUID> tweetIds, ResultMatcher expected) throws Exception {
        return reportRaw(viewerId, body(tweetIds.toArray(UUID[]::new)), expected);
    }

    private MvcResult reportRaw(UUID viewerId, String body, ResultMatcher expected) throws Exception {
        return mockMvc
                .perform(post(VIEWS_PATH)
                        .header(USER_ID_HEADER, viewerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expected)
                .andReturn();
    }

    private JsonNode read(UUID userId, String tweetIds, ResultMatcher expected) throws Exception {
        MockHttpServletRequestBuilder request = get(VIEWS_PATH)
                .header(USER_ID_HEADER, userId.toString())
                .param("tweetIds", tweetIds);
        MvcResult result = mockMvc
                .perform(request)
                .andExpect(expected)
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String body(UUID... tweetIds) {
        return objectMapper.writeValueAsString(Map.of("tweetIds", Arrays.asList(tweetIds)));
    }

    private String commaSeparated(List<UUID> ids) {
        return String.join(",", ids.stream().map(UUID::toString).toList());
    }

    /**
     * A tweet that already has the given number of distinct viewers.
     */
    private UUID viewedTweet(int viewers) {
        UUID tweetId = TestIds.tweetId();
        seedViews(tweetId, viewers);

        return tweetId;
    }

    /**
     * A tweet the tweet service holds, for the report tests.
     */
    private UUID existingTweet() {
        UUID tweetId = TestIds.tweetId();
        tweetsHeldByTheTweetService.add(objectMapper.writeValueAsString(Map.of(
                "id", tweetId,
                "authorId", TestIds.userId(),
                "content", "tweet " + tweetId,
                "createdAt", TWEET_CREATED_AT.toString(),
                "updatedAt", TWEET_CREATED_AT.plusSeconds(1).toString(),
                "images", List.of())));

        return tweetId;
    }

    private void stubTweets() {
        TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).willReturn(jsonArray(tweetsHeldByTheTweetService)));
    }

    private ResponseDefinitionBuilder jsonArray(List<String> elements) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[" + String.join(",", elements) + "]");
    }

    private List<String> requestedIds() {
        List<LoggedRequest> requests = TWEET_SERVICE_STUB.find(getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
        assertThat(requests).hasSize(1);

        return Stream
                .of(requests.getFirst().queryParameter("ids").firstValue().split(","))
                .toList();
    }
}
