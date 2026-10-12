package com.peter_gerdzhikov.twitter_shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class ErrorResponseDTOTest {

    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @Test
    void should_leave_the_code_out_of_the_json_when_it_is_null() throws Exception {
        ErrorResponseDTO body = new ErrorResponseDTO(400, List.of("Nope."), 1L);

        assertThat(body.getCode()).isNull();
        assertThat(objectMapper.writeValueAsString(body)).doesNotContain("\"code\"");
    }

    @Test
    void should_carry_the_code_in_the_json_when_it_is_set() throws Exception {
        ErrorResponseDTO body = new ErrorResponseDTO(400, List.of("Nope."), 1L, "CALLER_UNKNOWN");

        assertThat(objectMapper.writeValueAsString(body)).contains("\"code\":\"CALLER_UNKNOWN\"");
    }
}
