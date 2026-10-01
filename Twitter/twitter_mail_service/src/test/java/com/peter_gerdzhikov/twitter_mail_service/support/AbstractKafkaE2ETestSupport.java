package com.peter_gerdzhikov.twitter_mail_service.support;

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

import tools.jackson.databind.json.JsonMapper;

import org.awaitility.Awaitility;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Raw Kafka publish helpers shared by every e2e test, independent of whether the test also needs Mailpit -
 * {@code SmtpUnreachableIntegrationTest} needs Kafka and Redis only, its own context with SMTP pointed at a
 * closed port instead of a real Mailpit.
 */
public abstract class AbstractKafkaE2ETestSupport extends AbstractRedisIntegrationTest {

    protected static final String USER_CONFIRMATION_REQUESTED_TOPIC = "user.confirmation-requested";

    protected static final String USER_CONFIRMATION_REQUESTED_DLT_TOPIC = "user.confirmation-requested-dlt";

    protected static final String USER_FOLLOWED_TOPIC = "user.followed";

    protected static final String USER_FOLLOWED_DLT_TOPIC = "user.followed-dlt";

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private static final KafkaProducer<String, String> RAW_PRODUCER = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));

    protected static String uniqueRecipient() {
        return UUID.randomUUID() + "@example.com";
    }

    protected static UserConfirmationRequestedEventDTO aValidEvent(String email, Instant expiresAt) {
        return aValidEvent(UUID.randomUUID(), email, expiresAt);
    }

    protected static UserConfirmationRequestedEventDTO aValidEvent(UUID userId, String email, Instant expiresAt) {
        return UserConfirmationRequestedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .userId(userId)
                .email(email)
                .username("ana_k")
                .confirmationUrl("http://localhost:5173/confirm?token=" + UUID.randomUUID())
                .expiresAt(expiresAt)
                .build();
    }

    protected static UserFollowedEventDTO aValidFollowEvent(UUID followerId, UUID followeeId, String followeeEmail) {
        return UserFollowedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .followerId(followerId)
                .followeeId(followeeId)
                .occurredAt(Instant.parse("2026-10-01T12:00:00Z"))
                .followeeEmail(followeeEmail)
                .followerUsername("ana")
                .followeeUsername("bob")
                .build();
    }

    protected static String toJson(UserConfirmationRequestedEventDTO event) {
        return OBJECT_MAPPER.writeValueAsString(event);
    }

    protected static String toJson(UserFollowedEventDTO event) {
        return OBJECT_MAPPER.writeValueAsString(event);
    }

    protected static String confirmationRedisKey(UUID eventId) {
        return "mail:confirmation:" + eventId;
    }

    protected static String followRedisKey(UUID followerId, UUID followeeId) {
        return "mail:followed:" + followerId + ":" + followeeId;
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

    protected static void awaitDltRecordForKey(String kafkaKey) {
        awaitDltRecordForKey(USER_CONFIRMATION_REQUESTED_DLT_TOPIC, kafkaKey);
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
                    .atMost(Duration.ofSeconds(20))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(50)).forEach(records::add);
                        assertTrue(records.stream().anyMatch(record -> kafkaKey.equals(record.key())),
                                "Expected a DLT record for key " + kafkaKey);
                    });
        }
    }
}
