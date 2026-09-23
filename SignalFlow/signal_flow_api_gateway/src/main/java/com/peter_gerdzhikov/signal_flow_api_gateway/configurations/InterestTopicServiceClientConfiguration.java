package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The gateway's own calls to the topic service, as opposed to the forwarded client routes. Built on Boot's
 * {@link RestClient.Builder}, so it shares the proxy's {@code spring.http.clients.*} timeouts and its
 * HTTP/1.1 pin.
 */
@Configuration
public class InterestTopicServiceClientConfiguration {

    @Bean
    public RestClient interestTopicServiceRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.interest-topic-service.url}") String interestTopicServiceUrl
    ) {
        return restClientBuilder
                .baseUrl(interestTopicServiceUrl)
                .build();
    }
}
