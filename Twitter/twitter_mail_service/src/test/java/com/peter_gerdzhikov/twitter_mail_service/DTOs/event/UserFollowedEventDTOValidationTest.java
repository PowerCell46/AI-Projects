package com.peter_gerdzhikov.twitter_mail_service.DTOs.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

class UserFollowedEventDTOValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "a@example.com\r\nBcc: victim@example.com",
            "a@example.com\n",
            "a@example.com,b@example.com",
            "Evil <a@example.com>",
            "a b@example.com",
            " a@example.com",
            "a@@example.com",
            "a@localhost",
            "a@.example.com",
            "a@example..com",
            "a@example.com.",
            "a@example.c"
    })
    void should_reject_a_followee_email_that_could_inject_headers_or_recipients(String email) {
        UserFollowedEventDTO event = anEvent(email, "ana", "bob");

        assertTrue(hasViolationOn(event, "followeeEmail"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana\n", "ana\r\nBcc:x", "ana<b>", "a-b", "ana k", "ab", "abcdefghijklmnop", "ана_ка"})
    void should_reject_a_follower_username_that_is_not_three_to_fifteen_ascii_word_characters(String username) {
        UserFollowedEventDTO event = anEvent("a@example.com", username, "bob");

        assertTrue(hasViolationOn(event, "followerUsername"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bob\n", "bob\r\nBcc:x", "bob<b>", "a-b", "bob k", "ab", "abcdefghijklmnop", "ана_ка"})
    void should_reject_a_followee_username_that_is_not_three_to_fifteen_ascii_word_characters(String username) {
        UserFollowedEventDTO event = anEvent("a@example.com", "ana", username);

        assertTrue(hasViolationOn(event, "followeeUsername"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ana@mail.company.com",
            "x@fmi.uni-sofia.bg",
            "y@example.co.uk",
            "a@a.b.c.d.example.org"
    })
    void should_accept_a_followee_email_with_a_subdomain_or_a_second_level_domain(String email) {
        UserFollowedEventDTO event = anEvent(email, "ana", "bob");

        assertTrue(VALIDATOR.validate(event).isEmpty());
    }

    @Test
    void should_accept_a_plain_email_with_a_plus_tag_and_plain_usernames() {
        UserFollowedEventDTO event = anEvent("ok.name+tag@example.com", "ana", "bob");

        assertTrue(VALIDATOR.validate(event).isEmpty());
    }

    private static boolean hasViolationOn(UserFollowedEventDTO event, String property) {
        return VALIDATOR
                .validate(event)
                .stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(property));
    }

    private static UserFollowedEventDTO anEvent(String followeeEmail, String followerUsername, String followeeUsername) {
        return UserFollowedEventDTO.builder()
                .eventId(UUID.randomUUID())
                .followerId(UUID.randomUUID())
                .followeeId(UUID.randomUUID())
                .occurredAt(Instant.parse("2026-10-01T12:00:00Z"))
                .followeeEmail(followeeEmail)
                .followerUsername(followerUsername)
                .followeeUsername(followeeUsername)
                .build();
    }
}
