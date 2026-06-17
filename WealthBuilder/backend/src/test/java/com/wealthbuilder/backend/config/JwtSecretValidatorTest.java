package com.wealthbuilder.backend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;


/**
 * Unit test for the fail-fast guard around the public development JWT secret. The Spring
 * {@link Environment} is mocked so each test pins the active profiles precisely.
 */
@ExtendWith(MockitoExtension.class)
class JwtSecretValidatorTest {

    private static final String DEV_DEFAULT_SECRET = "dev-only-insecure-secret-change-me-0123456789abcdef";

    private static final String CUSTOM_SECRET = "a-private-production-secret-that-is-long-enough-0123456789";

    @Mock
    private Environment environment;

    @Test
    void should_Throw_When_DevSecretUnderNonDevProfile() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"prod"});
        final JwtSecretValidator validator = validatorWith(DEV_DEFAULT_SECRET);

        assertThatThrownBy(validator::rejectDevSecretOutsideDevelopment)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_NotThrow_When_DevSecretAndNoActiveProfile() {
        given(environment.getActiveProfiles()).willReturn(new String[0]);
        final JwtSecretValidator validator = validatorWith(DEV_DEFAULT_SECRET);

        assertThatCode(validator::rejectDevSecretOutsideDevelopment).doesNotThrowAnyException();
    }

    @Test
    void should_NotThrow_When_DevSecretUnderDevProfile() {
        given(environment.getActiveProfiles()).willReturn(new String[]{"dev"});
        final JwtSecretValidator validator = validatorWith(DEV_DEFAULT_SECRET);

        assertThatCode(validator::rejectDevSecretOutsideDevelopment).doesNotThrowAnyException();
    }

    @Test
    void should_NotThrow_When_CustomSecretUnderProdProfile() {
        final JwtSecretValidator validator = validatorWith(CUSTOM_SECRET);

        assertThatCode(validator::rejectDevSecretOutsideDevelopment).doesNotThrowAnyException();
    }

    private JwtSecretValidator validatorWith(String secret) {
        final AppProperties.Jwt jwt = new AppProperties.Jwt(secret, Duration.ofMinutes(30));
        final AppProperties.Moderator moderator = new AppProperties.Moderator("mod", "pw");
        final AppProperties appProperties = new AppProperties("http://localhost", jwt, moderator);

        return new JwtSecretValidator(appProperties, environment);
    }
}
