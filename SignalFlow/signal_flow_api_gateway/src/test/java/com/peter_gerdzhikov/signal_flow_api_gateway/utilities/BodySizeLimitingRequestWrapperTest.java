package com.peter_gerdzhikov.signal_flow_api_gateway.utilities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.RequestBodyTooLargeException;

class BodySizeLimitingRequestWrapperTest {

    private static final int MAX_BODY_BYTES = 8;

    @Test
    void should_throw_when_the_body_read_exceeds_the_cap() {
        BodySizeLimitingRequestWrapper wrapper = wrap("x".repeat(MAX_BODY_BYTES + 1));

        assertThatThrownBy(() -> wrapper.getInputStream().readAllBytes())
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    @Test
    void should_read_a_body_within_the_cap_unchanged() throws IOException {
        String body = "x".repeat(MAX_BODY_BYTES);
        BodySizeLimitingRequestWrapper wrapper = wrap(body);

        byte[] read = wrapper.getInputStream().readAllBytes();

        assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    void should_throw_when_the_cap_is_exceeded_through_the_reader() {
        BodySizeLimitingRequestWrapper wrapper = wrap("x".repeat(MAX_BODY_BYTES + 1));

        assertThatThrownBy(() -> wrapper.getReader().readLine())
                .isInstanceOf(RequestBodyTooLargeException.class);
    }

    private BodySizeLimitingRequestWrapper wrap(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        return new BodySizeLimitingRequestWrapper(request, MAX_BODY_BYTES);
    }
}
