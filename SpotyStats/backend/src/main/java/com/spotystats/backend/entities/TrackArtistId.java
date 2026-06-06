package com.spotystats.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * Composite key of {@link TrackArtist}: one row per (track, artist) credit.
 */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TrackArtistId implements Serializable {

    @Column(name = "track_id")
    private String trackId;

    @Column(name = "artist_id")
    private String artistId;
}
