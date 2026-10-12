package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.ages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;

@ExtendWith(MockitoExtension.class)
class MinimumAgeValidatorTest {

    private static final int MINIMUM_AGE = 13;

    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private MinimumAge constraint;

    @Mock
    private ConstraintValidatorContext context;

    private final MinimumAgeValidator validator = new MinimumAgeValidator(TODAY);

    @BeforeEach
    void setUp() {
        when(constraint.value()).thenReturn(MINIMUM_AGE);
        validator.initialize(constraint);
    }

    @Test
    void null_isValid() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void thirteenth_birthday_today_isValid() {
        assertThat(validator.isValid(LocalDate.of(2013, 10, 12), context)).isTrue();
    }

    @Test
    void one_day_short_of_thirteen_isInvalid() {
        assertThat(validator.isValid(LocalDate.of(2013, 10, 13), context)).isFalse();
    }

    @Test
    void older_than_thirteen_isValid() {
        assertThat(validator.isValid(LocalDate.of(1990, 5, 17), context)).isTrue();
    }

    @Test
    void implausibly_old_isValid() {
        assertThat(validator.isValid(LocalDate.of(1850, 1, 1), context)).isTrue();
    }
}
