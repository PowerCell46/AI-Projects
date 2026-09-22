package com.peter_gerdzhikov.url_shortener_backend.interceptors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.url_shortener_backend.exceptions.ShortUrlNotFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@ExtendWith(MockitoExtension.class)
class ScannerProbeInterceptorTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private ScannerProbeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new ScannerProbeInterceptor();
    }

    @ParameterizedTest
    @ValueSource(strings = {"nOpE42", "0", "aZ9"})
    void preHandle_returns_true_for_a_well_formed_code(String code) {
        when(request.getRequestURI()).thenReturn("/" + code);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.env", "/wp-config.php", "/backup.sql", "/docker-compose.yml"})
    void preHandle_throws_short_url_not_found_for_a_scanner_probe_path(String path) {
        when(request.getRequestURI()).thenReturn(path);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ShortUrlNotFoundException.class);
    }
}
