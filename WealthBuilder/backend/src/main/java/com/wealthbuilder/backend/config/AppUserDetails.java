package com.wealthbuilder.backend.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;


/**
 * Spring Security principal enriched with the account's token version, so the JWT filter can
 * compare a presented token's version against the user's current one and reject revoked tokens.
 */
public class AppUserDetails extends User {

    private final int tokenVersion;

    public AppUserDetails(String username, String passwordHash, int tokenVersion,
                          Collection<? extends GrantedAuthority> authorities) {
        super(username, passwordHash, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
