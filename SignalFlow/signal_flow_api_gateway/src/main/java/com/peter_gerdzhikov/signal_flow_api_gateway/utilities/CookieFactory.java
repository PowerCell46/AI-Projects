package com.peter_gerdzhikov.signal_flow_api_gateway.utilities;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code access_token} cookie with the attributes fixed in PLAN.md step 4 - HttpOnly,
 * Path=/, SameSite=Strict, Secure driven by {@code app.cookie.secure}. Not a service: it holds no
 * business logic, only cookie formatting, so it skips the interface/impl split.
 */
@Component
public class CookieFactory {

    public static final String COOKIE_NAME = "access_token";

    private final Duration ttl;
    private final boolean secure;

    public CookieFactory(@Value("${app.jwt.ttl}") Duration ttl, @Value("${app.cookie.secure}") boolean secure) {
        this.ttl = ttl;
        this.secure = secure;
    }

    public ResponseCookie issue(String token) {
        return baseCookie(token, ttl);
    }

    public ResponseCookie clear() {
        return baseCookie("", Duration.ZERO);
    }

    private ResponseCookie baseCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict")
                .maxAge(maxAge)
                .build();
    }
}
