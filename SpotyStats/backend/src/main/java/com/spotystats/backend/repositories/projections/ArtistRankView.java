package com.spotystats.backend.repositories.projections;

/**
 * Per-artist ranking aggregate: plays attributed to the artist as the track's
 * primary artist, within a time window.
 */
public interface ArtistRankView {

    String getArtistId();

    String getArtistName();

    long getPlayCount();

    long getListeningTimeMs();

    long getUniqueTracks();
}
