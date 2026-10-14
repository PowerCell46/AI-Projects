package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.users;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("No account has this email.");
    }
}
