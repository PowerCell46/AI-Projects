package com.spotystats.backend.repositories;

import com.spotystats.backend.entities.Artist;
import org.springframework.data.jpa.repository.JpaRepository;


public interface ArtistRepository extends JpaRepository<Artist, String> {
}
