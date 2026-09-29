package com.peter_gerdzhikov.twitter_api_gateway.support;

import java.util.UUID;

import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;

/**
 * Builds entities for repository tests, each with credentials no other call has handed out.
 */
public final class TestEntities {

    private TestEntities() {
    }

    public static User newUser() {
        TestUser credentials = TestUsers.unique();

        return User.builder()
                .username(credentials.getUsername())
                .email(credentials.getEmail())
                .password("hashed-password")
                .build();
    }

    public static DbFile newDbFile() {
        return DbFile.builder()
                .objectKey(UUID.randomUUID().toString())
                .contentType("image/png")
                .sizeBytes(1024)
                .build();
    }
}
