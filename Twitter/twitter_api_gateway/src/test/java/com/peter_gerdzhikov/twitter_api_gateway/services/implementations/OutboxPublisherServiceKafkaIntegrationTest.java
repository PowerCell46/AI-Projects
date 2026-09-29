package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.AuthService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.OutboxPublisherService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUser;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;

@SpringBootTest
@ActiveProfiles("test")
class OutboxPublisherServiceKafkaIntegrationTest extends AbstractMinioIntegrationTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(20);

    @Value("${app.kafka.user-confirmation-requested.name}")
    private String topicName;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

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

    private Outbox pendingRow(String key, String payload) {
        return Outbox.builder()
                .topic(topicName)
                .messageKey(key)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .build();
    }

    private ConsumerRecord<String, String> recordForKey(String key) {
        List<ConsumerRecord<String, String>> matches = new ArrayList<>();

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
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

        try (KafkaConsumer<String, String> consumer = newConsumer()) {
            consumer.poll(Duration.ofSeconds(2)).forEach(record -> {
                if (key.equals(record.key())) {
                    matches.add(record);
                }
            });
        }

        return matches;
    }

    private KafkaConsumer<String, String> newConsumer() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, UUID.randomUUID().toString());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topicName));
        return consumer;
    }
}
