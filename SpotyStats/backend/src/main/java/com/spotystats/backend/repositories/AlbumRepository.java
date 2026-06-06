package com.spotystats.backend.repositories;

import com.spotystats.backend.entities.Album;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AlbumRepository extends JpaRepository<Album, String> {
}
