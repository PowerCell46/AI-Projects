package com.peter_gerdzhikov.signal_flow_api_gateway.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Adds a WireMock container standing in for {@code signal_flow_interest_topic_service}, started once per
 * JVM like the inherited Postgres. The routes are pointed at it through {@code app.interest-topic-service.url},
 * and {@link #INTEREST_TOPIC_SERVICE_STUB} stubs responses and verifies what was forwarded.
 */
public abstract class AbstractInterestTopicServiceIntegrationTest extends AbstractPostgresIntegrationTest {

    protected static final WireMockContainer INTEREST_TOPIC_SERVICE = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMock INTEREST_TOPIC_SERVICE_STUB;

    static {
        INTEREST_TOPIC_SERVICE.start();
        INTEREST_TOPIC_SERVICE_STUB = new WireMock(INTEREST_TOPIC_SERVICE.getHost(), INTEREST_TOPIC_SERVICE.getPort());
    }

    @DynamicPropertySource
    static void pointTheRoutesAtTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("app.interest-topic-service.url", INTEREST_TOPIC_SERVICE::getBaseUrl);
    }
}
