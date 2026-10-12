package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.validation.ages;

import java.time.Clock;
import java.time.LocalDate;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Prototype scope: each @MinimumAge declaration needs its own minimumAge, which initialize() sets per instance.
@Component
@RequiredArgsConstructor
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MinimumAgeValidator implements ConstraintValidator<MinimumAge, LocalDate> {

    private int minimumAge;

    private final Clock clock;

    @Override
    public void initialize(MinimumAge constraint) {
        minimumAge = constraint.value();
    }

    @Override
    public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        LocalDate today = LocalDate.now(clock);

        return !value.isAfter(today.minusYears(minimumAge));
    }
}
