package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions;

public class SubscriptionLimitExceededException extends RuntimeException {

    public static final String MESSAGE = "You have reached the maximum number of subscriptions.";

    public SubscriptionLimitExceededException() {
        super(MESSAGE);
    }
}
