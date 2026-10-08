package com.peter_gerdzhikov.twitter_api_gateway.DTOs.client;

import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The two fields of the tweet service's create answer that the seeder needs; the rest is ignored.
 */
@Getter
@Setter
@NoArgsConstructor
public class SeededTweetClientDTO {

    private UUID id;

    private UUID authorId;
}
