package com.peter_gerdzhikov.signal_flow_mail_service.configurations;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisScriptConfiguration {

    /**
     * Compare-and-delete: releases a notification inbox claim only if it still holds this attempt's own
     * token, never a newer claim taken out by another consumer after ours expired.
     */
    @Bean
    public RedisScript<Long> releaseNotificationClaimScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/release-notification-claim.lua"));
        script.setResultType(Long.class);

        return script;
    }
}
