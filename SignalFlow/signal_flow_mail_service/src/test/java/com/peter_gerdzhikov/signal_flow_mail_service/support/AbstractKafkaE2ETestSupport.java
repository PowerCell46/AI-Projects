package com.peter_gerdzhikov.signal_flow_mail_service.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import tools.jackson.databind.json.JsonMapper;

import org.awaitility.Awaitility;

import org.springframework.kafka.support.KafkaHeaders;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Raw Kafka publish/DLT helpers shared by every notification e2e test, independent of whether the test
 * also needs Mailpit - {@code SmtpUnreachableIntegrationTest} needs Kafka and Redis only, its own context
 * with SMTP pointed at a closed port instead of a real Mailpit.
 */
public abstract class AbstractKafkaE2ETestSupport extends AbstractRedisIntegrationTest {

    protected static final String NOTIFICATION_REQUESTED_TOPIC = "topic-news.notification-requested";

    protected static final String NOTIFICATION_REQUESTED_DLT_TOPIC = "topic-news.notification-requested-dlt";

    private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

    private static final KafkaProducer<String, String> RAW_PRODUCER = new KafkaProducer<>(Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));

    protected static String uniqueRecipient() {
        return UUID.randomUUID() + "@example.com";
    }

    protected static TopicNewsNotificationEventDTO aValidEvent(String emailAddress) {
        return new TopicNewsNotificationEventDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Topic",
                "Category",
                LocalDate.of(2026, 9, 24),
                "<p>Body</p>",
                Instant.parse("2026-09-24T10:00:00Z"),
                UUID.randomUUID(),
                emailAddress);
    }

    protected static String toJson(TopicNewsNotificationEventDTO event) {
        return OBJECT_MAPPER.writeValueAsString(event);
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

    /**
     * Publishes directly to the DLT topic with a {@code kafka_dlt-exception-cause-fqcn} header, simulating
     * a record that already dead-lettered under that failure class - lets {@code DltReplayIntegrationTest}
     * exercise the replay classification without re-triggering a real failure through the whole pipeline.
     */
    protected static void publishToDlt(String key, String value, String exceptionCauseFqcn) {
        publishToDlt(NOTIFICATION_REQUESTED_DLT_TOPIC, key, value, exceptionCauseFqcn);
    }

    protected static void publishToDlt(String dltTopic, String key, String value, String exceptionCauseFqcn) {
        List<Header> headers = List.of(
                new RecordHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN, exceptionCauseFqcn.getBytes(StandardCharsets.UTF_8)));

        try {
            RAW_PRODUCER.send(new ProducerRecord<>(dltTopic, null, key, value, headers)).get(10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);

        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static void awaitDltRecordForKey(String kafkaKey) {
        awaitDltRecordForKey(NOTIFICATION_REQUESTED_DLT_TOPIC, kafkaKey);
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
            List<ConsumerRecord<String, byte[]>> matches = new ArrayList<>();

            Awaitility.await()
                    .atMost(Duration.ofSeconds(20))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> {
                        consumer.poll(Duration.ofMillis(50)).forEach(matches::add);
                        assertTrue(matches.stream().anyMatch(record -> kafkaKey.equals(record.key())),
                                "Expected a DLT record for key " + kafkaKey);
                    });
        }
    }

    protected static String redisKey(UUID newsId, UUID userId) {
        return "mail:notification:" + newsId + ":" + userId;
    }
}
