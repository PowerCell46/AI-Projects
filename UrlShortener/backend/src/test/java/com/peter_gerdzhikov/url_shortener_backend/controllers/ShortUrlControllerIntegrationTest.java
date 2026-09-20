package com.peter_gerdzhikov.url_shortener_backend.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.url_shortener_backend.DTOs.request.CreateShortUrlRequestDTO;
import com.peter_gerdzhikov.url_shortener_backend.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.url_shortener_backend.DTOs.response.ShortUrlResponseDTO;
import com.peter_gerdzhikov.url_shortener_backend.repositories.ShortUrlRepository;
import com.peter_gerdzhikov.url_shortener_backend.support.AbstractMongoIntegrationTest;

@SpringBootTest
@AutoConfigureRestTestClient
class ShortUrlControllerIntegrationTest extends AbstractMongoIntegrationTest {

    private static final String ORIGINAL_URL = "https://example.com/some/page";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private ShortUrlRepository shortUrlRepository;

    @BeforeEach
    void clearCollection() {
        shortUrlRepository.deleteAll();
    }

    @Nested
    class CreateShortUrl {

        @Test
        void should_create_a_new_short_url_and_return_it() {
            ShortUrlResponseDTO body = createShortUrl(ORIGINAL_URL)
                    .expectStatus().isOk()
                    .expectBody(ShortUrlResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getShortUrl()).isNotBlank();
        }

        @Test
        void should_return_the_same_code_when_the_same_url_is_posted_twice() {
            String firstShortUrl = createShortUrl(ORIGINAL_URL)
                    .expectBody(ShortUrlResponseDTO.class)
                    .returnResult()
                    .getResponseBody()
                    .getShortUrl();
            String secondShortUrl = createShortUrl(ORIGINAL_URL)
                    .expectBody(ShortUrlResponseDTO.class)
                    .returnResult()
                    .getResponseBody()
                    .getShortUrl();

            assertThat(secondShortUrl).isEqualTo(firstShortUrl);
        }

        @Test
        void should_reject_a_url_over_the_1000_byte_cap() {
            String tooLongUrl = "https://example.com/" + "a".repeat(1000);

            postUrl(tooLongUrl).expectStatus().isBadRequest();
        }

        @Test
        void should_reject_a_non_http_scheme() {
            postUrl("ftp://example.com/file").expectStatus().isBadRequest();
        }

        @Test
        void should_reject_an_internal_host() {
            postUrl("http://169.254.169.254/latest/meta-data").expectStatus().isBadRequest();
        }

        @Test
        void should_reject_a_blank_url() {
            postUrl("   ").expectStatus().isBadRequest();
        }

        @Test
        void should_return_an_error_body_for_an_invalid_url() {
            ErrorResponseDTO body = postUrl("ftp://example.com/file")
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getStatus()).isEqualTo(400);
            assertThat(body.getMessages()).hasSize(1);
            assertThat(body.getMessages().getFirst()).contains("url");
            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
        }

        @Test
        void should_return_400_for_a_malformed_request_body() {
            ErrorResponseDTO body = restTestClient.post()
                    .uri("/api/v1/urls")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ not valid json")
                    .exchange()
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getStatus()).isEqualTo(400);
            assertThat(body.getMessages()).hasSize(1);
            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
        }
    }

    private RestTestClient.ResponseSpec createShortUrl(String url) {
        return postUrl(url).expectStatus().isOk();
    }

    private RestTestClient.ResponseSpec postUrl(String url) {
        return restTestClient.post()
                .uri("/api/v1/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateShortUrlRequestDTO(url))
                .exchange();
    }
}
