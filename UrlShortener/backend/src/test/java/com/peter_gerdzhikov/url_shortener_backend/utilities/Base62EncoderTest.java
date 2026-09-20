package com.peter_gerdzhikov.url_shortener_backend.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Base62EncoderTest {

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 62L, Long.MAX_VALUE})
    void encode_never_returns_an_empty_string(long value) {
        assertThat(Base62Encoder.encode(value)).isNotEmpty();
    }

    @Test
    void encode_never_pads_a_positive_value_with_a_leading_zero() {
        LongStream.rangeClosed(1, 10_000)
                .forEach(value -> assertThat(Base62Encoder.encode(value)).doesNotStartWith("0"));
    }
}
