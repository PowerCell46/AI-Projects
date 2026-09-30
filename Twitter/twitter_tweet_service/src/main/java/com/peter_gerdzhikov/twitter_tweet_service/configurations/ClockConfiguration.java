package com.peter_gerdzhikov.twitter_tweet_service.configurations;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Everything that compares against "now" injects this clock instead of calling {@code Instant.now()},
 * so tests can move time forward instead of sleeping.
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
