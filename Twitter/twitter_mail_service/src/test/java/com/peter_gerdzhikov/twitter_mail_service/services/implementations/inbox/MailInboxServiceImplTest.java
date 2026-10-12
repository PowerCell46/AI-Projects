package com.peter_gerdzhikov.twitter_mail_service.services.implementations.inbox;

import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Nested
    class MarkSent {

        @Test
        void should_swallow_a_redis_connection_failure_without_throwing() throws Exception {
            int closedPort;
            try (ServerSocket socket = new ServerSocket(0)) {
                closedPort = socket.getLocalPort();
            }

            LettuceClientConfiguration clientConfiguration = LettuceClientConfiguration
                    .builder()
                    .commandTimeout(Duration.ofSeconds(5))
                    .build();
            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration("127.0.0.1", closedPort),
                    clientConfiguration);
            connectionFactory.afterPropertiesSet();
            StringRedisTemplate unreachableRedis = new StringRedisTemplate();
            unreachableRedis.setConnectionFactory(connectionFactory);
            unreachableRedis.afterPropertiesSet();
            MailInboxServiceImpl inboxService =
                    new MailInboxServiceImpl(Duration.ofMinutes(5), unreachableRedis, releaseMailClaimScript);

            assertThrows(RedisConnectionFailureException.class,
                    () -> unreachableRedis.opsForValue().set("mail:confirmation:test", "SENT", Duration.ofDays(7)));
            assertDoesNotThrow(() -> inboxService.markSent("mail:confirmation:test", Duration.ofDays(7)));

            connectionFactory.destroy();
        }
    }
}
