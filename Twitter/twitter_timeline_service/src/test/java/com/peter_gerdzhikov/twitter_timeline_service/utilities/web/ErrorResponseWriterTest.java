package com.peter_gerdzhikov.twitter_timeline_service.utilities.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.json.JsonMapper;

class ErrorResponseWriterTest {

    @Test
    void should_write_the_status_json_content_type_and_error_body() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        ErrorResponseWriter.write(response, JsonMapper.builder().build(), HttpStatus.UNAUTHORIZED, "Nope.");

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"status\":401", "\"messages\":[\"Nope.\"]", "\"timestamp\"");
    }
}
