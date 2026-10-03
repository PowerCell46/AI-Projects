package com.peter_gerdzhikov.twitter_timeline_service.utilities.paging;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TimelineCursor {

    private final UUID tweetId;

    private final Instant timestamp;
}
