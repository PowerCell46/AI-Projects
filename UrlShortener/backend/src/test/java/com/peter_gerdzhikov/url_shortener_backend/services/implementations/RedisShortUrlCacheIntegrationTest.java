package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.peter_gerdzhikov.url_shortener_backend.support.AbstractRedisIntegrationTest;

@DataRedisTest
class RedisShortUrlCacheIntegrationTest extends AbstractRedisIntegrationTest {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final String CODE = "aB3xY9";
    private static final String ORIGINAL_URL = "https://example.com/some/page";

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RedisShortUrlCache shortUrlCache;

    @BeforeEach
    void setUp() {
        shortUrlCache = new RedisShortUrlCache(redisTemplate, TTL);
        redisTemplate.delete(CODE);
    }

    @Test
    void should_round_trip_a_value_through_a_real_redis_instance() {
        shortUrlCache.put(CODE, ORIGINAL_URL);

        assertThat(shortUrlCache.get(CODE)).contains(ORIGINAL_URL);
    }

    @Test
    void should_apply_the_configured_ttl_to_the_stored_key() {
        shortUrlCache.put(CODE, ORIGINAL_URL);

        Long remainingTtlSeconds = redisTemplate.getExpire(CODE);

        assertThat(remainingTtlSeconds).isPositive().isLessThanOrEqualTo(TTL.getSeconds());
    }

    @Test
    void should_return_empty_when_the_code_is_not_cached() {
        assertThat(shortUrlCache.get("unknownCode")).isEmpty();
    }

    @Nested
    class WhenRedisIsUnreachable {

        private LettuceConnectionFactory unreachableConnectionFactory;
        private RedisShortUrlCache unreachableShortUrlCache;

        @BeforeEach
        void pointAtAnUnreachableRedis() {
            RedisStandaloneConfiguration configuration =
                    new RedisStandaloneConfiguration("localhost", findAnUnusedPort());
            unreachableConnectionFactory = new LettuceConnectionFactory(
                    configuration,
                    LettuceClientConfiguration.builder()
                            .commandTimeout(Duration.ofMillis(200))
                            .shutdownTimeout(Duration.ZERO)
                            .build()
            );
            unreachableConnectionFactory.afterPropertiesSet();

            StringRedisTemplate unreachableTemplate = new StringRedisTemplate(unreachableConnectionFactory);
            unreachableTemplate.afterPropertiesSet();

            unreachableShortUrlCache = new RedisShortUrlCache(unreachableTemplate, TTL);
        }

        @AfterEach
        void tearDown() {
            unreachableConnectionFactory.destroy();
        }

        @Test
        void should_not_throw_when_putting_into_an_unreachable_redis() {
            assertThatCode(() -> unreachableShortUrlCache.put(CODE, ORIGINAL_URL)).doesNotThrowAnyException();
        }

        @Test
        void should_return_empty_when_reading_from_an_unreachable_redis() {
            assertThat(unreachableShortUrlCache.get(CODE)).isEmpty();
        }
    }

    private static int findAnUnusedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
