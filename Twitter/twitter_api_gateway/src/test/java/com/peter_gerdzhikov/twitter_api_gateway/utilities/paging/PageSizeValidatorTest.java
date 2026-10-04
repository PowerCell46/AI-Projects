package com.peter_gerdzhikov.twitter_api_gateway.utilities.paging;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;

class PageSizeValidatorTest {

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void validate_acceptsTheBoundarySizes(int size) {
        assertThatCode(() -> PageSizeValidator.validate(size)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 101})
    void validate_rejectsSizesOutsideTheRangeWithThePageSizeMessage(int size) {
        assertThatThrownBy(() -> PageSizeValidator.validate(size))
                .isInstanceOf(InvalidPageSizeException.class)
                .hasMessage("Page size must be between 1 and 100.");
    }
}
