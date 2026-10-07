package com.peter_gerdzhikov.twitter_timeline_service.controllers.tweetdetails;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class TweetDetailsControllerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String DETAILS_PATH = "/api/v1/tweet-details";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USERS_PATH = "/internal/v1/users";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class Identity {

        @Test
        void should_return_400_when_the_user_id_header_is_missing() throws Exception {
            mockMvc
                    .perform(get(DETAILS_PATH + "/" + TestIds.tweetId()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Read {

        @Test
        void should_return_200_and_every_feed_item_field_when_the_tweet_exists() throws Exception {
            UUID viewer = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            seedSavedTweet(viewer, tweetId, authorId, TWEET_CREATED_AT);
            seedLike(viewer, tweetId, authorId, TWEET_CREATED_AT);
            seedViews(tweetId, 3);
            stubTweet(tweetId, authorId, 4);
            stubUser(authorId, "ana", "/api/v1/files/f1");

            JsonNode item = details(viewer, tweetId, status().isOk());

            assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                    "id", "views", "savedByMe", "likes", "likedByMe", "replyCount", "content", "createdAt", "updatedAt", "images", "author");
            assertThat(item.get("id").asString()).isEqualTo(tweetId.toString());
            assertThat(item.get("views").asLong()).isEqualTo(3);
            assertThat(item.get("savedByMe").asBoolean()).isTrue();
            assertThat(item.get("likes").asLong()).isEqualTo(1);
            assertThat(item.get("likedByMe").asBoolean()).isTrue();
            assertThat(item.get("replyCount").asLong()).isEqualTo(4);
            assertThat(item.get("content").asString()).isEqualTo("tweet " + tweetId);
            assertThat(item.get("images")).hasSize(1);
            assertThat(item.get("author").get("id").asString()).isEqualTo(authorId.toString());
            assertThat(item.get("author").get("username").asString()).isEqualTo("ana");
            assertThat(item.get("author").get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/f1");
        }

        @Test
        void should_return_the_viewers_own_flags_as_false_when_the_viewer_did_nothing_with_the_tweet() throws Exception {
            UUID authorId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweet(tweetId, authorId, 0);
            stubUser(authorId, "ana", null);

            JsonNode item = details(TestIds.userId(), tweetId, status().isOk());

            assertThat(item.get("savedByMe").asBoolean()).isFalse();
            assertThat(item.get("likedByMe").asBoolean()).isFalse();
            assertThat(item.get("views").asLong()).isZero();
            assertThat(item.get("replyCount").asLong()).isZero();
        }

        @Test
        void should_make_one_call_to_each_downstream_when_the_tweet_is_read() throws Exception {
            UUID authorId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweet(tweetId, authorId, 0);
            stubUser(authorId, "ana", null);

            details(TestIds.userId(), tweetId, status().isOk());

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH)).withQueryParam("ids", equalTo(tweetId.toString())));
            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH)).withQueryParam("ids", equalTo(authorId.toString())));
        }

        @Test
        void should_return_400_when_the_tweet_id_is_malformed() throws Exception {
            mockMvc
                    .perform(get(DETAILS_PATH + "/not-a-uuid").header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class MissingData {

        @Test
        void should_return_404_when_the_tweet_is_gone() throws Exception {
            stubJsonArray(TWEET_SERVICE_STUB, TWEETS_PATH, List.of());

            JsonNode body = details(TestIds.userId(), TestIds.tweetId(), status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Tweet not found.");
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }

        @Test
        void should_return_404_when_the_author_is_gone() throws Exception {
            UUID tweetId = TestIds.tweetId();
            stubTweet(tweetId, TestIds.userId(), 0);
            stubJsonArray(GATEWAY_STUB, USERS_PATH, List.of());

            JsonNode body = details(TestIds.userId(), tweetId, status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Author not found.");
        }
    }

    @Nested
    class Failures {

        @Test
        void should_return_502_when_the_tweet_service_is_down() throws Exception {
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = details(TestIds.userId(), TestIds.tweetId(), status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_502_when_the_gateway_is_down() throws Exception {
            UUID tweetId = TestIds.tweetId();
            stubTweet(tweetId, TestIds.userId(), 0);
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = details(TestIds.userId(), tweetId, status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_502_when_the_tweet_service_answers_5xx() throws Exception {
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).willReturn(aResponse().withStatus(503)));

            details(TestIds.userId(), TestIds.tweetId(), status().isBadGateway());
        }

        @Test
        void should_return_502_when_the_gateway_answers_5xx() throws Exception {
            UUID tweetId = TestIds.tweetId();
            stubTweet(tweetId, TestIds.userId(), 0);
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH)).willReturn(aResponse().withStatus(503)));

            details(TestIds.userId(), tweetId, status().isBadGateway());
        }

        @Test
        void should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout() throws Exception {
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = details(TestIds.userId(), TestIds.tweetId(), status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }

        @Test
        void should_return_504_when_the_gateway_is_slower_than_the_read_timeout() throws Exception {
            UUID tweetId = TestIds.tweetId();
            stubTweet(tweetId, TestIds.userId(), 0);
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = details(TestIds.userId(), tweetId, status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }
    }

    private JsonNode details(UUID viewer, UUID tweetId, ResultMatcher expected) throws Exception {
        return objectMapper.readTree(mockMvc
                .perform(get(DETAILS_PATH + "/" + tweetId).header(USER_ID_HEADER, viewer.toString()))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private void stubTweet(UUID tweetId, UUID authorId, long replyCount) {
        Instant createdAt = TWEET_CREATED_AT;
        stubJsonArray(TWEET_SERVICE_STUB, TWEETS_PATH, List.of(toJson(Map.of(
                "id", tweetId,
                "authorId", authorId,
                "content", "tweet " + tweetId,
                "replyCount", replyCount,
                "createdAt", createdAt.toString(),
                "updatedAt", createdAt.plusSeconds(1).toString(),
                "images", List.of(Map.of("id", UUID.randomUUID(), "sizeBytes", 1234, "contentType", "image/png"))))));
    }

    private void stubUser(UUID authorId, String username, String profilePictureUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", authorId);
        user.put("username", username);
        user.put("profilePictureUrl", profilePictureUrl);
        stubJsonArray(GATEWAY_STUB, USERS_PATH, List.of(toJson(user)));
    }

    private void stubJsonArray(WireMock stub, String path, List<String> elements) {
        ResponseDefinitionBuilder response = aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[" + String.join(",", elements) + "]");
        stub.register(WireMock.get(urlPathEqualTo(path)).willReturn(response));
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
