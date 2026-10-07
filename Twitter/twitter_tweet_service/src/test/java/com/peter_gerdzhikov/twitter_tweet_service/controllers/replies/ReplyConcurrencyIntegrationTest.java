package com.peter_gerdzhikov.twitter_tweet_service.controllers.replies;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractDownstreamIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

/**
 * Every scenario releases all its threads from one latch, so the requests really overlap, and asserts only
 * the final state, never an interleaving.
 */
@AutoConfigureMockMvc
class ReplyConcurrencyIntegrationTest extends AbstractDownstreamIntegrationTest {

    private static final int TIMEOUT_SECONDS = 60;

    private static final int RACE_ROUNDS = 20;

    private static final String USER_ID_HEADER = "X-User-Id";

    private static final String USERS_PATH = "/internal/v1/users";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReplyRepository replyRepository;

    @Autowired
    private TweetRepository tweetRepository;

    private UserClientDTO ana;

    @BeforeEach
    void stubTheCaller() throws Exception {
        ana = UserClientDTO
                .builder()
                .id(UUID.randomUUID())
                .username("ana")
                .build();
        GATEWAY_STUB.register(get(urlPathEqualTo(USERS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(List.of(ana)))));
    }

    @Nested
    class Creates {

        @Test
        void should_answer_201_to_every_create_and_count_twenty_when_twenty_creates_overlap() throws Exception {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            List<Callable<Integer>> creates = IntStream
                    .range(0, 20)
                    .<Callable<Integer>>mapToObj(i -> () -> createStatus(tweet.getId(), "reply-" + i))
                    .toList();
            List<Integer> statuses = runTogether(creates);

            assertThat(statuses).containsOnly(201);
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(20);
            assertThat(replyRepository.findFirstPage(tweet.getId(), 100)).hasSize(20);
        }

        @Test
        void should_leave_no_reply_and_no_tweet_when_ten_creates_race_the_tweet_delete() throws Exception {
            for (int round = 0; round < RACE_ROUNDS; round++) {
                Tweet tweet = tweetRepository.save(TestDocuments.tweet());
                List<Callable<Integer>> tasks = new ArrayList<>();
                IntStream
                        .range(0, 10)
                        .forEach(i -> tasks.add(() -> createStatus(tweet.getId(), "reply-" + i)));
                tasks.add(() -> deleteTweetStatus(tweet));

                List<Integer> statuses = runTogether(tasks);

                assertThat(statuses.subList(0, 10)).isSubsetOf(201, 404);
                assertThat(statuses.get(10)).isEqualTo(204);
                assertThat(tweetRepository.findById(tweet.getId())).isEmpty();
                assertThat(replyRepository.findFirstPage(tweet.getId(), 100)).isEmpty();
            }
        }
    }

    @Nested
    class Deletes {

        @Test
        void should_answer_one_204_and_one_404_and_decrement_once_when_two_deletes_overlap() throws Exception {
            Tweet tweet = tweetWithReplies(1);
            Reply reply = replyRepository.findFirstPage(tweet.getId(), 1).getFirst();

            List<Integer> statuses = runTogether(List.of(
                    () -> deleteReplyStatus(tweet.getId(), reply.getId(), ana.getId()),
                    () -> deleteReplyStatus(tweet.getId(), reply.getId(), tweet.getAuthorId())));

            assertThat(statuses).containsExactlyInAnyOrder(204, 404);
            assertThat(replyRepository.findById(reply.getId())).isEmpty();
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isZero();
        }
    }

    @Nested
    class Churn {

        @Test
        void should_end_with_a_reply_count_equal_to_the_documents_left_when_creates_and_deletes_churn() throws Exception {
            Tweet tweet = tweetWithReplies(10);
            List<Reply> seeded = replyRepository.findFirstPage(tweet.getId(), 100);
            List<Callable<Integer>> tasks = new ArrayList<>();
            IntStream
                    .range(0, 20)
                    .forEach(i -> tasks.add(() -> createStatus(tweet.getId(), "reply-" + i)));
            seeded.forEach(reply -> tasks.add(() -> deleteReplyStatus(tweet.getId(), reply.getId(), ana.getId())));

            List<Integer> statuses = runTogether(tasks);

            assertThat(statuses.subList(0, 20)).containsOnly(201);
            assertThat(statuses.subList(20, 30)).containsOnly(204);
            long documentsLeft = replyRepository.findFirstPage(tweet.getId(), 100).size();
            assertThat(documentsLeft).isEqualTo(20);
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(documentsLeft);
        }
    }

    private Tweet tweetWithReplies(int count) {
        Tweet tweet = tweetRepository.save(TestDocuments.tweet());
        for (int i = 0; i < count; i++) {
            Reply reply = TestDocuments.replyAt(tweet.getId(), TestDocuments.CREATED_AT.plusSeconds(i));
            reply.setAuthorId(ana.getId());
            replyRepository.save(reply);
        }

        if (count > 0) {
            tweetRepository.incrementReplyCount(tweet.getId(), count);
        }

        return tweet;
    }

    private int createStatus(UUID tweetId, String content) throws Exception {
        return mockMvc
                .perform(post("/api/v1/tweets/" + tweetId + "/replies")
                        .header(USER_ID_HEADER, ana.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int deleteReplyStatus(UUID tweetId, UUID replyId, UUID callerId) throws Exception {
        return mockMvc
                .perform(delete("/api/v1/tweets/" + tweetId + "/replies/" + replyId)
                        .header(USER_ID_HEADER, callerId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int deleteTweetStatus(Tweet tweet) throws Exception {
        return mockMvc
                .perform(delete("/api/v1/tweets/" + tweet.getId()).header(USER_ID_HEADER, tweet.getAuthorId().toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    /**
     * Starts every task on its own thread, holds them at a latch, releases them together, and returns the
     * results in task order.
     */
    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();

                    return task.call();
                }));
            }

            ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            start.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return results;

        } finally {
            executor.shutdownNow();
        }
    }
}
