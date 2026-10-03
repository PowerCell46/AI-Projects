package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class InternalApiSecretFilterTest {

    private static final String SECRET = "test-internal-secret-0123456789abcdef";

    private static final String INTERNAL_PATH = "/internal/v1/users";

    private InternalApiSecretFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiSecretFilter(SECRET, JsonMapper.builder().build());
    }

    @Nested
    class Guarding {

        @Test
        void should_return_404_without_calling_the_chain_when_the_secret_header_is_missing() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(new MockHttpServletRequest("GET", INTERNAL_PATH), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        void should_return_404_without_calling_the_chain_when_the_secret_is_wrong() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(requestWithSecret(INTERNAL_PATH, "y".repeat(SECRET.length())), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        void should_return_404_when_the_secret_is_only_a_prefix_of_the_real_one() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(
                    requestWithSecret(INTERNAL_PATH, SECRET.substring(0, SECRET.length() - 1)),
                    response,
                    new MockFilterChain()
            );

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @Test
        void should_return_404_when_the_secret_has_the_real_one_as_a_prefix() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(requestWithSecret(INTERNAL_PATH, SECRET + "x"), response, new MockFilterChain());

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        }

        @Test
        void should_answer_the_error_shape_an_unknown_path_gets_when_the_secret_is_wrong() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(new MockHttpServletRequest("GET", INTERNAL_PATH), response, new MockFilterChain());

            assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
            assertThat(response.getContentAsString())
                    .contains("\"status\":404", "\"timestamp\"")
                    .contains("No resource found for this path.");
        }

        @Test
        void should_not_echo_the_presented_secret_when_it_is_wrong() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(requestWithSecret(INTERNAL_PATH, "presented-secret"), response, new MockFilterChain());

            assertThat(response.getContentAsString()).doesNotContain("presented-secret", SECRET);
        }

        @Test
        void should_pass_the_request_down_the_chain_when_the_secret_is_right() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(requestWithSecret(INTERNAL_PATH, SECRET), response, chain);

            assertThat(chain.getRequest()).isNotNull();
            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/internal/v1",
                "/internal/v1/users/abc/follower-ids",
                "//internal/v1/users",
                "/internal//v1/users",
                "/internal/v1/users;jsessionid=1"
        })
        void should_guard_the_path_when_it_is_a_raw_variant_of_an_internal_one(String path) throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
            assertThat(chain.getRequest()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "/actuator/health",
                "/api/v1/users/me",
                "/internal/v10/users",
                "/internal/v2/users",
                "/internal"
        })
        void should_pass_the_request_down_the_chain_when_the_path_is_not_internal(String path) throws Exception {
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(new MockHttpServletRequest("GET", path), new MockHttpServletResponse(), chain);

            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Nested
    class Construction {

        @Test
        void should_refuse_a_secret_shorter_than_32_bytes() {
            assertThatThrownBy(() -> new InternalApiSecretFilter("x".repeat(31), JsonMapper.builder().build()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 32 bytes");
        }

        @Test
        void should_accept_a_secret_of_32_bytes() {
            assertThatCode(() -> new InternalApiSecretFilter("x".repeat(32), JsonMapper.builder().build()))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_count_bytes_not_characters_for_the_secret() {
            assertThatCode(() -> new InternalApiSecretFilter("é".repeat(16), JsonMapper.builder().build()))
                    .doesNotThrowAnyException();
        }
    }

    private MockHttpServletRequest requestWithSecret(String path, String secret) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader(InternalApiSecretFilter.SECRET_HEADER, secret);

        return request;
    }
}
