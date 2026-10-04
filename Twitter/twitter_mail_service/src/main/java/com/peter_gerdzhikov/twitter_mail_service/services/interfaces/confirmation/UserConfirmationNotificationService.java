package com.peter_gerdzhikov.twitter_mail_service.services.interfaces.confirmation;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserConfirmationRequestedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.MailClaimHeldException;

public interface UserConfirmationNotificationService {

    /**
     * Validates, skips an already-expired link, renders, claims the inbox, sends and marks sent - or releases
     * the claim and rethrows on a failure to send.
     *
     * @throws InvalidMailEventException on a validation failure - not retryable
     * @throws MailClaimHeldException    when another attempt already holds the inbox claim - retryable
     */
    void process(UserConfirmationRequestedEventDTO event);
}
