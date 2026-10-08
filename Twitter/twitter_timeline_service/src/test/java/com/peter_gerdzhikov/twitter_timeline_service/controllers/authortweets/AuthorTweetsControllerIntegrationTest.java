package com.peter_gerdzhikov.twitter_timeline_service.controllers.authortweets;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class AuthorTweetsControllerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String AUTHOR_TWEETS_PATH = "/api/v1/author-tweets";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USERS_PATH = "/internal/v1/users";

    private static final String TWEETS_PATH = "/internal/v1/tweets/by-author/";

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
                    .perform(get(AUTHOR_TWEETS_PATH + "/" + TestIds.userId()))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Read {

        @Test
        void should_return_200_and_every_feed_item_field_when_the_author_has_a_tweet() throws Exception {
            UUID viewer = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            seedSavedTweet(viewer, tweetId, authorId, TWEET_CREATED_AT);
            seedLike(viewer, tweetId, authorId, TWEET_CREATED_AT);
            seedViews(tweetId, 3);
            stubAuthor(authorId, "ana", "/api/v1/files/f1");
            stubPage(authorId, null, List.of(tweetJson(tweetId, authorId, 4)));

            JsonNode body = list(viewer, authorId, null, "20", status().isOk());

            assertThat(body.propertyNames()).containsExactlyInAnyOrder("nextCursor", "items");
            assertThat(body.get("nextCursor").isNull()).isTrue();
            JsonNode item = body.get("items").get(0);
            assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                    "id", "views", "savedByMe", "likes", "likedByMe", "replyCount", "content", "createdAt", "updatedAt", "images", "author");
            assertThat(item.get("id").asString()).isEqualTo(tweetId.toString());
            assertThat(item.get("views").asLong()).isEqualTo(3);
            assertThat(item.get("savedByMe").asBoolean()).isTrue();
            assertThat(item.get("likes").asLong()).isEqualTo(1);
            assertThat(item.get("likedByMe").asBoolean()).isTrue();
            assertThat(item.get("replyCount").asLong()).isEqualTo(4);
            assertThat(item.get("author").get("id").asString()).isEqualTo(authorId.toString());
            assertThat(item.get("author").get("username").asString()).isEqualTo("ana");
            assertThat(item.get("author").get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/f1");
        }

        @Test
        void should_return_the_viewers_own_flags_per_viewer_when_two_viewers_read_the_same_page() throws Exception {
            UUID liker = TestIds.userId();
            UUID other = TestIds.userId();
            UUID authorId = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            seedSavedTweet(liker, tweetId, authorId, TWEET_CREATED_AT);
            seedLike(liker, tweetId, authorId, TWEET_CREATED_AT);
            stubAuthor(authorId, "ana", null);
            stubPage(authorId, null, List.of(tweetJson(tweetId, authorId, 0)));

            JsonNode forLiker = list(liker, authorId, null, "20", status().isOk()).get("items").get(0);
            JsonNode forOther = list(other, authorId, null, "20", status().isOk()).get("items").get(0);

            assertThat(forLiker.get("likedByMe").asBoolean()).isTrue();
            assertThat(forLiker.get("savedByMe").asBoolean()).isTrue();
            assertThat(forOther.get("likedByMe").asBoolean()).isFalse();
            assertThat(forOther.get("savedByMe").asBoolean()).isFalse();
            assertThat(forOther.get("likes").asLong()).isEqualTo(1);
        }

        @Test
        void should_pass_the_cursor_through_in_both_directions_when_the_author_has_more_pages() throws Exception {
            UUID authorId = TestIds.userId();
            UUID first = TestIds.tweetId();
            UUID second = TestIds.tweetId();
            stubAuthor(authorId, "ana", null);
            stubPage(authorId, null, "next_cursor-1", List.of(tweetJson(first, authorId, 0)));
            stubPage(authorId, "next_cursor-1", null, List.of(tweetJson(second, authorId, 0)));

            JsonNode firstPage = list(TestIds.userId(), authorId, null, "1", status().isOk());
            JsonNode secondPage = list(TestIds.userId(), authorId, firstPage.get("nextCursor").asString(), "1", status().isOk());

            assertThat(firstPage.get("nextCursor").asString()).isEqualTo("next_cursor-1");
            assertThat(firstPage.get("items").get(0).get("id").asString()).isEqualTo(first.toString());
            assertThat(secondPage.get("nextCursor").isNull()).isTrue();
            assertThat(secondPage.get("items").get(0).get("id").asString()).isEqualTo(second.toString());
        }

        @Test
        void should_return_an_empty_page_when_the_author_has_no_tweets() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            stubPage(authorId, null, List.of());

            JsonNode body = list(TestIds.userId(), authorId, null, "20", status().isOk());

            assertThat(body.get("items")).isEmpty();
            assertThat(body.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_ask_the_tweet_service_for_20_when_the_size_is_missing() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            stubPage(authorId, null, List.of());

            mockMvc
                    .perform(get(AUTHOR_TWEETS_PATH + "/" + authorId).header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isOk());

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH + authorId + "/page"))
                    .withQueryParam("size", equalTo("20")));
        }

        @Test
        void should_make_one_call_to_each_downstream_when_a_page_is_read() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            stubPage(authorId, null, List.of(tweetJson(TestIds.tweetId(), authorId, 0)));

            list(TestIds.userId(), authorId, null, "20", status().isOk());

            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH)).withQueryParam("ids", equalTo(authorId.toString())));
            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH + authorId + "/page")));
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "101", "many"})
        void should_return_400_when_the_size_is_outside_1_to_100_or_not_a_number(String size) throws Exception {
            list(TestIds.userId(), TestIds.userId(), null, size, status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_author_id_is_malformed() throws Exception {
            mockMvc
                    .perform(get(AUTHOR_TWEETS_PATH + "/not-a-uuid").header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_invalid_cursor_when_the_tweet_service_refuses_the_cursor() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH + authorId + "/page"))
                    .willReturn(aResponse().withStatus(400)));

            JsonNode body = list(TestIds.userId(), authorId, "garbage", "20", status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Invalid cursor.");
        }
    }

    @Nested
    class MissingData {

        @Test
        void should_return_404_when_the_author_is_unknown_and_read_no_tweets() throws Exception {
            UUID authorId = TestIds.userId();
            stubJsonArray(GATEWAY_STUB, USERS_PATH, List.of());

            JsonNode body = list(TestIds.userId(), authorId, null, "20", status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Author not found.");
            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH + authorId + "/page")));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_return_502_when_the_gateway_is_down() throws Exception {
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = list(TestIds.userId(), TestIds.userId(), null, "20", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_502_when_the_tweet_service_is_down() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH + authorId + "/page"))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            list(TestIds.userId(), authorId, null, "20", status().isBadGateway());
        }

        @Test
        void should_return_502_when_the_tweet_service_answers_5xx() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH + authorId + "/page"))
                    .willReturn(aResponse().withStatus(503)));

            list(TestIds.userId(), authorId, null, "20", status().isBadGateway());
        }

        @Test
        void should_return_504_when_the_gateway_is_slower_than_the_read_timeout() throws Exception {
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = list(TestIds.userId(), TestIds.userId(), null, "20", status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }

        @Test
        void should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout() throws Exception {
            UUID authorId = TestIds.userId();
            stubAuthor(authorId, "ana", null);
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH + authorId + "/page"))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            list(TestIds.userId(), authorId, null, "20", status().isGatewayTimeout());
        }
    }

    private JsonNode list(UUID viewer, UUID authorId, String cursor, String size, ResultMatcher expected) throws Exception {
        MockHttpServletRequestBuilder request = get(AUTHOR_TWEETS_PATH + "/" + authorId)
                .header(USER_ID_HEADER, viewer.toString())
                .param("size", size);
        if (cursor != null) {
            request.param("cursor", cursor);
        }

        return objectMapper.readTree(mockMvc
                .perform(request)
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private String tweetJson(UUID tweetId, UUID authorId, long replyCount) {
        Instant createdAt = TWEET_CREATED_AT;

        return toJson(Map.of(
                "id", tweetId,
                "authorId", authorId,
                "content", "tweet " + tweetId,
                "replyCount", replyCount,
                "createdAt", createdAt.toString(),
                "updatedAt", createdAt.plusSeconds(1).toString(),
                "images", List.of(Map.of("id", UUID.randomUUID(), "sizeBytes", 1234, "contentType", "image/png"))));
    }

    private void stubPage(UUID authorId, String cursor, List<String> tweets) {
        stubPage(authorId, cursor, null, tweets);
    }

    private void stubPage(UUID authorId, String cursor, String nextCursor, List<String> tweets) {
        Map<String, Object> page = new HashMap<>();
        page.put("nextCursor", nextCursor);
        page.put("items", tweets.stream().map(objectMapper::readTree).toList());
        MappingBuilder mapping = WireMock
                .get(urlPathEqualTo(TWEETS_PATH + authorId + "/page"));
        mapping = cursor == null
                ? mapping.withQueryParam("size", WireMock.matching(".*"))
                : mapping.withQueryParam("cursor", equalTo(cursor));
        TWEET_SERVICE_STUB.register(mapping.willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(toJson(page))));
    }

    private void stubAuthor(UUID authorId, String username, String profilePictureUrl) {
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
