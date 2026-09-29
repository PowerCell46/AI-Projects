package com.peter_gerdzhikov.twitter_api_gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class TwitterApiGatewayApplicationTests extends AbstractMinioIntegrationTest {

    @Test
    void contextLoads() {
    }
}
