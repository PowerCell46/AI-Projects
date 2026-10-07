package com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream;

/**
 * A downstream service could not be reached, answered with an error, or sent something unreadable. The message
 * is fixed, so nothing the downstream said can reach a client.
 */
public class UpstreamUnavailableException extends RuntimeException {

    public static final String MESSAGE = "Upstream service unavailable.";

    public UpstreamUnavailableException() {
        super(MESSAGE);
    }

    public UpstreamUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
