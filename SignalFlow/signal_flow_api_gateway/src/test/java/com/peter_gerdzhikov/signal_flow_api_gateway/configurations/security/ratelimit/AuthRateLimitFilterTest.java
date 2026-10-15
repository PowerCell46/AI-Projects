package com.peter_gerdzhikov.signal_flow_api_gateway.configurations.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class AuthRateLimitFilterTest {

    private static final int MAX_REQUESTS = 2;

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private static final String REGISTER_PATH = "/api/v1/auth/register";

    private AuthRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimitFilter(MAX_REQUESTS, Duration.ofMinutes(1), 1, JsonMapper.builder().build());
    }

    @Test
    void should_pass_requests_down_the_chain_while_under_the_limit() throws Exception {
        for (int request = 0; request < MAX_REQUESTS; request++) {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(postTo(LOGIN_PATH, "1.1.1.1"), new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void should_answer_429_with_retry_after_and_the_apps_error_shape_when_over_the_limit() throws Exception {
        useUpTheLimit(LOGIN_PATH, "1.1.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(postTo(LOGIN_PATH, "1.1.1.1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(response.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(response.getContentAsString()).contains("\"status\":429", "\"messages\"", "\"timestamp\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void should_share_one_budget_between_login_and_register() throws Exception {
        filter.doFilter(postTo(LOGIN_PATH, "1.1.1.1"), new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(postTo(REGISTER_PATH, "1.1.1.1"), new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(postTo(REGISTER_PATH, "1.1.1.1"), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void should_limit_each_client_address_separately() throws Exception {
        useUpTheLimit(LOGIN_PATH, "1.1.1.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(postTo(LOGIN_PATH, "2.2.2.2"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_not_let_a_spoofed_leftmost_forwarded_for_entry_dodge_the_limit() throws Exception {
        useUpTheLimit(LOGIN_PATH, "1.1.1.1");
        MockHttpServletRequest spoofed = postTo(LOGIN_PATH, "1.1.1.1");
        spoofed.removeHeader("X-Forwarded-For");
        spoofed.addHeader("X-Forwarded-For", "9.9.9.9, 1.1.1.1, 172.18.0.2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(spoofed, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void should_never_limit_other_routes() throws Exception {
        useUpTheLimit(LOGIN_PATH, "1.1.1.1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(postTo("/api/v1/auth/logout", "1.1.1.1"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void should_not_limit_a_get_on_a_limited_path() throws Exception {
        useUpTheLimit(LOGIN_PATH, "1.1.1.1");
        MockHttpServletRequest get = new MockHttpServletRequest("GET", LOGIN_PATH);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(get, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private void useUpTheLimit(String path, String clientAddress) throws Exception {
        for (int request = 0; request < MAX_REQUESTS; request++) {
            filter.doFilter(postTo(path, clientAddress), new MockHttpServletResponse(), new MockFilterChain());
        }
    }

    private MockHttpServletRequest postTo(String path, String clientAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-Forwarded-For", clientAddress + ", 172.18.0.2");

        return request;
    }
}
