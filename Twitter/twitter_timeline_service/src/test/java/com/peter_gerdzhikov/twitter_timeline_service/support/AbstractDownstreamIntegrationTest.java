package com.peter_gerdzhikov.twitter_timeline_service.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.wiremock.integrations.testcontainers.WireMockContainer;

import com.github.tomakehurst.wiremock.client.WireMock;

import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedEntryCleanupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.FeedFanOutService;

/**
 * Adds two WireMock containers, one standing in for the gateway's internal API and one for the tweet service,
 * started once per JVM like the inherited containers. The clients are pointed at them through
 * {@code app.gateway-internal.url} and {@code app.tweet-service.url}; {@link #GATEWAY_STUB} and
 * {@link #TWEET_SERVICE_STUB} stub responses and verify what was sent; both are reset before every test.
 *
 * <p>Every full-context test extends this, so the whole suite shares one application context. That matters
 * for the Kafka listeners: a second cached context would join the same consumer group and take a share of
 * the partitions, and a record handled there would never reach this context's spies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractDownstreamIntegrationTest extends AbstractKafkaIntegrationTest {

    protected static final WireMockContainer GATEWAY = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMockContainer TWEET_SERVICE = new WireMockContainer("wiremock/wiremock:3.13.2");

    protected static final WireMock GATEWAY_STUB;

    protected static final WireMock TWEET_SERVICE_STUB;

    @MockitoSpyBean
    protected FeedFanOutService feedFanOutService;

    @MockitoSpyBean
    protected FeedEntryCleanupService feedEntryCleanupService;

    static {
        GATEWAY.start();
        TWEET_SERVICE.start();
        GATEWAY_STUB = new WireMock(GATEWAY.getHost(), GATEWAY.getPort());
        TWEET_SERVICE_STUB = new WireMock(TWEET_SERVICE.getHost(), TWEET_SERVICE.getPort());
    }

    @DynamicPropertySource
    static void pointTheClientsAtTheStandIns(DynamicPropertyRegistry registry) {
        registry.add("app.gateway-internal.url", GATEWAY::getBaseUrl);
        registry.add("app.tweet-service.url", TWEET_SERVICE::getBaseUrl);
    }

    @BeforeEach
    void resetTheStandIns() {
        GATEWAY_STUB.resetMappings();
        GATEWAY_STUB.resetRequests();
        TWEET_SERVICE_STUB.resetMappings();
        TWEET_SERVICE_STUB.resetRequests();
    }
}
