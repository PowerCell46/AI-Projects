package com.peter_gerdzhikov.twitter_tweet_service.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Adds a WireMock container standing in for the gateway's internal API, started once per JVM like the inherited
 * containers. The client is pointed at it through {@code app.gateway-internal.url}; {@link #GATEWAY_STUB} stubs
 * responses and verifies what was sent, and is reset before every test.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractDownstreamIntegrationTest extends AbstractMinioIntegrationTest {

    protected static final WireMockContainer GATEWAY = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMock GATEWAY_STUB;

    static {
        GATEWAY.start();
        GATEWAY_STUB = new WireMock(GATEWAY.getHost(), GATEWAY.getPort());
    }

    @DynamicPropertySource
    static void pointTheClientAtTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("app.gateway-internal.url", GATEWAY::getBaseUrl);
    }

    @BeforeEach
    void resetTheStandIn() {
        GATEWAY_STUB.resetMappings();
        GATEWAY_STUB.resetRequests();
    }
}
