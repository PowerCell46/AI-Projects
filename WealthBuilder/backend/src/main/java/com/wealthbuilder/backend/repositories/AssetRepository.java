package com.wealthbuilder.backend.repositories;

import com.wealthbuilder.backend.entities.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface AssetRepository extends JpaRepository<Asset, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<Asset> findByNameIgnoreCase(String name);
}
