package com.peter_gerdzhikov.signal_flow_interest_topic_service.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Adds a WireMock container standing in for OpenRouter, started once per JVM like the inherited
 * Postgres/Kafka. {@code app.openrouter.base-url} is pointed at it, replacing the unreachable default
 * from {@link AbstractIntegrationTest}, and {@link #OPENROUTER_STUB} stubs responses and verifies what
 * was sent.
 */
public abstract class AbstractOpenRouterIntegrationTest extends AbstractIntegrationTest {

    protected static final WireMockContainer OPENROUTER = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMock OPENROUTER_STUB;

    static {
        OPENROUTER.start();
        OPENROUTER_STUB = new WireMock(OPENROUTER.getHost(), OPENROUTER.getPort());
    }

    @DynamicPropertySource
    static void pointOpenRouterAtTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("app.openrouter.base-url", OPENROUTER::getBaseUrl);
    }
}
