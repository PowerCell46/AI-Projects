package com.peter_gerdzhikov.twitter_api_gateway.configurations.routing;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;

import java.util.List;
import java.util.function.Function;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * What every proxied route does to the caller's identity before forwarding: the downstream services trust
 * only {@code X-User-Id}, so the caller's credentials and any header they could have planted are dropped and
 * the id from the verified JWT is the one that goes through.
 */
public final class CallerIdentityFilters {

    public static final String USER_ID_HEADER = "X-User-Id";

    private static final String IDENTITY_HEADER_PREFIX = "X-User-";

    private CallerIdentityFilters() {
    }

    public static RouterFunctions.Builder forwardAsTheAuthenticatedUser(RouterFunctions.Builder route) {
        return route
                .before(removeRequestHeader(HttpHeaders.COOKIE))
                .before(removeRequestHeader(HttpHeaders.AUTHORIZATION))
                // Strip before adding: otherwise a caller could plant an X-User-Id of their own.
                .before(removeHeadersWithPrefix(IDENTITY_HEADER_PREFIX))
                .before(addAuthenticatedUserId());
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

        throw new IllegalStateException("A proxied route was reached without a JWT authentication.");
    }
}
