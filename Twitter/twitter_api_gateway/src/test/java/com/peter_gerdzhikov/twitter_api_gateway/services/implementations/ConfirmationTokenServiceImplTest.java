package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConfirmationTokenServiceImplTest {

    private static final String BASE64_URL_43 = "[A-Za-z0-9_-]{43}";

    private final ConfirmationTokenServiceImpl tokenService = new ConfirmationTokenServiceImpl();

    @Test
    void should_generate_a_43_character_base64url_token() {
        assertThat(tokenService.generateRawToken()).matches(BASE64_URL_43);
    }

    @Test
    void should_generate_a_different_token_each_time() {
        assertThat(tokenService.generateRawToken()).isNotEqualTo(tokenService.generateRawToken());
    }

    @Test
    void should_hash_the_same_token_to_the_same_value() {
        assertThat(tokenService.hash("some-token")).isEqualTo(tokenService.hash("some-token"));
    }

    @Test
    void should_hash_different_tokens_to_different_values() {
        assertThat(tokenService.hash("token-a")).isNotEqualTo(tokenService.hash("token-b"));
    }

    @Test
    void should_hash_to_a_43_character_base64url_value_that_differs_from_the_token() {
        String token = tokenService.generateRawToken();

        String hash = tokenService.hash(token);

        assertThat(hash).matches(BASE64_URL_43).isNotEqualTo(token);
    }

    @Test
    void should_hash_with_sha_256_over_the_utf_8_bytes_of_the_token() {
        assertThat(tokenService.hash("abc")).isEqualTo("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0");
    }
}
