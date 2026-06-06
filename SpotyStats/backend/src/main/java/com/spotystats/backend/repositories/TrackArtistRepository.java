package com.spotystats.backend.repositories;

import com.spotystats.backend.entities.TrackArtist;
import com.spotystats.backend.entities.TrackArtistId;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TrackArtistRepository extends JpaRepository<TrackArtist, TrackArtistId> {
}
