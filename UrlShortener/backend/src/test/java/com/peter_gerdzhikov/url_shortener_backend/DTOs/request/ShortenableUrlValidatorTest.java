package com.peter_gerdzhikov.url_shortener_backend.DTOs.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShortenableUrlValidatorTest {

    private final ShortenableUrlValidator validator = new ShortenableUrlValidator();

    @Test
    void isValid_treats_a_blank_value_as_valid_so_notBlank_reports_it() {
        assertThat(validator.isValid("", null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com", "https://example.com/some/page?q=1"})
    void isValid_accepts_http_and_https_urls(String url) {
        assertThat(validator.isValid(url, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com", "javascript:alert(1)", "file:///etc/passwd", "data:text/html,x"})
    void isValid_rejects_a_non_http_scheme(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }

    @Test
    void isValid_rejects_a_url_containing_a_control_character() {
        assertThat(validator.isValid("http://example.com/\r\nSet-Cookie: x", null)).isFalse();
    }

    @Test
    void isValid_rejects_a_url_over_the_1000_byte_cap() {
        String tooLongUrl = "http://example.com/" + "a".repeat(1000);

        assertThat(validator.isValid(tooLongUrl, null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://169.254.169.254/latest/meta-data", "http://10.0.0.5"})
    void isValid_rejects_an_internal_host(String url) {
        assertThat(validator.isValid(url, null)).isFalse();
    }
}
