package com.peter_gerdzhikov.twitter_timeline_service.configurations.downstream;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One client per downstream, with its base URL fixed. The timeouts are Boot's global
 * {@code spring.http.clients.*} settings, which every client built from the {@link RestClient.Builder} picks up.
 */
@Configuration
public class RestClientConfiguration {

    public static final String GATEWAY_REST_CLIENT = "gatewayRestClient";

    public static final String TWEET_SERVICE_REST_CLIENT = "tweetServiceRestClient";

    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private static final int MIN_SECRET_BYTES = 32;

    /**
     * Carries the internal secret on every request, because every gateway route this service calls needs it.
     * The service refuses to start with a secret the gateway itself would refuse.
     */
    @Bean(GATEWAY_REST_CLIENT)
    public RestClient gatewayRestClient(
            RestClient.Builder builder,
            @Value("${app.gateway-internal.url}") String gatewayUrl,
            @Value("${app.internal-api.secret}") String internalSecret
    ) {
        if (internalSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.internal-api.secret must be at least 32 bytes.");
        }

        return builder
                .baseUrl(gatewayUrl)
                .defaultHeader(INTERNAL_SECRET_HEADER, internalSecret)
                .build();
    }

    /**
     * Sends no secret: the tweet service has none, and must never be handed the gateway's.
     */
    @Bean(TWEET_SERVICE_REST_CLIENT)
    public RestClient tweetServiceRestClient(
            RestClient.Builder builder,
            @Value("${app.tweet-service.url}") String tweetServiceUrl
    ) {
        return builder
                .baseUrl(tweetServiceUrl)
                .build();
    }
}
