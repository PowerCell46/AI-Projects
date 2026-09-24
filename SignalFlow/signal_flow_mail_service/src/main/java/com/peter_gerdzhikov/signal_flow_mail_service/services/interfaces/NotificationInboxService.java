package com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces;

import java.util.UUID;

/**
 * Redis-backed dedupe inbox keyed on {@code (newsId, userId)}. The caller generates its own claim
 * {@code token} and holds onto it for the lifetime of one processing attempt, passing the same value to
 * {@link #claim} and, if the attempt fails, to {@link #release} - so a release can never delete a claim
 * it doesn't own.
 */
public interface NotificationInboxService {

    ClaimResult claim(UUID newsId, UUID userId, String token);

    void markSent(UUID newsId, UUID userId);

    void release(UUID newsId, UUID userId, String token);
}
