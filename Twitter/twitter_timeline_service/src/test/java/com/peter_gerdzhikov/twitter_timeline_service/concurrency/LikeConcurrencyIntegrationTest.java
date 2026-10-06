package com.peter_gerdzhikov.twitter_timeline_service.concurrency;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.peter_gerdzhikov.twitter_timeline_service.support.LatchedTasks.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

/**
 * Every scenario releases all its requests from one latch, so they really overlap, and asserts only the final
 * state and the statuses, never an interleaving. The requests go through the HTTP layer, so the statuses are
 * the ones a client sees.
 */
class LikeConcurrencyIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String LIKES_PATH = "/api/v1/likes/";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int PARALLEL_REQUESTS = 50;

    private static final int CHURNING_USERS = 20;

    private static final int CHURN_ROUNDS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class ParallelLikers {

        @Test
        void should_count_fifty_and_answer_204_to_every_request_when_fifty_users_like_one_tweet_in_parallel() throws Exception {
            UUID tweetId = existingTweet();

            List<Callable<Integer>> likes = IntStream
                    .range(0, PARALLEL_REQUESTS)
                    .<Callable<Integer>>mapToObj(i -> () -> like(TestIds.userId(), tweetId))
                    .toList();
            List<Integer> statuses = runTogether(likes);

            assertThat(statuses).hasSize(PARALLEL_REQUESTS).containsOnly(204);
            assertThat(likersOf(tweetId)).hasSize(PARALLEL_REQUESTS);
            assertThat(likesOf(tweetId)).isEqualTo(PARALLEL_REQUESTS);
        }
    }

    @Nested
    class OneUserLikingRepeatedly {

        @Test
        void should_count_one_and_answer_204_to_every_request_when_one_user_likes_one_tweet_fifty_times_in_parallel() throws Exception {
            UUID userId = TestIds.userId();
            UUID tweetId = existingTweet();

            List<Callable<Integer>> likes = IntStream
                    .range(0, PARALLEL_REQUESTS)
                    .<Callable<Integer>>mapToObj(i -> () -> like(userId, tweetId))
                    .toList();
            List<Integer> statuses = runTogether(likes);

            assertThat(statuses).hasSize(PARALLEL_REQUESTS).containsOnly(204);
            assertThat(likersOf(tweetId)).containsExactly(userId);
            assertThat(likesOf(tweetId)).isEqualTo(1);
        }
    }

    @Nested
    class LikeRacingUnlike {

        @Test
        void should_keep_the_counter_equal_to_the_row_count_when_users_like_and_unlike_at_once_for_ten_rounds() throws Exception {
            UUID tweetId = existingTweet();
            List<UUID> userIds = IntStream
                    .range(0, CHURNING_USERS)
                    .mapToObj(i -> TestIds.userId())
                    .toList();

            for (int round = 0; round < CHURN_ROUNDS; round++) {
                List<Callable<Integer>> requests = new ArrayList<>();
                for (UUID userId : userIds) {
                    requests.add(() -> like(userId, tweetId));
                    requests.add(() -> unlike(userId, tweetId));
                }

                assertThat(runTogether(requests)).containsOnly(204);
                assertThat(likesOf(tweetId)).isEqualTo(likersOf(tweetId).size());
            }
        }
    }

    @Nested
    class ParallelUnlikes {

        @Test
        void should_end_at_zero_and_answer_204_to_every_request_when_one_like_is_removed_fifty_times_in_parallel() throws Exception {
            UUID userId = TestIds.userId();
            UUID tweetId = existingTweet();
            seedLike(userId, tweetId, TestIds.userId(), Instant.parse("2026-01-02T00:00:00Z"));

            List<Callable<Integer>> unlikes = IntStream
                    .range(0, PARALLEL_REQUESTS)
                    .<Callable<Integer>>mapToObj(i -> () -> unlike(userId, tweetId))
                    .toList();
            List<Integer> statuses = runTogether(unlikes);

            assertThat(statuses).hasSize(PARALLEL_REQUESTS).containsOnly(204);
            assertThat(likersOf(tweetId)).isEmpty();
            assertThat(likesOf(tweetId)).isZero();
        }
    }

    private UUID existingTweet() {
        UUID tweetId = TestIds.tweetId();
        String tweet = objectMapper.writeValueAsString(Map.of(
                "id", tweetId,
                "authorId", TestIds.userId(),
                "content", "hello",
                "createdAt", TWEET_CREATED_AT.toString(),
                "updatedAt", TWEET_CREATED_AT.toString(),
                "images", List.of()));
        TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH))
                .withQueryParam("ids", WireMock.equalTo(tweetId.toString()))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" + tweet + "]")));

        return tweetId;
    }

    private int like(UUID userId, UUID tweetId) throws Exception {
        return mockMvc
                .perform(put(LIKES_PATH + tweetId).header(USER_ID_HEADER, userId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int unlike(UUID userId, UUID tweetId) throws Exception {
        return mockMvc
                .perform(delete(LIKES_PATH + tweetId).header(USER_ID_HEADER, userId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
