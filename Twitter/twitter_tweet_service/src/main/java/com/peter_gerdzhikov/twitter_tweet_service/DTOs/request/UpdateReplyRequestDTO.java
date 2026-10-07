package com.peter_gerdzhikov.twitter_tweet_service.DTOs.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The content rules run in the service, so the body carries no bean-validation constraints.
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateReplyRequestDTO {

    private String content;
}
