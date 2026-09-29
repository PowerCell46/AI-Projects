package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Caps a string by its UTF-8 size rather than its character count. bcrypt only accepts 72 bytes, so a
 * password that passes {@code @Size(max = 72)} with multi-byte characters would otherwise blow up in the
 * encoder as a 500.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "must be at most {value} bytes when UTF-8 encoded";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
