package com.peter_gerdzhikov.twitter_timeline_service.utilities.views;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.views.InvalidTweetIdsException;

/**
 * The tweet-id lists the view endpoints accept: at least one id, at most a limit, none of them {@code null}.
 * The limit counts the list as sent, before duplicates collapse.
 */
public final class TweetIdsValidator {

    private TweetIdsValidator() {
    }

    public static void validate(List<UUID> tweetIds, int maxSize) {
        if (tweetIds == null || tweetIds.isEmpty() || tweetIds.size() > maxSize || tweetIds.stream().anyMatch(Objects::isNull)) {
            throw new InvalidTweetIdsException(maxSize);
        }
    }
}
