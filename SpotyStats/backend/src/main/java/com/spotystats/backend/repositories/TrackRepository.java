package com.spotystats.backend.repositories;

import com.spotystats.backend.entities.Track;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TrackRepository extends JpaRepository<Track, String> {
}
