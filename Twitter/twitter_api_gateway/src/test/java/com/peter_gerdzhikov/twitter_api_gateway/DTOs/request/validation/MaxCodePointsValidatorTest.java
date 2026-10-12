package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;

@ExtendWith(MockitoExtension.class)
class MaxCodePointsValidatorTest {

    private static final int MAX_CODE_POINTS = 160;

    @Mock
    private MaxCodePoints constraint;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintViolationBuilder violationBuilder;

    private final MaxCodePointsValidator validator = new MaxCodePointsValidator();

    @BeforeEach
    void setUp() {
        when(constraint.value()).thenReturn(MAX_CODE_POINTS);
        validator.initialize(constraint);
    }

    @Test
    void null_isValid() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void short_text_isValid() {
        assertThat(validator.isValid("Hello", context)).isTrue();
    }

    @Test
    void text_at_the_limit_isValid() {
        assertThat(validator.isValid("x".repeat(160), context)).isTrue();
    }

    @Test
    void text_over_the_limit_isInvalid() {
        assertThat(validator.isValid("x".repeat(161), context)).isFalse();
    }

    @Test
    void emoji_at_the_limit_isValid() {
        assertThat(validator.isValid("😀".repeat(160), context)).isTrue();
    }

    @Test
    void nul_byte_isInvalid() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        assertThat(validator.isValid("a\0b", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate("must not contain null characters");
        verify(violationBuilder).addConstraintViolation();
    }

    @Test
    void padded_nul_byte_isInvalid() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);

        assertThat(validator.isValid("  \0  ", context)).isFalse();
    }
}
