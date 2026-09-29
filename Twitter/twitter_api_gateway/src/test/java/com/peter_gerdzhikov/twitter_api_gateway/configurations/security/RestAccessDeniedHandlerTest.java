package com.peter_gerdzhikov.twitter_api_gateway.configurations.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import tools.jackson.databind.json.JsonMapper;

class RestAccessDeniedHandlerTest {

    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(JsonMapper.builder().build());

    @Test
    void should_answer_403_with_the_apps_error_body_and_no_framework_detail() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentAsString())
                .contains("\"messages\":[\"Access is denied.\"]")
                .doesNotContain("internal detail");
    }
}
