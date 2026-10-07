package com.peter_gerdzhikov.twitter_tweet_service.controllers.replies;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@AutoConfigureMockMvc
class ReplyControllerIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USERS_PATH = "/internal/v1/users";

    private static final Instant FIRST_REPLY_AT = Instant.parse("2026-02-01T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReplyRepository replyRepository;

    @Autowired
    private TweetRepository tweetRepository;

    @Nested
    class Identity {

        @ParameterizedTest
        @CsvSource({"POST,", "GET,", "PUT,/{replyId}", "DELETE,/{replyId}"})
        void should_return_400_when_the_user_id_header_is_missing_on_a_reply_route(String method, String suffix) throws Exception {
            mockMvc
                    .perform(replyRoute(method, suffix))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @CsvSource({"POST,", "GET,", "PUT,/{replyId}", "DELETE,/{replyId}"})
        void should_return_400_when_the_user_id_header_is_not_a_uuid_on_a_reply_route(String method, String suffix) throws Exception {
            mockMvc
                    .perform(replyRoute(method, suffix).header(USER_ID_HEADER, "not-a-uuid"))
                    .andExpect(status().isBadRequest());
        }

        private MockHttpServletRequestBuilder replyRoute(String method, String suffix) {
            String path = "/api/v1/tweets/" + UUID.randomUUID() + "/replies" + (suffix == null ? "" : suffix.replace("{replyId}", UUID.randomUUID().toString()));

            return MockMvcRequestBuilders
                    .request(HttpMethod.valueOf(method), path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"content\":\"hello\"}");
        }
    }

    @Nested
    class CreateReply {

        @Test
        void should_return_201_and_the_reply_with_its_author_when_the_content_is_valid() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO caller = user("ana", "/api/v1/files/f1");
            stubUsers(caller);

            JsonNode body = createReply(tweet.getId(), caller.getId(), "hello", status().isCreated());

            Reply stored = replyRepository.findById(UUID.fromString(body.get("id").asString())).orElseThrow();
            Tweet storedTweet = tweetRepository.findById(tweet.getId()).orElseThrow();
            assertThat(body.get("tweetId").asString()).isEqualTo(tweet.getId().toString());
            assertThat(body.get("content").asString()).isEqualTo("hello");
            assertThat(body.get("edited").asBoolean()).isFalse();
            assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(mutableClock.instant());
            assertThat(body.get("updatedAt").asString()).isEqualTo(body.get("createdAt").asString());
            assertThat(body.get("author").get("id").asString()).isEqualTo(caller.getId().toString());
            assertThat(body.get("author").get("username").asString()).isEqualTo("ana");
            assertThat(body.get("author").get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/f1");
            assertThat(stored.getAuthorId()).isEqualTo(caller.getId());
            assertThat(stored.getTweetId()).isEqualTo(tweet.getId());
            assertThat(stored.isEdited()).isFalse();
            assertThat(storedTweet.getReplyCount()).isEqualTo(1);
            assertThat(storedTweet.getUpdatedAt()).isEqualTo(tweet.getUpdatedAt());
        }

        @Test
        void should_trim_the_content_when_a_reply_is_created() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO caller = user("ana", null);
            stubUsers(caller);

            JsonNode body = createReply(tweet.getId(), caller.getId(), "  hello  ", status().isCreated());

            assertThat(body.get("content").asString()).isEqualTo("hello");
            assertThat(replyRepository.findById(UUID.fromString(body.get("id").asString())).orElseThrow().getContent())
                    .isEqualTo("hello");
        }

        @Test
        void should_return_201_when_the_content_is_280_code_points() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO caller = user("ana", null);
            stubUsers(caller);

            createReply(tweet.getId(), caller.getId(), "\uD83D\uDE00".repeat(280), status().isCreated());

            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(1);
        }

        @Test
        void should_return_400_when_the_content_is_empty() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO caller = user("ana", null);
            stubUsers(caller);

            JsonNode body = createReply(tweet.getId(), caller.getId(), "   ", status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("A reply needs text.");
            assertNothingWritten(tweet);
        }

        @Test
        void should_return_400_when_the_content_is_281_code_points() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO caller = user("ana", null);
            stubUsers(caller);

            JsonNode body = createReply(tweet.getId(), caller.getId(), "a".repeat(281), status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("A reply can be at most 280 characters.");
            assertNothingWritten(tweet);
        }

        @Test
        void should_return_404_when_the_tweet_is_unknown() throws Exception {
            UserClientDTO caller = user("ana", null);
            stubUsers(caller);
            UUID unknownTweetId = UUID.randomUUID();

            JsonNode body = createReply(unknownTweetId, caller.getId(), "hello", status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Tweet not found.");
            assertThat(replyRepository.findFirstPage(unknownTweetId, 10)).isEmpty();
        }

        @Test
        void should_return_403_and_write_nothing_when_the_caller_is_unknown() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            stubUsers();

            JsonNode body = createReply(tweet.getId(), UUID.randomUUID(), "hello", status().isForbidden());

            assertThat(body.get("code").asString()).isEqualTo("CALLER_UNKNOWN");
            assertNothingWritten(tweet);
        }

        @Test
        void should_return_502_and_write_nothing_when_the_gateway_is_down() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            stubGatewayDown();

            JsonNode body = createReply(tweet.getId(), UUID.randomUUID(), "hello", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
            assertThat(body.has("code")).isFalse();
            assertNothingWritten(tweet);
        }

        private void assertNothingWritten(Tweet tweet) {
            assertThat(replyRepository.findFirstPage(tweet.getId(), 10)).isEmpty();
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isZero();
        }
    }

    @Nested
    class ListReplies {

        @Test
        void should_return_200_with_the_replies_oldest_first_and_a_next_cursor_when_the_tweet_has_replies() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", "/api/v1/files/f1");
            UserClientDTO bob = user("bob", null);
            stubUsers(ana, bob);
            Reply second = replyBy(tweet, bob, 20);
            Reply first = replyBy(tweet, ana, 10);
            replyBy(tweet, ana, 30);

            JsonNode body = listReplies(tweet.getId(), "?size=2", status().isOk());

            assertThat(body.get("items")).hasSize(2);
            assertThat(body.get("items").get(0).get("id").asString()).isEqualTo(first.getId().toString());
            assertThat(body.get("items").get(0).get("author").get("username").asString()).isEqualTo("ana");
            assertThat(body.get("items").get(0).get("author").get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/f1");
            assertThat(body.get("items").get(0).get("edited").asBoolean()).isFalse();
            assertThat(body.get("items").get(1).get("id").asString()).isEqualTo(second.getId().toString());
            assertThat(body.get("items").get(1).get("author").get("username").asString()).isEqualTo("bob");
            assertThat(body.get("nextCursor").asString()).isNotBlank();
        }

        @Test
        void should_return_the_next_page_when_the_cursor_is_followed() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            stubUsers(ana);
            replyBy(tweet, ana, 10);
            replyBy(tweet, ana, 20);
            Reply last = replyBy(tweet, ana, 30);
            String cursor = listReplies(tweet.getId(), "?size=2", status().isOk()).get("nextCursor").asString();

            JsonNode body = listReplies(tweet.getId(), "?size=2&cursor=" + cursor, status().isOk());

            assertThat(body.get("items")).hasSize(1);
            assertThat(body.get("items").get(0).get("id").asString()).isEqualTo(last.getId().toString());
            assertThat(body.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_return_200_with_no_items_when_the_tweet_has_no_replies() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            JsonNode body = listReplies(tweet.getId(), "", status().isOk());

            assertThat(body.get("items")).isEmpty();
            assertThat(body.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_return_404_when_the_tweet_is_unknown() throws Exception {
            JsonNode body = listReplies(UUID.randomUUID(), "", status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Tweet not found.");
        }

        @Test
        void should_return_400_when_the_cursor_is_bad() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            JsonNode body = listReplies(tweet.getId(), "?cursor=not-a-cursor!", status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Invalid cursor.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "101"})
        void should_return_400_when_the_size_is_outside_1_to_100(String size) throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            JsonNode body = listReplies(tweet.getId(), "?size=" + size, status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Page size must be between 1 and 100.");
        }

        @Test
        void should_leave_out_a_reply_when_the_lookup_does_not_return_its_author() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            UserClientDTO gone = user("gone", null);
            stubUsers(ana);
            Reply kept = replyBy(tweet, ana, 10);
            replyBy(tweet, gone, 20);

            JsonNode body = listReplies(tweet.getId(), "", status().isOk());

            assertThat(body.get("items")).hasSize(1);
            assertThat(body.get("items").get(0).get("id").asString()).isEqualTo(kept.getId().toString());
        }

        @Test
        void should_return_502_when_the_gateway_is_down() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            replyBy(tweet, user("ana", null), 10);
            stubGatewayDown();

            JsonNode body = listReplies(tweet.getId(), "", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }
    }

    @Nested
    class UpdateReply {

        @Test
        void should_return_200_and_mark_the_reply_edited_when_the_author_edits() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", "/api/v1/files/f1");
            stubUsers(ana);
            Reply reply = replyBy(tweet, ana, 10);
            mutableClock.advance(Duration.ofMinutes(5));

            JsonNode body = updateReply(tweet.getId(), reply.getId(), ana.getId(), "  changed  ", status().isOk());

            Reply stored = replyRepository.findById(reply.getId()).orElseThrow();
            assertThat(body.get("id").asString()).isEqualTo(reply.getId().toString());
            assertThat(body.get("content").asString()).isEqualTo("changed");
            assertThat(body.get("edited").asBoolean()).isTrue();
            assertThat(body.get("author").get("username").asString()).isEqualTo("ana");
            assertThat(Instant.parse(body.get("updatedAt").asString())).isEqualTo(mutableClock.instant());
            assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(reply.getCreatedAt());
            assertThat(stored.getContent()).isEqualTo("changed");
            assertThat(stored.isEdited()).isTrue();
        }

        @Test
        void should_mark_the_reply_edited_when_the_text_is_unchanged() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            stubUsers(ana);
            Reply reply = replyBy(tweet, ana, 10);
            mutableClock.advance(Duration.ofMinutes(5));

            JsonNode body = updateReply(tweet.getId(), reply.getId(), ana.getId(), reply.getContent(), status().isOk());

            assertThat(body.get("edited").asBoolean()).isTrue();
            assertThat(Instant.parse(body.get("updatedAt").asString())).isEqualTo(mutableClock.instant());
        }

        @Test
        void should_return_404_and_leave_the_reply_unchanged_when_someone_else_edits() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            UserClientDTO bob = user("bob", null);
            stubUsers(ana, bob);
            Reply reply = replyBy(tweet, ana, 10);

            JsonNode body = updateReply(tweet.getId(), reply.getId(), bob.getId(), "hijacked", status().isNotFound());

            Reply stored = replyRepository.findById(reply.getId()).orElseThrow();
            assertThat(body.get("messages").get(0).asString()).isEqualTo("Reply not found.");
            assertThat(stored.getContent()).isEqualTo(reply.getContent());
            assertThat(stored.isEdited()).isFalse();
            assertThat(stored.getUpdatedAt()).isEqualTo(reply.getUpdatedAt());
        }

        @Test
        void should_return_404_when_the_reply_is_unknown() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            stubUsers(ana);

            JsonNode body = updateReply(tweet.getId(), UUID.randomUUID(), ana.getId(), "changed", status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Reply not found.");
        }

        @Test
        void should_return_404_when_the_reply_belongs_to_another_tweet() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            Tweet otherTweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            stubUsers(ana);
            Reply reply = replyBy(otherTweet, ana, 10);

            updateReply(tweet.getId(), reply.getId(), ana.getId(), "changed", status().isNotFound());

            assertThat(replyRepository.findById(reply.getId()).orElseThrow().getContent()).isEqualTo(reply.getContent());
        }

        @Test
        void should_return_400_when_the_content_is_empty() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            stubUsers(ana);
            Reply reply = replyBy(tweet, ana, 10);

            JsonNode body = updateReply(tweet.getId(), reply.getId(), ana.getId(), "  ", status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("A reply needs text.");
            assertThat(replyRepository.findById(reply.getId()).orElseThrow().isEdited()).isFalse();
        }

        @Test
        void should_return_400_when_the_content_is_281_code_points() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            stubUsers(ana);
            Reply reply = replyBy(tweet, ana, 10);

            JsonNode body = updateReply(tweet.getId(), reply.getId(), ana.getId(), "a".repeat(281), status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("A reply can be at most 280 characters.");
            assertThat(replyRepository.findById(reply.getId()).orElseThrow().isEdited()).isFalse();
        }

        @Test
        void should_return_403_when_the_caller_is_unknown() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            UserClientDTO ana = user("ana", null);
            Reply reply = replyBy(tweet, ana, 10);
            stubUsers();

            JsonNode body = updateReply(tweet.getId(), reply.getId(), ana.getId(), "changed", status().isForbidden());

            assertThat(body.get("code").asString()).isEqualTo("CALLER_UNKNOWN");
            assertThat(replyRepository.findById(reply.getId()).orElseThrow().isEdited()).isFalse();
        }
    }

    @Nested
    class DeleteReply {

        @Test
        void should_return_204_and_decrement_the_count_when_the_reply_author_deletes() throws Exception {
            Tweet tweet = tweetWithReplyCount(2);
            UserClientDTO ana = user("ana", null);
            Reply reply = replyBy(tweet, ana, 10);

            deleteReply(tweet.getId(), reply.getId(), ana.getId(), status().isNoContent());

            assertThat(replyRepository.findById(reply.getId())).isEmpty();
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(1);
        }

        @Test
        void should_return_204_when_the_tweet_author_deletes() throws Exception {
            Tweet tweet = tweetWithReplyCount(1);
            Reply reply = replyBy(tweet, user("ana", null), 10);

            deleteReply(tweet.getId(), reply.getId(), tweet.getAuthorId(), status().isNoContent());

            assertThat(replyRepository.findById(reply.getId())).isEmpty();
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isZero();
        }

        @Test
        void should_return_404_and_change_nothing_when_anyone_else_deletes() throws Exception {
            Tweet tweet = tweetWithReplyCount(1);
            Reply reply = replyBy(tweet, user("ana", null), 10);

            JsonNode body = deleteReply(tweet.getId(), reply.getId(), UUID.randomUUID(), status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Reply not found.");
            assertThat(replyRepository.findById(reply.getId())).isPresent();
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(1);
        }

        @Test
        void should_return_404_when_the_reply_is_unknown() throws Exception {
            Tweet tweet = tweetWithReplyCount(0);

            JsonNode body = deleteReply(tweet.getId(), UUID.randomUUID(), tweet.getAuthorId(), status().isNotFound());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Reply not found.");
        }

        @Test
        void should_return_404_when_the_reply_belongs_to_another_tweet() throws Exception {
            Tweet tweet = tweetWithReplyCount(0);
            Tweet otherTweet = tweetWithReplyCount(1);
            Reply reply = replyBy(otherTweet, user("ana", null), 10);

            deleteReply(tweet.getId(), reply.getId(), tweet.getAuthorId(), status().isNotFound());

            assertThat(replyRepository.findById(reply.getId())).isPresent();
            assertThat(tweetRepository.findById(otherTweet.getId()).orElseThrow().getReplyCount()).isEqualTo(1);
        }

        @Test
        void should_return_404_when_the_reply_is_deleted_twice() throws Exception {
            Tweet tweet = tweetWithReplyCount(2);
            UserClientDTO ana = user("ana", null);
            Reply reply = replyBy(tweet, ana, 10);
            deleteReply(tweet.getId(), reply.getId(), ana.getId(), status().isNoContent());

            deleteReply(tweet.getId(), reply.getId(), ana.getId(), status().isNotFound());

            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(1);
        }

        private Tweet tweetWithReplyCount(long replyCount) {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            if (replyCount > 0) {
                tweetRepository.incrementReplyCount(tweet.getId(), replyCount);
            }

            return tweet;
        }
    }

    private JsonNode updateReply(UUID tweetId, UUID replyId, UUID callerId, String content, ResultMatcher expected) throws Exception {
        return readBody(mockMvc
                .perform(MockMvcRequestBuilders
                        .put("/api/v1/tweets/" + tweetId + "/replies/" + replyId)
                        .header(USER_ID_HEADER, callerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode deleteReply(UUID tweetId, UUID replyId, UUID callerId, ResultMatcher expected) throws Exception {
        return readBody(mockMvc
                .perform(MockMvcRequestBuilders
                        .delete("/api/v1/tweets/" + tweetId + "/replies/" + replyId)
                        .header(USER_ID_HEADER, callerId.toString()))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode readBody(String body) {
        return body.isEmpty() ? null : objectMapper.readTree(body);
    }

    private UserClientDTO user(String username, String profilePictureUrl) {
        return UserClientDTO
                .builder()
                .id(UUID.randomUUID())
                .username(username)
                .profilePictureUrl(profilePictureUrl)
                .build();
    }

    private Reply replyBy(Tweet tweet, UserClientDTO author, int secondsAfterTheFirstReply) {
        Reply reply = TestDocuments.replyAt(tweet.getId(), FIRST_REPLY_AT.plusSeconds(secondsAfterTheFirstReply));
        reply.setAuthorId(author.getId());

        return replyRepository.save(reply);
    }

    private void stubUsers(UserClientDTO... users) throws Exception {
        GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(List.of(users)))));
    }

    private void stubGatewayDown() {
        GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH)).willReturn(aResponse().withStatus(500)));
    }

    private JsonNode createReply(UUID tweetId, UUID callerId, String content, ResultMatcher expected) throws Exception {
        return objectMapper.readTree(mockMvc
                .perform(post("/api/v1/tweets/" + tweetId + "/replies")
                        .header(USER_ID_HEADER, callerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private JsonNode listReplies(UUID tweetId, String query, ResultMatcher expected) throws Exception {
        return objectMapper.readTree(mockMvc
                .perform(MockMvcRequestBuilders
                        .get("/api/v1/tweets/" + tweetId + "/replies" + query)
                        .header(USER_ID_HEADER, UUID.randomUUID().toString()))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString());
    }
}
