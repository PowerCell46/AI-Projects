package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.subscriptions;

public class SubscriptionNotFoundException extends RuntimeException {

    public static final String MESSAGE = "You are not subscribed to this interest topic.";

    public SubscriptionNotFoundException() {
        super(MESSAGE);
    }
}
