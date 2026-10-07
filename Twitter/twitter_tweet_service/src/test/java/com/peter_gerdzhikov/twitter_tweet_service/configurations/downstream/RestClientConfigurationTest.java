package com.peter_gerdzhikov.twitter_tweet_service.configurations.downstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestClientConfigurationTest {

    private static final String GATEWAY_URL = "http://localhost:8080";

    private final RestClientConfiguration configuration = new RestClientConfiguration();

    @Test
    void should_refuse_an_internal_secret_shorter_than_32_bytes() {
        assertThatThrownBy(() -> configuration.gatewayRestClient(RestClient.builder(), GATEWAY_URL, "x".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void should_accept_an_internal_secret_of_32_bytes() {
        assertThat(configuration.gatewayRestClient(RestClient.builder(), GATEWAY_URL, "x".repeat(32))).isNotNull();
    }

    @Test
    void should_count_bytes_not_characters_for_the_internal_secret() {
        assertThat(configuration.gatewayRestClient(RestClient.builder(), GATEWAY_URL, "é".repeat(16))).isNotNull();
    }
}
