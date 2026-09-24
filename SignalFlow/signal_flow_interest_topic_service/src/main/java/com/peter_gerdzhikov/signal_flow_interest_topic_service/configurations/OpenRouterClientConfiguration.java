package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * This service's only outbound HTTP client. The API key is baked into a default header here so it never
 * reaches {@code NewsGenerationServiceImpl} or a log line.
 */
@Configuration
public class OpenRouterClientConfiguration {

    @Bean
    public RestClient openRouterRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.openrouter.base-url}") String baseUrl,
            @Value("${app.openrouter.api-key}") String apiKey
    ) {
        return restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }

    /**
     * The JDK client otherwise attempts an h2c upgrade over plain {@code http://}, which an HTTP/2-capable
     * downstream (Jetty, as in WireMock) answers by resetting the stream. The hop is a single call per
     * topic, so HTTP/2 buys nothing.
     */
    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> http1OnlyOpenRouterClient() {
        return builder -> builder
                .withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1));
    }
}
