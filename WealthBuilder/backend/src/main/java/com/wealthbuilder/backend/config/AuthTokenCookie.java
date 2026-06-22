package com.wealthbuilder.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;


/**
 * Builds the httpOnly cookie that carries the JWT. Centralised so the attributes (HttpOnly,
 * Secure, SameSite, Path, Max-Age) are identical when the cookie is issued at login and when it
 * is cleared at logout — a mismatch on any attribute would leave the browser holding a stale
 * cookie the clear never overwrites.
 */
@Component
@RequiredArgsConstructor
public class AuthTokenCookie {

    private final AppProperties appProperties;

    /** The cookie carrying the token, valid for the JWT's lifetime. */
    public ResponseCookie issue(String token) {
        return baseBuilder(token)
                .maxAge(appProperties.getJwt().getTtl())
                .build();
    }

    /** An immediately-expiring cookie of the same name/path, so logout drops the session. */
    public ResponseCookie clear() {
        return baseBuilder("")
                .maxAge(0)
                .build();
    }

    public String getName() {
        return appProperties.getAuth().getCookieName();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        final AppProperties.Auth auth = appProperties.getAuth();

        return ResponseCookie
                .from(auth.getCookieName(), value)
                .httpOnly(true)
                .secure(auth.isCookieSecure())
                .sameSite(auth.getCookieSameSite())
                .path("/");
    }
}
