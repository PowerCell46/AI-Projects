package com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmtpCredentialsValidatorTest {

    @Nested
    class Constructor {

        @Test
        void should_start_when_auth_is_on_and_both_credentials_are_set() {
            assertDoesNotThrow(() -> new SmtpCredentialsValidator(true, "sender@example.com", "app-password"));
        }

        @ParameterizedTest
        @CsvSource(value = {
                "'', app-password",
                "sender@example.com, ''",
                "'', ''",
                "'   ', app-password",
                "sender@example.com, '   '"
        })
        void should_fail_to_start_naming_both_settings_when_auth_is_on_and_a_credential_is_blank(
                String username, String password
        ) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> new SmtpCredentialsValidator(true, username, password));

            assertTrue(thrown.getMessage().contains("MAIL_USERNAME"));
            assertTrue(thrown.getMessage().contains("MAIL_PASSWORD"));
        }

        @Test
        void should_not_leak_the_credentials_when_it_fails() {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> new SmtpCredentialsValidator(true, "sender@example.com", ""));

            assertFalse(thrown.getMessage().contains("sender@example.com"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void should_start_when_auth_is_off_and_the_credentials_are_blank(String blank) {
            assertDoesNotThrow(() -> new SmtpCredentialsValidator(false, blank, blank));
        }
    }
}
