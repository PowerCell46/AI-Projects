package com.peter_gerdzhikov.twitter_shared.outbox;

/**
 * One pending outbox entry, behind whatever row or document the service stores it in.
 */
public interface OutboxRecord {

    Object id();

    String topic();

    String messageKey();

    String payload();

    int attempts();

    void attempts(int attempts);

    OutboxStatus status();

    void status(OutboxStatus status);
}
