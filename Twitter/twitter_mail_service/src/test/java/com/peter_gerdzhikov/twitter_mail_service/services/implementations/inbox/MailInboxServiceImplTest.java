package com.peter_gerdzhikov.twitter_mail_service.services.implementations.inbox;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class MailInboxServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisScript<Long> releaseMailClaimScript;

    @Nested
    class Constructor {

        @ParameterizedTest
        @ValueSource(longs = {0, -1, -300})
        void should_fail_to_start_when_the_claim_ttl_is_zero_or_negative(long claimTtlSeconds) {
            Duration claimTtl = Duration.ofSeconds(claimTtlSeconds);

            assertThrows(IllegalStateException.class,
                    () -> new MailInboxServiceImpl(claimTtl, redisTemplate, releaseMailClaimScript));
        }
    }
}
