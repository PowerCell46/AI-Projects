package com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.InvalidNotificationEventException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.NotificationClaimHeldException;

public interface TopicNewsNotificationService {

    /**
     * Validates, claims the Redis inbox, sends and marks sent - or releases the claim and rethrows on a
     * send failure.
     *
     * @throws InvalidNotificationEventException on a validation failure - not retryable
     * @throws NotificationClaimHeldException    when another attempt already holds the inbox claim - retryable
     */
    void process(TopicNewsNotificationEventDTO event);
}
