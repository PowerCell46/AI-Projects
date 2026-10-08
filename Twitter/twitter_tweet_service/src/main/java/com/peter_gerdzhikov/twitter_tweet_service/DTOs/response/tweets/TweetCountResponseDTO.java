package com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TweetCountResponseDTO {

    private final long count;
}
