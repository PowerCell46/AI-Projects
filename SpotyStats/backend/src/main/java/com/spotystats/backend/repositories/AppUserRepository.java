package com.spotystats.backend.repositories;

import com.spotystats.backend.entities.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;


public interface AppUserRepository extends JpaRepository<AppUser, String> {
}
