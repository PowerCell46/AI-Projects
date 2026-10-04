package com.peter_gerdzhikov.twitter_timeline_service.DTOs.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The gateway's answer to {@code GET /internal/v1/users/{followerId}/follows/{followeeId}}. The field is a
 * {@code Boolean} so that an answer without it can be told from {@code false}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowCheckClientDTO {

    private Boolean following;
}
