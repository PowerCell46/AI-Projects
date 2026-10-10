package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxPublisherService;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.TweetService;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

/**
 * A negative assertion ("this key never arrived") publishes a sentinel message after the action and waits for
 * it, so the absence is proven against a record that did arrive rather than against a timeout.
 */
@SpringBootTest
@ActiveProfiles("test")
class OutboxPublisherServiceKafkaIntegrationTest extends AbstractMinioIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    @Value("${app.outbox.max-attempts}")
    private int maxAttempts;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Value("${app.kafka.tweet-created.name}")
    private String createdTopic;

    @Value("${app.kafka.tweet-deleted.name}")
    private String deletedTopic;

    @Autowired
    private TweetService tweetService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Nested
    class PublishPending {

        @Test
        void should_publish_a_pending_message_with_its_key_and_exact_payload_then_delete_it() {
            String key = UUID.randomUUID().toString();
            String payload = "{\"marker\":\"" + key + "\"}";
            OutboxMessage message = outboxMessageRepository.save(pendingMessage(createdTopic, key, payload));

            outboxPublisherService.publishPending();

            ConsumerRecord<String, String> record = recordFor(createdTopic, key);
            assertThat(record.value()).isEqualTo(payload);
            assertThat(outboxMessageRepository.findById(message.getId())).isEmpty();
        }

        @Test
        void should_never_publish_a_failed_message() {
            String failedKey = UUID.randomUUID().toString();
            OutboxMessage failed = pendingMessage(createdTopic, failedKey, "{}");
            failed.setStatus(OutboxStatus.FAILED);
            failed = outboxMessageRepository.save(failed);
            String sentinelKey = UUID.randomUUID().toString();
            outboxMessageRepository.save(pendingMessage(createdTopic, sentinelKey, "{}"));

            outboxPublisherService.publishPending();
            outboxPublisherService.publishPending();

            List<ConsumerRecord<String, String>> records = recordsUntilSeen(createdTopic, sentinelKey);
            assertThat(records).noneMatch(record -> failedKey.equals(record.key()));
            assertThat(outboxMessageRepository.findById(failed.getId())).isPresent();
        }
    }

    @Nested
    class Outages {

        @Test
        void should_keep_a_message_pending_with_no_attempt_counted_while_kafka_is_unreachable_then_publish_it_when_kafka_is_back() {
            String key = UUID.randomUUID().toString();
            OutboxMessage message = outboxMessageRepository.save(pendingMessage(createdTopic, key, "{}"));

            publishWhileKafkaIsUnreachable(maxAttempts + 2);

            OutboxMessage afterTheOutage = outboxMessageRepository.findById(message.getId()).orElseThrow();
            assertThat(afterTheOutage.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(afterTheOutage.getAttempts()).isZero();

            outboxPublisherService.publishPending();

            assertThat(recordFor(createdTopic, key).value()).isEqualTo("{}");
            assertThat(outboxMessageRepository.findById(message.getId())).isEmpty();
        }

        @Test
        void should_mark_a_rejected_message_failed_after_the_max_attempts_and_still_publish_the_messages_after_it() {
            OutboxMessage rejected = outboxMessageRepository.save(
                    pendingMessage("not a valid topic name", UUID.randomUUID().toString(), "{}"));
            String healthyKey = UUID.randomUUID().toString();
            outboxMessageRepository.save(pendingMessage(createdTopic, healthyKey, "{}"));

            for (int poll = 0; poll < maxAttempts; poll++) {
                outboxPublisherService.publishPending();
            }

            OutboxMessage afterwards = outboxMessageRepository.findById(rejected.getId()).orElseThrow();
            assertThat(afterwards.getStatus()).isEqualTo(OutboxStatus.FAILED);
            assertThat(afterwards.getAttempts()).isEqualTo(maxAttempts);
            assertThat(recordFor(createdTopic, healthyKey)).isNotNull();
        }
    }

    @Nested
    class EndToEnd {

        @Test
        void should_put_the_created_event_on_the_topic_keyed_by_the_tweet_id_when_a_tweet_is_created() throws Exception {
            UUID authorId = UUID.randomUUID();
            TweetResponseDTO tweet = tweetService.create(authorId, "hello kafka", null);

            outboxPublisherService.publishPending();

            ConsumerRecord<String, String> record = recordFor(createdTopic, tweet.getId().toString());
            JsonNode event = objectMapper.readTree(record.value());
            assertThat(event.get("tweetId").asString()).isEqualTo(tweet.getId().toString());
            assertThat(event.get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(event.get("content").asString()).isEqualTo("hello kafka");
            assertThat(event.get("eventId").asString()).isNotBlank();
            assertThat(event.get("imageIds")).isEmpty();
        }

        @Test
        void should_put_the_deleted_event_on_the_topic_keyed_by_the_tweet_id_when_a_tweet_is_deleted() throws Exception {
            UUID authorId = UUID.randomUUID();
            TweetResponseDTO tweet = tweetService.create(authorId, "bye kafka", null);
            tweetService.delete(authorId, tweet.getId());

            outboxPublisherService.publishPending();

            ConsumerRecord<String, String> record = recordFor(deletedTopic, tweet.getId().toString());
            JsonNode event = objectMapper.readTree(record.value());
            assertThat(event.get("tweetId").asString()).isEqualTo(tweet.getId().toString());
            assertThat(event.get("authorId").asString()).isEqualTo(authorId.toString());
            assertThat(event.get("deletedAt").asString()).isNotBlank();
        }
    }

    @Test
    void should_create_both_topics_with_three_partitions() throws Exception {
        try (AdminClient admin = AdminClient.create(consumerProperties())) {
            var descriptions = admin
                    .describeTopics(List.of(createdTopic, deletedTopic))
                    .allTopicNames()
                    .get();

            assertThat(descriptions.get(createdTopic).partitions()).hasSize(3);
            assertThat(descriptions.get(deletedTopic).partitions()).hasSize(3);
        }
    }

    private OutboxMessage pendingMessage(String topic, String key, String payload) {
        OutboxMessage message = TestDocuments.outboxMessage(OutboxStatus.PENDING, TestDocuments.CREATED_AT);
        message.setTopic(topic);
        message.setMessageKey(key);
        message.setPayload(payload);

        return message;
    }

    private void publishWhileKafkaIsUnreachable(int polls) {
        DefaultKafkaProducerFactory<String, String> unreachable = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 300));
        OutboxPublisherServiceImpl publisherWhileKafkaIsDown = new OutboxPublisherServiceImpl(
                batchSize, maxAttempts, Duration.ofSeconds(5), new KafkaTemplate<>(unreachable), outboxMessageRepository);

        try {
            for (int poll = 0; poll < polls; poll++) {
                publisherWhileKafkaIsDown.publishPending();
            }

        } finally {
            unreachable.destroy();
        }
    }

    private ConsumerRecord<String, String> recordFor(String topic, String key) {
        return recordsUntilSeen(topic, key)
                .stream()
                .filter(record -> key.equals(record.key()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * Everything read up to and including the first record with the given key.
     */
    private List<ConsumerRecord<String, String>> recordsUntilSeen(String topic, String key) {
        List<ConsumerRecord<String, String>> records = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = newConsumer(topic)) {
            await().atMost(AWAIT_TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);

                return records.stream().anyMatch(record -> key.equals(record.key()));
            });
        }

        return records;
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties properties = consumerProperties();
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));

        return consumer;
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());

        return properties;
    }
}
