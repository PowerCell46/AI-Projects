package com.peter_gerdzhikov.twitter_timeline_service.utilities.paging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.paging.InvalidPageSizeException;

class PageSizeValidatorTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 100})
    void should_accept_a_size_between_1_and_100(int size) {
        assertThatCode(() -> PageSizeValidator.validate(size)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 101, Integer.MAX_VALUE})
    void should_reject_a_size_outside_1_to_100(int size) {
        assertThatThrownBy(() -> PageSizeValidator.validate(size))
                .isInstanceOf(InvalidPageSizeException.class)
                .hasMessage("Page size must be between 1 and 100.");
    }
}
