package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.AuthService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.follows.FollowService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.outbox.OutboxPublisherService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUser;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;

@SpringBootTest
@ActiveProfiles("test")
class OutboxPublisherServiceKafkaIntegrationTest extends AbstractMinioIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    @Value("${app.outbox.max-attempts}")
    private int maxAttempts;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Value("${app.kafka.user-confirmation-requested.name}")
    private String topicName;

    @Value("${app.kafka.user-unfollowed.name}")
    private String unfollowedTopicName;

    @Autowired
    private AuthService authService;

    @Autowired
    private FollowService followService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private OutboxPublisherService outboxPublisherService;

    @Test
    void should_publish_a_pending_row_with_its_key_and_exact_payload_then_delete_it() {
        String key = UUID.randomUUID().toString();
        String payload = "{\"marker\":\"" + key + "\"}";
        Outbox row = outboxRepository.save(pendingRow(key, payload));

        outboxPublisherService.publishPending();

        ConsumerRecord<String, String> record = recordForKey(key);
        assertThat(record.value()).isEqualTo(payload);
        assertThat(outboxRepository.findById(row.getId())).isEmpty();
    }

    @Test
    void should_never_publish_a_failed_row() {
        String key = UUID.randomUUID().toString();
        Outbox failed = pendingRow(key, "{}");
        failed.setStatus(OutboxStatus.FAILED);
        failed = outboxRepository.save(failed);
        String marker = UUID.randomUUID().toString();
        outboxRepository.save(pendingRow(marker, "{}"));

        outboxPublisherService.publishPending();

        assertThat(recordForKey(marker)).isNotNull();
        assertThat(outboxRepository.findById(failed.getId())).isPresent();
        assertThat(consumeAll(key)).isEmpty();
    }

    @Test
    void should_keep_a_row_pending_with_no_attempt_counted_while_kafka_is_unreachable_then_publish_it_when_kafka_is_back() {
        String key = UUID.randomUUID().toString();
        Outbox row = outboxRepository.save(pendingRow(key, "{}"));

        publishWhileKafkaIsUnreachable(maxAttempts + 2);

        Outbox afterTheOutage = outboxRepository.findById(row.getId()).orElseThrow();
        assertThat(afterTheOutage.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(afterTheOutage.getAttempts()).isZero();

        outboxPublisherService.publishPending();

        assertThat(recordForKey(key).value()).isEqualTo("{}");
        assertThat(outboxRepository.findById(row.getId())).isEmpty();
    }

    @Test
    void should_mark_a_rejected_row_failed_after_the_max_attempts_and_still_publish_the_rows_after_it() {
        Outbox rejected = outboxRepository.save(pendingRowOn("not a valid topic name", UUID.randomUUID().toString(), "{}"));
        String healthyKey = UUID.randomUUID().toString();
        outboxRepository.save(pendingRow(healthyKey, "{}"));

        for (int poll = 0; poll < maxAttempts; poll++) {
            outboxPublisherService.publishPending();
        }

        Outbox afterwards = outboxRepository.findById(rejected.getId()).orElseThrow();
        assertThat(afterwards.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(afterwards.getAttempts()).isEqualTo(maxAttempts);
        assertThat(recordForKey(healthyKey)).isNotNull();
    }

    @Test
    void should_put_the_registration_event_on_the_topic_keyed_by_the_user_id() throws Exception {
        TestUser credentials = TestUsers.unique();
        User user = authService.register(RegisterRequestDTO.builder()
                .email(credentials.getEmail())
                .username(credentials.getUsername())
                .password(credentials.getPassword())
                .build());

        outboxPublisherService.publishPending();

        ConsumerRecord<String, String> record = recordForKey(user.getId().toString());
        assertThat(objectMapper.readTree(record.value()).get("email").asString()).isEqualTo(credentials.getEmail());
    }

    @Test
    void should_put_the_unfollowed_event_on_its_topic_keyed_by_the_follower_id() throws Exception {
        User follower = confirmedUser();
        User target = confirmedUser();
        followService.follow(follower.getId(), target.getUsername());
        followService.unfollow(follower.getId(), target.getUsername());

        outboxPublisherService.publishPending();

        JsonNode payload = objectMapper.readTree(
                recordForKey(unfollowedTopicName, follower.getId().toString()).value());
        assertThat(payload.get("followerId").asString()).isEqualTo(follower.getId().toString());
        assertThat(payload.get("followeeId").asString()).isEqualTo(target.getId().toString());
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }

    private Outbox pendingRow(String key, String payload) {
        return pendingRowOn(topicName, key, payload);
    }

    private Outbox pendingRowOn(String topic, String key, String payload) {
        return Outbox.builder()
                .topic(topic)
                .messageKey(key)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();
    }

    private void publishWhileKafkaIsUnreachable(int polls) {
        DefaultKafkaProducerFactory<String, String> unreachable = new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 300));
        OutboxPublisherServiceImpl publisherWhileKafkaIsDown = new OutboxPublisherServiceImpl(
                batchSize, maxAttempts, Duration.ofSeconds(5), outboxRepository, new KafkaTemplate<>(unreachable));

        try {
            for (int poll = 0; poll < polls; poll++) {
                publisherWhileKafkaIsDown.publishPending();
            }

        } finally {
            unreachable.destroy();
        }
    }

    private ConsumerRecord<String, String> recordForKey(String key) {
        return recordForKey(topicName, key);
    }

    private ConsumerRecord<String, String> recordForKey(String topic, String key) {
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = newConsumer(topic)) {
            await().atMost(AWAIT_TIMEOUT).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (key.equals(record.key())) {
                        matches.add(record);
                    }
                });
                return !matches.isEmpty();
            });
        }

        return matches.getFirst();
    }

    private List<ConsumerRecord<String, String>> consumeAll(String key) {
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = newConsumer(topicName)) {
            consumer.poll(Duration.ofSeconds(2)).forEach(record -> {
                if (key.equals(record.key())) {
                    matches.add(record);
                }
            });
        }

        return matches;
    }

    private KafkaConsumer<String, String> newConsumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
