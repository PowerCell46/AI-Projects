package com.wealthbuilder.backend.services.interfaces;

import com.wealthbuilder.backend.DTOs.auth.AuthResponse;
import com.wealthbuilder.backend.DTOs.auth.CurrentUserResponse;
import com.wealthbuilder.backend.DTOs.auth.LoginRequest;
import com.wealthbuilder.backend.DTOs.auth.RegisterRequest;


/**
 * Registration, login, and current-user lookup. Both register and login return a
 * ready-to-use bearer token so the SPA can authenticate immediately.
 */
public interface AuthService {

    /**
     * Creates a regular user (after ensuring the username is free) and issues a token.
     */
    AuthResponse register(RegisterRequest request);

    /**
     * Validates credentials and issues a token; bad credentials surface as 401.
     */
    AuthResponse login(LoginRequest request);

    /**
     * Returns the authenticated account plus its derived balance.
     */
    CurrentUserResponse me(String username);
}
