package com.peter_gerdzhikov.url_shortener_backend.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.peter_gerdzhikov.url_shortener_backend.entities.ShortUrl;
import com.peter_gerdzhikov.url_shortener_backend.repositories.ShortUrlRepository;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortCodeGenerator;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlCache;

@ExtendWith(MockitoExtension.class)
class ShortUrlServiceImplTest {

    private static final String ORIGINAL_URL = "https://example.com/some/page";
    private static final String CODE = "aB3xY9";

    @Mock
    private ShortUrlRepository shortUrlRepository;

    @Mock
    private ShortCodeGenerator shortCodeGenerator;

    @Mock
    private ShortUrlCache shortUrlCache;

    @InjectMocks
    private ShortUrlServiceImpl shortUrlService;

    @Nested
    class CreateOrGetShortUrl {

        @Test
        void should_return_the_existing_code_when_the_url_is_already_known() {
            ShortUrl existing = new ShortUrl(ORIGINAL_URL, CODE);
            when(shortUrlRepository.findById(ORIGINAL_URL)).thenReturn(Optional.of(existing));

            ShortUrl result = shortUrlService.createOrGetShortUrl(ORIGINAL_URL);

            assertThat(result).isSameAs(existing);
            verify(shortCodeGenerator, never()).generate();
            verify(shortUrlRepository, never()).save(any());
        }

        @Test
        void should_generate_and_save_a_new_code_when_the_url_is_unknown() {
            when(shortUrlRepository.findById(ORIGINAL_URL)).thenReturn(Optional.empty());
            when(shortCodeGenerator.generate()).thenReturn(CODE);
            when(shortUrlRepository.save(any(ShortUrl.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ShortUrl result = shortUrlService.createOrGetShortUrl(ORIGINAL_URL);

            assertThat(result.getId()).isEqualTo(ORIGINAL_URL);
            assertThat(result.getCode()).isEqualTo(CODE);
        }

        @Test
        void should_return_the_winners_code_when_a_concurrent_insert_wins_the_race() {
            ShortUrl winner = new ShortUrl(ORIGINAL_URL, "zZ9aA0");
            when(shortUrlRepository.findById(ORIGINAL_URL)).thenReturn(Optional.empty(), Optional.of(winner));
            when(shortCodeGenerator.generate()).thenReturn(CODE);
            when(shortUrlRepository.save(any(ShortUrl.class))).thenThrow(new DuplicateKeyException("dup"));

            ShortUrl result = shortUrlService.createOrGetShortUrl(ORIGINAL_URL);

            assertThat(result).isSameAs(winner);
        }
    }

    @Nested
    class ResolveOriginalUrl {

        @Test
        void should_return_the_cached_url_without_querying_mongo_when_the_cache_has_it() {
            when(shortUrlCache.get(CODE)).thenReturn(Optional.of(ORIGINAL_URL));

            Optional<String> result = shortUrlService.resolveOriginalUrl(CODE);

            assertThat(result).contains(ORIGINAL_URL);
            verify(shortUrlRepository, never()).findByCode(any());
        }

        @Test
        void should_fall_back_to_mongo_and_warm_the_cache_when_the_cache_misses() {
            when(shortUrlCache.get(CODE)).thenReturn(Optional.empty());
            when(shortUrlRepository.findByCode(CODE)).thenReturn(Optional.of(new ShortUrl(ORIGINAL_URL, CODE)));

            Optional<String> result = shortUrlService.resolveOriginalUrl(CODE);

            assertThat(result).contains(ORIGINAL_URL);
            verify(shortUrlCache).put(CODE, ORIGINAL_URL);
        }

        @Test
        void should_return_empty_when_the_code_is_unknown_in_both_the_cache_and_mongo() {
            when(shortUrlCache.get(CODE)).thenReturn(Optional.empty());
            when(shortUrlRepository.findByCode(CODE)).thenReturn(Optional.empty());

            Optional<String> result = shortUrlService.resolveOriginalUrl(CODE);

            assertThat(result).isEmpty();
            verify(shortUrlCache, never()).put(any(), any());
        }
    }
}
