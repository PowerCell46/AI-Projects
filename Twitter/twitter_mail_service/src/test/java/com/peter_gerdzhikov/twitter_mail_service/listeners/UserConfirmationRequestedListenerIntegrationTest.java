package com.peter_gerdzhikov.twitter_mail_service.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.mail.internet.MimeMessage;

import tools.jackson.databind.JsonNode;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.support.AbstractNotificationE2ETest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UserConfirmationRequestedListenerIntegrationTest extends AbstractNotificationE2ETest {

    private static final Instant FIXED_NOW = Instant.parse("2026-10-01T12:00:00Z");

    private static final String IGNORE_LINE = "If you didn't sign up, ignore this email.";

    private static final String EXPIRY_IN_SOFIA = "2 October 2026, 15:00 EEST";

    @Test
    void should_send_one_email_from_the_configured_sender_with_the_confirmation_subject() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, mutableClock.instant().plus(Duration.ofHours(24)));

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        JsonNode message = fetchMessage(awaitEmail(recipient));
        assertEquals("Confirm your email", message.get("Subject").asString());
        assertEquals("twitter-test@example.com", message.get("From").get("Address").asString());
        assertEquals(recipient, message.get("To").get(0).get("Address").asString());
        awaitEmailCount(recipient, 1);
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_render_the_html_part_with_the_link_username_expiry_and_ignore_line() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = anEventWithAFixedClockAndExpiry(recipient);

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        String html = fetchMessage(awaitEmail(recipient)).get("HTML").asString();
        assertTrue(html.contains("href=\"" + event.getConfirmationUrl() + "\""));
        assertTrue(html.contains(">" + event.getConfirmationUrl() + "</a>"));
        assertTrue(html.contains("Hi ana_k,"));
        assertTrue(html.contains("This link expires on " + EXPIRY_IN_SOFIA + "."));
        assertTrue(html.contains(IGNORE_LINE));
    }

    @Test
    void should_render_the_text_part_with_the_same_content_as_the_html_part() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = anEventWithAFixedClockAndExpiry(recipient);

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        String text = fetchMessage(awaitEmail(recipient)).get("Text").asString();
        assertTrue(text.contains(event.getConfirmationUrl()));
        assertTrue(text.contains("Hi ana_k,"));
        assertTrue(text.contains("This link expires on " + EXPIRY_IN_SOFIA + "."));
        assertTrue(text.contains(IGNORE_LINE));
    }

    @Test
    void should_mark_the_redis_key_sent_with_a_ttl_within_the_configured_window() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, mutableClock.instant().plus(Duration.ofHours(24)));
        String redisKey = confirmationRedisKey(event.getEventId());

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertEquals("SENT", redisTemplate.opsForValue().get(redisKey)));
        long sevenDaysInSeconds = Duration.ofDays(7).toSeconds();
        Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertTrue(ttlSeconds > sevenDaysInSeconds - 60 && ttlSeconds <= sevenDaysInSeconds,
                "Expected TTL within 7 days, was " + ttlSeconds);
    }

    @Test
    void should_send_only_one_email_when_the_same_record_is_published_twice() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, mutableClock.instant().plus(Duration.ofHours(24)));
        String key = event.getUserId().toString();
        String json = toJson(event);

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, key, json);
        publish(USER_CONFIRMATION_REQUESTED_TOPIC, key, json);
        awaitSentinelProcessed(event.getUserId());

        awaitEmailCount(recipient, 1);
    }

    @Test
    void should_send_two_emails_when_two_events_for_the_same_user_have_different_event_ids() {
        String recipient = uniqueRecipient();
        UUID userId = UUID.randomUUID();
        Instant expiresAt = mutableClock.instant().plus(Duration.ofHours(24));
        UserConfirmationRequestedEventDTO firstEvent = aValidEvent(userId, recipient, expiresAt);
        UserConfirmationRequestedEventDTO resentEvent = aValidEvent(userId, recipient, expiresAt);

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, userId.toString(), toJson(firstEvent));
        publish(USER_CONFIRMATION_REQUESTED_TOPIC, userId.toString(), toJson(resentEvent));

        awaitEmailCount(recipient, 2);
    }

    @Test
    void should_send_nothing_when_the_redis_key_is_already_sent() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, mutableClock.instant().plus(Duration.ofHours(24)));
        String redisKey = confirmationRedisKey(event.getEventId());
        redisTemplate.opsForValue().set(redisKey, "SENT", Duration.ofHours(1));

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));
        awaitSentinelProcessed(event.getUserId());

        assertNoEmail(recipient);
        assertEquals("SENT", redisTemplate.opsForValue().get(redisKey));
    }

    @Test
    void should_send_nothing_and_leave_no_redis_key_when_expires_at_equals_the_clock() {
        String recipient = uniqueRecipient();
        mutableClock.setInstant(FIXED_NOW);
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, FIXED_NOW);

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));
        awaitSentinelProcessed(event.getUserId());

        assertNoEmail(recipient);
        assertFalse(redisTemplate.hasKey(confirmationRedisKey(event.getEventId())));
    }

    @Test
    void should_send_the_email_when_expires_at_is_one_second_after_the_clock() {
        String recipient = uniqueRecipient();
        mutableClock.setInstant(FIXED_NOW);
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, FIXED_NOW.plusSeconds(1));

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        awaitEmail(recipient);
    }

    @Test
    void should_dead_letter_and_leave_a_long_held_claim_untouched() {
        UserConfirmationRequestedEventDTO event = aValidEvent(uniqueRecipient(), mutableClock.instant().plus(Duration.ofHours(24)));
        String redisKey = confirmationRedisKey(event.getEventId());
        redisTemplate.opsForValue().set(redisKey, "PROCESSING:other", Duration.ofSeconds(60));

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));
        awaitSentinelProcessed(event.getUserId());

        awaitDltRecordForKey(event.getUserId().toString());
        assertNoEmail(event.getEmail());
        assertEquals("PROCESSING:other", redisTemplate.opsForValue().get(redisKey));
        // The test profile retries 3 times: 1 attempt + 3 retries for this record, plus the sentinel.
        verify(userConfirmationNotificationService, times(5)).process(any(UserConfirmationRequestedEventDTO.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_send_one_email_after_a_short_held_claim_expires() {
        String recipient = uniqueRecipient();
        UserConfirmationRequestedEventDTO event = aValidEvent(recipient, mutableClock.instant().plus(Duration.ofHours(24)));
        String redisKey = confirmationRedisKey(event.getEventId());
        redisTemplate.opsForValue().set(redisKey, "PROCESSING:other", Duration.ofMillis(150));

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        awaitEmail(recipient);
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertEquals("SENT", redisTemplate.opsForValue().get(redisKey)));
        awaitEmailCount(recipient, 1);
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_malformed_json_without_sending_or_touching_the_inbox() {
        UUID userId = UUID.randomUUID();

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, userId.toString(), "{not valid json");
        awaitSentinelProcessed(userId);

        awaitDltRecordForKey(userId.toString());
        // The service - the only code that touches the inbox - ran once, for the sentinel alone.
        verify(userConfirmationNotificationService, times(1)).process(any(UserConfirmationRequestedEventDTO.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_a_missing_event_id_without_retries() {
        UserConfirmationRequestedEventDTO event = aValidEvent(uniqueRecipient(), mutableClock.instant().plus(Duration.ofHours(24)));
        event.setEventId(null);

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_blank_email_without_retries() {
        UserConfirmationRequestedEventDTO event = aValidEvent("", mutableClock.instant().plus(Duration.ofHours(24)));

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_username_outside_the_pattern_without_retries() {
        UserConfirmationRequestedEventDTO event = aValidEvent(uniqueRecipient(), mutableClock.instant().plus(Duration.ofHours(24)));
        event.setUsername("a-b");

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_missing_expires_at_without_retries() {
        UserConfirmationRequestedEventDTO event = aValidEvent(uniqueRecipient(), null);

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_2049_character_confirmation_url_without_retries() {
        UserConfirmationRequestedEventDTO event = aValidEvent(uniqueRecipient(), mutableClock.instant().plus(Duration.ofHours(24)));
        event.setConfirmationUrl("http://localhost:5173/confirm?token=" + "a".repeat(2049 - 36));
        assertEquals(2049, event.getConfirmationUrl().length());

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_after_exactly_one_attempt_when_the_recipient_gets_a_permanent_smtp_rejection() {
        String disallowedRecipient = UUID.randomUUID() + "@not-example.org";
        UserConfirmationRequestedEventDTO event = aValidEvent(disallowedRecipient, mutableClock.instant().plus(Duration.ofHours(24)));
        String redisKey = confirmationRedisKey(event.getEventId());

        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));

        awaitDltRecordForKey(event.getUserId().toString());
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertFalse(redisTemplate.hasKey(redisKey)));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        verify(userConfirmationNotificationService, times(1)).process(any(UserConfirmationRequestedEventDTO.class));
    }

    private UserConfirmationRequestedEventDTO anEventWithAFixedClockAndExpiry(String recipient) {
        mutableClock.setInstant(FIXED_NOW);

        return aValidEvent(recipient, Instant.parse("2026-10-02T12:00:00Z"));
    }

    private void assertDeadLetteredWithoutRetries(UserConfirmationRequestedEventDTO event) {
        publish(USER_CONFIRMATION_REQUESTED_TOPIC, event.getUserId().toString(), toJson(event));
        awaitSentinelProcessed(event.getUserId());

        awaitDltRecordForKey(event.getUserId().toString());
        assertFalse(redisTemplate.hasKey(confirmationRedisKey(event.getEventId())));
        // One call for the invalid record - a retry would add more - plus one for the sentinel, and the
        // sentinel's is the only send.
        verify(userConfirmationNotificationService, times(2)).process(any(UserConfirmationRequestedEventDTO.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }
}
