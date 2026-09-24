package com.peter_gerdzhikov.signal_flow_api_gateway.jobs;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.NotificationOutboxPublisherService;

import lombok.RequiredArgsConstructor;

/**
 * Drains {@code PENDING} {@code notification_outbox} rows to Kafka. Single instance today, no row-claiming
 * - two instances polling concurrently could both pick and publish the same row before either deletes it.
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxPublisherJob {

    private final NotificationOutboxPublisherService notificationOutboxPublisherService;

    @Scheduled(fixedDelayString = "${app.notification-outbox.poll-fixed-delay-ms}")
    public void publishPendingNotifications() {
        notificationOutboxPublisherService.publishPendingNotifications();
    }
}
