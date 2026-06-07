package com.spotystats.backend.repositories.projections;

/**
 * Per-track play aggregate for the most-played list.
 */
public interface TopTrackView {

    String getTrackId();

    String getTitle();

    String getArtistName();

    String getAlbumArtUrl();

    long getPlayCount();

    long getListeningTimeMs();
}
