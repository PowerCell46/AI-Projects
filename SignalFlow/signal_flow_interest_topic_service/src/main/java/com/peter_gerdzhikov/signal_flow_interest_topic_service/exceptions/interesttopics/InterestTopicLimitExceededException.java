package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.interesttopics;

public class InterestTopicLimitExceededException extends RuntimeException {

    public static final String MESSAGE = "The maximum number of interest topics has been reached.";

    public InterestTopicLimitExceededException() {
        super(MESSAGE);
    }
}
