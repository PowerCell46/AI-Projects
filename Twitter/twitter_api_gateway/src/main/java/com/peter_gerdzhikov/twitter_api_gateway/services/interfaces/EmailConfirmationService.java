package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces;

public interface EmailConfirmationService {

    /**
     * Enables the token's user and consumes the token.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidConfirmationTokenException
     *         when the token is unknown, already used or expired
     */
    void confirm(String rawToken);

    /**
     * Issues a fresh token and event for a pending user. Silent no-op for an unknown or confirmed email and
     * inside the cooldown, so the caller cannot tell those apart.
     */
    void resend(String email);
}
