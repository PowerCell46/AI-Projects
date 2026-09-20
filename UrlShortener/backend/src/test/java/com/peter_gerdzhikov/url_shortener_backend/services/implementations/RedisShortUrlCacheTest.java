package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RedisShortUrlCacheTest {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String CODE = "aB3xY9";
    private static final String ORIGINAL_URL = "https://example.com/some/page";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisShortUrlCache shortUrlCache;

    @BeforeEach
    void setUp() {
        shortUrlCache = new RedisShortUrlCache(redisTemplate, TTL);
    }

    @Nested
    class Put {

        @BeforeEach
        void stubValueOperations() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        void should_store_the_original_url_under_the_code_with_the_configured_ttl() {
            shortUrlCache.put(CODE, ORIGINAL_URL);

            verify(valueOperations).set(CODE, ORIGINAL_URL, TTL);
        }

        @Test
        void should_swallow_the_exception_when_redis_is_unreachable() {
            when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("down"));

            assertThatCode(() -> shortUrlCache.put(CODE, ORIGINAL_URL)).doesNotThrowAnyException();
        }
    }

    @Nested
    class Get {

        @BeforeEach
        void stubValueOperations() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        }

        @Test
        void should_return_the_cached_url_when_present() {
            when(valueOperations.get(CODE)).thenReturn(ORIGINAL_URL);

            assertThat(shortUrlCache.get(CODE)).contains(ORIGINAL_URL);
        }

        @Test
        void should_return_empty_when_the_code_is_not_cached() {
            when(valueOperations.get(CODE)).thenReturn(null);

            assertThat(shortUrlCache.get(CODE)).isEmpty();
        }

        @Test
        void should_return_empty_when_redis_is_unreachable() {
            when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("down"));

            assertThat(shortUrlCache.get(CODE)).isEmpty();
        }
    }
}
