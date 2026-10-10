package com.peter_gerdzhikov.twitter_mail_service.listeners;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.mail.internet.MimeMessage;

import tools.jackson.databind.JsonNode;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.support.AbstractNotificationE2ETest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class UserFollowedListenerIntegrationTest extends AbstractNotificationE2ETest {

    private static final String FEED_URL = "http://localhost:5173/feed";

    private static final String FOLLOWER_LINE = "ana (@ana) is now following you.";

    @Value("${app.mail-inbox.follow-window}")
    private Duration followWindow;

    @Test
    void should_send_one_email_to_the_followee_with_the_follower_in_the_subject() {
        String recipient = uniqueRecipient();
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), recipient, mutableClock.instant());

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));

        JsonNode message = fetchMessage(awaitEmail(recipient));
        assertEquals("ana followed you", message.get("Subject").asString());
        assertEquals("twitter-test@example.com", message.get("From").get("Address").asString());
        assertEquals(recipient, message.get("To").get(0).get("Address").asString());
        awaitEmailCount(recipient, 1);
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_render_the_html_part_with_the_greeting_follower_line_and_feed_link() {
        String recipient = uniqueRecipient();
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), recipient, mutableClock.instant());

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));

        String html = fetchMessage(awaitEmail(recipient)).get("HTML").asString();
        assertTrue(html.contains("Hi bob,"));
        assertTrue(html.contains(FOLLOWER_LINE));
        assertTrue(html.contains("href=\"" + FEED_URL + "\""));
    }

    @Test
    void should_render_the_text_part_with_the_same_content_as_the_html_part() {
        String recipient = uniqueRecipient();
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), recipient, mutableClock.instant());

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));

        String text = fetchMessage(awaitEmail(recipient)).get("Text").asString();
        assertTrue(text.contains("Hi bob,"));
        assertTrue(text.contains(FOLLOWER_LINE));
        assertTrue(text.contains(FEED_URL));
    }

    @Test
    void should_mark_the_pair_key_sent_with_a_ttl_within_the_follow_window() {
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), uniqueRecipient(), mutableClock.instant());
        String redisKey = followRedisKey(event.getFollowerId(), event.getFolloweeId());

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));

        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertEquals("SENT", redisTemplate.opsForValue().get(redisKey)));
        long windowInSeconds = Duration.ofHours(24).toSeconds();
        Long ttlSeconds = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        assertTrue(ttlSeconds > windowInSeconds - 60 && ttlSeconds <= windowInSeconds,
                "Expected TTL within 24 hours, was " + ttlSeconds);
    }

    @Test
    void should_send_only_one_email_when_the_same_record_is_published_twice() {
        String recipient = uniqueRecipient();
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), recipient, mutableClock.instant());
        String key = event.getFolloweeId().toString();
        String json = toJson(event);

        publish(USER_FOLLOWED_TOPIC, key, json);
        publish(USER_FOLLOWED_TOPIC, key, json);
        awaitFollowSentinelProcessed(event.getFolloweeId());

        awaitEmailCount(recipient, 1);
    }

    @Test
    void should_send_one_email_in_total_when_the_same_pair_follows_again_inside_the_window() {
        String recipient = uniqueRecipient();
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        UserFollowedEventDTO firstFollow = aValidFollowEvent(followerId, followeeId, recipient, mutableClock.instant());
        UserFollowedEventDTO refollow = aValidFollowEvent(followerId, followeeId, recipient, mutableClock.instant());

        publish(USER_FOLLOWED_TOPIC, followeeId.toString(), toJson(firstFollow));
        publish(USER_FOLLOWED_TOPIC, followeeId.toString(), toJson(refollow));
        awaitFollowSentinelProcessed(followeeId);

        awaitEmailCount(recipient, 1);
    }

    @Test
    void should_send_two_emails_when_two_followers_follow_the_same_followee() {
        String recipient = uniqueRecipient();
        UUID followeeId = UUID.randomUUID();
        UserFollowedEventDTO firstFollow = aValidFollowEvent(UUID.randomUUID(), followeeId, recipient, mutableClock.instant());
        UserFollowedEventDTO secondFollow = aValidFollowEvent(UUID.randomUUID(), followeeId, recipient, mutableClock.instant());

        publish(USER_FOLLOWED_TOPIC, followeeId.toString(), toJson(firstFollow));
        publish(USER_FOLLOWED_TOPIC, followeeId.toString(), toJson(secondFollow));

        awaitEmailCount(recipient, 2);
    }

    @Test
    void should_send_two_emails_when_one_follower_follows_two_followees() {
        UUID followerId = UUID.randomUUID();
        UserFollowedEventDTO firstFollow = aValidFollowEvent(followerId, UUID.randomUUID(), uniqueRecipient(), mutableClock.instant());
        UserFollowedEventDTO secondFollow = aValidFollowEvent(followerId, UUID.randomUUID(), uniqueRecipient(), mutableClock.instant());

        publish(USER_FOLLOWED_TOPIC, firstFollow.getFolloweeId().toString(), toJson(firstFollow));
        publish(USER_FOLLOWED_TOPIC, secondFollow.getFolloweeId().toString(), toJson(secondFollow));

        awaitEmailCount(firstFollow.getFolloweeEmail(), 1);
        awaitEmailCount(secondFollow.getFolloweeEmail(), 1);
    }

    @Test
    void should_send_nothing_and_leave_no_redis_key_when_the_follow_is_older_than_the_window() {
        String recipient = uniqueRecipient();
        Instant tooOld = mutableClock.instant().minus(followWindow).minusSeconds(1);
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), recipient, tooOld);

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));
        awaitFollowSentinelProcessed(event.getFolloweeId());

        assertNoEmail(recipient);
        assertFalse(redisTemplate.hasKey(followRedisKey(event.getFollowerId(), event.getFolloweeId())));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_send_the_email_when_the_follow_is_exactly_one_window_old() {
        String recipient = uniqueRecipient();
        Instant atTheEdge = mutableClock.instant().minus(followWindow);
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), recipient, atTheEdge);

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));

        awaitEmail(recipient);
    }

    @Test
    void should_dead_letter_malformed_json_without_sending_or_touching_the_inbox() {
        UUID followeeId = UUID.randomUUID();

        publish(USER_FOLLOWED_TOPIC, followeeId.toString(), "{not valid json");
        awaitFollowSentinelProcessed(followeeId);

        awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, followeeId.toString());
        // The service - the only code that touches the inbox - ran once, for the sentinel alone.
        verify(userFollowedNotificationService, times(1)).process(any(UserFollowedEventDTO.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    void should_dead_letter_a_missing_follower_id_without_retries() {
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), uniqueRecipient(), mutableClock.instant());
        event.setFollowerId(null);

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_blank_followee_email_without_retries() {
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), "", mutableClock.instant());

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_follower_username_outside_the_pattern_without_retries() {
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), uniqueRecipient(), mutableClock.instant());
        event.setFollowerUsername("a-b");

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_a_followee_username_outside_the_pattern_without_retries() {
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), uniqueRecipient(), mutableClock.instant());
        event.setFolloweeUsername("a-b");

        assertDeadLetteredWithoutRetries(event);
    }

    @Test
    void should_dead_letter_after_exactly_one_attempt_and_release_the_claim_when_the_recipient_gets_a_permanent_smtp_rejection() {
        String disallowedRecipient = UUID.randomUUID() + "@not-example.org";
        UserFollowedEventDTO event = aValidFollowEvent(UUID.randomUUID(), UUID.randomUUID(), disallowedRecipient, mutableClock.instant());
        String redisKey = followRedisKey(event.getFollowerId(), event.getFolloweeId());

        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));

        awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, event.getFolloweeId().toString());
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertFalse(redisTemplate.hasKey(redisKey)));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        verify(userFollowedNotificationService, times(1)).process(any(UserFollowedEventDTO.class));
    }

    private void assertDeadLetteredWithoutRetries(UserFollowedEventDTO event) {
        publish(USER_FOLLOWED_TOPIC, event.getFolloweeId().toString(), toJson(event));
        awaitFollowSentinelProcessed(event.getFolloweeId());

        awaitDltRecordForKey(USER_FOLLOWED_DLT_TOPIC, event.getFolloweeId().toString());
        assertFalse(redisTemplate.hasKey(followRedisKey(event.getFollowerId(), event.getFolloweeId())));
        // One call for the invalid record - a retry would add more - plus one for the sentinel, and the
        // sentinel's is the only send.
        verify(userFollowedNotificationService, times(2)).process(any(UserFollowedEventDTO.class));
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }
}
