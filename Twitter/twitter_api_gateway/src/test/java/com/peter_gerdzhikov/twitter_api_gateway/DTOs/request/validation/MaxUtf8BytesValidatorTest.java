package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;

@ExtendWith(MockitoExtension.class)
class MaxUtf8BytesValidatorTest {

    private static final int MAX_BYTES = 72;

    @Mock
    private MaxUtf8Bytes constraint;

    @Mock
    private ConstraintValidatorContext context;

    private final MaxUtf8BytesValidator validator = new MaxUtf8BytesValidator();

    @BeforeEach
    void setUp() {
        when(constraint.value()).thenReturn(MAX_BYTES);
        validator.initialize(constraint);
    }

    @Test
    void null_isValid() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void ascii_at_the_limit_isValid() {
        assertThat(validator.isValid("x".repeat(72), context)).isTrue();
    }

    @Test
    void ascii_over_the_limit_isInvalid() {
        assertThat(validator.isValid("x".repeat(73), context)).isFalse();
    }

    @Test
    void two_byte_characters_at_the_limit_isValid() {
        assertThat(validator.isValid("é".repeat(36), context)).isTrue();
    }

    @Test
    void two_byte_characters_over_the_limit_isInvalid() {
        assertThat(validator.isValid("é".repeat(37), context)).isFalse();
    }
}
