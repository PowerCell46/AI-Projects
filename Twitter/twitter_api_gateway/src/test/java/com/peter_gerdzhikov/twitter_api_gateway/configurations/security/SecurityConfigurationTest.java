package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration = new SecurityConfiguration(null, null);

    @Test
    void should_refuse_a_jwt_secret_shorter_than_32_bytes() {
        assertThatThrownBy(() -> configuration.jwtSecretKey("x".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void should_accept_a_jwt_secret_of_32_bytes() {
        assertThat(configuration.jwtSecretKey("x".repeat(32)).getAlgorithm()).isEqualTo("HmacSHA256");
    }

    @Test
    void should_count_bytes_not_characters_for_the_jwt_secret() {
        assertThat(configuration.jwtSecretKey("é".repeat(16)).getEncoded()).hasSize(32);
    }
}
