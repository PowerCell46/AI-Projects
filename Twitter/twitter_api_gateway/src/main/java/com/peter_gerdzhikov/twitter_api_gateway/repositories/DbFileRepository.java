package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;

public interface DbFileRepository extends JpaRepository<DbFile, UUID> {
}
