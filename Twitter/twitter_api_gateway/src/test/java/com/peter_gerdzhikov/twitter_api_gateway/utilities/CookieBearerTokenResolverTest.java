package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import jakarta.servlet.http.Cookie;

class CookieBearerTokenResolverTest {

    private static final String TOKEN = "signed-jwt-value";

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver(
            PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/auth/login")
    );

    @Test
    void should_return_the_value_of_the_access_token_cookie() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setCookies(new Cookie(CookieFactory.COOKIE_NAME, TOKEN));

        assertThat(resolver.resolve(request)).isEqualTo(TOKEN);
    }

    @Test
    void should_return_null_when_there_are_no_cookies() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void should_return_null_when_only_other_cookies_are_present() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setCookies(new Cookie("theme", "dark"));

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void should_ignore_the_cookie_on_a_public_route() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setCookies(new Cookie(CookieFactory.COOKIE_NAME, TOKEN));

        assertThat(resolver.resolve(request)).isNull();
    }
}
