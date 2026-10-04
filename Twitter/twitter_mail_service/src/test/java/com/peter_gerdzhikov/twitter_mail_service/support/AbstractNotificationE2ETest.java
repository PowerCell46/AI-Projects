package com.peter_gerdzhikov.twitter_mail_service.support;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import tools.jackson.databind.JsonNode;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.confirmation.UserConfirmationNotificationService;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.follow.UserFollowedNotificationService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared e2e base: Kafka + Redis + Mailpit, a real (spied) {@link JavaMailSender} so send attempts can be
 * counted, the mutable test clock, and Mailpit-specific await/assert helpers. Every subclass extends this with
 * no extra {@code @SpringBootTest}/{@code @ActiveProfiles}/bean-override configuration of its own, so all of
 * them share one cached Spring context.
 *
 * <p>{@code SmtpUnreachableIntegrationTest} needs its own context (a closed SMTP port), so it extends
 * {@link AbstractKafkaE2ETestSupport} directly instead.
 */
@ActiveProfiles("test")
@Import(TestClockConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class AbstractNotificationE2ETest extends AbstractMailpitIntegrationTest {

    @Autowired
    protected MutableClock mutableClock;

    @MockitoSpyBean
    protected JavaMailSender javaMailSender;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @MockitoSpyBean
    protected UserConfirmationNotificationService userConfirmationNotificationService;

    @MockitoSpyBean
    protected UserFollowedNotificationService userFollowedNotificationService;

    @BeforeEach
    void resetSharedState() {
        MAILPIT_CLIENT.deleteAll();
        mutableClock.reset();
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

    protected static void awaitEmailCount(String recipientEmail, int expectedCount) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertEquals(expectedCount,
                        MAILPIT_CLIENT.searchByRecipient(recipientEmail).get("messages").size(),
                        "Expected " + expectedCount + " emails to " + recipientEmail));
    }

    protected static void assertNoEmail(String recipientEmail) {
        JsonNode messages = MAILPIT_CLIENT.searchByRecipient(recipientEmail).get("messages");

        assertEquals(0, messages.size(), "Expected no email to " + recipientEmail);
    }

    protected static JsonNode fetchMessage(JsonNode summary) {
        return MAILPIT_CLIENT.fetchMessage(summary.get("ID").asString());
    }

    /**
     * Publishes a fresh valid record under the same Kafka key (so the same partition) and awaits its own
     * email. This consumer processes records of one partition in order, so once the sentinel's email is
     * observed every record published before it - including its retries - is fully processed, and
     * "nothing happened" assertions about those records can be made without racing them.
     */
    protected UserConfirmationRequestedEventDTO awaitSentinelProcessed(UUID userId) {
        UserConfirmationRequestedEventDTO sentinel =
                aValidEvent(userId, uniqueRecipient(), mutableClock.instant().plus(Duration.ofDays(1)));
        publish(USER_CONFIRMATION_REQUESTED_TOPIC, userId.toString(), toJson(sentinel));

        awaitEmail(sentinel.getEmail());

        return sentinel;
    }

    /**
     * The follow counterpart of {@link #awaitSentinelProcessed}: a fresh valid follow event from a new
     * follower, under the same Kafka key (the followee id) and so on the same partition, to its own recipient.
     */
    protected UserFollowedEventDTO awaitFollowSentinelProcessed(UUID followeeId) {
        UserFollowedEventDTO sentinel = aValidFollowEvent(UUID.randomUUID(), followeeId, uniqueRecipient());
        publish(USER_FOLLOWED_TOPIC, followeeId.toString(), toJson(sentinel));

        awaitEmail(sentinel.getFolloweeEmail());

        return sentinel;
    }
}
