package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.interesttopics;

/**
 * The topic service gave no usable feed page - unreachable, timed out, non-2xx or an unreadable body.
 */
public class InterestTopicFeedUnavailableException extends RuntimeException {

    public static final String MESSAGE = "The interest topic service did not answer the feed request.";

    public InterestTopicFeedUnavailableException() {
        super(MESSAGE);
    }

    public InterestTopicFeedUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
