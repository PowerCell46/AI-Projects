package com.peter_gerdzhikov.twitter_mail_service.services.implementations.inbox;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailEventValidationServiceImplTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final MailEventValidationServiceImpl validationService = new MailEventValidationServiceImpl(VALIDATOR);

    @Nested
    class Validate {

        @Test
        void should_return_silently_when_the_event_is_valid() {
            EventFixture event = new EventFixture("ana", "ana@example.com");

            assertDoesNotThrow(() -> validationService.validate(event, "follow"));
        }

        @Test
        void should_throw_naming_the_property_and_the_message_when_the_event_is_invalid() {
            EventFixture event = new EventFixture("", "ana@example.com");

            InvalidMailEventException thrown =
                    assertThrows(InvalidMailEventException.class, () -> validationService.validate(event, "follow"));

            assertEquals("Invalid follow: name must not be blank.", thrown.getMessage());
        }

        @Test
        void should_join_every_violation_when_the_event_has_several() {
            EventFixture event = new EventFixture("", "");

            InvalidMailEventException thrown =
                    assertThrows(InvalidMailEventException.class, () -> validationService.validate(event, "follow"));

            assertTrue(thrown.getMessage().contains("name must not be blank"));
            assertTrue(thrown.getMessage().contains("email must not be blank"));
        }
    }

    private static class EventFixture {

        @NotBlank(message = "must not be blank")
        private final String name;

        @NotBlank(message = "must not be blank")
        private final String email;

        private EventFixture(String name, String email) {
            this.name = name;
            this.email = email;
        }
    }
}
