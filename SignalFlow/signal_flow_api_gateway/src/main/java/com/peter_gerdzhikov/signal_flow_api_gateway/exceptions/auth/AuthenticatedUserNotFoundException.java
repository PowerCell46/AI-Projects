package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.auth;

public class AuthenticatedUserNotFoundException extends RuntimeException {

    public AuthenticatedUserNotFoundException() {
        super("Please log in again.");
    }
}
