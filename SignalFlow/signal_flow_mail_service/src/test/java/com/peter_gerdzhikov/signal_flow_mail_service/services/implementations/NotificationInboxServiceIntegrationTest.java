package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.ClaimResult;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.NotificationInboxService;
import com.peter_gerdzhikov.signal_flow_mail_service.support.AbstractRedisIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NotificationInboxServiceIntegrationTest extends AbstractRedisIntegrationTest {

    private static final String KEY_FORMAT = "mail:notification:%s:%s";

    @Autowired
    private NotificationInboxService notificationInboxService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Nested
    class Claim {

        @Test
        void should_claim_when_key_is_absent() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            ClaimResult result = notificationInboxService.claim(newsId, userId, UUID.randomUUID().toString());
            Long ttl = redisTemplate.getExpire(key(newsId, userId), TimeUnit.SECONDS);

            assertEquals(ClaimResult.CLAIMED, result);
            assertTrue(ttl >= 290 && ttl <= 300, "Expected TTL in [290, 300], was " + ttl);
        }

        @Test
        void should_return_held_when_key_is_already_claimed() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            notificationInboxService.claim(newsId, userId, UUID.randomUUID().toString());

            ClaimResult result = notificationInboxService.claim(newsId, userId, UUID.randomUUID().toString());

            assertEquals(ClaimResult.HELD, result);
        }

        @Test
        void should_return_already_sent_when_key_is_marked_sent() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String token = UUID.randomUUID().toString();
            notificationInboxService.claim(newsId, userId, token);
            notificationInboxService.markSent(newsId, userId);

            ClaimResult result = notificationInboxService.claim(newsId, userId, UUID.randomUUID().toString());

            assertEquals(ClaimResult.ALREADY_SENT, result);
        }

        @Test
        void should_be_independent_for_different_user_ids_with_same_news_id() {
            UUID newsId = UUID.randomUUID();
            UUID firstUserId = UUID.randomUUID();
            UUID secondUserId = UUID.randomUUID();
            notificationInboxService.claim(newsId, firstUserId, UUID.randomUUID().toString());

            ClaimResult result = notificationInboxService.claim(newsId, secondUserId, UUID.randomUUID().toString());

            assertEquals(ClaimResult.CLAIMED, result);
        }
    }

    @Nested
    class MarkSent {

        @Test
        void should_set_sent_value_with_a_seven_day_ttl() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            notificationInboxService.claim(newsId, userId, UUID.randomUUID().toString());

            notificationInboxService.markSent(newsId, userId);

            String value = redisTemplate.opsForValue().get(key(newsId, userId));
            Long ttl = redisTemplate.getExpire(key(newsId, userId), TimeUnit.SECONDS);
            long sevenDaysInSeconds = 604800L;
            assertEquals("SENT", value);
            assertTrue(ttl > sevenDaysInSeconds - 300 && ttl <= sevenDaysInSeconds,
                    "Expected TTL close to 7 days, was " + ttl);
        }
    }

    @Nested
    class Release {

        @Test
        void should_delete_when_token_matches() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String token = UUID.randomUUID().toString();
            notificationInboxService.claim(newsId, userId, token);

            notificationInboxService.release(newsId, userId, token);

            assertNull(redisTemplate.opsForValue().get(key(newsId, userId)));
        }

        @Test
        void should_leave_a_newer_claim_untouched_when_the_token_is_stale() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String staleToken = UUID.randomUUID().toString();
            String newerToken = UUID.randomUUID().toString();
            notificationInboxService.claim(newsId, userId, staleToken);
            redisTemplate.opsForValue().set(key(newsId, userId), "PROCESSING:" + newerToken);

            notificationInboxService.release(newsId, userId, staleToken);

            assertEquals("PROCESSING:" + newerToken, redisTemplate.opsForValue().get(key(newsId, userId)));
        }

        @Test
        void should_allow_a_fresh_claim_after_release() {
            UUID newsId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            String token = UUID.randomUUID().toString();
            notificationInboxService.claim(newsId, userId, token);
            notificationInboxService.release(newsId, userId, token);

            ClaimResult result = notificationInboxService.claim(newsId, userId, UUID.randomUUID().toString());

            assertEquals(ClaimResult.CLAIMED, result);
        }
    }

    private static String key(UUID newsId, UUID userId) {
        return String.format(KEY_FORMAT, newsId, userId);
    }
}
