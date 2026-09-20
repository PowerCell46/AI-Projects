package com.peter_gerdzhikov.url_shortener_backend.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import com.peter_gerdzhikov.url_shortener_backend.configurations.MongoConfiguration;
import com.peter_gerdzhikov.url_shortener_backend.entities.ShortUrl;
import com.peter_gerdzhikov.url_shortener_backend.support.AbstractMongoIntegrationTest;

@DataMongoTest
@Import(MongoConfiguration.class)
class ShortUrlRepositoryTest extends AbstractMongoIntegrationTest {

    private static final int MAX_URL_BYTES = 1000;
    private static final String ORIGINAL_URL = "https://example.com/some/page?q=1";
    private static final String CODE = "aB3xY9";

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @BeforeEach
    void clearCollection() {
        shortUrlRepository.deleteAll();
    }

    @Nested
    class Save {

        @Test
        void should_populate_audit_timestamps_when_saved() {
            ShortUrl saved = shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, CODE));

            assertThat(saved.getCreatedAt()).isNotNull();
            assertThat(saved.getUpdatedAt()).isNotNull();
        }

        @Test
        void should_store_the_original_url_as_the_id() {
            ShortUrl saved = shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, CODE));

            assertThat(saved.getId()).isEqualTo(ORIGINAL_URL);
            assertThat(saved.getCode()).isEqualTo(CODE);
        }

        @Test
        void should_save_url_at_the_1000_byte_cap() {
            String maximumLengthUrl = urlOfExactByteLength(MAX_URL_BYTES);

            shortUrlRepository.save(new ShortUrl(maximumLengthUrl, CODE));

            assertThat(shortUrlRepository.findById(maximumLengthUrl)).isPresent();
        }

        @Test
        void should_reject_a_second_document_with_the_same_code() {
            shortUrlRepository.save(new ShortUrl("https://first.example.com", CODE));

            assertThatThrownBy(() -> shortUrlRepository.save(new ShortUrl("https://second.example.com", CODE)))
                    .isInstanceOf(DuplicateKeyException.class);
        }

        @Test
        void should_reject_a_second_document_for_the_same_url() {
            shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, CODE));

            assertThatThrownBy(() -> shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, "zZ9aA0")))
                    .isInstanceOf(DuplicateKeyException.class);

            assertThat(shortUrlRepository.count()).isEqualTo(1);
        }

        @Test
        void should_mark_a_freshly_saved_document_as_version_zero() {
            ShortUrl saved = shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, CODE));

            assertThat(saved.getVersion()).isZero();
        }
    }

    @Nested
    class FindById {

        @BeforeEach
        void saveShortUrl() {
            shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, CODE));
        }

        @Test
        void should_find_the_document_when_the_original_url_is_known() {
            Optional<ShortUrl> found = shortUrlRepository.findById(ORIGINAL_URL);

            assertThat(found).isPresent();
            assertThat(found.get().getCode()).isEqualTo(CODE);
        }

        @Test
        void should_return_empty_when_the_original_url_is_unknown() {
            assertThat(shortUrlRepository.findById("https://never.seen.example.com")).isEmpty();
        }
    }

    @Nested
    class FindByCode {

        @BeforeEach
        void saveShortUrl() {
            shortUrlRepository.save(new ShortUrl(ORIGINAL_URL, CODE));
        }

        @Test
        void should_find_the_document_when_the_code_is_known() {
            Optional<ShortUrl> found = shortUrlRepository.findByCode(CODE);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(ORIGINAL_URL);
        }

        @Test
        void should_return_empty_when_the_code_is_unknown() {
            assertThat(shortUrlRepository.findByCode("nOpE42")).isEmpty();
        }
    }

    private static String urlOfExactByteLength(int byteLength) {
        String prefix = "https://example.com/";
        String url = prefix + "a".repeat(byteLength - prefix.length());

        assertThat(url.getBytes(StandardCharsets.UTF_8)).hasSize(byteLength);

        return url;
    }
}
