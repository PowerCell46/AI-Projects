package com.spotystats.backend.services.interfaces;

import java.util.List;
import java.util.Map;


public interface LikedTracksService {

    Map<String, Boolean> likedStatuses(List<String> trackIds);

    void setLiked(String trackId, boolean liked);
}
