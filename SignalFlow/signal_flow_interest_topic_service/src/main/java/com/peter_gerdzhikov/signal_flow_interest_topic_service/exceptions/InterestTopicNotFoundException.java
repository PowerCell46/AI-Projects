package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions;

public class InterestTopicNotFoundException extends RuntimeException {

    public static final String MESSAGE = "Interest topic not found.";

    public InterestTopicNotFoundException() {
        super(MESSAGE);
    }
}
