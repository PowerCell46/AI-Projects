package com.spotystats.backend.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;


/**
 * A single listening event. De-duplicated on (userId, playedAt) — a user cannot
 * play two things at the same instant, so sync uses it as a natural key.
 */
@Entity
@Table(name = "play")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Play {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id")
    private Track track;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    public Play(String userId, Track track, Instant playedAt) {
        this.userId = userId;
        this.track = track;
        this.playedAt = playedAt;
    }
}
