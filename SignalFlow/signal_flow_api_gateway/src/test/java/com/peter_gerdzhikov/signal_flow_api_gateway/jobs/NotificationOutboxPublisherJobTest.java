package com.peter_gerdzhikov.signal_flow_api_gateway.jobs;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.NotificationOutboxPublisherService;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherJobTest {

    @Mock
    private NotificationOutboxPublisherService notificationOutboxPublisherService;

    @InjectMocks
    private NotificationOutboxPublisherJob notificationOutboxPublisherJob;

    @Test
    void should_publish_pending_notifications_when_triggered() {
        notificationOutboxPublisherJob.publishPendingNotifications();

        verify(notificationOutboxPublisherService).publishPendingNotifications();
    }
}
