package com.spotystats.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * One credited artist on a track, ordered by {@code position}. Powers the full
 * "artist(s)" list on history cards; charts use only the track's primary artist.
 */
@Entity
@Table(name = "track_artist")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TrackArtist {

    @EmbeddedId
    private TrackArtistId id;

    @MapsId("trackId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @MapsId("artistId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id")
    private Artist artist;

    @Column(nullable = false)
    private Integer position;

    public TrackArtist(Track track, Artist artist) {
        this.id = new TrackArtistId(track.getSpotifyId(), artist.getSpotifyId());
        this.track = track;
        this.artist = artist;
    }
}
