package com.peter_gerdzhikov.twitter_mail_service.services.interfaces.inbox;

import java.time.Duration;

/**
 * Redis-backed note that an email was already sent, shared by every email type. The caller builds its own
 * dedupe {@code key} and generates a claim {@code token} that it holds for the lifetime of one processing
 * attempt, passing the same token to {@link #claim} and, if the attempt fails, to {@link #release} - so a
 * release can never delete a claim it doesn't own.
 */
public interface MailInboxService {

    ClaimResult claim(String key, String token);

    void markSent(String key, Duration ttl);

    void release(String key, String token);
}
