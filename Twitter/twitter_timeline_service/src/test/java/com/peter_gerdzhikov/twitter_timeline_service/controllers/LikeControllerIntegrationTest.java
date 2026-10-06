package com.peter_gerdzhikov.twitter_timeline_service.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
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

import com.peter_gerdzhikov.twitter_timeline_service.entities.likes.TweetLike;
import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.MutableClock;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;
import com.peter_gerdzhikov.twitter_timeline_service.utilities.paging.TimelineCursorCodec;

class LikeControllerIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String LIKES_PATH = "/api/v1/likes";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USERS_PATH = "/internal/v1/users";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int OVER_THE_BODY_LIMIT_BYTES = 9000;

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 3000;

    private static final Instant LIKED_AT = Instant.parse("2026-01-01T00:00:00.123Z");

    private static final UUID LOW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final UUID HIGH_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Value("${app.internal-api.secret}")
    private String internalSecret;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> tweetsHeldByTheTweetService = new ArrayList<>();

    private final List<String> usersKnownToTheGateway = new ArrayList<>();

    @AfterEach
    void resetTheClock() {
        clock.reset();
    }

    @Nested
    class Identity {

        @Test
        void should_return_400_when_the_user_id_header_is_missing_on_like() throws Exception {
            mockMvc
                    .perform(put(LIKES_PATH + "/" + TestIds.tweetId()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_user_id_header_is_missing_on_unlike() throws Exception {
            mockMvc
                    .perform(delete(LIKES_PATH + "/" + TestIds.tweetId()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_return_400_when_the_user_id_header_is_missing_on_list() throws Exception {
            mockMvc
                    .perform(get(LIKES_PATH))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest
        @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", ""})
        void should_return_400_when_the_user_id_header_is_not_a_uuid(String header) throws Exception {
            mockMvc
                    .perform(put(LIKES_PATH + "/" + TestIds.tweetId()).header(USER_ID_HEADER, header))
                    .andExpect(status().isBadRequest());
            mockMvc
                    .perform(delete(LIKES_PATH + "/" + TestIds.tweetId()).header(USER_ID_HEADER, header))
                    .andExpect(status().isBadRequest());
            mockMvc
                    .perform(get(LIKES_PATH).header(USER_ID_HEADER, header))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class Like {

        @Test
        void should_return_204_and_store_the_like_with_its_author_when_the_tweet_exists() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = TestIds.userId();
            UUID tweetId = existingTweet(author);
            clock.setInstant(LIKED_AT);
            stubTweetsOnly();

            like(owner, tweetId, status().isNoContent());

            List<TweetLike> stored = likedBy(owner);
            assertThat(stored).hasSize(1);
            assertThat(stored.getFirst().getTweetId()).isEqualTo(tweetId);
            assertThat(stored.getFirst().getAuthorId()).isEqualTo(author);
            assertThat(stored.getFirst().getLikedAt()).isEqualTo(LIKED_AT);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_keep_one_row_and_the_original_liked_at_when_the_tweet_is_liked_again() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = existingTweet(TestIds.userId());
            stubTweetsOnly();
            clock.setInstant(LIKED_AT);
            like(owner, tweetId, status().isNoContent());
            clock.setInstant(LIKED_AT.plusSeconds(60));

            like(owner, tweetId, status().isNoContent());

            List<TweetLike> stored = likedBy(owner);
            assertThat(stored).hasSize(1);
            assertThat(stored.getFirst().getLikedAt()).isEqualTo(LIKED_AT);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_count_both_likes_when_a_second_user_likes_the_tweet() throws Exception {
            UUID tweetId = existingTweet(TestIds.userId());
            stubTweetsOnly();

            like(TestIds.userId(), tweetId, status().isNoContent());
            like(TestIds.userId(), tweetId, status().isNoContent());

            assertThat(likesOf(tweetId)).isEqualTo(2);
        }

        @Test
        void should_return_204_and_count_the_like_when_the_author_likes_their_own_tweet() throws Exception {
            UUID author = TestIds.userId();
            UUID tweetId = existingTweet(author);
            stubTweetsOnly();

            like(author, tweetId, status().isNoContent());

            assertThat(likedBy(author)).hasSize(1);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_return_404_and_store_nothing_when_the_tweet_is_unknown() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = TestIds.tweetId();
            stubTweetsOnly();

            MvcResult result = like(owner, tweetId, status().isNotFound());

            assertThat(result.getResponse().getContentAsString()).contains("Tweet not found.");
            assertThat(likedBy(owner)).isEmpty();
            assertThat(tweetLikeCountRepository.existsById(tweetId)).isFalse();
        }

        @Test
        void should_return_400_and_make_no_tweet_call_when_the_tweet_id_is_malformed() throws Exception {
            MvcResult result = mockMvc
                    .perform(put(LIKES_PATH + "/abc").header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isBadRequest())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("Malformed request parameter.");
            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
        }

        @Test
        void should_keep_one_row_when_the_tweet_is_liked_again_with_an_upper_case_id() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = existingTweet(TestIds.userId());
            stubTweetsOnly();
            like(owner, tweetId, status().isNoContent());

            likeByPath(owner, tweetId.toString().toUpperCase(), status().isNoContent());

            assertThat(likedBy(owner)).hasSize(1);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_keep_one_row_when_the_tweet_is_liked_again_with_the_short_form_of_its_id() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = UUID.fromString("00000001-0001-0001-0001-000000000001");
            holdTweet(tweetId, TestIds.userId(), LIKED_AT);
            stubTweetsOnly();
            like(owner, tweetId, status().isNoContent());

            likeByPath(owner, "1-1-1-1-1", status().isNoContent());

            assertThat(likedBy(owner)).hasSize(1);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_ignore_the_body_when_a_like_carries_one() throws Exception {
            UUID owner = TestIds.userId();
            UUID someoneElse = TestIds.userId();
            UUID tweetId = existingTweet(TestIds.userId());
            stubTweetsOnly();

            mockMvc
                    .perform(put(LIKES_PATH + "/" + tweetId)
                            .header(USER_ID_HEADER, owner.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"likes\": 1000, \"userId\": \"" + someoneElse + "\"}"))
                    .andExpect(status().isNoContent());

            assertThat(likedBy(owner)).hasSize(1);
            assertThat(likedBy(someoneElse)).isEmpty();
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_return_413_and_store_nothing_when_the_body_is_over_8_kb() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = existingTweet(TestIds.userId());
            stubTweetsOnly();

            MvcResult result = mockMvc
                    .perform(put(LIKES_PATH + "/" + tweetId)
                            .header(USER_ID_HEADER, owner.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"padding\": \"" + "a".repeat(OVER_THE_BODY_LIMIT_BYTES) + "\"}"))
                    .andExpect(status().isContentTooLarge())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("The request body is too large.");
            assertThat(likedBy(owner)).isEmpty();
        }

        @Test
        void should_return_405_when_the_like_path_is_posted_to() throws Exception {
            MvcResult result = mockMvc
                    .perform(post(LIKES_PATH + "/" + TestIds.tweetId()).header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isMethodNotAllowed())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString()).contains("This method is not supported for this endpoint.");
        }
    }

    @Nested
    class LikeCalls {

        @Test
        void should_make_one_tweet_call_for_that_tweet_and_no_user_call_when_a_tweet_is_liked() throws Exception {
            UUID tweetId = existingTweet(TestIds.userId());
            stubTweetsOnly();

            like(TestIds.userId(), tweetId, status().isNoContent());

            assertThat(requestedIds(TWEET_SERVICE_STUB, TWEETS_PATH)).containsExactly(tweetId.toString());
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }
    }

    @Nested
    class LikeFailures {

        @Test
        void should_return_502_and_store_nothing_when_the_tweet_service_is_down() throws Exception {
            UUID owner = TestIds.userId();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            MvcResult result = like(owner, TestIds.tweetId(), status().isBadGateway());

            assertThat(result.getResponse().getContentAsString()).contains("Upstream service unavailable.");
            assertThat(likedBy(owner)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {500, 404, 200})
        void should_return_502_and_store_nothing_when_the_tweet_service_answers_5xx_4xx_or_an_unreadable_body(int status) throws Exception {
            UUID owner = TestIds.userId();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse()
                            .withStatus(status)
                            .withHeader("Content-Type", "application/json")
                            .withBody("not a json array")));

            like(owner, TestIds.tweetId(), status().isBadGateway());

            assertThat(likedBy(owner)).isEmpty();
        }

        @Test
        void should_return_504_and_store_nothing_when_the_tweet_service_is_slower_than_the_read_timeout() throws Exception {
            UUID owner = TestIds.userId();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            MvcResult result = like(owner, TestIds.tweetId(), status().isGatewayTimeout());

            assertThat(result.getResponse().getContentAsString()).contains("Upstream service timed out.");
            assertThat(likedBy(owner)).isEmpty();
        }

        @Test
        void should_leave_the_counter_untouched_when_the_like_fails() throws Exception {
            UUID tweetId = likedTweet(TestIds.userId(), TestIds.userId(), LIKED_AT);
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(503)));

            like(TestIds.userId(), tweetId, status().isBadGateway());

            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_not_leak_the_downstream_body_when_the_like_fails() throws Exception {
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(500).withBody("secret downstream detail")));

            MvcResult result = like(TestIds.userId(), TestIds.tweetId(), status().isBadGateway());

            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("secret downstream detail")
                    .contains("Upstream service unavailable.");
        }
    }

    @Nested
    class Unlike {

        @Test
        void should_return_204_and_remove_the_row_and_decrement_the_counter_when_the_tweet_is_liked() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = likedTweet(owner, TestIds.userId(), LIKED_AT);

            unlike(owner, tweetId);

            assertThat(likedBy(owner)).isEmpty();
            assertThat(likesOf(tweetId)).isZero();
        }

        @Test
        void should_return_204_and_keep_the_counter_when_the_caller_did_not_like_the_tweet() throws Exception {
            UUID tweetId = likedTweet(TestIds.userId(), TestIds.userId(), LIKED_AT);

            unlike(TestIds.userId(), tweetId);

            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_return_204_and_keep_the_counter_at_zero_when_the_tweet_is_unliked_twice() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = likedTweet(owner, TestIds.userId(), LIKED_AT);

            unlike(owner, tweetId);
            unlike(owner, tweetId);

            assertThat(likesOf(tweetId)).isZero();
        }

        @Test
        void should_remove_only_the_callers_row_when_two_users_liked_the_tweet() throws Exception {
            UUID owner = TestIds.userId();
            UUID otherUser = TestIds.userId();
            UUID author = TestIds.userId();
            UUID tweetId = likedTweet(owner, author, LIKED_AT);
            likeRecordingService.like(otherUser, tweetId, author, LIKED_AT);

            unlike(owner, tweetId);

            assertThat(likedBy(owner)).isEmpty();
            assertThat(likedBy(otherUser)).extracting(TweetLike::getTweetId).containsExactly(tweetId);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }

        @Test
        void should_return_204_and_remove_the_row_when_the_tweet_is_unknown_to_the_tweet_service() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = likedTweetWithoutTweet(owner, TestIds.userId(), LIKED_AT);

            unlike(owner, tweetId);

            assertThat(likedBy(owner)).isEmpty();
        }

        @Test
        void should_return_400_when_the_tweet_id_is_malformed() throws Exception {
            mockMvc
                    .perform(delete(LIKES_PATH + "/not-a-uuid").header(USER_ID_HEADER, TestIds.userId().toString()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void should_make_no_downstream_call_when_a_tweet_is_unliked() throws Exception {
            UUID owner = TestIds.userId();
            UUID tweetId = likedTweet(owner, TestIds.userId(), LIKED_AT);

            unlike(owner, tweetId);

            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }
    }

    @Nested
    class Read {

        @Test
        void should_return_an_empty_page_when_nothing_is_liked() throws Exception {
            JsonNode body = list(TestIds.userId(), "", status().isOk());

            assertThat(body.get("items")).isEmpty();
            assertThat(body.get("nextCursor").isNull()).isTrue();
            TWEET_SERVICE_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
            GATEWAY_STUB.verifyThat(0, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }

        @Test
        void should_return_the_most_recently_liked_first_when_several_are_liked() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID oldest = likedTweet(owner, author, LIKED_AT);
            UUID newest = likedTweet(owner, author, LIKED_AT.plusSeconds(2));
            UUID middle = likedTweet(owner, author, LIKED_AT.plusSeconds(1));
            stubDownstreams();

            JsonNode body = list(owner, "", status().isOk());

            assertThat(itemIds(body)).containsExactly(newest.toString(), middle.toString(), oldest.toString());
        }

        @Test
        void should_break_ties_on_the_liked_time_by_tweet_id_when_likes_share_a_timestamp() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            likedTweetWithId(owner, LOW_ID, author, LIKED_AT);
            likedTweetWithId(owner, HIGH_ID, author, LIKED_AT);
            stubDownstreams();

            JsonNode body = list(owner, "", status().isOk());

            assertThat(itemIds(body)).containsExactly(HIGH_ID.toString(), LOW_ID.toString());
        }

        @Test
        void should_return_the_tweet_and_the_author_from_the_stubs_when_a_tweet_is_liked() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana", "/api/v1/files/pic-1");
            UUID tweetId = likedTweet(owner, author, LIKED_AT);
            stubDownstreams();

            JsonNode item = list(owner, "", status().isOk()).get("items").get(0);

            assertThat(item.propertyNames()).containsExactlyInAnyOrder(
                    "id", "views", "likes", "likedByMe", "savedByMe", "content", "createdAt", "updatedAt", "images", "author");
            assertThat(item.get("id").asString()).isEqualTo(tweetId.toString());
            assertThat(item.get("likedByMe").asBoolean()).isTrue();
            assertThat(item.get("content").asString()).isEqualTo("tweet " + tweetId);
            assertThat(item.get("images")).hasSize(1);
            assertThat(item.get("author").propertyNames()).containsExactlyInAnyOrder("id", "username", "profilePictureUrl");
            assertThat(item.get("author").get("id").asString()).isEqualTo(author.toString());
            assertThat(item.get("author").get("username").asString()).isEqualTo("ana");
            assertThat(item.get("author").get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/pic-1");
        }

        @Test
        void should_return_a_null_picture_url_when_the_author_has_no_picture() throws Exception {
            UUID owner = TestIds.userId();
            likedTweet(owner, knownAuthor("bob"), LIKED_AT);
            stubDownstreams();

            JsonNode author = list(owner, "", status().isOk()).get("items").get(0).get("author");

            assertThat(author.get("profilePictureUrl").isNull()).isTrue();
        }

        @Test
        void should_return_only_the_callers_likes_when_other_users_have_likes() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID mine = likedTweet(owner, author, LIKED_AT);
            UUID someoneElses = likedTweet(TestIds.userId(), author, LIKED_AT.plusSeconds(1));
            stubDownstreams();

            JsonNode body = list(owner, "", status().isOk());

            assertThat(itemIds(body)).containsExactly(mine.toString());
            assertThat(requestedIds(TWEET_SERVICE_STUB, TWEETS_PATH)).doesNotContain(someoneElses.toString());
        }

        @Test
        void should_not_move_a_tweet_up_when_it_is_liked_again() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID older = likedTweet(owner, author, LIKED_AT);
            UUID newer = likedTweet(owner, author, LIKED_AT.plusSeconds(1));
            stubDownstreams();
            clock.setInstant(LIKED_AT.plusSeconds(10));

            like(owner, older, status().isNoContent());

            assertThat(itemIds(list(owner, "", status().isOk()))).containsExactly(newer.toString(), older.toString());
        }

        @Test
        void should_move_a_tweet_to_the_top_when_it_is_unliked_and_liked_again() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID older = likedTweet(owner, author, LIKED_AT);
            UUID newer = likedTweet(owner, author, LIKED_AT.plusSeconds(1));
            stubDownstreams();
            unlike(owner, older);
            clock.setInstant(LIKED_AT.plusSeconds(10));

            like(owner, older, status().isNoContent());

            assertThat(itemIds(list(owner, "", status().isOk()))).containsExactly(older.toString(), newer.toString());
        }

        @Test
        void should_return_the_whole_counter_as_likes_when_another_user_liked_the_tweet_too() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID tweetId = likedTweet(owner, author, LIKED_AT);
            seedLike(TestIds.userId(), tweetId, author, LIKED_AT);
            stubDownstreams();

            JsonNode item = list(owner, "", status().isOk()).get("items").get(0);

            assertThat(item.get("likes").asLong()).isEqualTo(2);
        }

        @Test
        void should_mark_saved_by_me_only_when_the_caller_also_saved_the_tweet() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID savedAndLiked = likedTweet(owner, author, LIKED_AT);
            UUID onlyLiked = likedTweet(owner, author, LIKED_AT.plusSeconds(1));
            seedSavedTweet(owner, savedAndLiked, author, LIKED_AT);
            stubDownstreams();

            Map<String, Boolean> savedByMe = itemSavedByMe(list(owner, "", status().isOk()));

            assertThat(savedByMe)
                    .containsEntry(savedAndLiked.toString(), true)
                    .containsEntry(onlyLiked.toString(), false);
        }
    }

    @Nested
    class Paging {

        @Test
        void should_return_20_items_when_no_size_is_given() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            for (int index = 0; index < 25; index++) {
                likedTweet(owner, author, LIKED_AT.plusSeconds(index));
            }
            stubDownstreams();

            JsonNode body = list(owner, "", status().isOk());

            assertThat(body.get("items")).hasSize(20);
            assertThat(body.get("nextCursor").isNull()).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "101", "-1"})
        void should_return_400_when_the_size_is_out_of_range(String size) throws Exception {
            JsonNode body = list(TestIds.userId(), "?size=" + size, status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Page size must be between 1 and 100.");
        }

        @Test
        void should_return_400_when_the_size_is_not_a_number() throws Exception {
            list(TestIds.userId(), "?size=many", status().isBadRequest());
        }

        @Test
        void should_visit_every_liked_tweet_exactly_once_when_walking_the_cursor() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            List<String> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(likedTweet(owner, author, LIKED_AT.plusSeconds(index)).toString());
            }
            stubDownstreams();

            List<String> seen = walk(owner, 3);

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @Test
        void should_visit_every_liked_tweet_exactly_once_when_a_page_boundary_falls_inside_a_run_of_equal_timestamps() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            List<String> all = new ArrayList<>();
            for (int index = 0; index < 7; index++) {
                all.add(likedTweet(owner, author, LIKED_AT).toString());
            }
            stubDownstreams();

            List<String> seen = walk(owner, 3);

            assertThat(seen).hasSize(7);
            assertThat(seen).containsExactlyInAnyOrderElementsOf(all);
        }

        @ParameterizedTest
        @ValueSource(strings = {"truncated", "padded", "uppercase-id", "not-a-cursor!"})
        void should_return_400_when_the_cursor_is_tampered_with_or_not_canonical(String kind) throws Exception {
            JsonNode body = list(TestIds.userId(), "?cursor=" + cursorOfKind(kind), status().isBadRequest());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Invalid cursor.");
        }

        private List<String> walk(UUID owner, int size) throws Exception {
            List<String> seen = new ArrayList<>();
            String query = "?size=" + size;
            while (true) {
                JsonNode page = list(owner, query, status().isOk());
                seen.addAll(itemIds(page));
                if (page.get("nextCursor").isNull()) {
                    return seen;
                }

                query = "?size=" + size + "&cursor=" + page.get("nextCursor").asString();
            }
        }

        private String cursorOfKind(String kind) {
            String raw = "1767225600123456:" + LOW_ID;

            return switch (kind) {
                case "truncated" -> TimelineCursorCodec.encode(LIKED_AT, LOW_ID).substring(0, 40);
                case "padded" -> Base64.getUrlEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                case "uppercase-id" -> Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(("1767225600123456:" + UUID.randomUUID().toString().toUpperCase()).getBytes(StandardCharsets.UTF_8));
                default -> kind;
            };
        }
    }

    @Nested
    class MissingData {

        @Test
        void should_skip_the_like_and_still_advance_the_cursor_when_the_tweet_is_missing() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID older = likedTweet(owner, author, LIKED_AT);
            likedTweetWithoutTweet(owner, author, LIKED_AT.plusSeconds(1));
            UUID newest = likedTweet(owner, author, LIKED_AT.plusSeconds(2));
            stubDownstreams();

            JsonNode firstPage = list(owner, "?size=2", status().isOk());
            JsonNode secondPage = list(owner, "?size=2&cursor=" + firstPage.get("nextCursor").asString(), status().isOk());

            assertThat(itemIds(firstPage)).containsExactly(newest.toString());
            assertThat(firstPage.get("nextCursor").isNull()).isFalse();
            assertThat(itemIds(secondPage)).containsExactly(older.toString());
            assertThat(secondPage.get("nextCursor").isNull()).isTrue();
        }

        @Test
        void should_skip_the_like_and_still_advance_the_cursor_when_the_author_is_missing() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID older = likedTweet(owner, author, LIKED_AT);
            likedTweet(owner, TestIds.userId(), LIKED_AT.plusSeconds(1));
            UUID newest = likedTweet(owner, author, LIKED_AT.plusSeconds(2));
            stubDownstreams();

            JsonNode firstPage = list(owner, "?size=2", status().isOk());
            JsonNode secondPage = list(owner, "?size=2&cursor=" + firstPage.get("nextCursor").asString(), status().isOk());

            assertThat(itemIds(firstPage)).containsExactly(newest.toString());
            assertThat(firstPage.get("nextCursor").isNull()).isFalse();
            assertThat(itemIds(secondPage)).containsExactly(older.toString());
        }

        @Test
        void should_return_an_empty_page_with_a_cursor_when_every_like_of_the_page_is_missing() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            UUID kept = likedTweet(owner, author, LIKED_AT);
            likedTweetWithoutTweet(owner, author, LIKED_AT.plusSeconds(1));
            likedTweetWithoutTweet(owner, author, LIKED_AT.plusSeconds(2));
            stubDownstreams();

            JsonNode page = list(owner, "?size=2", status().isOk());

            assertThat(page.get("items")).isEmpty();
            assertThat(page.get("nextCursor").isNull()).isFalse();
            assertThat(itemIds(list(owner, "?size=2&cursor=" + page.get("nextCursor").asString(), status().isOk())))
                    .containsExactly(kept.toString());
        }
    }

    @Nested
    class Calls {

        @Test
        void should_make_one_tweet_call_and_one_user_call_when_a_page_is_read() throws Exception {
            UUID owner = TestIds.userId();
            for (int index = 0; index < 5; index++) {
                likedTweet(owner, knownAuthor("user" + index), LIKED_AT.plusSeconds(index));
            }
            stubDownstreams();

            list(owner, "?size=5", status().isOk());

            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH)));
            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH)));
        }

        @Test
        void should_send_only_the_ids_of_the_page_in_both_calls() throws Exception {
            UUID owner = TestIds.userId();
            UUID firstAuthor = knownAuthor("ana");
            UUID secondAuthor = knownAuthor("bob");
            UUID thirdAuthor = knownAuthor("cat");
            UUID oldest = likedTweet(owner, thirdAuthor, LIKED_AT);
            UUID middle = likedTweet(owner, secondAuthor, LIKED_AT.plusSeconds(1));
            UUID newest = likedTweet(owner, firstAuthor, LIKED_AT.plusSeconds(2));
            stubDownstreams();

            list(owner, "?size=2", status().isOk());

            assertThat(requestedIds(TWEET_SERVICE_STUB, TWEETS_PATH))
                    .containsExactlyInAnyOrder(newest.toString(), middle.toString())
                    .doesNotContain(oldest.toString());
            assertThat(requestedIds(GATEWAY_STUB, USERS_PATH))
                    .containsExactlyInAnyOrder(firstAuthor.toString(), secondAuthor.toString())
                    .doesNotContain(thirdAuthor.toString());
        }

        @Test
        void should_ask_for_each_author_once_when_an_author_wrote_several_tweets_of_the_page() throws Exception {
            UUID owner = TestIds.userId();
            UUID author = knownAuthor("ana");
            likedTweet(owner, author, LIKED_AT);
            likedTweet(owner, author, LIKED_AT.plusSeconds(1));
            stubDownstreams();

            list(owner, "", status().isOk());

            assertThat(requestedIds(GATEWAY_STUB, USERS_PATH)).containsExactly(author.toString());
        }

        @Test
        void should_send_the_internal_secret_on_the_user_call() throws Exception {
            UUID owner = TestIds.userId();
            likedTweet(owner, knownAuthor("ana"), LIKED_AT);
            stubDownstreams();

            list(owner, "", status().isOk());

            GATEWAY_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(USERS_PATH))
                    .withHeader("X-Internal-Secret", equalTo(internalSecret)));
            TWEET_SERVICE_STUB.verifyThat(1, getRequestedFor(urlPathEqualTo(TWEETS_PATH))
                    .withoutHeader("X-Internal-Secret"));
        }
    }

    @Nested
    class Failures {

        @Test
        void should_return_502_when_the_tweet_service_is_down_on_a_read() throws Exception {
            UUID owner = givenOneTweetLike();
            stubUsersOnly();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = list(owner, "", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_502_when_the_gateway_is_down_on_a_read() throws Exception {
            UUID owner = givenOneTweetLike();
            stubTweetsOnly();
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

            JsonNode body = list(owner, "", status().isBadGateway());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service unavailable.");
        }

        @Test
        void should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout_on_a_read() throws Exception {
            UUID owner = givenOneTweetLike();
            stubUsersOnly();
            TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = list(owner, "", status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }

        @Test
        void should_return_504_when_the_gateway_is_slower_than_the_read_timeout_on_a_read() throws Exception {
            UUID owner = givenOneTweetLike();
            stubTweetsOnly();
            GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH))
                    .willReturn(aResponse().withStatus(200).withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS)));

            JsonNode body = list(owner, "", status().isGatewayTimeout());

            assertThat(body.get("messages").get(0).asString()).isEqualTo("Upstream service timed out.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"tweet-service", "gateway"})
        void should_return_502_when_a_downstream_answers_5xx_on_a_read(String downstream) throws Exception {
            UUID owner = givenOneTweetLike();
            stubDownstreams();
            failWith(downstream, aResponse().withStatus(503));

            list(owner, "", status().isBadGateway());
        }

        @ParameterizedTest
        @ValueSource(strings = {"tweet-service", "gateway"})
        void should_not_leak_the_downstream_body_when_a_downstream_fails_on_a_read(String downstream) throws Exception {
            UUID owner = givenOneTweetLike();
            stubDownstreams();
            failWith(downstream, aResponse().withStatus(500).withBody("secret downstream detail"));

            MvcResult result = mockMvc
                    .perform(listRequest(owner, ""))
                    .andExpect(status().isBadGateway())
                    .andReturn();

            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("secret downstream detail")
                    .contains("Upstream service unavailable.");
        }

        private UUID givenOneTweetLike() {
            UUID owner = TestIds.userId();
            likedTweet(owner, knownAuthor("ana"), LIKED_AT);

            return owner;
        }

        private void failWith(String downstream, ResponseDefinitionBuilder response) {
            if (downstream.equals("tweet-service")) {
                TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).atPriority(1).willReturn(response));

            } else {
                GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH)).atPriority(1).willReturn(response));
            }
        }
    }

    private MvcResult like(UUID userId, UUID tweetId, ResultMatcher expected) throws Exception {
        return likeByPath(userId, tweetId.toString(), expected);
    }

    private MvcResult likeByPath(UUID userId, String tweetIdInPath, ResultMatcher expected) throws Exception {
        return mockMvc
                .perform(put(LIKES_PATH + "/" + tweetIdInPath).header(USER_ID_HEADER, userId.toString()))
                .andExpect(expected)
                .andReturn();
    }

    private void unlike(UUID userId, UUID tweetId) throws Exception {
        mockMvc
                .perform(delete(LIKES_PATH + "/" + tweetId).header(USER_ID_HEADER, userId.toString()))
                .andExpect(status().isNoContent());
    }

    private MockHttpServletRequestBuilder listRequest(UUID userId, String query) {
        return get(LIKES_PATH + query).header(USER_ID_HEADER, userId.toString());
    }

    private JsonNode list(UUID userId, String query, ResultMatcher expected) throws Exception {
        MvcResult result = mockMvc
                .perform(listRequest(userId, query))
                .andExpect(expected)
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<TweetLike> likedBy(UUID userId) {
        return tweetLikeRepository.findFirstPage(userId, PageRequest.of(0, 100));
    }

    private List<String> itemIds(JsonNode page) {
        return page
                .get("items")
                .valueStream()
                .map(item -> item.get("id").asString())
                .toList();
    }

    private Map<String, Boolean> itemSavedByMe(JsonNode page) {
        Map<String, Boolean> savedByMe = new LinkedHashMap<>();
        page
                .get("items")
                .forEach(item -> savedByMe.put(item.get("id").asString(), item.get("savedByMe").asBoolean()));

        return savedByMe;
    }

    private List<String> requestedIds(WireMock stub, String path) {
        List<LoggedRequest> requests = stub.find(getRequestedFor(urlPathEqualTo(path)));
        assertThat(requests).hasSize(1);

        return Stream
                .of(requests.getFirst().queryParameter("ids").firstValue().split(","))
                .toList();
    }

    /**
     * A user the gateway will know. Returns the id to give to {@link #likedTweet}.
     */
    private UUID knownAuthor(String username) {
        return knownAuthor(username, null);
    }

    private UUID knownAuthor(String username, String profilePictureUrl) {
        UUID authorId = TestIds.userId();
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", authorId);
        user.put("username", username);
        user.put("profilePictureUrl", profilePictureUrl);
        usersKnownToTheGateway.add(toJson(user));

        return authorId;
    }

    /**
     * A tweet the tweet service holds but nobody has liked yet, for the like tests.
     */
    private UUID existingTweet(UUID authorId) {
        UUID tweetId = TestIds.tweetId();
        holdTweet(tweetId, authorId, LIKED_AT);

        return tweetId;
    }

    /**
     * A liked tweet whose tweet the tweet service will return.
     */
    private UUID likedTweet(UUID owner, UUID authorId, Instant likedAt) {
        return likedTweetWithId(owner, TestIds.tweetId(), authorId, likedAt);
    }

    private UUID likedTweetWithId(UUID owner, UUID tweetId, UUID authorId, Instant likedAt) {
        likeRecordingService.like(owner, tweetId, authorId, likedAt);
        holdTweet(tweetId, authorId, likedAt);

        return tweetId;
    }

    /**
     * A liked tweet that was deleted since: the tweet service no longer knows it.
     */
    private UUID likedTweetWithoutTweet(UUID owner, UUID authorId, Instant likedAt) {
        UUID tweetId = TestIds.tweetId();
        likeRecordingService.like(owner, tweetId, authorId, likedAt);

        return tweetId;
    }

    private void holdTweet(UUID tweetId, UUID authorId, Instant createdAt) {
        tweetsHeldByTheTweetService.add(toJson(Map.of(
                "id", tweetId,
                "authorId", authorId,
                "content", "tweet " + tweetId,
                "createdAt", createdAt.toString(),
                "updatedAt", createdAt.plusSeconds(1).toString(),
                "images", List.of(Map.of("id", UUID.randomUUID(), "sizeBytes", 1234, "contentType", "image/png")))));
    }

    private void stubDownstreams() {
        stubTweetsOnly();
        stubUsersOnly();
    }

    private void stubTweetsOnly() {
        TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).willReturn(jsonArray(tweetsHeldByTheTweetService)));
    }

    private void stubUsersOnly() {
        GATEWAY_STUB.register(WireMock.get(urlPathEqualTo(USERS_PATH)).willReturn(jsonArray(usersKnownToTheGateway)));
    }

    private ResponseDefinitionBuilder jsonArray(List<String> elements) {
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[" + String.join(",", elements) + "]");
    }

    private String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }
}
