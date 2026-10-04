package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

/**
 * Runs against a real servlet container: MockMvc never applies the multipart size limits or the body-size
 * filter to a parsed upload, so only a real HTTP request proves they are wired to the picture routes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class PictureUploadLimitsIntegrationTest extends AbstractMinioIntegrationTest {

    private static final String BOUNDARY = "picture-upload-boundary";

    private static final int LARGER_THAN_THE_NORMAL_BODY_CAP = 100 * 1024;

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Value("${app.upload.max-file-bytes}")
    private int maxFileBytes;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Nested
    class PutPicture {

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_200_when_the_file_is_far_over_the_normal_body_cap_but_within_the_upload_cap(String slot) {
            User user = confirmedUser();

            ProfileResponseDTO body = put(slot, pngOfSize(LARGER_THAN_THE_NORMAL_BODY_CAP), user)
                    .expectStatus()
                    .isOk()
                    .expectBody(ProfileResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat("profile-picture".equals(slot) ? body.getProfilePictureUrl() : body.getCoverPictureUrl())
                    .startsWith("/api/v1/files/");
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_413_with_the_apps_error_shape_when_the_file_is_one_byte_over_the_file_limit(String slot) {
            User user = confirmedUser();

            ErrorResponseDTO body = put(slot, pngOfSize(maxFileBytes + 1), user)
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("The uploaded file is too large.");
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_200_when_the_file_is_exactly_the_file_limit(String slot) {
            User user = confirmedUser();

            put(slot, pngOfSize(maxFileBytes), user)
                    .expectStatus()
                    .isOk();
        }
    }

    private RestTestClient.ResponseSpec put(String slot, byte[] file, User user) {
        return restTestClient
                .put()
                .uri("/api/v1/users/me/" + slot)
                .cookie(CookieFactory.COOKIE_NAME, tokenService.mint(user))
                .contentType(MediaType.parseMediaType("multipart/form-data; boundary=" + BOUNDARY))
                .body(multipartBody(file))
                .exchange();
    }

    /**
     * Built by hand so the request carries a {@code Content-Length}; a streamed multipart body goes out
     * chunked, and the server answering 413 mid-upload then resets the connection under the client.
     */
    private byte[] multipartBody(byte[] file) {
        byte[] head = ("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] tail = ("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[head.length + file.length + tail.length];
        System.arraycopy(head, 0, body, 0, head.length);
        System.arraycopy(file, 0, body, head.length, file.length);
        System.arraycopy(tail, 0, body, head.length + file.length, tail.length);

        return body;
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }

    private byte[] pngOfSize(int size) {
        return Arrays.copyOf(PNG_SIGNATURE, size);
    }
}
