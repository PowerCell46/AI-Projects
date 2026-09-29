package com.peter_gerdzhikov.twitter_api_gateway.exceptions.users;

public class UserNotFoundException extends RuntimeException {

    public static final String MESSAGE = "User not found.";

    public UserNotFoundException() {
        super(MESSAGE);
    }
}
