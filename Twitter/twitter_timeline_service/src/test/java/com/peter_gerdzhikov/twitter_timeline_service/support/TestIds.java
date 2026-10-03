package com.peter_gerdzhikov.twitter_timeline_service.support;

import java.util.UUID;

/**
 * Hands out ids that no other call has, so tests never share data or depend on run order.
 */
public final class TestIds {

    private TestIds() {
    }

    public static UUID userId() {
        return UUID.randomUUID();
    }

    public static UUID tweetId() {
        return UUID.randomUUID();
    }
}
