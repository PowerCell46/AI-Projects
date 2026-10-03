package com.peter_gerdzhikov.twitter_timeline_service.concurrency;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.peter_gerdzhikov.twitter_timeline_service.support.LatchedTasks.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
class SavedTweetConcurrencyIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String SAVED_TWEETS_PATH = "/api/v1/saved-tweets/";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int PARALLEL_SAVES = 50;

    private static final int RACE_ROUNDS = 20;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    class ParallelSaves {

        @Test
        void should_keep_one_row_and_answer_204_to_every_request_when_one_user_saves_one_tweet_in_parallel() throws Exception {
            UUID userId = TestIds.userId();
            UUID tweetId = existingTweet(TestIds.userId());

            List<Callable<Integer>> saves = IntStream
                    .range(0, PARALLEL_SAVES)
                    .<Callable<Integer>>mapToObj(i -> () -> save(userId, tweetId))
                    .toList();
            List<Integer> statuses = runTogether(saves);

            assertThat(statuses).hasSize(PARALLEL_SAVES).containsOnly(204);
            assertThat(tweetIdsSavedBy(userId)).containsExactly(tweetId);
        }
    }

    @Nested
    class SaveRacingUnsave {

        @Test
        void should_end_with_the_row_present_or_absent_and_never_fail_when_a_save_races_an_unsave() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                UUID userId = TestIds.userId();
                UUID tweetId = existingTweet(TestIds.userId());

                List<Integer> statuses = runTogether(List.of(() -> save(userId, tweetId), () -> unsave(userId, tweetId)));

                assertThat(statuses).containsExactly(204, 204);
                assertThat(tweetIdsSavedBy(userId)).isSubsetOf(tweetId);
            }
        }

        @Test
        void should_leave_the_row_gone_when_the_unsave_is_repeated_after_the_race() throws Exception {
            UUID userId = TestIds.userId();
            UUID tweetId = existingTweet(TestIds.userId());
            runTogether(List.of(() -> save(userId, tweetId), () -> unsave(userId, tweetId)));

            unsave(userId, tweetId);

            assertThat(tweetIdsSavedBy(userId)).isEmpty();
        }
    }

    private UUID existingTweet(UUID authorId) {
        UUID tweetId = TestIds.tweetId();
        String tweet = objectMapper.writeValueAsString(Map.of(
                "id", tweetId,
                "authorId", authorId,
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

    private int save(UUID userId, UUID tweetId) throws Exception {
        return mockMvc
                .perform(put(SAVED_TWEETS_PATH + tweetId).header(USER_ID_HEADER, userId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int unsave(UUID userId, UUID tweetId) throws Exception {
        return mockMvc
                .perform(delete(SAVED_TWEETS_PATH + tweetId).header(USER_ID_HEADER, userId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
