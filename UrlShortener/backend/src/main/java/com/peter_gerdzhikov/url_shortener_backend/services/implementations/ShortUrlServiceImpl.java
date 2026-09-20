package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.url_shortener_backend.entities.ShortUrl;
import com.peter_gerdzhikov.url_shortener_backend.repositories.ShortUrlRepository;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortCodeGenerator;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlCache;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortUrlServiceImpl implements ShortUrlService {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortCodeGenerator shortCodeGenerator;
    private final ShortUrlCache shortUrlCache;

    @Override
    public ShortUrl createOrGetShortUrl(String originalUrl) {
        Optional<ShortUrl> existingShortUrl = shortUrlRepository.findById(originalUrl);
        if (existingShortUrl.isPresent()) {
            ShortUrl shortUrl = existingShortUrl.get();
            log.info("Short URL already exists for code '{}'.", shortUrl.getCode());
            return shortUrl;
        }

        return insertNewShortUrl(originalUrl);
    }

    @Override
    public Optional<String> resolveOriginalUrl(String code) {
        Optional<String> cachedUrl = shortUrlCache.get(code);
        if (cachedUrl.isPresent()) {
            return cachedUrl;
        }

        return shortUrlRepository
                .findByCode(code)
                .map(ShortUrl::getId)
                .map(originalUrl -> warmCache(code, originalUrl));
    }

    private ShortUrl insertNewShortUrl(String originalUrl) {
        ShortUrl shortUrl = new ShortUrl(originalUrl, shortCodeGenerator.generate());

        try {
            ShortUrl savedShortUrl = shortUrlRepository.save(shortUrl);
            log.info("Created new short URL with code '{}'.", savedShortUrl.getCode());
            return savedShortUrl;

        } catch (DuplicateKeyException e) {
            log.warn("Concurrent create for '{}' lost the insert race; returning the winner's code.", originalUrl);
            return shortUrlRepository.findById(originalUrl).orElseThrow(() -> e);
        }
    }

    private String warmCache(String code, String originalUrl) {
        shortUrlCache.put(code, originalUrl);
        return originalUrl;
    }
}
