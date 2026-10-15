package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@SpringBootTest(properties = "app.rate-limit.auth.max-requests=3")
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class AuthRateLimitIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    @Autowired
    private RestTestClient restTestClient;

    @Test
    void should_answer_429_with_retry_after_once_a_client_address_exceeds_the_login_limit() {
        String clientAddress = "198.51.100." + (UUID.randomUUID().getMostSignificantBits() & 0x7f);

        for (int attempt = 0; attempt < 3; attempt++) {
            login(clientAddress).expectStatus().isUnauthorized();
        }

        login(clientAddress)
                .expectStatus().isEqualTo(429)
                .expectHeader().valueEquals("Retry-After", "60");
    }

    @Test
    void should_keep_answering_other_client_addresses_while_one_is_limited() {
        for (int attempt = 0; attempt < 4; attempt++) {
            login("203.0.113.1");
        }

        assertThat(login("203.0.113.2").returnResult(String.class).getStatus().value()).isEqualTo(401);
    }

    private RestTestClient.ResponseSpec login(String clientAddress) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("nobody@example.com");
        request.setPassword("Password123");

        return restTestClient.post()
                .uri(LOGIN_PATH)
                .header("X-Forwarded-For", clientAddress + ", 172.18.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }
}
