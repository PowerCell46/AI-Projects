package com.peter_gerdzhikov.twitter_tweet_service.configurations.retry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.peter_gerdzhikov.twitter_tweet_service.utilities.retry.MonotonicTimeSource;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.retry.Sleeper;

@Configuration
public class RetryConfiguration {

    @Bean
    public MonotonicTimeSource monotonicTimeSource() {
        return System::nanoTime;
    }

    @Bean
    public Sleeper sleeper() {
        return duration -> Thread.sleep(duration);
    }
}
