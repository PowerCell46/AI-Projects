package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

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

import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.BodySizeLimitingRequestWrapper;

class RequestBodySizeLimitFilterTest {

    private static final int MAX_BODY_BYTES = 16;

    private static final int MAX_TOPIC_BODY_BYTES = 64;

    private static final String INTERNAL_PATH = "/internal/v1/interest-topics/existing";

    private static final String TOPIC_PATH = "/api/v1/interest-topics";

    private RequestBodySizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestBodySizeLimitFilter(MAX_BODY_BYTES, MAX_TOPIC_BODY_BYTES, JsonMapper.builder().build());
    }

    @Test
    void should_return_413_without_calling_the_chain_when_the_declared_body_is_over_the_cap() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody(INTERNAL_PATH, "x".repeat(MAX_BODY_BYTES + 1)), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_answer_an_oversized_body_with_the_apps_error_shape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithBody(INTERNAL_PATH, "x".repeat(MAX_BODY_BYTES + 1)), response, new MockFilterChain());

        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":413", "\"messages\"", "\"timestamp\"");
    }

    @Test
    void should_pass_a_body_within_the_cap_down_the_chain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody(INTERNAL_PATH, "x".repeat(MAX_BODY_BYTES)), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_wrap_the_forwarded_request_so_an_undeclared_body_is_counted_while_read() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody(INTERNAL_PATH, "x"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isInstanceOf(BodySizeLimitingRequestWrapper.class);
    }

    @Test
    void should_pass_a_topic_route_body_over_the_default_cap_but_within_the_topic_cap() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody(TOPIC_PATH, "x".repeat(MAX_TOPIC_BODY_BYTES)), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_return_413_for_a_topic_route_body_over_the_topic_cap() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody(TOPIC_PATH, "x".repeat(MAX_TOPIC_BODY_BYTES + 1)), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
    }

    private MockHttpServletRequest requestWithBody(String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
