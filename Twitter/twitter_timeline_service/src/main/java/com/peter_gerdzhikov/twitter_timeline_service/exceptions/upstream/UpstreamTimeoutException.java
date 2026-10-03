package com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream;

/**
 * A downstream service accepted the connection but did not answer within the read timeout.
 */
public class UpstreamTimeoutException extends RuntimeException {

    public static final String MESSAGE = "Upstream service timed out.";

    public UpstreamTimeoutException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
