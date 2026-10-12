package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.ages;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Requires a birthdate to be at least {@code value} years before today. There is deliberately no upper
 * bound: an implausible age is harmless, while a wrong ceiling would lock out real users.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = MinimumAgeValidator.class)
public @interface MinimumAge {

    int value();

    String message() default "must be at least {value} years old";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
