package com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.auth;

public class DuplicateEmailException extends RuntimeException {

    public static final String MESSAGE = "An account with this email already exists.";

    public DuplicateEmailException() {
        super(MESSAGE);
    }
}
