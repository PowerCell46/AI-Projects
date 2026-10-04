package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.MailEventValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailEventValidationServiceImpl implements MailEventValidationService {

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
                .collect(Collectors.joining(", "));

        log.warn("Dropping an invalid {}: {}.", eventLabel, violationMessages);

        throw new InvalidMailEventException("Invalid " + eventLabel + ": " + violationMessages + ".");
    }
}
