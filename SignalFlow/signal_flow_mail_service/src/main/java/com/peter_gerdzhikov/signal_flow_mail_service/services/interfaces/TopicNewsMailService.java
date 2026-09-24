package com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.TransientMailDeliveryException;

public interface TopicNewsMailService {

    /**
     * @throws PermanentMailDeliveryException on a failure that will never succeed on retry
     * @throws TransientMailDeliveryException on a failure that may succeed on retry
     */
    void send(TopicNewsNotificationEventDTO event);
}
