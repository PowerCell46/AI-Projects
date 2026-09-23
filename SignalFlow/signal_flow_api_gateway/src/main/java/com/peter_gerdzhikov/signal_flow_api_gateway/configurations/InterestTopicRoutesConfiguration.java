package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Forwards the topic service's two prefixes verbatim. Who may call them is decided in
 * {@link SecurityConfiguration}; by the time a request gets here it is already authorized.
 */
@Configuration
public class InterestTopicRoutesConfiguration {

    public static final String CATEGORIES_PATH = "/api/v1/categories/**";

    public static final String INTEREST_TOPICS_PATH = "/api/v1/interest-topics/**";

    @Bean
    public RouterFunction<ServerResponse> interestTopicServiceRoutes(
            @Value("${app.interest-topic-service.url}") String interestTopicServiceUrl
    ) {
        return route("interest-topic-service")
                .route(path(CATEGORIES_PATH, INTEREST_TOPICS_PATH), http())
                .before(uri(interestTopicServiceUrl))
                // The downstream trusts the gateway and consumes no identity, so the caller's JWT never leaves here.
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .before(removeRequestHeader(HttpHeaders.AUTHORIZATION))
                .build();
    }

    /**
     * The JDK client otherwise attempts an h2c upgrade over plain {@code http://}, which an HTTP/2-capable
     * downstream (Jetty, as in WireMock) answers by resetting the stream. The hop is a private network, so
     * HTTP/2 buys nothing there.
     */
    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> http1OnlyUpstreamClient() {
        return builder -> builder
                .withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1));
    }
}
