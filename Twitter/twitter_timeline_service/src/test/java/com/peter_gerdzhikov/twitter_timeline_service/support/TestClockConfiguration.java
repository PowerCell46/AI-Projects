package com.peter_gerdzhikov.twitter_timeline_service.support;

import java.time.Clock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfiguration {

    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
