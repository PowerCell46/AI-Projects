package com.peter_gerdzhikov.twitter_api_gateway.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import com.github.tomakehurst.wiremock.client.WireMock;

/**
 * Adds a WireMock container standing in for {@code twitter_tweet_service}, started once per JVM like the
 * inherited containers. The routes are pointed at it through {@code app.tweet-service.url}, and
 * {@link #TWEET_SERVICE_STUB} stubs responses and verifies what was forwarded.
 */
public abstract class AbstractTweetServiceIntegrationTest extends AbstractMinioIntegrationTest {

    protected static final WireMockContainer TWEET_SERVICE = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMock TWEET_SERVICE_STUB;

    static {
        TWEET_SERVICE.start();
        TWEET_SERVICE_STUB = new WireMock(TWEET_SERVICE.getHost(), TWEET_SERVICE.getPort());
    }

    @DynamicPropertySource
    static void pointTheRoutesAtTheStandIn(DynamicPropertyRegistry registry) {
        registry.add("app.tweet-service.url", TWEET_SERVICE::getBaseUrl);
    }
}
