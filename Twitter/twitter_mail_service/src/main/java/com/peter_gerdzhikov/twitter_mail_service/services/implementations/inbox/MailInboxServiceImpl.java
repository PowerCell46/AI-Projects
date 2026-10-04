package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.ClaimResult;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailInboxService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MailInboxServiceImpl implements MailInboxService {

    private static final String SENT_VALUE = "SENT";

    private static final String PROCESSING_PREFIX = "PROCESSING:";

    private final Duration claimTtl;

    private final StringRedisTemplate redisTemplate;

    private final RedisScript<Long> releaseMailClaimScript;

    public MailInboxServiceImpl(
            @Value("${app.mail-inbox.claim-ttl}") Duration claimTtl,
            StringRedisTemplate redisTemplate,
            RedisScript<Long> releaseMailClaimScript
    ) {
        this.claimTtl = claimTtl;
        this.redisTemplate = redisTemplate;
        this.releaseMailClaimScript = releaseMailClaimScript;
    }

    @Override
    public ClaimResult claim(String key, String token) {
        Boolean claimed = redisTemplate
                .opsForValue()
                .setIfAbsent(key, PROCESSING_PREFIX + token, claimTtl);

        if (Boolean.TRUE.equals(claimed)) {
            return ClaimResult.CLAIMED;
        }

        String existingValue = redisTemplate.opsForValue().get(key);

        return SENT_VALUE.equals(existingValue) ? ClaimResult.ALREADY_SENT : ClaimResult.HELD;
    }

    @Override
    public void markSent(String key, Duration ttl) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(key, SENT_VALUE, ttl);

        } catch (RuntimeException e) {
            log.error("Failed to mark mail inbox key '{}' as sent after a successful send.", key, e);
        }
    }

    @Override
    public void release(String key, String token) {
        redisTemplate.execute(releaseMailClaimScript, List.of(key), PROCESSING_PREFIX + token);
    }
}
