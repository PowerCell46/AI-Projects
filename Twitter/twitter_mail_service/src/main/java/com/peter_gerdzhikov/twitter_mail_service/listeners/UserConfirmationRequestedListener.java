package com.peter_gerdzhikov.twitter_mail_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.UserConfirmationNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserConfirmationRequestedListener {

    private final UserConfirmationNotificationService userConfirmationNotificationService;

    @KafkaListener(
            topics = "${app.kafka.user-confirmation-requested.name}",
            containerFactory = "userConfirmationRequestedListenerContainerFactory"
    )
    public void onMessage(UserConfirmationRequestedEventDTO event) {
        userConfirmationNotificationService.process(event);
    }
}
