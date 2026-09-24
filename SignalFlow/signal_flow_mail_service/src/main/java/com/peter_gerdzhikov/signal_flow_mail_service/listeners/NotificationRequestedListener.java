package com.peter_gerdzhikov.signal_flow_mail_service.listeners;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.TopicNewsNotificationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationRequestedListener {

    private final TopicNewsNotificationService topicNewsNotificationService;

    @KafkaListener(
            topics = "${app.kafka.notification-requested.name}",
            containerFactory = "notificationRequestedListenerContainerFactory")
    public void onMessage(TopicNewsNotificationEventDTO event) {
        topicNewsNotificationService.process(event);
    }
}
