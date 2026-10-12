package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.usernames;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Rejects the usernames the platform reserves for itself, so nobody can register as e.g. {@code admin}
 * and impersonate official communication.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = NotReservedUsernameValidator.class)
public @interface NotReservedUsername {

    String message() default "is reserved";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
