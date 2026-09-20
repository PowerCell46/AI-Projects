package com.peter_gerdzhikov.url_shortener_backend.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.peter_gerdzhikov.url_shortener_backend.entities.ShortUrl;

@Repository
public interface ShortUrlRepository extends MongoRepository<ShortUrl, String> {

    Optional<ShortUrl> findByCode(String code);
}
