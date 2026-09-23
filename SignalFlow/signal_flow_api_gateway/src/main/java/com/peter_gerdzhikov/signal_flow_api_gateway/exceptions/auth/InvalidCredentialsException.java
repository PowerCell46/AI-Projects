package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.auth;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
