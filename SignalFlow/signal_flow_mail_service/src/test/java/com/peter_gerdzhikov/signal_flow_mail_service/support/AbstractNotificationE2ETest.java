package com.peter_gerdzhikov.signal_flow_mail_service.support;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared e2e base: Kafka + Redis + Mailpit, a real (spied) {@link JavaMailSender} so send attempts can be
 * counted, and Mailpit-specific await/assert helpers. Every subclass extends this with no extra
 * {@code @SpringBootTest}/{@code @ActiveProfiles}/bean-override configuration of its own, so all of them
 * share one cached Spring context - see reliability rule 5 for why that matters.
 *
 * <p>{@code SmtpUnreachableIntegrationTest} needs its own context (a closed SMTP port), so it extends
 * {@link AbstractKafkaE2ETestSupport} directly instead - the raw Kafka publish/DLT helpers live there so
 * both bases can share them.
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractNotificationE2ETest extends AbstractMailpitIntegrationTest {

    @MockitoSpyBean
    protected JavaMailSender javaMailSender;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearMailpit() {
        MAILPIT_CLIENT.deleteAll();
    }

    protected static JsonNode awaitEmail(String recipientEmail) {
        AtomicReference<JsonNode> found = new AtomicReference<>();
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    JsonNode messages = MAILPIT_CLIENT.searchByRecipient(recipientEmail).get("messages");
                    assertTrue(messages.size() > 0, "Expected an email to " + recipientEmail);
                    found.set(messages.get(0));
                });

        return found.get();
    }

    protected static void assertNoEmail(String recipientEmail) {
        JsonNode messages = MAILPIT_CLIENT.searchByRecipient(recipientEmail).get("messages");
        assertEquals(0, messages.size(), "Expected no email to " + recipientEmail);
    }

    /**
     * Publishes {@code json} under {@code kafkaKey}, then a fresh valid sentinel record under the same
     * key, and awaits the sentinel's own email. Same key means same partition, so this consumer finishes
     * processing {@code json} - including any retries and a DLT publish - strictly before the sentinel;
     * once the sentinel's email is observed, {@code json}'s processing is guaranteed complete, so its own
     * "nothing happened" assertions can be made without racing it.
     */
    protected static void publishThenAwaitSentinelProcessed(String kafkaKey, String json) {
        publish(NOTIFICATION_REQUESTED_TOPIC, kafkaKey, json);

        String sentinelRecipient = uniqueRecipient();
        publish(NOTIFICATION_REQUESTED_TOPIC, kafkaKey, toJson(aValidEvent(sentinelRecipient)));

        awaitEmail(sentinelRecipient);
    }
}
