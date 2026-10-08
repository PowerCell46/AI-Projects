package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaxCodePointsValidator implements ConstraintValidator<MaxCodePoints, CharSequence> {

    private int maxCodePoints;

    @Override
    public void initialize(MaxCodePoints constraint) {
        maxCodePoints = constraint.value();
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String stripped = value.toString().strip();

        return stripped.codePointCount(0, stripped.length()) <= maxCodePoints;
    }
}
