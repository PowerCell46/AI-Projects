package com.spotystats.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;


/**
 * A Spotify track, keyed by its Spotify id. Charts attribute plays to the
 * {@code primaryArtist}; {@code credits} carries the full ordered artist list
 * for display on history cards.
 */
@Entity
@Table(name = "track")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Track {

    @Id
    @Column(name = "spotify_id")
    private String spotifyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    private Integer popularity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "primary_artist_id")
    private Artist primaryArtist;

    @OneToMany(mappedBy = "track")
    @OrderBy("position")
    private List<TrackArtist> credits = new ArrayList<>();

    public Track(String spotifyId) {
        this.spotifyId = spotifyId;
    }
}
