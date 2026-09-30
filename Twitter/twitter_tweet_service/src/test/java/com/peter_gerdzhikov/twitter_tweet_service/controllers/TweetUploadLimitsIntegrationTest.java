package com.peter_gerdzhikov.twitter_tweet_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;

/**
 * Runs against a real servlet container: MockMvc never applies Spring's multipart size limits, and a raw
 * body it sends is never parsed as multipart, so only a real HTTP request proves the limits are wired to
 * the create route.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class TweetUploadLimitsIntegrationTest extends AbstractMinioIntegrationTest {

    private static final String BOUNDARY = "tweet-upload-boundary";

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Value("${app.upload.max-file-bytes}")
    private int maxFileBytes;

    @Autowired
    private RestTestClient restTestClient;

    @Nested
    class CreateTweet {

        @Test
        void should_return_201_when_four_images_are_each_exactly_the_file_limit() {
            byte[] image = pngOfSize(maxFileBytes);

            post(multipartBody(image, image, image, image))
                    .expectStatus()
                    .isCreated();
        }

        @Test
        void should_return_413_with_the_apps_error_shape_when_an_image_is_one_byte_over_the_file_limit() {
            ErrorResponseDTO body = post(multipartBody(pngOfSize(maxFileBytes + 1)))
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("The uploaded file is too large.");
        }
    }

    private RestTestClient.ResponseSpec post(byte[] body) {
        return restTestClient
                .post()
                .uri("/api/v1/tweets")
                .header("X-User-Id", UUID.randomUUID().toString())
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY))
                .body(body)
                .exchange();
    }

    /**
     * Built by hand so the request carries a {@code Content-Length}; a streamed multipart body goes out
     * chunked, and the server answering 413 mid-upload then resets the connection under the client.
     */
    private byte[] multipartBody(byte[]... images) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(ascii("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"content\"\r\n\r\nlimits\r\n"));
        for (byte[] image : images) {
            body.writeBytes(ascii("--" + BOUNDARY + "\r\n"
                    + "Content-Disposition: form-data; name=\"images\"; filename=\"photo.png\"\r\n"
                    + "Content-Type: image/png\r\n\r\n"));
            body.writeBytes(image);
            body.writeBytes(ascii("\r\n"));
        }
        body.writeBytes(ascii("--" + BOUNDARY + "--\r\n"));

        return body.toByteArray();
    }

    private byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private byte[] pngOfSize(int size) {
        return Arrays.copyOf(PNG_SIGNATURE, size);
    }
}
