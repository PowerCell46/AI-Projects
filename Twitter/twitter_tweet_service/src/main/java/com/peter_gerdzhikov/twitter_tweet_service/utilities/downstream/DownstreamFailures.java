package com.peter_gerdzhikov.twitter_tweet_service.utilities.downstream;

import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamTimeoutException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamUnavailableException;

public final class DownstreamFailures {

    private DownstreamFailures() {
    }

    /**
     * Only a read timeout is a timeout: a connect timeout is an unreachable downstream like a refused
     * connection. Every other failure, an error status or an unreadable answer included, is "unavailable".
     */
    public static RuntimeException translate(RestClientException failure) {
        if (isReadTimeout(failure)) {
            return new UpstreamTimeoutException(failure);
        }

        return new UpstreamUnavailableException(failure);
    }

    private static boolean isReadTimeout(RestClientException failure) {
        Throwable cause = failure.getCause();

        return failure instanceof ResourceAccessException
                && cause instanceof HttpTimeoutException
                && !(cause instanceof HttpConnectTimeoutException);
    }
}
