package com.peter_gerdzhikov.twitter_api_gateway.configurations.routing;

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
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Forwards the tweet service's prefix with the path unchanged. The security chain has already
 * authenticated the caller by the time a request gets here.
 */
@Configuration
public class TweetRoutesConfiguration {

    public static final String TWEETS_PATH = "/api/v1/tweets/**";

    @Bean
    public RouterFunction<ServerResponse> tweetServiceRoutes(@Value("${app.tweet-service.url}") String tweetServiceUrl) {
        return CallerIdentityFilters.forwardAsTheAuthenticatedUser(route("tweet-service")
                        .route(path(TWEETS_PATH), http())
                        .before(uri(tweetServiceUrl)))
                .build();
    }

    /**
     * The JDK client otherwise attempts an h2c upgrade over plain {@code http://}, which an HTTP/2-capable
     * downstream answers by resetting the stream. The hop is a private network, so HTTP/2 buys nothing there.
     */
    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> http1OnlyUpstreamClient() {
        return builder -> builder
                .withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1));
    }
}
