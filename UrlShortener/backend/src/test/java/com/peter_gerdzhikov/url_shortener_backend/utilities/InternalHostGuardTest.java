package com.peter_gerdzhikov.url_shortener_backend.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalHostGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "LOCALHOST", "::1", "127.0.0.1", "169.254.169.254", "10.0.0.5",
            "172.16.0.5", "192.168.1.1", "0.0.0.0", "fd00::1", "::ffff:127.0.0.1",
            "2130706433", "127.1", "127.0.0.1.nip.io", "this-host-cannot-possibly-resolve.invalid",
            "::127.0.0.1", "::10.0.0.1", "::169.254.169.254", "0177.0.0.1", "012.0.0.1", "00.0.0.1"})
    void isInternal_returns_true_for_internal_hosts(String host) {
        assertThat(InternalHostGuard.isInternal(host)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.com", "8.8.8.8", "93.184.216.34"})
    void isInternal_returns_false_for_public_hosts(String host) {
        assertThat(InternalHostGuard.isInternal(host)).isFalse();
    }
}
