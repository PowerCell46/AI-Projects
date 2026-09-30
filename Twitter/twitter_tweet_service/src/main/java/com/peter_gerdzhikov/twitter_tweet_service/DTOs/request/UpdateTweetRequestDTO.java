package com.peter_gerdzhikov.twitter_tweet_service.DTOs.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Text only: a tweet's images never change. The content rules run in the service, against the stored
 * tweet's images, so the body carries no bean-validation constraints.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateTweetRequestDTO {

    private String content;
}
