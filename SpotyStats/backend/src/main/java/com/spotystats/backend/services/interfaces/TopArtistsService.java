package com.spotystats.backend.services.interfaces;

import com.spotystats.backend.dtos.listening.ArtistRankingResponse;


public interface TopArtistsService {

    ArtistRankingResponse longTermTopArtists();
}
