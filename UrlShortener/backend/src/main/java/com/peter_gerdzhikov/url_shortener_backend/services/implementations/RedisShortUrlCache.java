package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlCache;

import lombok.extern.slf4j.Slf4j;

/**
 * Never the source of truth: any Redis failure is caught here, logged and turned into a cache
 * miss, so a redirect always falls back to Mongo instead of failing the request.
 */
@Slf4j
@Service
public class RedisShortUrlCache implements ShortUrlCache {

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisShortUrlCache(StringRedisTemplate redisTemplate, @Value("${url-shortener.cache.ttl}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public void put(String code, String originalUrl) {
        try {
            redisTemplate.opsForValue().set(code, originalUrl, ttl);

        } catch (DataAccessException e) {
            log.warn("Redis unavailable while caching code '{}'; skipping cache write.", code, e);
        }
    }

    @Override
    public Optional<String> get(String code) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(code));

        } catch (DataAccessException e) {
            log.warn("Redis unavailable while reading code '{}'; returning empty.", code, e);
            return Optional.empty();
        }
    }
}
