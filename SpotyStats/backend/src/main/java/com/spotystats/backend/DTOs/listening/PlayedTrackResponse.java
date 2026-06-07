package com.spotystats.backend.dtos.listening;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;


/**
 * One row on the history panel. {@code id} is the play's database id (unique per
 * listen, safe as a React key); {@code trackId} is the Spotify track id used to
 * like/unlike the track.
 */
@Value
@Builder
public class PlayedTrackResponse {

    String id;

    String trackId;

    String title;

    String artist;

    String album;

    String albumArtUrl;

    Instant playedAt;

    int durationMs;

    boolean liked;
}
