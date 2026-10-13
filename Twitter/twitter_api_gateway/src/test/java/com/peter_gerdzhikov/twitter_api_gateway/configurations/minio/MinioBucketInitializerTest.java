package com.peter_gerdzhikov.twitter_api_gateway.configurations.minio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

class MinioBucketInitializerTest {

    @Test
    void should_be_registered_as_a_component() {
        assertThat(MinioBucketInitializer.class.getAnnotation(Component.class)).isNotNull();
    }

    @Test
    void should_run_first_of_all_runners() {
        Order order = MinioBucketInitializer.class.getAnnotation(Order.class);

        assertThat(order).isNotNull();
        assertThat(order.value()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
