package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.interesttopics;

/**
 * The topic service gave no usable answer. Never read as "none of the topics exist".
 */
public class InterestTopicLookupFailedException extends RuntimeException {

    public static final String MESSAGE = "The interest topic service did not answer the existence lookup.";

    public InterestTopicLookupFailedException() {
        super(MESSAGE);
    }

    public InterestTopicLookupFailedException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
