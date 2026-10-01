package com.peter_gerdzhikov.twitter_mail_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.UserFollowedNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFollowedListener {

    private final UserFollowedNotificationService userFollowedNotificationService;

    @KafkaListener(
            topics = "${app.kafka.user-followed.name}",
            containerFactory = "userFollowedListenerContainerFactory"
    )
    public void onMessage(UserFollowedEventDTO event) {
        userFollowedNotificationService.process(event);
    }
}
