package com.peter_gerdzhikov.twitter_mail_service.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserConfirmationRequestedEventDTOValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "a@example.com\r\nBcc: victim@example.com",
            "a@example.com\n",
            "a@example.com,b@example.com",
            "a@example.com;b@example.com",
            "Evil <a@example.com>",
            "<a@example.com>",
            "a b@example.com",
            " a@example.com",
            "a@example.com ",
            "a(comment)@example.com",
            "a@@example.com",
            "\"a b\"@example.com",
            "\"a@b\"@example.com",
            "a@[127.0.0.1]",
            "a@localhost",
            "a%b@example.com"
    })
    void should_reject_an_email_the_gateway_would_not_accept_or_that_could_inject_headers_or_recipients(String email) {
        UserConfirmationRequestedEventDTO event = anEvent(email, "ana_k");

        assertTrue(hasViolationOn(event, "email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana_k\n", "ana_k\r\nBcc:x", "ana<b>", "a-b", "ana k", "ab", "abcdefghijklmnop", "ана_ка"})
    void should_reject_a_username_that_is_not_three_to_fifteen_ascii_word_characters(String username) {
        UserConfirmationRequestedEventDTO event = anEvent("a@example.com", username);

        assertTrue(hasViolationOn(event, "username"));
    }

    @Test
    void should_accept_a_plain_email_with_a_plus_tag_and_a_plain_username() {
        UserConfirmationRequestedEventDTO event = anEvent("ok.name+tag@example.com", "ana_k");

        assertTrue(VALIDATOR.validate(event).isEmpty());
    }

    private static boolean hasViolationOn(UserConfirmationRequestedEventDTO event, String property) {
        return VALIDATOR
                .validate(event)
                .stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(property));
    }

    private static UserConfirmationRequestedEventDTO anEvent(String email, String username) {
        return UserConfirmationRequestedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .email(email)
                .username(username)
                .confirmationUrl("http://localhost:5173/confirm?token=abc")
                .expiresAt(Instant.parse("2026-10-02T12:00:00Z"))
                .build();
    }
}
