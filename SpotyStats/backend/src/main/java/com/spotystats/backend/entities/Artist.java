package com.spotystats.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * A Spotify artist, keyed by its Spotify id so syncing can upsert without
 * surrogate-key lookups.
 */
@Entity
@Table(name = "artist")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "spotifyId")
public class Artist {

    @Id
    @Column(name = "spotify_id")
    private String spotifyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "image_url")
    private String imageUrl;

    public Artist(String spotifyId) {
        this.spotifyId = spotifyId;
    }
}
