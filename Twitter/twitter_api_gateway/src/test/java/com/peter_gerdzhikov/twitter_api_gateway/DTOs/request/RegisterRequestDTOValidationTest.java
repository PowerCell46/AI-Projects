package com.peter_gerdzhikov.twitter_api_gateway.DTOs.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class RegisterRequestDTOValidationTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "ana@example.com",
            "ok.name+tag@example.com",
            "ana@mail.company.com",
            "x@fmi.uni-sofia.bg",
            "y@example.co.uk",
            "a@a.b.c.d.example.org"
    })
    void should_accept_an_email_with_a_plus_tag_a_subdomain_or_a_second_level_domain(String email) {
        assertThat(violatedPropertiesFor(email)).doesNotContain("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "a@example.com\r\nBcc: victim@example.com",
            "a@example.com\n",
            "a@example.com,b@example.com",
            "Evil <a@example.com>",
            "a b@example.com",
            " a@example.com",
            "a@@example.com",
            "a@localhost",
            "a@example",
            "a@example.c",
            "a@.example.com",
            "a@example..com",
            "a@example.com."
    })
    void should_reject_an_email_that_is_not_a_plain_address_with_a_dotted_domain(String email) {
        assertThat(violatedPropertiesFor(email)).contains("email");
    }

    private static Set<String> violatedPropertiesFor(String email) {
        RegisterRequestDTO request = RegisterRequestDTO.builder()
                .email(email)
                .username("ana_k")
                .password("Str0ng-password")
                .build();

        return VALIDATOR
                .validate(request)
                .stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
