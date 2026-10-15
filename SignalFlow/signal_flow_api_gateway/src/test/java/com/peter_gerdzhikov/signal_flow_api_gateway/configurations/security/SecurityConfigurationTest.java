package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SecurityConfigurationTest {

    private SecurityConfiguration securityConfiguration;

    @BeforeEach
    void setUp() {
        securityConfiguration = new SecurityConfiguration(null, null);
    }

    @Test
    void should_refuse_a_jwt_secret_shorter_than_32_bytes() {
        assertThatThrownBy(() -> securityConfiguration.jwtSecretKey("a".repeat(31)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void should_refuse_a_short_secret_even_when_it_has_many_characters_of_one_byte_each() {
        assertThatThrownBy(() -> securityConfiguration.jwtSecretKey("abc"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_accept_a_jwt_secret_of_exactly_32_bytes() {
        SecretKey key = securityConfiguration.jwtSecretKey("a".repeat(32));

        assertThat(key.getEncoded()).hasSize(32);
    }
}
