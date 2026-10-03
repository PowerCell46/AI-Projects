package com.peter_gerdzhikov.twitter_api_gateway.configurations.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Forwards the timeline service's paths unchanged. The security chain has already authenticated the caller
 * by the time a request gets here, and the default rule requires it.
 */
@Configuration
public class TimelineRoutesConfiguration {

    public static final String FEED_PATH = "/api/v1/feed";

    @Bean
    public RouterFunction<ServerResponse> timelineServiceRoutes(
            @Value("${app.timeline-service.url}") String timelineServiceUrl
    ) {
        return CallerIdentityFilters.forwardAsTheAuthenticatedUser(route("timeline-service")
                        .route(path(FEED_PATH), http())
                        .before(uri(timelineServiceUrl)))
                .build();
    }
}
