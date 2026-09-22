package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions;

public class DuplicateSubscriptionException extends RuntimeException {

    public static final String MESSAGE = "You are already subscribed to this interest topic.";

    public DuplicateSubscriptionException() {
        super(MESSAGE);
    }
}
