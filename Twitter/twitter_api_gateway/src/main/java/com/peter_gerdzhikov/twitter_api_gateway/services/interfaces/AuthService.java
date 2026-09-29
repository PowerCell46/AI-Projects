package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;

public interface AuthService {

    /**
     * Creates a disabled user and requests its confirmation email, in one transaction.
     */
    User register(RegisterRequestDTO request);

    /**
     * Checks the credentials of a confirmed user. The identifier is an email when it contains {@code @},
     * otherwise a username.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.InvalidCredentialsException
     *         for an unknown identifier or a wrong password
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.auth.EmailNotConfirmedException
     *         when the password is right but the account is not confirmed
     */
    User login(LoginRequestDTO request);
}
