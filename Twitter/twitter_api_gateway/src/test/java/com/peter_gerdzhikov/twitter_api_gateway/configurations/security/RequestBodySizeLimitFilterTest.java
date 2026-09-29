package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

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

import com.peter_gerdzhikov.twitter_api_gateway.utilities.BodySizeLimitingRequestWrapper;

class RequestBodySizeLimitFilterTest {

    private static final int MAX_BODY_BYTES = 16;

    private static final int MAX_UPLOAD_BODY_BYTES = 64;

    private static final String PICTURE_PATH = "/api/v1/users/me/profile-picture";

    private static final String COVER_PATH = "/api/v1/users/me/cover-picture";

    private static final String AUTH_PATH = "/api/v1/auth/register";

    private RequestBodySizeLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestBodySizeLimitFilter(MAX_BODY_BYTES, MAX_UPLOAD_BODY_BYTES, JsonMapper.builder().build());
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
    void should_answer_an_oversized_body_with_the_apps_error_shape() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES + 1)), response, new MockFilterChain());

        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":413", "\"messages\"", "\"timestamp\"");
    }

    @Test
    void should_pass_a_body_within_the_cap_down_the_chain() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("x".repeat(MAX_BODY_BYTES)), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_wrap_the_forwarded_request_so_an_undeclared_body_is_counted_while_read() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("x"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isInstanceOf(BodySizeLimitingRequestWrapper.class);
    }

    @Test
    void should_pass_a_body_over_the_normal_cap_down_the_chain_when_it_is_a_picture_upload() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("PUT", PICTURE_PATH, MAX_BODY_BYTES + 1), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_pass_a_body_over_the_normal_cap_down_the_chain_when_it_is_a_cover_upload() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("PUT", COVER_PATH, MAX_UPLOAD_BODY_BYTES), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_return_413_when_a_picture_upload_body_is_over_the_upload_cap() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("PUT", PICTURE_PATH, MAX_UPLOAD_BODY_BYTES + 1), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_keep_the_normal_cap_when_the_method_is_not_put_on_an_upload_path() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithBody("DELETE", PICTURE_PATH, MAX_BODY_BYTES + 1), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
    }

    @Test
    void should_keep_the_normal_cap_when_a_put_targets_another_path() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithBody("PUT", "/api/v1/users/me", MAX_BODY_BYTES + 1), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());
    }

    @Test
    void should_let_an_upload_body_at_the_upload_cap_be_read_in_full_through_the_wrapper() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(requestWithBody("PUT", PICTURE_PATH, MAX_UPLOAD_BODY_BYTES), new MockHttpServletResponse(), chain);

        BodySizeLimitingRequestWrapper wrapper = (BodySizeLimitingRequestWrapper) chain.getRequest();
        assertThat(wrapper.getInputStream().readAllBytes()).hasSize(MAX_UPLOAD_BODY_BYTES);
    }

    private MockHttpServletRequest requestWithBody(String body) {
        return requestWithBody("POST", AUTH_PATH, body);
    }

    private MockHttpServletRequest requestWithBody(String method, String path, int bodyLength) {
        return requestWithBody(method, path, "x".repeat(bodyLength));
    }

    private MockHttpServletRequest requestWithBody(String method, String path, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
