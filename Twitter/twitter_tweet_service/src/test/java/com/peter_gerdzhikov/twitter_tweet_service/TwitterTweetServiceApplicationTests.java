package com.peter_gerdzhikov.twitter_tweet_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class TwitterTweetServiceApplicationTests extends AbstractMinioIntegrationTest {

    @Test
    void contextLoads() {
    }
}
