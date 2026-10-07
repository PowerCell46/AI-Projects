package com.peter_gerdzhikov.twitter_tweet_service.services.implementations.lookups;

import java.util.Collection;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.peter_gerdzhikov.twitter_tweet_service.utilities.downstream.DownstreamFailures;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * What the lookups share: running a call so that every failure becomes one of two exceptions, and writing an id
 * list the way the downstream endpoints read it.
 */
@Slf4j
@RequiredArgsConstructor
abstract class DownstreamLookupSupport {

    private final String downstreamName;

    protected <T> T execute(Supplier<T> call) {
        try {
            return call.get();

        } catch (RestClientException e) {
            log.warn("A call to the {} failed: {}.", downstreamName, describe(e));
            throw DownstreamFailures.translate(e);
        }
    }

    protected String commaSeparated(Collection<UUID> ids) {
        return ids
                .stream()
                .map(UUID::toString)
                .collect(Collectors.joining(","));
    }

    /**
     * Only the status or the failure types: the answer's body and the exception messages can carry what the
     * downstream sent, which is never logged.
     */
    private String describe(RestClientException failure) {
        if (failure instanceof RestClientResponseException responseFailure) {
            return "status " + responseFailure.getStatusCode().value();
        }

        Throwable cause = failure.getCause();

        return cause == null
                ? failure.getClass().getSimpleName()
                : failure.getClass().getSimpleName() + " caused by " + cause.getClass().getSimpleName();
    }
}
