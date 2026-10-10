package com.peter_gerdzhikov.twitter_mail_service.services.implementations.inbox;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.ClaimResult;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox.MailInboxService;
import com.peter_gerdzhikov.twitter_mail_service.support.AbstractRedisIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MailInboxServiceIntegrationTest extends AbstractRedisIntegrationTest {

    private static final Duration SENT_TTL = Duration.ofDays(7);

    @Autowired
    private MailInboxService mailInboxService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Nested
    class Claim {

        @Test
        void should_claim_with_the_claim_ttl_when_the_key_is_absent() {
            String key = uniqueKey();

            ClaimResult result = mailInboxService.claim(key, uniqueToken());
            Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);

            assertEquals(ClaimResult.CLAIMED, result);
            assertTrue(ttlSeconds >= 110 && ttlSeconds <= 120, "Expected TTL in [110, 120], was " + ttlSeconds);
        }

        @Test
        void should_return_held_when_the_key_is_already_claimed_by_another_token() {
            String key = uniqueKey();
            mailInboxService.claim(key, uniqueToken());

            ClaimResult result = mailInboxService.claim(key, uniqueToken());

            assertEquals(ClaimResult.HELD, result);
        }

        @Test
        void should_return_already_sent_when_the_key_is_marked_sent() {
            String key = uniqueKey();
            mailInboxService.claim(key, uniqueToken());
            mailInboxService.markSent(key, SENT_TTL);

            ClaimResult result = mailInboxService.claim(key, uniqueToken());

            assertEquals(ClaimResult.ALREADY_SENT, result);
        }

        @Test
        void should_claim_independently_when_the_keys_differ() {
            mailInboxService.claim(uniqueKey(), uniqueToken());

            ClaimResult result = mailInboxService.claim(uniqueKey(), uniqueToken());

            assertEquals(ClaimResult.CLAIMED, result);
        }
    }

    @Nested
    class MarkSent {

        @Test
        void should_set_the_sent_value_with_the_given_ttl() {
            String key = uniqueKey();
            mailInboxService.claim(key, uniqueToken());

            mailInboxService.markSent(key, SENT_TTL);

            Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            assertEquals("SENT", redisTemplate.opsForValue().get(key));
            assertTrue(ttlSeconds > SENT_TTL.toSeconds() - 300 && ttlSeconds <= SENT_TTL.toSeconds(),
                    "Expected TTL close to 7 days, was " + ttlSeconds);
        }
    }

    @Nested
    class Release {

        @Test
        void should_delete_the_claim_when_the_token_matches() {
            String key = uniqueKey();
            String token = uniqueToken();
            mailInboxService.claim(key, token);

            mailInboxService.release(key, token);

            assertNull(redisTemplate.opsForValue().get(key));
        }

        @Test
        void should_leave_a_newer_claim_untouched_when_the_token_is_stale() {
            String key = uniqueKey();
            String staleToken = uniqueToken();
            String newerToken = uniqueToken();
            mailInboxService.claim(key, staleToken);
            redisTemplate.opsForValue().set(key, "PROCESSING:" + newerToken);

            mailInboxService.release(key, staleToken);

            assertEquals("PROCESSING:" + newerToken, redisTemplate.opsForValue().get(key));
        }

        @Test
        void should_leave_a_sent_key_untouched_when_released_with_any_token() {
            String key = uniqueKey();
            mailInboxService.claim(key, uniqueToken());
            mailInboxService.markSent(key, SENT_TTL);

            mailInboxService.release(key, uniqueToken());

            assertEquals("SENT", redisTemplate.opsForValue().get(key));
        }

        @Test
        void should_allow_a_fresh_claim_after_release() {
            String key = uniqueKey();
            String token = uniqueToken();
            mailInboxService.claim(key, token);
            mailInboxService.release(key, token);

            ClaimResult result = mailInboxService.claim(key, uniqueToken());

            assertEquals(ClaimResult.CLAIMED, result);
        }
    }

    private static String uniqueKey() {
        return "mail:test:" + UUID.randomUUID();
    }

    private static String uniqueToken() {
        return UUID.randomUUID().toString();
    }
}
