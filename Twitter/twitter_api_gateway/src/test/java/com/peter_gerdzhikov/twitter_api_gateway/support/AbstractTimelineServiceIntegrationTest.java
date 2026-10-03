package com.peter_gerdzhikov.twitter_api_gateway.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Adds a second WireMock container standing in for {@code twitter_timeline_service}, started once per JVM like
 * the inherited containers. The routes are pointed at it through {@code app.timeline-service.url}, and
 * {@link #TIMELINE_SERVICE_STUB} stubs responses and verifies what was forwarded.
 */
public abstract class AbstractTimelineServiceIntegrationTest extends AbstractTweetServiceIntegrationTest {

    protected static final WireMockContainer TIMELINE_SERVICE = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMock TIMELINE_SERVICE_STUB;

    static {
        TIMELINE_SERVICE.start();
        TIMELINE_SERVICE_STUB = new WireMock(TIMELINE_SERVICE.getHost(), TIMELINE_SERVICE.getPort());
    }

    @DynamicPropertySource
    static void pointTheFeedRouteAtTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("app.timeline-service.url", TIMELINE_SERVICE::getBaseUrl);
    }
}
