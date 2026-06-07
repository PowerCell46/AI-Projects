package com.spotystats.backend.dtos.listening;

import lombok.Builder;
import lombok.Value;


/**
 * One track from the user's Spotify "Liked Songs" library.
 */
@Value
@Builder
public class LikedTrackResponse {

    String trackId;

    String title;

    String artist;

    String album;

    String albumArtUrl;

    String addedAt;

    int durationMs;
}
