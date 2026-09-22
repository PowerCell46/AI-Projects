package com.peter_gerdzhikov.url_shortener_backend.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.url_shortener_backend.DTOs.request.CreateShortUrlRequestDTO;
import com.peter_gerdzhikov.url_shortener_backend.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.url_shortener_backend.DTOs.response.ShortUrlResponseDTO;
import com.peter_gerdzhikov.url_shortener_backend.repositories.ShortUrlRepository;
import com.peter_gerdzhikov.url_shortener_backend.services.interfaces.ShortUrlCache;
import com.peter_gerdzhikov.url_shortener_backend.support.AbstractMongoAndRedisIntegrationTest;

@SpringBootTest
@AutoConfigureRestTestClient
class RedirectControllerIntegrationTest extends AbstractMongoAndRedisIntegrationTest {

    private static final String ORIGINAL_URL = "https://example.com/some/page";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @Autowired
    private ShortUrlCache shortUrlCache;

    private String code;

    @BeforeEach
    void createAShortUrl() {
        shortUrlRepository.deleteAll();
        code = createShortUrl(ORIGINAL_URL);
    }

    @Test
    void should_redirect_to_the_original_url_and_warm_the_cache() {
        restTestClient.get()
                .uri("/{code}", code)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().location(ORIGINAL_URL);

        assertThat(shortUrlCache.get(code)).contains(ORIGINAL_URL);
    }

    @Test
    void should_return_404_with_an_error_body_for_an_unknown_code() {
        ErrorResponseDTO body = restTestClient.get()
                .uri("/{code}", "nOpE42")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getMessages()).hasSize(1);
        assertThat(body.getMessages().getFirst()).doesNotContain("nOpE42", "Exception", "com.peter_gerdzhikov");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.env", "/wp-config.php", "/backup.sql", "/docker-compose.yml"})
    void should_return_404_for_a_scanner_probe_path_without_a_database_lookup(String path) {
        ErrorResponseDTO body = restTestClient.get()
                .uri(path)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getMessages()).hasSize(1);
        assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/.git/HEAD", "/.ssh/id_rsa", "/actuator/heapdump", "/storage/logs/laravel.log"})
    void should_return_404_for_a_multi_segment_scanner_probe_path(String path) {
        restTestClient.get()
                .uri(path)
                .exchange()
                .expectStatus().isNotFound();
    }

    private String createShortUrl(String url) {
        ShortUrlResponseDTO body = restTestClient.post()
                .uri("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateShortUrlRequestDTO(url))
                .exchange()
                .expectStatus().isOk()
                .expectBody(ShortUrlResponseDTO.class)
                .returnResult()
                .getResponseBody();

        return body.getShortUrl().substring(body.getShortUrl().lastIndexOf('/') + 1);
    }
}
