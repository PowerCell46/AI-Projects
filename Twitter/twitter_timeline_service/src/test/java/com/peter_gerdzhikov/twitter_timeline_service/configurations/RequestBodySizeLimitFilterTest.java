package com.peter_gerdzhikov.twitter_timeline_service.configurations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class RequestBodySizeLimitFilterTest {

    private static final int MAX_BODY_BYTES = 16;

    private static final String VIEWS_PATH = "/api/v1/views";

    private RequestBodySizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestBodySizeLimitFilter(MAX_BODY_BYTES, JsonMapper.builder().build());
    }

    @Test
    void should_return_413_without_calling_the_chain_when_the_declared_body_is_over_the_cap() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES + 1)), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_pass_a_body_within_the_cap_down_the_chain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES)), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletRequest requestWithBody(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", VIEWS_PATH);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
