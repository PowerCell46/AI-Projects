package com.spotystats.backend.DTOs.listening;

import lombok.Builder;
import lombok.Value;


/**
 * Rolling 7-day listening aggregates. {@code tracksPlayedDeltaPercent} compares
 * against the 7 days before that and is null when there is nothing to compare to.
 */
@Value
@Builder
public class WeekStatsResponse {

    long tracksPlayed;

    Integer tracksPlayedDeltaPercent;

    long listeningTimeMs;

    long uniqueArtists;

    long newArtists;

    long uniqueTracks;
}
