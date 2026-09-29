package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxUtf8BytesValidator implements ConstraintValidator<MaxUtf8Bytes, CharSequence> {

    private int maxBytes;

    @Override
    public void initialize(MaxUtf8Bytes constraint) {
        maxBytes = constraint.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.toString().getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }
}
