package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.news;

import org.springframework.http.HttpStatusCode;

import lombok.Getter;

@Getter
public class NewsGenerationFailedException extends RuntimeException {

    private final HttpStatusCode status;

    public NewsGenerationFailedException(String message) {
        super(message);
        this.status = null;
    }

    public NewsGenerationFailedException(String message, Throwable cause) {
        super(message, cause);
        this.status = null;
    }

    public NewsGenerationFailedException(HttpStatusCode status, String providerMessage) {
        super("OpenRouter request failed with status %s: %s".formatted(status, providerMessage));
        this.status = status;
    }
}
