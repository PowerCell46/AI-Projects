package com.peter_gerdzhikov.twitter_shared.paging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvalidPageSizeExceptionTest {

    @Test
    void should_carry_the_shared_message_without_arguments() {
        assertThat(new InvalidPageSizeException().getMessage()).isEqualTo(InvalidPageSizeException.MESSAGE);
    }

    @Test
    void should_format_the_bounds_into_the_message() {
        assertThat(new InvalidPageSizeException(1, 100).getMessage())
                .isEqualTo("Page size must be between 1 and 100.");
    }
}
