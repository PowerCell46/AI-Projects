package com.peter_gerdzhikov.signal_flow_api_gateway.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientAddressResolverTest {

    private static final String SOCKET_ADDRESS = "10.0.0.9";

    @Test
    void should_use_the_socket_address_when_there_is_no_forwarded_for_header() {
        assertThat(ClientAddressResolver.resolve(requestForwardedFor(null), 1)).isEqualTo(SOCKET_ADDRESS);
    }

    @Test
    void should_use_the_socket_address_when_the_header_is_blank() {
        assertThat(ClientAddressResolver.resolve(requestForwardedFor("  "), 1)).isEqualTo(SOCKET_ADDRESS);
    }

    @Test
    void should_pick_the_entry_the_given_places_from_the_right() {
        MockHttpServletRequest request = requestForwardedFor("203.0.113.7, 172.18.0.2");

        assertThat(ClientAddressResolver.resolve(request, 1)).isEqualTo("203.0.113.7");
        assertThat(ClientAddressResolver.resolve(request, 0)).isEqualTo("172.18.0.2");
    }

    @Test
    void should_ignore_entries_a_client_prepended_to_the_header() {
        MockHttpServletRequest request = requestForwardedFor("6.6.6.6, 7.7.7.7, 203.0.113.7, 172.18.0.2");

        assertThat(ClientAddressResolver.resolve(request, 1)).isEqualTo("203.0.113.7");
    }

    @Test
    void should_use_the_socket_address_when_the_header_has_fewer_entries_than_expected() {
        assertThat(ClientAddressResolver.resolve(requestForwardedFor("203.0.113.7"), 1)).isEqualTo(SOCKET_ADDRESS);
    }

    private MockHttpServletRequest requestForwardedFor(String header) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(SOCKET_ADDRESS);
        if (header != null) {
            request.addHeader("X-Forwarded-For", header);
        }

        return request;
    }
}
