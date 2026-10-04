package com.peter_gerdzhikov.twitter_timeline_service.support;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.awaitility.Awaitility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import tools.jackson.databind.json.JsonMapper;

import lombok.Value;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;

import com.peter_gerdzhikov.twitter_timeline_service.entities.feed.FeedEntry;
import com.peter_gerdzhikov.twitter_timeline_service.entities.savedtweets.SavedTweet;
import com.peter_gerdzhikov.twitter_timeline_service.entities.views.TweetView;
import com.peter_gerdzhikov.twitter_timeline_service.entities.views.TweetViewCount;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.SavedTweetRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewRecordingService;

/**
 * What every listener suite needs: a raw producer for the three input topics, the events written the way their
 * producers write them, a way to read a dead-letter topic, the gateway's follower pages stubbed, and the feed
 * read back from the database. Every helper is keyed by the test's own ids, so tests never see each other's data.
 */
public abstract class AbstractListenerIntegrationTest extends AbstractDownstreamIntegrationTest {

    protected static final String TWEET_CREATED_TOPIC = "tweet.created";

    protected static final String TWEET_CREATED_DLT_TOPIC = "tweet.created-dlt";

    protected static final String TWEET_DELETED_TOPIC = "tweet.deleted";

    protected static final String USER_UNFOLLOWED_TOPIC = "user.unfollowed";

    protected static final String USER_UNFOLLOWED_DLT_TOPIC = "user.unfollowed-dlt";

    protected static final String USER_FOLLOWED_TOPIC = "user.followed";

    protected static final String USER_FOLLOWED_DLT_TOPIC = "user.followed-timeline-dlt";

    protected static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    protected static final Instant TWEET_CREATED_AT = Instant.parse("2026-01-01T00:00:00.123Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final KafkaProducer<String, String> RAW_PRODUCER = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));

    @Autowired
    protected FeedEntryRepository feedEntryRepository;

    @Autowired
    protected SavedTweetRepository savedTweetRepository;

    @Autowired
    protected TweetViewRepository tweetViewRepository;

    @Autowired
    protected ViewRecordingService viewRecordingService;

    @Autowired
    protected TweetViewCountRepository tweetViewCountRepository;

    /**
     * The event as the tweet service writes it, with the text and image ids this service ignores.
     */
    protected static String tweetCreatedJson(UUID tweetId, UUID authorId, Instant createdAt) {
        return JSON.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID(),
                "tweetId", tweetId,
                "authorId", authorId,
                "content", "hello",
                "createdAt", createdAt.toString(),
                "imageIds", List.of(UUID.randomUUID())));
    }

    protected static String tweetDeletedJson(UUID tweetId, UUID authorId) {
        return JSON.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID(),
                "tweetId", tweetId,
                "authorId", authorId,
                "deletedAt", TWEET_CREATED_AT.plusSeconds(60).toString()));
    }

    protected static String userUnfollowedJson(UUID followerId, UUID followeeId, Instant occurredAt) {
        return JSON.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID(),
                "followerId", followerId,
                "followeeId", followeeId,
                "occurredAt", occurredAt.toString()));
    }

    /**
     * The event as the gateway writes it, with the followee's email and the usernames this service must ignore.
     */
    protected static String userFollowedJson(UUID followerId, UUID followeeId, Instant occurredAt) {
        return JSON.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID(),
                "followerId", followerId,
                "followeeId", followeeId,
                "occurredAt", occurredAt.toString(),
                "followeeEmail", "bob@example.com",
                "followerUsername", "ana",
                "followeeUsername", "bob"));
    }

    protected static void publish(String topic, String key, String value) {
        try {
            RAW_PRODUCER.send(new ProducerRecord<>(topic, key, value)).get(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static void awaitDltRecordForKey(String dltTopic, String kafkaKey) {
        Map<String, Object> consumerProperties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-check-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(consumerProperties)) {
            consumer.subscribe(List.of(dltTopic));
            List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();

            Awaitility.await()
                    .atMost(AWAIT_TIMEOUT)
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(50)).forEach(records::add);
                        assertThat(records)
                                .as("a record on %s with key %s", dltTopic, kafkaKey)
                                .anyMatch(record -> kafkaKey.equals(record.key()));
                    });
        }
    }

    protected static UrlPathPattern followerIdsPath(UUID authorId) {
        return urlPathEqualTo("/internal/v1/users/" + authorId + "/follower-ids");
    }

    /**
     * Stubs the gateway's follower pages for an author: the first without a cursor, each later one under the
     * cursor {@code cursor-<n>} the page before it returned, the last returning no cursor.
     */
    protected static void stubFollowerPages(UUID authorId, List<List<UUID>> pages) {
        for (int index = 0; index < pages.size(); index++) {
            String nextCursor = index == pages.size() - 1 ? null : "cursor-" + (index + 1);
            GATEWAY_STUB.register(get(followerIdsPath(authorId))
                    .withQueryParam("cursor", index == 0 ? absent() : equalTo("cursor-" + index))
                    .willReturn(followerPage(pages.get(index), nextCursor)));
        }
    }

    protected static ResponseDefinitionBuilder followerPage(List<UUID> ids, String nextCursor) {
        String idList = String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList());
        String cursor = nextCursor == null ? "null" : "\"" + nextCursor + "\"";

        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"ids\":[" + idList + "],\"nextCursor\":" + cursor + "}");
    }

    protected static UrlPathPattern tweetsByAuthorPath(UUID authorId) {
        return urlPathEqualTo("/internal/v1/tweets/by-author/" + authorId);
    }

    protected static UrlPathPattern followCheckPath(UUID followerId, UUID followeeId) {
        return urlPathEqualTo("/internal/v1/users/" + followerId + "/follows/" + followeeId);
    }

    /**
     * Stubs the tweet service's by-author read with the tweets as given, so the caller lists them newest first.
     */
    protected static void stubTweetsByAuthor(UUID authorId, List<AuthorTweet> tweets) {
        String items = String.join(",", tweets
                .stream()
                .map(tweet -> "{\"id\":\"" + tweet.getId() + "\",\"createdAt\":\"" + tweet.getCreatedAt() + "\"}")
                .toList());

        TWEET_SERVICE_STUB.register(get(tweetsByAuthorPath(authorId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[" + items + "]")));
    }

    protected static void stubFollowExists(UUID followerId, UUID followeeId) {
        stubFollowCheck(followerId, followeeId, true);
    }

    protected static void stubFollowMissing(UUID followerId, UUID followeeId) {
        stubFollowCheck(followerId, followeeId, false);
    }

    private static void stubFollowCheck(UUID followerId, UUID followeeId, boolean isFollowing) {
        GATEWAY_STUB.register(get(followCheckPath(followerId, followeeId))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"following\":" + isFollowing + "}")));
    }

    protected static List<UUID> newUsers(int count) {
        List<UUID> users = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            users.add(TestIds.userId());
        }

        return users;
    }

    protected void seedEntry(UUID userId, UUID tweetId, UUID authorId, Instant tweetCreatedAt) {
        feedEntryRepository.insertIfAbsent(new UUID[]{userId}, tweetId, authorId, tweetCreatedAt);
    }

    protected List<UUID> tweetIdsInFeedOf(UUID userId) {
        return feedEntryRepository
                .findFirstPage(userId, PageRequest.of(0, 100))
                .stream()
                .map(FeedEntry::getTweetId)
                .toList();
    }

    protected void awaitFeedHolds(UUID userId, UUID tweetId) {
        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(tweetIdsInFeedOf(userId)).contains(tweetId));
    }

    protected void awaitFeedLacks(UUID userId, UUID tweetId) {
        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(tweetIdsInFeedOf(userId)).doesNotContain(tweetId));
    }

    protected void seedSavedTweet(UUID userId, UUID tweetId, UUID authorId, Instant savedAt) {
        savedTweetRepository.insertIfAbsent(userId, tweetId, authorId, savedAt);
    }

    protected List<UUID> tweetIdsSavedBy(UUID userId) {
        return savedTweetRepository
                .findFirstPage(userId, PageRequest.of(0, 100))
                .stream()
                .map(SavedTweet::getTweetId)
                .toList();
    }

    protected void awaitSavedLacks(UUID userId, UUID tweetId) {
        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(tweetIdsSavedBy(userId)).doesNotContain(tweetId));
    }

    /**
     * A view recorded the way a report records it, so the counter and the view rows agree.
     */
    protected void seedView(UUID viewerId, UUID tweetId) {
        viewRecordingService.record(viewerId, List.of(tweetId));
    }

    protected void seedViews(UUID tweetId, int viewers) {
        newUsers(viewers).forEach(viewerId -> seedView(viewerId, tweetId));
    }

    protected long viewsOf(UUID tweetId) {
        return tweetViewCountRepository
                .findById(tweetId)
                .map(TweetViewCount::getViews)
                .orElse(0L);
    }

    protected List<UUID> viewersOf(UUID tweetId) {
        return tweetViewRepository
                .findAll()
                .stream()
                .filter(view -> view.getTweetId().equals(tweetId))
                .map(TweetView::getViewerId)
                .toList();
    }

    protected void awaitViewsGone(UUID tweetId) {
        Awaitility.await()
                .atMost(AWAIT_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    assertThat(viewersOf(tweetId)).isEmpty();
                    assertThat(tweetViewCountRepository.existsById(tweetId)).isFalse();
                });
    }

    @Value
    protected static class AuthorTweet {

        private final UUID id;

        private final Instant createdAt;
    }
}
