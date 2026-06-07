package com.spotystats.backend.dtos.listening;

import lombok.Value;

import java.time.Instant;
import java.util.List;


/**
 * The artist ranking plus how far back the underlying data reaches. Spotify
 * only exposes the most recent plays, so {@code trackedSince} marks the
 * earliest play we ever captured — periods reaching further back than that
 * are necessarily incomplete.
 */
@Value
public class ArtistRankingResponse {

    Instant trackedSince;

    List<RankedArtistResponse> artists;
}
