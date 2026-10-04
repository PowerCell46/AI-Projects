package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;

public interface ConfirmationRequestService {

    /**
     * Stores a fresh confirmation token for the user and queues the event that carries the raw token to
     * the mail service. Joins the caller's transaction, so a rollback drops the token and the event
     * together.
     */
    void requestConfirmation(User user);
}
