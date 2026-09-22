package com.peter_gerdzhikov.url_shortener_backend.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ShortCodeFormatTest {

    @ParameterizedTest
    @ValueSource(strings = {"nOpE42", "0", "aZ9", "1234567890"})
    void isValid_returns_true_for_base62_codes(String code) {
        assertThat(ShortCodeFormat.isValid(code)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", ".env", "wp-config.php", "backup.sql", "docker-compose.yml",
            ".ssh", "favicon.ico", "a b"})
    void isValid_returns_false_for_non_base62_input(String code) {
        assertThat(ShortCodeFormat.isValid(code)).isFalse();
    }
}
