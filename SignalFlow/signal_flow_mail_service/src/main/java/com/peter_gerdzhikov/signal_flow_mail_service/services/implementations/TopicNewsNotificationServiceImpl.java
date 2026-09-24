package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.NotificationClaimHeldException;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.ClaimResult;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.NotificationInboxService;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.TopicNewsMailService;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.TopicNewsNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TopicNewsNotificationServiceImpl implements TopicNewsNotificationService {

    private final Validator validator;

    private final TopicNewsMailService topicNewsMailService;

    private final NotificationInboxService notificationInboxService;

    public TopicNewsNotificationServiceImpl(
            Validator validator,
            TopicNewsMailService topicNewsMailService,
            NotificationInboxService notificationInboxService
    ) {
        this.validator = validator;
        this.topicNewsMailService = topicNewsMailService;
        this.notificationInboxService = notificationInboxService;
    }

    @Override
    public void process(TopicNewsNotificationEventDTO event) {
        validate(event);

        String token = UUID.randomUUID().toString();
        ClaimResult claimResult = notificationInboxService.claim(event.getNewsId(), event.getUserId(), token);

        switch (claimResult) {
            case ALREADY_SENT -> log.info("Notification for newsId '{}', userId '{}' was already sent; skipping.",
                    event.getNewsId(), event.getUserId());
            case HELD -> throw new NotificationClaimHeldException(
                    "Notification inbox claim for newsId '" + event.getNewsId() + "', userId '" + event.getUserId() + "' is held.");
            case CLAIMED -> sendAndMark(event, token);
        }
    }

    private void sendAndMark(TopicNewsNotificationEventDTO event, String token) {
        try {
            topicNewsMailService.send(event);

        } catch (RuntimeException e) {
            notificationInboxService.release(event.getNewsId(), event.getUserId(), token);
            throw e;
        }

        try {
            notificationInboxService.markSent(event.getNewsId(), event.getUserId());

        } catch (RuntimeException e) {
            log.error("Failed to mark notification as sent for newsId '{}', userId '{}' after a successful send.",
                    event.getNewsId(), event.getUserId(), e);
        }
    }

    private void validate(TopicNewsNotificationEventDTO event) {
        Set<ConstraintViolation<TopicNewsNotificationEventDTO>> violations = validator.validate(event);
        if (violations.isEmpty()) {
            return;
        }

        String violationMessages = violations.stream()
                .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                .collect(Collectors.joining(", "));
        throw new InvalidNotificationEventException("Invalid topic news notification event: " + violationMessages + ".");
    }
}
