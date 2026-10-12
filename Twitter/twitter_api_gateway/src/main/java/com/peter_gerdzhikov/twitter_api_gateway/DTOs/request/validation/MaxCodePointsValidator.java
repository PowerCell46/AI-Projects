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

        if (value.toString().indexOf('\0') >= 0) {
            context.disableDefaultConstraintViolation();
            context
                    .buildConstraintViolationWithTemplate("must not contain null characters")
                    .addConstraintViolation();
            return false;
        }

        String stripped = value.toString().strip();

        return stripped.codePointCount(0, stripped.length()) <= maxCodePoints;
    }
}
