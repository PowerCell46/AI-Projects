package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Caps a string by its Unicode code points after stripping, the way the frontend counts it. {@code @Size}
 * counts UTF-16 units, so an emoji would cost two and a full 160-emoji bio would be rejected.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = MaxCodePointsValidator.class)
public @interface MaxCodePoints {

    int value();

    String message() default "must be at most {value} characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
