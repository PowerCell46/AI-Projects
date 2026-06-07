package com.spotystats.backend.dtos.listening;

import lombok.Builder;
import lombok.Value;


/**
 * Listening aggregates for a toggled period (today or the rolling week).
 * {@code tracksPlayedDeltaPercent} compares against the equally long window
 * before the period and is null when there is nothing to compare to.
 */
@Value
@Builder
public class PeriodStatsResponse {

    long tracksPlayed;

    Integer tracksPlayedDeltaPercent;

    long listeningTimeMs;

    long uniqueArtists;

    long newArtists;

    long uniqueTracks;
}
