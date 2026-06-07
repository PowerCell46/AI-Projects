package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.dtos.listening.LikedPageResponse;

import java.util.List;
import java.util.Map;


public interface LikedTracksService {

    /**
     * Liked state per track id. Degrades to an empty map when Spotify rejects
     * the lookup (e.g. missing library scope) — liked state is decoration on
     * the history views, so a Spotify hiccup must not fail the whole request.
     */
    Map<String, Boolean> likedStatuses(List<String> trackIds);

    void setLiked(String trackId, boolean liked);

    LikedPageResponse likedPage(int limit, int offset);
}
