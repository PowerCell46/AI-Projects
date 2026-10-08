package com.peter_gerdzhikov.twitter_tweet_service.utilities.paging;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KeysetCursor {

    private final UUID id;

    private final Instant createdAt;
}
