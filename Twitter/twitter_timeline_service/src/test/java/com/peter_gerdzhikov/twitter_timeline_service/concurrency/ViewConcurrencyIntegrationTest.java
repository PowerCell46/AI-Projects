package com.peter_gerdzhikov.twitter_timeline_service.concurrency;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.peter_gerdzhikov.twitter_timeline_service.support.LatchedTasks.runTogether;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.client.WireMock;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_timeline_service.support.AbstractListenerIntegrationTest;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

/**
 * Every scenario releases all its requests from one latch, so they really overlap, and asserts only the final
 * state and the statuses, never an interleaving. The requests go through the HTTP layer, so the statuses are
 * the ones a client sees, except where two reports must meet inside the database: the tweet-service call in
 * front of each request spreads them apart, so that scenario calls the recording service directly.
 */
class ViewConcurrencyIntegrationTest extends AbstractListenerIntegrationTest {

    private static final String VIEWS_PATH = "/api/v1/views";

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String TWEETS_PATH = "/internal/v1/tweets";

    private static final int PARALLEL_REPORTS = 50;

    private static final int REPORTS_PER_WORKER = 100;

    private static final int OVERLAPPING_TWEETS = 200;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<String> tweetsHeldByTheTweetService = new ArrayList<>();

    @Nested
    class ParallelViewers {

        @Test
        void should_count_fifty_and_answer_204_to_every_request_when_fifty_viewers_report_one_tweet_in_parallel() throws Exception {
            UUID tweetId = existingTweet();
            stubTweets();

            List<Callable<Integer>> reports = IntStream
                    .range(0, PARALLEL_REPORTS)
                    .<Callable<Integer>>mapToObj(i -> () -> report(TestIds.userId(), List.of(tweetId)))
                    .toList();
            List<Integer> statuses = runTogether(reports);

            assertThat(statuses).hasSize(PARALLEL_REPORTS).containsOnly(204);
            assertThat(viewsOf(tweetId)).isEqualTo(PARALLEL_REPORTS);
            assertThat(viewersOf(tweetId)).hasSize(PARALLEL_REPORTS);
        }
    }

    @Nested
    class OneViewerReportingRepeatedly {

        @Test
        void should_count_one_and_answer_204_to_every_request_when_one_viewer_reports_one_tweet_fifty_times_in_parallel() throws Exception {
            UUID viewerId = TestIds.userId();
            UUID tweetId = existingTweet();
            stubTweets();

            List<Callable<Integer>> reports = IntStream
                    .range(0, PARALLEL_REPORTS)
                    .<Callable<Integer>>mapToObj(i -> () -> report(viewerId, List.of(tweetId)))
                    .toList();
            List<Integer> statuses = runTogether(reports);

            assertThat(statuses).hasSize(PARALLEL_REPORTS).containsOnly(204);
            assertThat(viewsOf(tweetId)).isEqualTo(1);
            assertThat(viewersOf(tweetId)).containsExactly(viewerId);
        }
    }

    @Nested
    class OverlappingIds {

        @Test
        void should_finish_both_and_count_each_tweet_once_per_viewer_when_two_workers_report_the_same_ids_in_opposite_order() throws Exception {
            List<UUID> forward = IntStream
                    .range(0, OVERLAPPING_TWEETS)
                    .mapToObj(i -> TestIds.tweetId())
                    .toList();
            List<UUID> backward = new ArrayList<>(forward);
            Collections.reverse(backward);
            seedViews(forward);

            runTogether(List.of(
                    () -> reportRepeatedly(forward),
                    () -> reportRepeatedly(backward)));

            long expectedViews = 1 + 2L * REPORTS_PER_WORKER;
            forward.forEach(tweetId -> assertThat(viewsOf(tweetId)).isEqualTo(expectedViews));
        }
    }

    /**
     * Gives every tweet a counter first, so the two workers contend for rows that already exist.
     */
    private void seedViews(List<UUID> tweetIds) {
        viewRecordingService.record(TestIds.userId(), tweetIds);
    }

    private int reportRepeatedly(List<UUID> tweetIds) {
        for (int report = 0; report < REPORTS_PER_WORKER; report++) {
            viewRecordingService.record(TestIds.userId(), tweetIds);
        }

        return REPORTS_PER_WORKER;
    }

    private UUID existingTweet() {
        UUID tweetId = TestIds.tweetId();
        tweetsHeldByTheTweetService.add(objectMapper.writeValueAsString(Map.of(
                "id", tweetId,
                "authorId", TestIds.userId(),
                "content", "hello",
                "createdAt", TWEET_CREATED_AT.toString(),
                "updatedAt", TWEET_CREATED_AT.toString(),
                "images", List.of())));

        return tweetId;
    }

    private void stubTweets() {
        TWEET_SERVICE_STUB.register(WireMock.get(urlPathEqualTo(TWEETS_PATH)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("[" + String.join(",", tweetsHeldByTheTweetService) + "]")));
    }

    private int report(UUID viewerId, List<UUID> tweetIds) throws Exception {
        return mockMvc
                .perform(post(VIEWS_PATH)
                        .header(USER_ID_HEADER, viewerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("tweetIds", tweetIds))))
                .andReturn()
                .getResponse()
                .getStatus();
    }
}
