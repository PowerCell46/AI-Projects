package com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.TransientMailDeliveryException;

/**
 * Builds the MIME message (a {@code text/plain} and a {@code text/html} part, UTF-8) and sends it over SMTP.
 * Shared by every email type.
 */
public interface MailDeliveryService {

    /**
     * @throws PermanentMailDeliveryException on a failure that will never succeed on retry
     * @throws TransientMailDeliveryException on a failure that may succeed on retry
     */
    void send(String recipient, String subject, String html, String text);
}
