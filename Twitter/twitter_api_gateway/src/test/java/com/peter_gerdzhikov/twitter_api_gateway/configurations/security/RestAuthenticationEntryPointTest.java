package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import tools.jackson.databind.json.JsonMapper;

class RestAuthenticationEntryPointTest {

    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(JsonMapper.builder().build());

    @Test
    void should_answer_401_with_the_apps_error_body_and_no_framework_detail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new BadCredentialsException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentAsString())
                .contains("\"messages\":[\"Authentication is required.\"]")
                .doesNotContain("internal detail");
    }
}
