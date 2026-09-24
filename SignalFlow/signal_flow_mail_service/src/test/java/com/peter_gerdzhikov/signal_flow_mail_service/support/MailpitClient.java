package com.peter_gerdzhikov.signal_flow_mail_service.support;

import tools.jackson.databind.JsonNode;

import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

/**
 * Minimal Mailpit HTTP API client for e2e tests only - search by recipient, fetch one message, delete
 * all. Test support, not production code: nothing outside the test suite talks to Mailpit.
 */
public class MailpitClient {

    private final RestClient restClient;

    public MailpitClient(String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public JsonNode searchByRecipient(String recipient) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/search")
                        .queryParam("query", "to:\"" + recipient + "\"")
                        .build())
                .retrieve()
                .body(JsonNode.class);
    }

    public JsonNode fetchMessage(String messageId) {
        return restClient.get()
                .uri("/api/v1/message/{messageId}", messageId)
                .retrieve()
                .body(JsonNode.class);
    }

    public void deleteAll() {
        restClient.method(HttpMethod.DELETE)
                .uri("/api/v1/messages")
                .retrieve()
                .toBodilessEntity();
    }
}
