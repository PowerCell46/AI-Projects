package com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces;

/**
 * Outcome of {@link NotificationInboxService#claim}. {@code HELD} covers both a genuinely live claim
 * held by another consumer and a crashed holder whose claim key hasn't expired yet - either way, the
 * caller retries later rather than treating it as a permanent failure.
 */
public enum ClaimResult {

    CLAIMED,

    ALREADY_SENT,

    HELD
}
