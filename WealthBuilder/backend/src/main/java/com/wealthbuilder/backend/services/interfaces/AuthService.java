package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.DTOs.auth.AuthenticatedUser;
import com.wealthbuilder.backend.DTOs.auth.CurrentUserResponse;
import com.wealthbuilder.backend.DTOs.auth.LoginRequest;
import com.wealthbuilder.backend.DTOs.auth.RegisterRequest;


/**
 * Registration, login, and current-user lookup. Register and login mint a JWT and return it
 * alongside the user; the controller delivers the token in an httpOnly cookie.
 */
public interface AuthService {

    /**
     * Creates a regular user (after ensuring the username is free) and issues a token.
     */
    AuthenticatedUser register(RegisterRequest request);

    /**
     * Validates credentials and issues a token; bad credentials surface as 401.
     */
    AuthenticatedUser login(LoginRequest request);

    /**
     * Returns the authenticated account plus its derived balance.
     */
    CurrentUserResponse me(String username);

    /**
     * Revokes every token previously issued for the user, so logging out kills the session
     * everywhere rather than merely dropping the cookie in the current browser.
     */
    void logout(String username);
}
