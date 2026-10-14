package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.users;

public class UserNotDeletableException extends RuntimeException {

    public UserNotDeletableException() {
        super("Admin accounts cannot be deleted.");
    }
}
