package com.spotystats.backend.repositories.projections;

/**
 * Per-artist listening aggregate: plays attributed to the artist as the track's
 * primary artist, within a time window.
 */
public interface ArtistShareView {

    String getArtistName();

    long getTrackCount();

    long getListeningTimeMs();
}
