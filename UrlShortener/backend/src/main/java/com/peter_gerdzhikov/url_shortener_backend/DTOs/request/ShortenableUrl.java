package com.peter_gerdzhikov.url_shortener_backend.DTOs.request;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ShortenableUrlValidator.class)
public @interface ShortenableUrl {

    String message() default "must be an http(s) URL, no longer than 1000 bytes, not pointing to an internal host";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
