package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.TweetService;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestImages;

import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;

/**
 * Every scenario releases all its threads from one latch, so the requests really overlap, and asserts only
 * the final state, never an interleaving.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TweetConcurrencyIntegrationTest extends AbstractMinioIntegrationTest {

    private static final int TIMEOUT_SECONDS = 60;

    private static final int EDIT_DELETE_ROUNDS = 50;

    private static final String USER_ID_HEADER = "X-User-Id";

    @Value("${app.minio.bucket}")
    private String bucket;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TweetService tweetService;

    @Autowired
    private TweetRepository tweetRepository;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Nested
    class Views {

        @Test
        void should_count_every_view_and_hand_out_distinct_counts_when_fifty_reads_overlap() throws Exception {
            UUID authorId = UUID.randomUUID();
            UUID tweetId = tweetService.create(authorId, "popular", null).getId();

            List<Callable<Long>> reads = IntStream
                    .range(0, 50)
                    .<Callable<Long>>mapToObj(i -> () -> viewsFromGet(tweetId))
                    .toList();
            List<Long> counts = runTogether(reads);

            assertThat(tweetRepository.findById(tweetId).orElseThrow().getViews()).isEqualTo(50);
            assertThat(Set.copyOf(counts)).hasSize(50);
            assertThat(counts).containsExactlyInAnyOrderElementsOf(expectedCounts());
        }
    }

    @Nested
    class Deletes {

        @Test
        void should_answer_one_204_and_nineteen_404s_and_write_one_event_when_twenty_deletes_overlap() throws Exception {
            UUID authorId = UUID.randomUUID();
            TweetResponseDTO created = tweetService.create(
                    authorId, "doomed", List.of(image(), image()));
            List<String> objectKeys = tweetRepository
                    .findById(created.getId())
                    .orElseThrow()
                    .getImages()
                    .stream()
                    .map(TweetImage::getObjectKey)
                    .toList();

            List<Callable<Integer>> deletes = IntStream
                    .range(0, 20)
                    .<Callable<Integer>>mapToObj(i -> () -> deleteStatus(created.getId(), authorId))
                    .toList();
            List<Integer> statuses = runTogether(deletes);

            assertThat(statuses.stream().filter(status -> status == 204)).hasSize(1);
            assertThat(statuses.stream().filter(status -> status == 404)).hasSize(19);
            assertThat(tweetRepository.findById(created.getId())).isEmpty();
            assertThat(deletedEventsFor(created.getId())).hasSize(1);
            for (String objectKey : objectKeys) {
                assertThat(objectExists(objectKey)).isFalse();
            }
        }
    }

    @Nested
    class Edits {

        @Test
        void should_end_with_no_tweet_and_never_answer_500_when_an_edit_races_a_delete_fifty_times() throws Exception {
            for (int round = 0; round < EDIT_DELETE_ROUNDS; round++) {
                UUID authorId = UUID.randomUUID();
                UUID tweetId = tweetService.create(authorId, "before", null).getId();

                List<Integer> statuses = runTogether(List.of(
                        () -> editStatus(tweetId, authorId, "edited"),
                        () -> deleteStatus(tweetId, authorId)));

                assertThat(statuses.get(0)).isIn(200, 404);
                assertThat(statuses.get(1)).isEqualTo(204);
                assertThat(tweetRepository.findById(tweetId)).isEmpty();
                assertThat(deletedEventsFor(tweetId)).hasSize(1);
            }
        }

        @Test
        void should_answer_200_to_every_edit_and_keep_one_of_the_contents_when_twenty_edits_overlap() throws Exception {
            UUID authorId = UUID.randomUUID();
            UUID tweetId = tweetService.create(authorId, "start", null).getId();
            List<String> contents = IntStream
                    .range(0, 20)
                    .mapToObj(i -> "edit-" + i)
                    .toList();

            List<Callable<Integer>> edits = contents
                    .stream()
                    .<Callable<Integer>>map(content -> () -> editStatus(tweetId, authorId, content))
                    .toList();
            List<Integer> statuses = runTogether(edits);

            assertThat(statuses).containsOnly(200);
            Tweet stored = tweetRepository.findById(tweetId).orElseThrow();
            assertThat(contents).contains(stored.getContent());
        }
    }

    private List<Long> expectedCounts() {
        return IntStream
                .rangeClosed(1, 50)
                .mapToObj(Long::valueOf)
                .toList();
    }

    private long viewsFromGet(UUID tweetId) throws Exception {
        String body = mockMvc
                .perform(get("/api/v1/tweets/" + tweetId).header(USER_ID_HEADER, UUID.randomUUID().toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("views").asLong();
    }

    private int deleteStatus(UUID tweetId, UUID callerId) throws Exception {
        return mockMvc
                .perform(delete("/api/v1/tweets/" + tweetId).header(USER_ID_HEADER, callerId.toString()))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int editStatus(UUID tweetId, UUID callerId, String content) throws Exception {
        return mockMvc
                .perform(put("/api/v1/tweets/" + tweetId)
                        .header(USER_ID_HEADER, callerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("images", "a.png", MediaType.IMAGE_PNG_VALUE, TestImages.png());
    }

    private List<OutboxMessage> deletedEventsFor(UUID tweetId) {
        return outboxMessageRepository
                .findAll()
                .stream()
                .filter(message -> tweetId.toString().equals(message.getMessageKey()))
                .filter(message -> "tweet.deleted".equals(message.getTopic()))
                .toList();
    }

    private boolean objectExists(String objectKey) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());

            return true;

        } catch (ErrorResponseException e) {
            return false;
        }
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
