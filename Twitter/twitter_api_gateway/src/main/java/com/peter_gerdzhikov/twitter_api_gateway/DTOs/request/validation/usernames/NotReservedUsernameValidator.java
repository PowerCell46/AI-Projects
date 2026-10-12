package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.usernames;

import java.util.Locale;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotReservedUsernameValidator implements ConstraintValidator<NotReservedUsername, String> {

    private static final Set<String> RESERVED_USERNAMES = Set.of(
            "abuse",
            "admin",
            "administrator",
            "api",
            "help",
            "info",
            "mail",
            "me",
            "postmaster",
            "root",
            "security",
            "settings",
            "support",
            "system",
            "twitter",
            "webmaster");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        return !RESERVED_USERNAMES.contains(value.toLowerCase(Locale.ROOT));
    }
}
