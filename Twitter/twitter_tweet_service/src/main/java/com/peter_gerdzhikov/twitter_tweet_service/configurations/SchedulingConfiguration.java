package com.peter_gerdzhikov.twitter_tweet_service.configurations;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kept off the application class so slice tests don't pick up scheduling.
 */
@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
