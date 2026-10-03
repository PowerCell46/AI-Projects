package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventValidationServiceImpl implements EventValidationService {

    private final Validator validator;

    @Override
    public void validate(Object event, String eventLabel) {
        Set<ConstraintViolation<Object>> violations = validator.validate(event);
        if (violations.isEmpty()) {
            return;
        }

        String violationMessages = violations
                .stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));

        log.warn("Dropping an invalid {}: {}.", eventLabel, violationMessages);

        throw new InvalidEventException("Invalid " + eventLabel + ": " + violationMessages + ".");
    }
}
