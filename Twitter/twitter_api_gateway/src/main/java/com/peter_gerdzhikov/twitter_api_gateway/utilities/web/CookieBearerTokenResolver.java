package com.peter_gerdzhikov.twitter_api_gateway.utilities.web;

import java.util.Arrays;
import java.util.List;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the bearer token from the {@code access_token} cookie instead of the Authorization header -
 * the token never leaves the browser via JS since the cookie is HttpOnly.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final List<RequestMatcher> ignoredMatchers;

    public CookieBearerTokenResolver(RequestMatcher... ignoredMatchers) {
        this.ignoredMatchers = ignoredMatchers == null ? List.of() : List.of(ignoredMatchers);
    }

    @Override
    public String resolve(HttpServletRequest request) {
        for (RequestMatcher matcher : ignoredMatchers) {
            if (matcher.matches(request)) {
                return null;
            }
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> CookieFactory.COOKIE_NAME.equals(cookie.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }
}

