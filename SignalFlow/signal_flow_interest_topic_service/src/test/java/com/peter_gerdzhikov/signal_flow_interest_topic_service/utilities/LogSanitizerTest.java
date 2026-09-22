package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LogSanitizerTest {

    @ParameterizedTest
    @CsvSource({
            "rust,rust",
            "'rust\nERROR forged line','rust_ERROR forged line'",
            "'rust\r\nERROR forged line','rust__ERROR forged line'",
            "'',''"
    })
    void sanitize_stripsCarriageReturnsAndNewlines(String input, String expected) {
        assertThat(LogSanitizer.sanitize(input)).isEqualTo(expected);
    }
}
