package com.peter_gerdzhikov.signal_flow_mail_service.listeners;

import java.time.Duration;
import java.util.UUID;

import jakarta.mail.internet.MimeMessage;

import tools.jackson.databind.JsonNode;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.support.AbstractNotificationE2ETest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * e2e over real Kafka, Redis and Mailpit containers. See {@code TESTING.md} for the scenario catalog -
 * update it in the same change as any edit here.
 */
class NotificationRequestedListenerIntegrationTest extends AbstractNotificationE2ETest {

    @Test
    void should_send_one_email_on_happy_path() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        String key = event.getUserId().toString();

        publish(NOTIFICATION_REQUESTED_TOPIC, key, toJson(event));

        JsonNode summary = awaitEmail(recipient);
        JsonNode message = MAILPIT_CLIENT.fetchMessage(summary.get("ID").asString());

        assertEquals("[SignalFlow] Topic — 2026-09-24", message.get("Subject").asString());
        assertEquals("signalflow-test@example.com", message.get("From").get("Address").asString());
        String html = message.get("HTML").asString();
        assertTrue(html.contains("Topic"));
        assertTrue(html.contains("Category"));
        assertTrue(html.contains("<p>Body</p>"));
        assertTrue(html.contains("24 September 2026"));
        assertEquals("SENT", redisTemplate.opsForValue().get(redisKey(event.getNewsId(), event.getUserId())));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_send_only_one_email_on_exact_redelivery() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        String key = event.getUserId().toString();
        String json = toJson(event);

        publish(NOTIFICATION_REQUESTED_TOPIC, key, json);
        publish(NOTIFICATION_REQUESTED_TOPIC, key, json);

        awaitEmail(recipient);
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> verify(javaMailSender, times(1)).send(any(MimeMessage.class)));
    }

    @Test
    void should_send_two_emails_for_the_same_news_to_two_users() {
        UUID sharedNewsId = UUID.randomUUID();
        String firstRecipient = uniqueRecipient();
        String secondRecipient = uniqueRecipient();
        TopicNewsNotificationEventDTO first = aValidEvent(firstRecipient);
        first.setNewsId(sharedNewsId);
        TopicNewsNotificationEventDTO second = aValidEvent(secondRecipient);
        second.setNewsId(sharedNewsId);

        publish(NOTIFICATION_REQUESTED_TOPIC, first.getUserId().toString(), toJson(first));
        publish(NOTIFICATION_REQUESTED_TOPIC, second.getUserId().toString(), toJson(second));

        awaitEmail(firstRecipient);
        awaitEmail(secondRecipient);
        assertEquals("SENT", redisTemplate.opsForValue().get(redisKey(sharedNewsId, first.getUserId())));
        assertEquals("SENT", redisTemplate.opsForValue().get(redisKey(sharedNewsId, second.getUserId())));
    }

    @Test
    void should_skip_sending_when_the_sent_key_already_exists() {
        TopicNewsNotificationEventDTO event = aValidEvent(uniqueRecipient());
        String key = redisKey(event.getNewsId(), event.getUserId());
        redisTemplate.opsForValue().set(key, "SENT", Duration.ofDays(7));

        publishThenAwaitSentinelProcessed(event.getUserId().toString(), toJson(event));

        assertNoEmail(event.getEmailAddress());
        assertEquals("SENT", redisTemplate.opsForValue().get(key));
        // Total sends for this test method is 1 (the sentinel's) - proves this record sent zero.
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_and_leave_a_long_held_claim_untouched() {
        TopicNewsNotificationEventDTO event = aValidEvent(uniqueRecipient());
        String key = redisKey(event.getNewsId(), event.getUserId());
        redisTemplate.opsForValue().set(key, "PROCESSING:other", Duration.ofSeconds(60));

        publishThenAwaitSentinelProcessed(event.getUserId().toString(), toJson(event));

        awaitDltRecordForKey(event.getUserId().toString());
        assertNoEmail(event.getEmailAddress());
        assertEquals("PROCESSING:other", redisTemplate.opsForValue().get(key));
        // Total sends for this test method is 1 (the sentinel's) - proves this record sent zero.
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_send_one_email_after_a_short_held_claim_expires() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        String redisKey = redisKey(event.getNewsId(), event.getUserId());
        redisTemplate.opsForValue().set(redisKey, "PROCESSING:other", Duration.ofMillis(150));

        publish(NOTIFICATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        awaitEmail(recipient);
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertEquals("SENT", redisTemplate.opsForValue().get(redisKey)));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_malformed_json_without_sending() {
        String key = UUID.randomUUID().toString();

        publishThenAwaitSentinelProcessed(key, "{not valid json");

        awaitDltRecordForKey(key);
        // Total sends for this test method is 1 (the sentinel's) - proves the malformed record sent zero.
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_a_blank_email_address_without_sending() {
        TopicNewsNotificationEventDTO event = aValidEvent("");
        String key = event.getUserId().toString();

        publishThenAwaitSentinelProcessed(key, toJson(event));

        awaitDltRecordForKey(key);
        // Validation fails before the Redis inbox is ever touched.
        assertNull(redisTemplate.opsForValue().get(redisKey(event.getNewsId(), event.getUserId())));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_a_missing_news_id_without_sending() {
        TopicNewsNotificationEventDTO event = aValidEvent(uniqueRecipient());
        event.setNewsId(null);
        String key = event.getUserId().toString();

        publishThenAwaitSentinelProcessed(key, toJson(event));

        awaitDltRecordForKey(key);
        assertNoEmail(event.getEmailAddress());
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_after_exactly_one_permanent_smtp_rejection() {
        String disallowedRecipient = UUID.randomUUID() + "@not-example.org";
        TopicNewsNotificationEventDTO event = aValidEvent(disallowedRecipient);
        String key = event.getUserId().toString();

        publish(NOTIFICATION_REQUESTED_TOPIC, key, toJson(event));

        awaitDltRecordForKey(key);
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> verify(javaMailSender, times(1)).send(any(MimeMessage.class)));
        assertNull(redisTemplate.opsForValue().get(redisKey(event.getNewsId(), event.getUserId())));
    }

    @Test
    void should_escape_html_injection_in_topic_name() {
        String recipient = uniqueRecipient();
        TopicNewsNotificationEventDTO event = aValidEvent(recipient);
        event.setTopicName("<img src=x onerror=alert(1)>");

        publish(NOTIFICATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        JsonNode summary = awaitEmail(recipient);
        JsonNode message = MAILPIT_CLIENT.fetchMessage(summary.get("ID").asString());
        String html = message.get("HTML").asString();

        assertFalse(html.contains("<img src=x onerror=alert(1)>"));
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
    }
}
