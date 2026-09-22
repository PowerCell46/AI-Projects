package com.peter_gerdzhikov.signal_flow_api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class SignalFlowApiGatewayApplicationTests extends AbstractPostgresIntegrationTest {

    @Test
    void contextLoads() {
    }
}
