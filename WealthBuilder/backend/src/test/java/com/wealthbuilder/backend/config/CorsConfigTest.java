package com.wealthbuilder.backend.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Verifies the CORS origin-pattern matching that lets the Vite dev SPA call the API. The
 * {@code [*]} port wildcard is the easy thing to get subtly wrong, so it is asserted here
 * rather than discovered at runtime as a 403 preflight.
 */
class CorsConfigTest {

    private static final String LOGIN_PATH = "/api/auth/login";

    private CorsConfiguration configuration;

    @BeforeEach
    void setUp() {
        final AppProperties properties = new AppProperties(
                List.of("http://localhost:[*]", "http://127.0.0.1:[*]"),
                null,
                null,
                null);

        final CorsConfigurationSource source = new CorsConfig(properties, new StandardEnvironment()).corsConfigurationSource();

        this.configuration = source.getCorsConfiguration(optionsPreflightTo(LOGIN_PATH));
    }

    @Test
    @DisplayName("a CORS configuration is registered for the API path")
    void should_RegisterConfigForApiPath() {
        assertThat(configuration).isNotNull();
    }

    @Test
    @DisplayName("allows the Vite dev origin on its default port")
    void should_AllowLocalhostDefaultPort() {
        assertThat(configuration.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("allows localhost / 127.0.0.1 on any port")
    void should_AllowLoopbackOnAnyPort() {
        assertThat(configuration.checkOrigin("http://localhost:61234")).isNotNull();
        assertThat(configuration.checkOrigin("http://127.0.0.1:5173")).isNotNull();
    }

    @Test
    @DisplayName("rejects a foreign origin")
    void should_RejectForeignOrigin() {
        assertThat(configuration.checkOrigin("http://evil.example.com")).isNull();
    }

    @Test
    @DisplayName("permits the preflight method and headers")
    void should_PermitPreflightMethodAndHeaders() {
        assertThat(configuration.checkHttpMethod(org.springframework.http.HttpMethod.POST)).isNotNull();
        assertThat(configuration.checkHeaders(List.of("content-type", "authorization"))).isNotNull();
    }

    private static MockHttpServletRequest optionsPreflightTo(String path) {
        final MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "POST");

        return request;
    }
}
