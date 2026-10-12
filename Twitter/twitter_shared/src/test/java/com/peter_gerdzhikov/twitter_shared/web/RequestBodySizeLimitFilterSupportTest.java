package com.peter_gerdzhikov.twitter_shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class RequestBodySizeLimitFilterSupportTest {

    private static final int MAX_BODY_BYTES = 16;

    @Test
    void should_return_413_without_calling_the_chain_when_the_declared_body_is_over_the_cap() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(false).doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES + 1)), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_answer_an_oversized_body_with_the_apps_error_shape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter(false).doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES + 1)), response, new MockFilterChain());

        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":413", "\"messages\"", "\"timestamp\"");
    }

    @Test
    void should_pass_a_body_within_the_cap_down_the_chain_wrapped_for_counting() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter(false).doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES)), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isInstanceOf(BodySizeLimitingRequestWrapper.class);
    }

    @Test
    void should_short_circuit_the_chain_when_reject_early_answers_the_request() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter(true).doFilter(requestWithBody("x"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_let_the_request_through_when_reject_early_accepts_it() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter(false).doFilter(requestWithBody("x"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private RequestBodySizeLimitFilterSupport filter(boolean rejectEarly) {
        return new RequestBodySizeLimitFilterSupport(JsonMapper.builder().build()) {

            @Override
            protected boolean rejectEarly(HttpServletRequest request, HttpServletResponse response) throws IOException {
                if (!rejectEarly) {
                    return false;
                }

                ErrorResponseWriter.write(response, objectMapper(), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Nope.");

                return true;
            }

            @Override
            protected long maxBytesFor(HttpServletRequest request) {
                return MAX_BODY_BYTES;
            }
        };
    }

    private MockHttpServletRequest requestWithBody(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/register");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        return request;
    }
}
