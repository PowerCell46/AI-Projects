package com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream;

/**
 * A downstream service could not be reached, answered with an error, or sent something unreadable. The message
 * is fixed, so nothing the downstream said can reach a client.
 */
public class UpstreamUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Upstream service unavailable.";

    public UpstreamUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
