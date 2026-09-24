package com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.interesttopics;

public class DuplicateInterestTopicNameException extends RuntimeException {

    public static final String MESSAGE = "This interest topic name is already in use.";

    public DuplicateInterestTopicNameException() {
        super(MESSAGE);
    }
}
