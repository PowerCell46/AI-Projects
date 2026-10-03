package com.peter_gerdzhikov.twitter_api_gateway.exceptions.users;

public class UserIdsOutOfRangeException extends RuntimeException {

    public UserIdsOutOfRangeException(int maxIds) {
        super("Provide between 1 and %d user ids.".formatted(maxIds));
    }
}
