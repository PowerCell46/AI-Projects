package com.peter_gerdzhikov.twitter_api_gateway.support;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Hands out a user that no other call has, so tests never share data or depend on run order. The username
 * stays inside the 15-character limit for any realistic number of users.
 */
public final class TestUsers {

    public static final String VALID_PASSWORD = "Passw0rdOk1";

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestUsers() {
    }

    public static TestUser unique() {
        long id = SEQUENCE.incrementAndGet();

        return new TestUser("user_" + id, "user_" + id + "@example.test", VALID_PASSWORD);
    }
}
