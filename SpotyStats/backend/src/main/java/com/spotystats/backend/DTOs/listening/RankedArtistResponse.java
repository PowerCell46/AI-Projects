package com.spotystats.backend.dtos.listening;

import lombok.Builder;
import lombok.Value;


/**
 * One artist's row in the ranking, attributed by primary artist. The metrics
 * are null when the ranking comes from Spotify's long-term top artists, which
 * only shares the order — not play counts or listening time.
 */
@Value
@Builder
public class RankedArtistResponse {

    String artistId;

    String artistName;

    String imageUrl;

    Long playCount;

    Long listeningTimeMs;

    Long uniqueTracks;

    boolean followed;
}
