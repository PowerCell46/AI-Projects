package com.peter_gerdzhikov.twitter_timeline_service.DTOs.request;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The tweets the browser reports as seen. The list is checked by {@code TweetIdsValidator}, so a missing field
 * and a bad size get the same answer.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportViewsRequestDTO {

    private List<UUID> tweetIds;
}
