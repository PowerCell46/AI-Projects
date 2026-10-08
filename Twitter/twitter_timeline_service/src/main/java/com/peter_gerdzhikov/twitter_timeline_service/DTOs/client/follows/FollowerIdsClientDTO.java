package com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.follows;

import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One page of the gateway's {@code GET /internal/v1/users/{id}/follower-ids}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowerIdsClientDTO {

    private List<UUID> ids;

    private String nextCursor;
}
