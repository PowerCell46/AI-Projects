package com.peter_gerdzhikov.twitter_tweet_service.configurations.minio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

class MinioBucketInitializerTest {

    @Test
    void should_be_registered_as_a_component() {
        assertThat(MinioBucketInitializer.class.getAnnotation(Component.class)).isNotNull();
    }
}
