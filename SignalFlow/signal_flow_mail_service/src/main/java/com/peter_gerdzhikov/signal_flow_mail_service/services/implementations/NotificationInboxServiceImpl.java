package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.ClaimResult;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.NotificationInboxService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationInboxServiceImpl implements NotificationInboxService {

    private static final String KEY_FORMAT = "mail:notification:%s:%s";

    private static final String PROCESSING_PREFIX = "PROCESSING:";

    private static final String SENT_VALUE = "SENT";

    private final long claimTtlSeconds;

    private final long sentTtlSeconds;

    private final StringRedisTemplate redisTemplate;

    private final RedisScript<Long> releaseNotificationClaimScript;

    public NotificationInboxServiceImpl(
            @Value("${app.notification-inbox.claim-ttl-seconds}") long claimTtlSeconds,
            @Value("${app.notification-inbox.sent-ttl-seconds}") long sentTtlSeconds,
            StringRedisTemplate redisTemplate,
            RedisScript<Long> releaseNotificationClaimScript
    ) {
        this.claimTtlSeconds = claimTtlSeconds;
        this.sentTtlSeconds = sentTtlSeconds;
        this.redisTemplate = redisTemplate;
        this.releaseNotificationClaimScript = releaseNotificationClaimScript;
    }

    @Override
    public ClaimResult claim(UUID newsId, UUID userId, String token) {
        String key = key(newsId, userId);
        Boolean claimed = redisTemplate
                .opsForValue()
                .setIfAbsent(key, PROCESSING_PREFIX + token, Duration.ofSeconds(claimTtlSeconds));

        if (Boolean.TRUE.equals(claimed)) {
            return ClaimResult.CLAIMED;
        }

        String existingValue = redisTemplate.opsForValue().get(key);
        return SENT_VALUE.equals(existingValue) ? ClaimResult.ALREADY_SENT : ClaimResult.HELD;
    }

    @Override
    public void markSent(UUID newsId, UUID userId) {
        String key = key(newsId, userId);
        try {
            redisTemplate.opsForValue().set(key, SENT_VALUE, Duration.ofSeconds(sentTtlSeconds));

        } catch (RuntimeException e) {
            log.error("Failed to mark notification inbox key '{}' as sent after a successful send.", key, e);
        }
    }

    @Override
    public void release(UUID newsId, UUID userId, String token) {
        String key = key(newsId, userId);
        redisTemplate.execute(releaseNotificationClaimScript, List.of(key), PROCESSING_PREFIX + token);
    }

    private String key(UUID newsId, UUID userId) {
        return String.format(KEY_FORMAT, newsId, userId);
    }
}
