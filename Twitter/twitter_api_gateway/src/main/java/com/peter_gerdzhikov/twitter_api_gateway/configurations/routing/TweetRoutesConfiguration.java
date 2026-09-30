package com.peter_gerdzhikov.twitter_api_gateway.configurations.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import java.net.http.HttpClient;
import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Forwards the tweet service's prefix with the path unchanged. The security chain has already
 * authenticated the caller by the time a request gets here.
 */
@Configuration
public class TweetRoutesConfiguration {

    public static final String TWEETS_PATH = "/api/v1/tweets/**";

    public static final String USER_ID_HEADER = "X-User-Id";

    private static final String IDENTITY_HEADER_PREFIX = "X-User-";

    @Bean
    public RouterFunction<ServerResponse> tweetServiceRoutes(@Value("${app.tweet-service.url}") String tweetServiceUrl) {
        return route("tweet-service")
                .route(path(TWEETS_PATH), http())
                .before(uri(tweetServiceUrl))
                // The tweet service trusts only the identity header below, so the caller's credentials never leave here.
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .before(removeRequestHeader(HttpHeaders.AUTHORIZATION))
                // Strip before adding: otherwise a caller could plant an X-User-Id of their own.
                .before(removeHeadersWithPrefix(IDENTITY_HEADER_PREFIX))
                .before(addAuthenticatedUserId())
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

    private static Function<ServerRequest, ServerRequest> removeHeadersWithPrefix(String prefix) {
        return request -> ServerRequest.from(request)
                .headers(httpHeaders -> namesStartingWith(httpHeaders, prefix).forEach(httpHeaders::remove))
                .build();
    }

    private static Function<ServerRequest, ServerRequest> addAuthenticatedUserId() {
        return request -> ServerRequest.from(request)
                .headers(httpHeaders -> httpHeaders.set(USER_ID_HEADER, authenticatedUserId()))
                .build();
    }

    private static List<String> namesStartingWith(HttpHeaders httpHeaders, String prefix) {
        return httpHeaders.headerNames().stream()
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                .toList();
    }

    private static String authenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken().getSubject();
        }

        throw new IllegalStateException("A tweet route was reached without a JWT authentication.");
    }
}
