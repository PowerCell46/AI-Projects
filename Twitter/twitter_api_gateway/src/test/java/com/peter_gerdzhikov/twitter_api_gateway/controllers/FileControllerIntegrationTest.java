package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ObjectStorageService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.CookieFactory;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class FileControllerIntegrationTest extends AbstractMinioIntegrationTest {

    private static final byte[] CONTENT = {(byte) 0x89, 0x50, 0x4E, 0x47, 1, 2, 3, 4, (byte) 0xFF, 0};

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DbFileRepository dbFileRepository;

    @Autowired
    private ObjectStorageService objectStorageService;

    @Nested
    class GetFile {

        @Test
        void should_return_200_with_the_exact_bytes_and_stored_content_type_when_the_file_exists() {
            DbFile file = storedFile("image/png");

            byte[] body = getFile(file.getId().toString(), loggedInCookie())
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .contentType(MediaType.IMAGE_PNG)
                    .expectHeader()
                    .valueEquals(HttpHeaders.CONTENT_LENGTH, String.valueOf(CONTENT.length))
                    .expectBody(byte[].class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body).isEqualTo(CONTENT);
        }

        @Test
        void should_return_the_private_immutable_cache_and_nosniff_headers_when_the_file_exists() {
            DbFile file = storedFile("image/png");

            HttpHeaders headers = getFile(file.getId().toString(), loggedInCookie())
                    .expectStatus()
                    .isOk()
                    .returnResult(byte[].class)
                    .getResponseHeaders();

            assertThat(headers.getFirst(HttpHeaders.CACHE_CONTROL))
                    .contains("private", "max-age=31536000", "immutable")
                    .doesNotContain("no-cache", "no-store");
            assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        }

        @Test
        void should_return_200_when_the_file_belongs_to_another_user() {
            User owner = confirmedUser();
            DbFile file = storedFile("image/png");
            owner.setProfilePicture(file);
            userRepository.save(owner);

            getFile(file.getId().toString(), loggedInCookie())
                    .expectStatus()
                    .isOk();
        }

        @Test
        void should_return_404_when_the_id_is_unknown() {
            getFile(UUID.randomUUID().toString(), loggedInCookie())
                    .expectStatus()
                    .isNotFound();
        }

        @Test
        void should_return_400_when_the_id_is_not_a_uuid() {
            getFile("not-a-uuid", loggedInCookie())
                    .expectStatus()
                    .isBadRequest();
        }

        @Test
        void should_return_401_when_there_is_no_cookie() {
            DbFile file = storedFile("image/png");

            getFile(file.getId().toString(), null)
                    .expectStatus()
                    .isUnauthorized();
        }

        private RestTestClient.ResponseSpec getFile(String id, String cookie) {
            RestTestClient.RequestHeadersSpec<?> spec = restTestClient
                    .get()
                    .uri("/api/v1/files/{id}", id);
            if (cookie != null) {
                spec.cookie(CookieFactory.COOKIE_NAME, cookie);
            }

            return spec.exchange();
        }
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }

    private String loggedInCookie() {
        return tokenService.mint(confirmedUser());
    }

    private DbFile storedFile(String contentType) {
        DbFile file = TestEntities.newDbFile();
        file.setContentType(contentType);
        file.setSizeBytes(CONTENT.length);
        objectStorageService.put(file.getObjectKey(), new ByteArrayInputStream(CONTENT), CONTENT.length, contentType);

        return dbFileRepository.save(file);
    }
}
