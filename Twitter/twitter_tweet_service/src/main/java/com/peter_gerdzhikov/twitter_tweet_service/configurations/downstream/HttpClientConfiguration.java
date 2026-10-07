package com.peter_gerdzhikov.twitter_tweet_service.configurations.downstream;

import java.net.http.HttpClient;

import org.springframework.boot.http.client.JdkClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HttpClientConfiguration {

    /**
     * The JDK client otherwise attempts an h2c upgrade over plain {@code http://}, which an HTTP/2-capable
     * downstream answers by resetting the stream. The hop is a private network, so HTTP/2 buys nothing there.
     */
    @Bean
    public ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder> http1OnlyDownstreamClient() {
        return builder -> builder
                .withHttpClientCustomizer(client -> client.version(HttpClient.Version.HTTP_1_1));
    }
}
