package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.usernames;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;

@ExtendWith(MockitoExtension.class)
class NotReservedUsernameValidatorTest {

    @Mock
    private ConstraintValidatorContext context;

    private final NotReservedUsernameValidator validator = new NotReservedUsernameValidator();

    @Test
    void null_isValid() {
        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    void blank_isValid() {
        assertThat(validator.isValid("", context)).isTrue();
    }

    @Test
    void admin_isInvalid() {
        assertThat(validator.isValid("admin", context)).isFalse();
    }

    @Test
    void mixed_case_ADMIN_isInvalid() {
        assertThat(validator.isValid("AdMiN", context)).isFalse();
    }

    @Test
    void admin_with_a_suffix_isValid() {
        assertThat(validator.isValid("adminx", context)).isTrue();
    }

    @Test
    void ordinary_name_isValid() {
        assertThat(validator.isValid("ana", context)).isTrue();
    }
}
