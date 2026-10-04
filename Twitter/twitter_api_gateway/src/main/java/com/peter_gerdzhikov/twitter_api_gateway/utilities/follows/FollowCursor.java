package com.peter_gerdzhikov.twitter_api_gateway.utilities.follows;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FollowCursor {

    private final UUID id;

    private final Instant createdAt;
}
