package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.confirmations;

public interface UnconfirmedUserCleanupService {

    /**
     * Hard-deletes accounts still unconfirmed after the retention window. Their tokens cascade; queued
     * outbox events are left alone.
     *
     * @return how many users were deleted
     */
    int deleteExpiredUnconfirmedUsers();
}
