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
 * A Spotify album, keyed by its Spotify id so syncing can upsert without
 * surrogate-key lookups.
 */
@Entity
@Table(name = "album")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "spotifyId")
public class Album {

    @Id
    @Column(name = "spotify_id")
    private String spotifyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "cover_url")
    private String coverUrl;

    public Album(String spotifyId) {
        this.spotifyId = spotifyId;
    }
}
