package com.peter_gerdzhikov.signal_flow_interest_topic_service.configurations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractOpenRouterIntegrationTest;

@SpringBootTest
class OpenRouterClientConfigurationIntegrationTest extends AbstractOpenRouterIntegrationTest {

    @Autowired
    private RestClient openRouterRestClient;

    @Value("${app.openrouter.model}")
    private String openRouterModel;

    @BeforeEach
    void resetStub() {
        OPENROUTER_STUB.resetMappings();
    }

    @Test
    void should_reach_the_wiremock_stand_in_through_the_dynamic_base_url() {
        OPENROUTER_STUB.register(get("/ping")
                .willReturn(aResponse().withStatus(200).withBody("pong")));

        String body = openRouterRestClient
                .get()
                .uri("/ping")
                .retrieve()
                .body(String.class);

        assertThat(body).isEqualTo("pong");
    }

    @Test
    void should_resolve_the_model_property_with_its_online_suffix_intact() {
        assertThat(openRouterModel).isEqualTo("deepseek/deepseek-v4.1-flash:online");
    }
}
