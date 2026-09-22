package com.peter_gerdzhikov.signal_flow_api_gateway.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class CookieFactoryTest {

    private static final Duration TTL = Duration.ofHours(1);
    private static final String TOKEN = "signed-jwt-value";

    private CookieFactory cookieFactory;

    @BeforeEach
    void setUp() {
        cookieFactory = new CookieFactory(TTL, true);
    }

    @Test
    void should_issue_a_cookie_carrying_the_token_and_the_configured_ttl() {
        ResponseCookie cookie = cookieFactory.issue(TOKEN);

        assertThat(cookie.getName()).isEqualTo(CookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo(TOKEN);
        assertThat(cookie.getMaxAge()).isEqualTo(TTL);
    }

    @Test
    void should_issue_a_cookie_with_http_only_path_root_and_same_site_strict() {
        ResponseCookie cookie = cookieFactory.issue(TOKEN);

        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
    }

    @Test
    void should_issue_a_secure_cookie_when_configured_secure() {
        ResponseCookie cookie = cookieFactory.issue(TOKEN);

        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    void should_issue_a_non_secure_cookie_when_configured_not_secure() {
        cookieFactory = new CookieFactory(TTL, false);

        assertThat(cookieFactory.issue(TOKEN).isSecure()).isFalse();
    }

    @Test
    void should_clear_the_cookie_with_an_empty_value_and_zero_max_age() {
        ResponseCookie cookie = cookieFactory.clear();

        assertThat(cookie.getName()).isEqualTo(CookieFactory.COOKIE_NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isZero();
    }
}
