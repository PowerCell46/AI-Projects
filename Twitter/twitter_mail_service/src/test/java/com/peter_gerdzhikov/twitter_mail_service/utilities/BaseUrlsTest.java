package com.peter_gerdzhikov.twitter_mail_service.utilities;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseUrlsTest {

    @Nested
    class RequireAbsoluteHttpUrl {

        @ParameterizedTest
        @ValueSource(strings = {
                "http://localhost:5173",
                "https://twitter.example.com",
                "https://twitter.example.com/",
                "https://example.com/twitter",
                "HTTPS://TWITTER.EXAMPLE.COM"
        })
        void should_return_the_same_url_when_it_is_absolute_http_or_https(String url) {
            assertEquals(url, BaseUrls.requireAbsoluteHttpUrl(url, "app.some-url"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "",
                "   ",
                "localhost:5173",
                "twitter.example.com",
                "/feed",
                "ftp://twitter.example.com",
                "https://",
                "https://twitter.example.com?next=1",
                "https://twitter.example.com#top",
                "https://twitter example.com"
        })
        void should_throw_naming_the_property_when_the_url_is_not_an_absolute_http_url(String url) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> BaseUrls.requireAbsoluteHttpUrl(url, "app.some-url"));

            assertTrue(thrown.getMessage().contains("app.some-url"));
        }
    }

    @Nested
    class IsPlainHttpOutsideLocalhost {

        @ParameterizedTest
        @ValueSource(strings = {"http://twitter.example.com", "http://192.168.1.5:5173", "HTTP://Twitter.Example.com"})
        void should_be_true_when_the_url_is_http_on_another_host(String url) {
            assertTrue(BaseUrls.isPlainHttpOutsideLocalhost(url));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "http://localhost:5173",
                "HTTP://LOCALHOST:5173",
                "http://127.0.0.1:5173",
                "http://[::1]:5173"
        })
        void should_be_false_when_the_url_is_http_on_localhost(String url) {
            assertFalse(BaseUrls.isPlainHttpOutsideLocalhost(url));
        }

        @Test
        void should_be_false_when_the_url_is_https() {
            assertFalse(BaseUrls.isPlainHttpOutsideLocalhost("https://twitter.example.com"));
        }
    }
}
