package com.peter_gerdzhikov.twitter_mail_service.services.interfaces;

import com.peter_gerdzhikov.twitter_mail_service.DTOs.event.UserFollowedEventDTO;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.InvalidMailEventException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.MailClaimHeldException;

public interface UserFollowedNotificationService {

    /**
     * Validates, renders, claims the follower-followee pair in the inbox, sends and marks the pair sent for the
     * follow window - or releases the claim and rethrows on a failure to send. A pair already sent inside the
     * window sends nothing, whatever its {@code eventId}.
     *
     * @throws InvalidMailEventException on a validation failure - not retryable
     * @throws MailClaimHeldException    when another attempt already holds the inbox claim - retryable
     */
    void process(UserFollowedEventDTO event);
}
