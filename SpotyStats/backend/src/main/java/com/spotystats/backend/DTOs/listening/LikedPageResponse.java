package com.spotystats.backend.dtos.listening;

import lombok.Value;

import java.util.List;


/**
 * One page of the user's Liked Songs, in Spotify's order (most recently added
 * first). {@code total} is the library-wide count for pagination.
 */
@Value
public class LikedPageResponse {

    long total;

    int limit;

    int offset;

    List<LikedTrackResponse> items;
}
