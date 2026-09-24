package com.peter_gerdzhikov.signal_flow_mail_service;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.signal_flow_mail_service.support.AbstractRedisIntegrationTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SignalFlowMailServiceApplicationTests extends AbstractRedisIntegrationTest {

    @LocalServerPort
    private int localServerPort;

    @Test
    void should_report_up_with_redis_component_and_no_mail_component_when_health_is_checked() {
        String body = RestClient.create("http://localhost:" + localServerPort)
                .get()
                .uri("/actuator/health")
                .retrieve()
                .body(String.class);

        assertTrue(body.contains("\"status\":\"UP\""));
        assertTrue(body.contains("\"redis\""));
        assertFalse(body.contains("\"mail\""));
    }
}
