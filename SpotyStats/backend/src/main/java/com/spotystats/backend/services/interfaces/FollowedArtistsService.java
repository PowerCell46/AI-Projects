package com.spotystats.backend.services.interfaces;

import java.util.List;
import java.util.Map;


public interface FollowedArtistsService {

    /**
     * Follow state per artist id. Degrades to an empty map when Spotify rejects
     * the lookup (e.g. missing follow scope) — follow state is decoration on
     * rankings, so a Spotify hiccup must not fail the whole request.
     */
    Map<String, Boolean> followedStatuses(List<String> artistIds);

    void setFollowed(String artistId, boolean followed);
}
